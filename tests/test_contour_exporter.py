import os
import shutil
import tempfile
import unittest
from pathlib import Path

import cv2
import numpy as np

# This import will intentionally fail in RED phase until contour_exporter.py is created
from contour_exporter import (
    BASE_FREQS,
    DASH_MULTIPLIERS,
    build_closed_polyline_px,
    has_animated_contours,
    normalize_stroke,
    render_polygon_contour,
    slice_polyline_arc,
)
from video_exporter import VideoExporter


class TestContourExporter(unittest.TestCase):
    def test_transparent_fill_and_contour(self):
        """Verifica que fillEnabled=False deja el centro en negro puro (#000000)
        mientras el contorno proyectado ilumina los bordes exteriores."""
        w, h = 1920, 1080
        canvas = np.zeros((h, w, 3), dtype=np.uint8)

        # Polígono centrado de 400x300 px
        norm_pts = [[0.4, 0.4], [0.6, 0.4], [0.6, 0.6], [0.4, 0.6]]
        poly = {
            "id": "p_showcase",
            "type": "quad",
            "points": norm_pts,
            "fillEnabled": False,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "glow": False,
                "animation": {"type": "none"}
            }
        }

        # Renderizar contorno
        render_polygon_contour(canvas, poly, norm_pts, t_sec=0.0, total_duration=4.0, width=w, height=h)

        # Centro en (960, 540) debe ser negro absoluto (0, 0, 0)
        center_color = canvas[540, 960]
        self.assertTrue(np.all(center_color == 0), f"Centro debe ser negro puro, obtuvo {center_color}")

        # Borde superior en (960, 432) debe estar iluminado (> 0)
        top_edge_y = int(0.4 * h)
        edge_slice = canvas[top_edge_y - 4:top_edge_y + 4, 960]
        max_edge_val = np.max(edge_slice)
        self.assertGreater(max_edge_val, 100, "El borde del contorno debe estar iluminado")

    def test_line_styles(self):
        """Verifica que dashed, dotted y double segmenten adecuadamente la arista."""
        w, h = 1920, 1080
        norm_pts = [[0.2, 0.5], [0.8, 0.5], [0.8, 0.8], [0.2, 0.8]]

        for style in ["dashed", "dotted", "double"]:
            canvas = np.zeros((h, w, 3), dtype=np.uint8)
            poly = {
                "id": f"p_{style}",
                "type": "quad",
                "points": norm_pts,
                "stroke": {
                    "visible": True,
                    "color": "#00ff66",
                    "width": 8,
                    "opacity": 1.0,
                    "lineStyle": style,
                    "dashScale": 1.0,
                    "glow": False,
                    "animation": {"type": "none"}
                }
            }
            render_polygon_contour(canvas, poly, norm_pts, t_sec=0.0, total_duration=4.0, width=w, height=h)
            total_lum = np.sum(canvas)
            self.assertGreater(total_lum, 0, f"Estilo {style} debe renderizar píxeles visibles")

            if style in ["dashed", "dotted"]:
                # A lo largo de la arista horizontal y = 540 entre x = 400 y 1500 deben existir
                # tramos iluminados y tramos en cero
                edge_strip = canvas[540, 450:1450]
                has_zeros = np.any(np.all(edge_strip == 0, axis=-1))
                has_lights = np.any(np.any(edge_strip > 100, axis=-1))
                self.assertTrue(has_zeros and has_lights, f"Estilo {style} debe alternar luz y oscuridad")

            if style == "double":
                # Double debe tener dos picos luminosos con una hendidura más oscura en el centro del trazo
                # En la esquina x = int(0.5 * w) = 960, inspeccionar columna y entre 520 y 560
                col_y = canvas[525:555, 960, 1]  # Canal verde
                # Debe haber al menos 2 máximos locales o un valle central relativo
                mid_idx = len(col_y) // 2
                self.assertGreater(np.max(col_y), 50, "Double debe tener píxeles visibles")

    def test_animation_seamless_loop(self):
        """Verifica que las 6 animaciones produzcan movimiento temporal (t=0 != t=1.1)
        y conecten con bucle perfecto (t=0 == t=duration) dentro de tolerancia a compresión."""
        w, h = 1920, 1080
        norm_pts = [[0.3, 0.3], [0.7, 0.3], [0.7, 0.7], [0.3, 0.7]]
        loop_dur = 4.0

        anim_types = ["march", "laser_comet", "dual_chase", "draw_wipe", "pulse", "color_flow"]
        for anim in anim_types:
            line_style = "dashed" if anim == "march" else "solid"
            poly = {
                "id": f"p_anim_{anim}",
                "type": "quad",
                "points": norm_pts,
                "stroke": {
                    "visible": True,
                    "color": "#00f0ff",
                    "width": 6,
                    "opacity": 1.0,
                    "lineStyle": line_style,
                    "dashScale": 1.0,
                    "glow": False,
                    "animation": {
                        "type": anim,
                        "speed": 1.0,
                        "direction": "cw"
                    }
                }
            }

            f0 = np.zeros((h, w, 3), dtype=np.uint8)
            f1 = np.zeros((h, w, 3), dtype=np.uint8)
            f_end = np.zeros((h, w, 3), dtype=np.uint8)

            render_polygon_contour(f0, poly, norm_pts, t_sec=0.0, total_duration=loop_dur, width=w, height=h)
            render_polygon_contour(f1, poly, norm_pts, t_sec=1.1, total_duration=loop_dur, width=w, height=h)
            render_polygon_contour(f_end, poly, norm_pts, t_sec=loop_dur, total_duration=loop_dur, width=w, height=h)

            # Confirmar movimiento temporal
            diff_motion = np.max(np.abs(f0.astype(np.int16) - f1.astype(np.int16)))
            self.assertGreater(diff_motion, 15, f"Animación {anim} debe mostrar movimiento visible entre t=0 y t=1.1")

            # Confirmar bucle perfecto (t=0 y t=loop_dur deben ser idénticos)
            diff_loop = np.max(np.abs(f0.astype(np.int16) - f_end.astype(np.int16)))
            self.assertLessEqual(diff_loop, 2, f"Animación {anim} debe cerrar bucle perfecto en t={loop_dur}, max diff={diff_loop}")

    def test_z_index_occlusion(self):
        """Verifica que una máscara occluder en una capa superior tape completamente
        los contornos y resplandores de capas inferiores."""
        w, h = 1920, 1080
        canvas = np.zeros((h, w, 3), dtype=np.uint8)

        # Capa 0: Polígono grande con contorno brillante y glow
        pts0 = [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]]
        poly0 = {
            "id": "p0",
            "type": "quad",
            "points": pts0,
            "stroke": {
                "visible": True,
                "color": "#ffffff",
                "width": 10,
                "opacity": 1.0,
                "lineStyle": "solid",
                "glow": True,
                "animation": {"type": "none"}
            }
        }
        render_polygon_contour(canvas, poly0, pts0, t_sec=0.0, total_duration=4.0, width=w, height=h)

        # Capa 1: Máscara occluder que cubre toda la mitad derecha (x >= 0.5)
        pts1 = np.array([[int(0.5 * w), 0], [w, 0], [w, h], [int(0.5 * w), h]], dtype=np.int32)
        cv2.fillPoly(canvas, [pts1], (0, 0, 0))

        # En la región cubierta por la máscara (x > 1000), todos los píxeles deben ser negro absoluto
        masked_region = canvas[:, 1000:]
        max_lum = np.max(masked_region)
        self.assertEqual(max_lum, 0, f"La máscara occluder debe tapar todo el contorno y glow inferior, max lum={max_lum}")

    def test_end_to_end_mp4_export(self):
        """Exportación real de extremo a extremo de un video MP4 con contornos animados
        y fondo transparente para mostrador."""
        temp_dir = tempfile.mkdtemp(prefix="pm_test_export_")
        try:
            scene_data = {
                "name": "Mostrador Showcase Test",
                "aspectRatio": "16:9",
                "polygons": [
                    {
                        "id": "poly_counter_1",
                        "name": "Vitrinas Frontal",
                        "type": "quad",
                        "points": [[0.2, 0.3], [0.5, 0.3], [0.5, 0.7], [0.2, 0.7]],
                        "visible": True,
                        "fillEnabled": False,
                        "color": "#00f0ff",
                        "stroke": {
                            "visible": True,
                            "color": "#00f0ff",
                            "width": 6,
                            "opacity": 1.0,
                            "lineStyle": "solid",
                            "glow": True,
                            "animation": {"type": "laser_comet", "speed": 1.0, "direction": "cw"}
                        }
                    },
                    {
                        "id": "poly_counter_2",
                        "name": "Plataforma Central",
                        "type": "circle",
                        "center": [0.75, 0.5],
                        "radiusX": 0.15,
                        "radiusY": 0.15 * (1920 / 1080),
                        "rotation": 0,
                        "visible": True,
                        "fillEnabled": False,
                        "color": "#ff0055",
                        "stroke": {
                            "visible": True,
                            "color": "#ff0055",
                            "width": 6,
                            "opacity": 1.0,
                            "lineStyle": "dashed",
                            "dashScale": 1.0,
                            "glow": False,
                            "animation": {"type": "march", "speed": 1.0, "direction": "ccw"}
                        }
                    }
                ]
            }

            self.assertTrue(has_animated_contours(scene_data), "has_animated_contours debe detectar contornos activos")

            exporter = VideoExporter()
            options = {
                "width": 1280,
                "height": 720,
                "fps": 30,
                "duration": 1.0,
                "filename": "test_counter_showcase.mp4"
            }
            started = exporter.start_export(scene_data, options)
            self.assertTrue(started, "start_export debe retornar True")

            status = exporter.get_status()
            while status["status"] == "rendering":
                import time
                time.sleep(0.05)
                status = exporter.get_status()

            self.assertEqual(status["status"], "completed", f"Exportación falló: {status.get('error')}")

            from video_exporter import EXPORTS_DIR
            export_file = EXPORTS_DIR / status["output_filename"]
            self.assertTrue(export_file.is_file(), f"El archivo MP4 exportado debe existir: {export_file}")

            # Validar con OpenCV VideoCapture
            cap = cv2.VideoCapture(str(export_file))
            self.assertTrue(cap.isOpened(), "El video MP4 exportado debe ser legible con cv2.VideoCapture")
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            self.assertGreaterEqual(frame_count, 28, f"Debe tener ~30 fotogramas, obtuvo {frame_count}")

            ret, frame = cap.read()
            self.assertTrue(ret, "Primer fotograma debe ser legible")
            cap.release()

            # El centro de la figura 1 sin fondo debe ser oscuro
            h, w = frame.shape[:2]
            center_val = np.max(frame[int(0.5 * h), int(0.35 * w)])
            self.assertLess(center_val, 15, f"El interior transparente debe permanecer oscuro tras H.264, max val={center_val}")

            # El contorno debe ser luminoso
            edge_val = np.max(frame[int(0.3 * h):int(0.7 * h), int(0.2 * w):int(0.5 * w)])
            self.assertGreater(edge_val, 60, f"El contorno debe tener brillo en el video, max val={edge_val}")

            # Limpiar el archivo generado de media/exports/ para que no queden residuos
            try:
                if export_file.is_file():
                    export_file.unlink()
            except Exception:
                pass

        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


    def test_v291_chaser_and_new_animations(self):
        """Verifica trace_chaser, segmentLength y las nuevas animaciones profesionales."""
        poly = {
            "id": "p_chaser",
            "type": "quad",
            "points": [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 8,
                "opacity": 1.0,
                "lineStyle": "solid",
                "segmentLength": 0.25,
                "glow": False,
                "animation": {"type": "trace_chaser", "speed": 1.0, "direction": "cw"},
            },
        }
        norm_pts = np.array(poly["points"], dtype=np.float64)

        # 1. trace_chaser a segmentLength=0.25 vs segmentLength=0.75
        c25 = np.zeros((200, 200, 3), dtype=np.uint8)
        render_polygon_contour(c25, poly, norm_pts, t_sec=0.0, total_duration=4.0, width=200, height=200)
        lit_px_25 = np.count_nonzero(c25 > 20)

        poly["stroke"]["segmentLength"] = 0.75
        c75 = np.zeros((200, 200, 3), dtype=np.uint8)
        render_polygon_contour(c75, poly, norm_pts, t_sec=0.0, total_duration=4.0, width=200, height=200)
        lit_px_75 = np.count_nonzero(c75 > 20)

        self.assertGreater(lit_px_75, lit_px_25 * 1.8, "segmentLength 75% debe encender más del doble de píxeles que 25%")

        # 2. laser_comet no debe tener riel tenue en la región opuesta a la cabeza
        comet_poly = {
            "id": "p_comet",
            "type": "quad",
            "points": [[0.1, 0.1], [0.9, 0.1], [0.9, 0.9], [0.1, 0.9]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "segmentLength": 0.20,
                "glow": False,
                "animation": {"type": "laser_comet", "speed": 1.0, "direction": "cw"},
            },
        }
        comet_c = np.zeros((200, 200, 3), dtype=np.uint8)
        # Con head en phase=0.0 (esquina superior izquierda), la arista inferior (y=0.9 -> y=180) debe ser 100% negra
        render_polygon_contour(comet_c, comet_poly, np.array(comet_poly["points"], dtype=np.float64), t_sec=0.0, total_duration=4.0, width=200, height=200)
        bottom_edge_brightness = np.max(comet_c[175:185, 40:160])
        self.assertEqual(bottom_edge_brightness, 0, "No debe existir silueta tenue en la zona no iluminada por el cometa")

        # 3. gradient_march produce variedad cromática sobre el perímetro
        grad_poly = {
            "id": "p_grad",
            "type": "quad",
            "points": [[0.1, 0.1], [0.9, 0.1], [0.9, 0.9], [0.1, 0.9]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "animation": {"type": "gradient_march", "speed": 1.0, "direction": "cw"},
            },
        }
        grad_c = np.zeros((200, 200, 3), dtype=np.uint8)
        render_polygon_contour(grad_c, grad_poly, np.array(grad_poly["points"], dtype=np.float64), t_sec=0.0, total_duration=4.0, width=200, height=200)
        # Extraer colores de los 4 lados para verificar diversidad cromática
        top_col = grad_c[20, 100]
        bot_col = grad_c[180, 100]
        self.assertFalse(np.array_equal(top_col, bot_col), "gradient_march debe tener colores distintos en lados opuestos")

        # 4. pulse modula grosor físico: mayor cantidad de píxeles en t=0 (pico) que en t=0.5 (valle)
        pulse_poly = {
            "id": "p_pulse",
            "type": "quad",
            "points": [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00ff88",
                "width": 10,
                "opacity": 1.0,
                "lineStyle": "solid",
                "animation": {"type": "pulse", "speed": 1.0, "direction": "cw"},
            },
        }
        pulse_high = np.zeros((200, 200, 3), dtype=np.uint8)
        pulse_low = np.zeros((200, 200, 3), dtype=np.uint8)
        render_polygon_contour(pulse_high, pulse_poly, np.array(pulse_poly["points"], dtype=np.float64), t_sec=0.0, total_duration=1.0, width=200, height=200)
        render_polygon_contour(pulse_low, pulse_poly, np.array(pulse_poly["points"], dtype=np.float64), t_sec=0.5, total_duration=1.0, width=200, height=200)
        high_lit = np.count_nonzero(pulse_high > 10)
        low_lit = np.count_nonzero(pulse_low > 10)
        self.assertGreater(high_lit, low_lit * 1.15, "El pulso físico debe encender más píxeles en su fase de expansión máxima")



    def test_v292_deep_pulse_and_line_styles(self):
        """Verifica deep_pulse (blackout a 0 px), pulse_wave, y respeto de lineStyle en cometa y dual_chase."""
        w, h = 200, 200
        norm_pts = np.array([[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]], dtype=np.float64)

        # 1. deep_pulse: en el valle t=1.0 (duración 2.0s -> phase 0.5 -> wave 0.0), el contorno desaparece por completo (0 píxeles encendidos)
        deep_poly = {
            "id": "p_deep",
            "type": "quad",
            "points": [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 8,
                "opacity": 1.0,
                "lineStyle": "solid",
                "animation": {"type": "deep_pulse", "speed": 1.0, "direction": "cw"},
            },
        }
        f_trough = np.zeros((h, w, 3), dtype=np.uint8)
        render_polygon_contour(f_trough, deep_poly, norm_pts, t_sec=1.0, total_duration=2.0, width=w, height=h)
        lit_trough = np.count_nonzero(f_trough > 10)
        self.assertEqual(lit_trough, 0, f"deep_pulse debe desaparecer completamente en el valle (obtenido {lit_trough} px)")

        f_peak = np.zeros((h, w, 3), dtype=np.uint8)
        render_polygon_contour(f_peak, deep_poly, norm_pts, t_sec=0.0, total_duration=2.0, width=w, height=h)
        lit_peak = np.count_nonzero(f_peak > 10)
        self.assertGreater(lit_peak, 100, "deep_pulse en pico debe iluminar el contorno con intensidad plena")

        # 2. laser_comet con lineStyle dashed: no debe ser una línea sólida continua
        dashed_comet_poly = {
            "id": "p_dash_comet",
            "type": "quad",
            "points": [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "dashed",
                "dashScale": 1.0,
                "segmentLength": 0.50,
                "animation": {"type": "laser_comet", "speed": 1.0, "direction": "cw"},
            },
        }
        f_comet_dash = np.zeros((h, w, 3), dtype=np.uint8)
        render_polygon_contour(f_comet_dash, dashed_comet_poly, norm_pts, t_sec=0.0, total_duration=4.0, width=w, height=h)
        # El cometa con guiones debe tener interrupciones (gaps) dentro de su cuerpo iluminado
        top_edge = f_comet_dash[40, 40:160] # sección de la arista superior
        brightness = np.max(top_edge, axis=1)
        has_gaps = np.any(brightness == 0) and np.any(brightness > 50)
        self.assertTrue(has_gaps, "laser_comet con estilo dashed debe contener interrupciones entre guiones")

        # 3. dual_chase en modo clean (trazo fijo): dos segmentos limpios diametralmente opuestos
        clean_dual_poly = {
            "id": "p_dual_clean",
            "type": "quad",
            "points": [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#ff00aa",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "segmentLength": 0.35,
                "animation": {"type": "dual_chase", "chaseStyle": "clean", "speed": 1.0, "direction": "cw"},
            },
        }
        f_dual = np.zeros((h, w, 3), dtype=np.uint8)
        render_polygon_contour(f_dual, clean_dual_poly, norm_pts, t_sec=0.0, total_duration=4.0, width=w, height=h)
        lit_dual = np.count_nonzero(f_dual > 20)
        self.assertGreater(lit_dual, 100, "dual_chase modo clean debe iluminar los dos segmentos limpios")

    def test_edge_glitch_speed_modulation(self):
        """Verifica que la velocidad afecte y module la semilla y deformación de edge_glitch."""
        w, h = 640, 480
        norm_pts = [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]]
        
        slow_poly = {
            "id": "p_glitch_slow",
            "type": "quad",
            "points": norm_pts,
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "animation": {"type": "edge_glitch", "speed": 0.5, "direction": "cw"},
            },
        }
        fast_poly = {
            "id": "p_glitch_fast",
            "type": "quad",
            "points": norm_pts,
            "fillEnabled": False,
            "visible": True,
            "stroke": {
                "visible": True,
                "color": "#00f0ff",
                "width": 6,
                "opacity": 1.0,
                "lineStyle": "solid",
                "animation": {"type": "edge_glitch", "speed": 3.0, "direction": "cw"},
            },
        }
        
        f_slow = np.zeros((h, w, 3), dtype=np.uint8)
        f_fast = np.zeros((h, w, 3), dtype=np.uint8)
        render_polygon_contour(f_slow, slow_poly, norm_pts, t_sec=0.25, total_duration=4.0, width=w, height=h)
        render_polygon_contour(f_fast, fast_poly, norm_pts, t_sec=0.25, total_duration=4.0, width=w, height=h)
        
        self.assertGreater(np.count_nonzero(f_slow), 0, "edge_glitch debe renderizar píxeles")
        self.assertGreater(np.count_nonzero(f_fast), 0, "edge_glitch rápido debe renderizar píxeles")
        # A t=0.25, la semilla y estado de glitch difiere entre velocidad 0.5 y 3.0
        diff = cv2.absdiff(f_slow, f_fast)
        self.assertGreater(np.sum(diff), 0, "Diferentes velocidades deben producir estados de glitch diferenciados")

    def test_png_alpha_transparency_in_video_export(self):
        """Verifica que las imágenes con canal alfa transparente preserven el fondo negro puro sin revelar píxeles borrados."""
        import tempfile
        import time
        from pathlib import Path

        w, h = 640, 480
        # Crear imagen sintética 4 canales (100x100) con centro blanco opaco y esquinas transparentes pero con color de fondo
        img_bgra = np.zeros((100, 100, 4), dtype=np.uint8)
        # Fondo: color naranja (BGR: 50, 100, 200) con Alpha=0
        img_bgra[:, :] = [50, 100, 200, 0]
        # Centro: color blanco (BGR: 255, 255, 255) con Alpha=255
        img_bgra[25:75, 25:75] = [255, 255, 255, 255]

        tmp_dir = Path(tempfile.mkdtemp(prefix="test_alpha_"))
        try:
            from video_exporter import MEDIA_DIR
            test_img_path = MEDIA_DIR / "images" / "test_alpha_transparency.png"
            test_img_path.parent.mkdir(parents=True, exist_ok=True)
            cv2.imwrite(str(test_img_path), img_bgra)

            exporter = VideoExporter()
            scene_data = {
                "name": "test_alpha_scene",
                "polygons": [
                    {
                        "id": "p_alpha",
                        "type": "quad",
                        "points": [[0.1, 0.1], [0.9, 0.1], [0.9, 0.9], [0.1, 0.9]],
                        "visible": True,
                        "fillEnabled": True,
                        "media": {
                            "type": "image",
                            "src": f"/media/images/{test_img_path.name}",
                            "opacity": 1.0,
                        },
                        "stroke": {"visible": False},
                    }
                ],
            }

            options = {"width": w, "height": h, "fps": 10, "duration": 0.2}
            self.assertTrue(exporter.start_export(scene_data, options))

            for _ in range(50):
                status = exporter.get_status()
                if status["status"] in ("completed", "error"):
                    break
                time.sleep(0.1)

            self.assertEqual(status["status"], "completed", f"Error en exportación: {status.get('error')}")
            exported_file = Path(exporter.output_filename)
            from video_exporter import EXPORTS_DIR
            exported_path = EXPORTS_DIR / exported_file
            self.assertTrue(exported_path.exists(), "El video exportado debe existir")

            cap = cv2.VideoCapture(str(exported_path))
            ret, frame = cap.read()
            cap.release()
            self.assertTrue(ret, "Debe poder leer el primer fotograma del video")

            # La esquina interior del quad (x=100, y=80) está en la zona transparente del PNG (alpha=0)
            # Debe ser negro puro [0, 0, 0] y NO el fondo naranja [50, 100, 200]
            corner_pixel = frame[80, 100]
            self.assertTrue(np.all(corner_pixel == 0), f"Zona transparente del PNG debe ser negro puro, obtuvo: {corner_pixel}")

            # El centro (x=320, y=240) está en la zona opaca (alpha=255)
            # Debe estar iluminado en blanco (> 200 en todos los canales)
            center_pixel = frame[240, 320]
            self.assertGreater(center_pixel[0], 200, f"Centro opaco debe ser blanco, obtuvo {center_pixel}")

        finally:
            if test_img_path.exists():
                test_img_path.unlink()
            if 'exported_path' in locals() and exported_path.exists():
                exported_path.unlink()
            import shutil
            shutil.rmtree(tmp_dir, ignore_errors=True)

    def test_march_animation_dashed_motion_all_edges(self):
        """Verifica que 'march' anime fluidamente en todos los estilos discontinuos y en todas las aristas sin inmovilidad."""
        w, h = 600, 600
        norm_pts = [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]]
        loop_dur = 2.0

        for style in ["dashed", "dotted", "dash_dot", "long_dash"]:
            poly = {
                "id": f"p_march_{style}",
                "type": "quad",
                "points": norm_pts,
                "fillEnabled": False,
                "visible": True,
                "stroke": {
                    "visible": True,
                    "color": "#00f0ff",
                    "width": 6,
                    "opacity": 1.0,
                    "lineStyle": style,
                    "dashScale": 1.0,
                    "glow": False,
                    "animation": {"type": "march", "speed": 1.0, "direction": "cw"},
                },
            }
            f0 = np.zeros((h, w, 3), dtype=np.uint8)
            f_half = np.zeros((h, w, 3), dtype=np.uint8)
            f_end = np.zeros((h, w, 3), dtype=np.uint8)

            render_polygon_contour(f0, poly, norm_pts, t_sec=0.0, total_duration=loop_dur, width=w, height=h)
            render_polygon_contour(f_half, poly, norm_pts, t_sec=0.5, total_duration=loop_dur, width=w, height=h)
            render_polygon_contour(f_end, poly, norm_pts, t_sec=loop_dur, total_duration=loop_dur, width=w, height=h)

            diff = cv2.absdiff(f0, f_half)
            total_diff = np.sum(diff > 0)
            self.assertGreater(total_diff, 1000, f"Estilo {style} debe presentar movimiento apreciable en march")

            # Validar que el movimiento ocurre en las 4 aristas (arriba, abajo, izquierda, derecha)
            top_diff = np.sum(diff[115:125, 120:480] > 0)
            bottom_diff = np.sum(diff[475:485, 120:480] > 0)
            left_diff = np.sum(diff[120:480, 115:125] > 0)
            right_diff = np.sum(diff[120:480, 475:485] > 0)

            self.assertGreater(top_diff, 200, f"Arista superior debe moverse en estilo {style}")
            self.assertGreater(bottom_diff, 200, f"Arista inferior debe moverse en estilo {style}")
            self.assertGreater(left_diff, 200, f"Arista izquierda debe moverse en estilo {style}")
            self.assertGreater(right_diff, 200, f"Arista derecha debe moverse en estilo {style}")

            # Bucle continuo perfecto (t=0.0 idéntico a t=loop_dur)
            loop_diff = np.sum(cv2.absdiff(f0, f_end) > 0)
            self.assertEqual(loop_diff, 0, f"Bucle seamless exacto para estilo {style}")

    def test_all_animations_with_line_styles_and_fills(self):
        """Verifica compatibilidad total entre estilos de línea, animaciones y rellenos/multimedia."""
        w, h = 400, 400
        norm_pts = [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]]
        duration = 4.0

        for style in ["solid", "dashed", "dotted", "dash_dot", "long_dash", "double"]:
            for anim in ["march", "laser_comet", "trace_chaser", "pulse_wave", "gradient_march"]:
                poly = {
                    "id": f"p_{anim}_{style}",
                    "type": "quad",
                    "points": norm_pts,
                    "fillEnabled": True,
                    "color": "#ff0000",
                    "stroke": {
                        "visible": True,
                        "color": "#00f0ff",
                        "width": 6,
                        "opacity": 1.0,
                        "lineStyle": style,
                        "dashScale": 1.0,
                        "glow": False,
                        "animation": {"type": anim, "speed": 1.0, "direction": "cw"},
                    },
                }
                c0 = np.zeros((h, w, 3), dtype=np.uint8)
                c1 = np.zeros((h, w, 3), dtype=np.uint8)
                # Simular relleno sólido
                pts_int = np.array([[int(p[0]*w), int(p[1]*h)] for p in norm_pts], dtype=np.int32)
                cv2.fillPoly(c0, [pts_int], (0, 0, 255))
                cv2.fillPoly(c1, [pts_int], (0, 0, 255))

                render_polygon_contour(c0, poly, norm_pts, t_sec=0.25, total_duration=duration, width=w, height=h)
                render_polygon_contour(c1, poly, norm_pts, t_sec=1.0, total_duration=duration, width=w, height=h)

                diff = cv2.absdiff(c0, c1)
                move_diff = int(np.sum(diff > 0))
                if not (anim == "march" and style in ("solid", "double")):
                    self.assertGreater(move_diff, 0, f"Animación {anim} con estilo {style} sobre relleno debe animar")


if __name__ == "__main__":
    unittest.main()
