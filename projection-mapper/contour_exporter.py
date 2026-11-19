"""contour_exporter.py - Motor determinista de renderizado de contornos proyectados y animaciones perimetrales en OpenCV / FFmpeg.

Garantiza paridad matemática 1:1 con `static/js/contour_engine.js`:
- Cuantización espacial de patrones discontinuos para evitar costuras en el vértice 0.
- Cuantización temporal de ciclos para garantizar bucle cerrado perfecto (seamless loop) en MP4.
- Renderizado de alta fidelidad con suavizado de bordes cv2.LINE_AA y resplandor óptico por ROI.
"""

from __future__ import annotations

import colorsys
import math
import re
from typing import Any, Dict, List, Sequence, Tuple

import cv2
import numpy as np

BASE_FREQS: Dict[str, float] = {
    "none": 0.0,
    "march": 0.5,
    "trace_chaser": 0.35,
    "laser_comet": 0.35,
    "dual_chase": 0.35,
    "draw_wipe": 0.25,
    "pulse": 0.6,
    "pulse_wave": 0.35,
    "edge_glitch": 0.8,
    "gradient_march": 0.25,
    "color_flow": 0.25,
}

DASH_MULTIPLIERS: Dict[str, List[float]] = {
    "dashed": [4.0, 3.0],
    "dotted": [0.25, 2.5],
    "dash_dot": [5.0, 2.4, 0.35, 2.4],
    "long_dash": [10.0, 3.8],
}

LINE_STYLES = ("solid", "dashed", "dotted", "dash_dot", "long_dash", "double")
ANIMATION_TYPES = ("none", "march", "trace_chaser", "laser_comet", "dual_chase", "draw_wipe", "pulse", "pulse_wave", "edge_glitch", "gradient_march", "color_flow")


def hex_to_bgr(hex_str: str) -> Tuple[int, int, int]:
    """Convierte un color hexadecimal '#rrggbb' a tupla BGR (0..255)."""
    if not isinstance(hex_str, str) or not re.match(r"^#[0-9a-fA-F]{6}$", hex_str):
        hex_str = "#00f0ff"
    h = hex_str.lstrip("#")
    r = int(h[0:2], 16)
    g = int(h[2:4], 16)
    b = int(h[4:6], 16)
    return (b, g, r)


def bgr_to_hex(bgr: Tuple[int, int, int]) -> str:
    b, g, r = bgr
    return f"#{r:02x}{g:02x}{b:02x}"


def hsl_shift_bgr(bgr: Tuple[int, int, int], hue_shift_deg: float) -> Tuple[int, int, int]:
    """Desplaza el tono HSL preservando luminosidad y saturación para color_flow."""
    b, g, r = [c / 255.0 for c in bgr]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if s < 0.2:
        s = 0.85
        l = max(0.45, min(0.65, l))
    h = (h + (hue_shift_deg / 360.0)) % 1.0
    nr, ng, nb = colorsys.hls_to_rgb(h, l, s)
    return (int(round(nb * 255)), int(round(ng * 255)), int(round(nr * 255)))


def normalize_stroke(raw: Any, fallback_color: str = "#00f0ff") -> Dict[str, Any]:
    """Normaliza la configuración del contorno aplicando valores por defecto y rangos seguros."""
    if not isinstance(raw, dict):
        raw = {}

    def _finite_float(val: Any, default: float, min_val: float, max_val: float) -> float:
        try:
            f = float(val)
            if math.isfinite(f):
                return max(min_val, min(max_val, f))
        except (TypeError, ValueError):
            pass
        return default

    color = raw.get("color")
    if not isinstance(color, str) or not re.match(r"^#[0-9a-fA-F]{6}$", color):
        color = fallback_color if isinstance(fallback_color, str) and re.match(r"^#[0-9a-fA-F]{6}$", fallback_color) else "#00f0ff"

    line_style = raw.get("lineStyle")
    if line_style not in LINE_STYLES:
        line_style = "solid"

    anim = raw.get("animation") if isinstance(raw.get("animation"), dict) else {}
    anim_type = anim.get("type")
    pulse_style = "deep" if anim.get("pulseStyle") == "deep" else "rhythmic"
    chase_style = "clean" if anim.get("chaseStyle") == "clean" else "comet"
    if anim_type == "deep_pulse":
        anim_type = "pulse"
        pulse_style = "deep"
    if anim_type not in ANIMATION_TYPES:
        anim_type = "none"

    segment_len = _finite_float(raw.get("segmentLength"), 0.35 if anim_type in ("trace_chaser", "laser_comet", "dual_chase") else 1.0, 0.05, 1.0)

    return {
        "visible": bool(raw.get("visible", False)),
        "color": color.lower(),
        "width": _finite_float(raw.get("width"), 4.0, 1.0, 32.0),
        "opacity": _finite_float(raw.get("opacity"), 1.0, 0.05, 1.0),
        "lineStyle": line_style,
        "dashScale": _finite_float(raw.get("dashScale"), 1.0, 0.25, 4.0),
        "segmentLength": segment_len,
        "glow": bool(raw.get("glow", False)),
        "animation": {
            "type": anim_type,
            "speed": _finite_float(anim.get("speed"), 1.0, 0.1, 5.0),
            "direction": "ccw" if anim.get("direction") == "ccw" else "cw",
            "pulseStyle": pulse_style,
            "chaseStyle": chase_style,
        },
    }


def has_animated_contours(scene_data: Dict[str, Any]) -> bool:
    """Retorna True si la escena contiene al menos un contorno visible no-máscara."""
    for p in scene_data.get("polygons", []):
        if not p.get("visible", True):
            continue
        ptype = p.get("type", "")
        if ptype in ("occluder", "circle_occluder"):
            continue
        stroke = p.get("stroke")
        if isinstance(stroke, dict) and stroke.get("visible"):
            return True
    return False


def build_closed_polyline_px(norm_pts: Sequence[Sequence[float]], width: int, height: int) -> Tuple[np.ndarray, np.ndarray, float]:
    """Convierte puntos normalizados [0.0 - 1.0] a polilínea cerrada en píxeles y calcula
    las distancias acumuladas por longitud de arco."""
    if len(norm_pts) < 3:
        empty = np.zeros((0, 2), dtype=np.float64)
        return empty, np.zeros((0,), dtype=np.float64), 0.0

    pts = np.array([[float(p[0]) * width, float(p[1]) * height] for p in norm_pts], dtype=np.float64)
    # Cerrar bucle agregando el primer vértice al final si no coincide
    if not np.allclose(pts[0], pts[-1], atol=1e-3):
        pts = np.vstack([pts, pts[0:1]])

    diffs = np.diff(pts, axis=0)
    seg_lens = np.hypot(diffs[:, 0], diffs[:, 1])
    cum = np.zeros(len(pts), dtype=np.float64)
    cum[1:] = np.cumsum(seg_lens)
    total_len = float(cum[-1])
    return pts, cum, total_len


def interpolate_pt_at_s(pts: np.ndarray, cum: np.ndarray, s: float) -> np.ndarray:
    """Interpola un punto en píxeles sobre la polilínea a la distancia de arco s."""
    total_len = cum[-1]
    if total_len <= 1e-6:
        return pts[0].copy()
    s = s % total_len
    idx = int(np.searchsorted(cum, s, side="right"))
    if idx <= 0:
        return pts[0].copy()
    if idx >= len(pts):
        return pts[-1].copy()

    s0 = cum[idx - 1]
    s1 = cum[idx]
    ds = s1 - s0
    ratio = (s - s0) / ds if ds > 1e-6 else 0.0
    return pts[idx - 1] + (pts[idx] - pts[idx - 1]) * ratio


def slice_polyline_arc(pts: np.ndarray, cum: np.ndarray, total_len: float, s_start: float, s_end: float) -> List[np.ndarray]:
    """Extrae sub-polilíneas exactas para el intervalo de arco [s_start, s_end],
    manejando envoltura modular alrededor de total_len."""
    if total_len <= 1e-6 or s_end <= s_start:
        return []

    arc_len = s_end - s_start
    if arc_len >= total_len:
        # Polilínea completa cerrada
        return [np.round(pts).astype(np.int32).reshape(-1, 1, 2)]

    s0 = s_start % total_len
    s1 = s0 + arc_len

    if s1 <= total_len:
        sub = _slice_simple(pts, cum, s0, s1)
        return [sub] if len(sub) >= 2 else []
    else:
        # Envuelve alrededor del vértice 0: produce 2 tramos
        sub_a = _slice_simple(pts, cum, s0, total_len)
        sub_b = _slice_simple(pts, cum, 0.0, s1 - total_len)
        res = []
        if len(sub_a) >= 2:
            res.append(sub_a)
        if len(sub_b) >= 2:
            res.append(sub_b)
        return res


def _slice_simple(pts: np.ndarray, cum: np.ndarray, s0: float, s1: float) -> np.ndarray:
    pt_start = interpolate_pt_at_s(pts, cum, s0)
    pt_end = interpolate_pt_at_s(pts, cum, s1)

    # Vértices intermedios
    mask = (cum > s0 + 1e-4) & (cum < s1 - 1e-4)
    mid_pts = pts[mask]

    all_pts = [pt_start]
    if len(mid_pts) > 0:
        all_pts.extend(mid_pts)
    all_pts.append(pt_end)

    return np.round(np.array(all_pts, dtype=np.float64)).astype(np.int32).reshape(-1, 1, 2)


def compute_dash_pattern(line_style: str, stroke_width: float, dash_scale: float, total_len: float) -> List[float]:
    """Calcula el patrón de trazos cuantizado para eliminar costuras en el vértice 0."""
    if line_style not in DASH_MULTIPLIERS or total_len <= 1e-3:
        return []

    base_pattern = [m * max(1.0, stroke_width) * max(0.25, dash_scale) for m in DASH_MULTIPLIERS[line_style]]
    raw_period = sum(base_pattern)
    if raw_period <= 1e-3:
        return []

    repeats = max(1, int(round(total_len / raw_period)))
    target_period = total_len / repeats
    scale = target_period / raw_period
    return [v * scale for v in base_pattern]


def compute_phase(anim_type: str, speed: float, direction: str, t_sec: float, loop_duration: float | None = None) -> float:
    """Calcula la fase de animación en [0, 1) cuantizando ciclos enteros sobre loop_duration."""
    if anim_type == "none":
        return 0.0

    base_freq = BASE_FREQS.get(anim_type, 0.5)
    if loop_duration is not None and loop_duration > 0:
        cycles = max(1, int(round(loop_duration * base_freq * speed)))
        raw_u = (t_sec / loop_duration) * cycles
        raw_prog = ((raw_u % 1.0) + 1.0) % 1.0
    else:
        raw_u = t_sec * base_freq * speed
        raw_prog = ((raw_u % 1.0) + 1.0) % 1.0

    if direction == "ccw":
        phase = (1.0 - raw_prog) % 1.0
    else:
        phase = raw_prog

    return phase if phase > 1e-7 else 0.0


def render_polygon_contour(
    canvas: np.ndarray,
    poly: Dict[str, Any],
    norm_pts: Sequence[Sequence[float]],
    t_sec: float,
    total_duration: float,
    width: int,
    height: int,
) -> None:
    """Renderiza el contorno proyectado y sus animaciones perimetrales sobre el lienzo in-place."""
    if poly.get("type") in ("occluder", "circle_occluder") or not poly.get("visible", True):
        return

    stroke = normalize_stroke(poly.get("stroke"), poly.get("color", "#00f0ff"))
    if not stroke["visible"] or len(norm_pts) < 3:
        return

    pts_px, cum, total_len = build_closed_polyline_px(norm_pts, width, height)
    if total_len <= 1.0 or len(pts_px) < 3:
        return

    res_scale = max(0.25, width / 1920.0)
    base_w = stroke["width"] * res_scale
    anim = stroke["animation"]
    phase = compute_phase(anim["type"], anim["speed"], anim["direction"], t_sec, total_duration)

    # Color y opacidad base
    bgr_color = hex_to_bgr(stroke["color"])
    opacity = stroke["opacity"]
    eff_w = base_w

    anim_type = anim["type"]
    line_style = stroke["lineStyle"]

    if anim_type == "pulse" or anim_type == "deep_pulse":
        is_deep = (anim.get("pulseStyle") == "deep") or (anim_type == "deep_pulse")
        wave = 0.5 + 0.5 * math.cos(2.0 * math.pi * phase)
        if is_deep:
            # Pulso Profundo: se desvanece por completo a 0.00 (blackout)
            opacity = opacity * (wave ** 1.8)
            eff_w = base_w * (0.25 + 1.15 * wave)
        else:
            # Pulso Rítmico Suave: respiración continua
            opacity = opacity * (0.30 + 0.70 * wave)
            eff_w = base_w * (0.55 + 0.85 * wave)

    elif anim_type == "color_flow":
        bgr_color = hsl_shift_bgr(bgr_color, phase * 360.0)

    # Calcular ROI delimitador para optimizar operaciones de resplandor (Glow) y alpha blending
    min_x = max(0, int(np.min(pts_px[:, 0]) - base_w * 4 - 32 * res_scale))
    max_x = min(width, int(np.max(pts_px[:, 0]) + base_w * 4 + 32 * res_scale))
    min_y = max(0, int(np.min(pts_px[:, 1]) - base_w * 4 - 32 * res_scale))
    max_y = min(height, int(np.max(pts_px[:, 1]) + base_w * 4 + 32 * res_scale))

    if max_x <= min_x or max_y <= min_y:
        return

    roi_w = max_x - min_x
    roi_h = max_y - min_y

    # Capa de contorno en coordenadas locales de ROI (float32 para mezcla suave)
    contour_layer = np.zeros((roi_h, roi_w, 3), dtype=np.uint8)
    alpha_layer = np.zeros((roi_h, roi_w), dtype=np.float32)

    pts_roi = pts_px.copy()
    pts_roi[:, 0] -= min_x
    pts_roi[:, 1] -= min_y

    # Helper para trazar arcos sobre la capa ROI
    def _draw_sub_arcs(sub_polys: List[np.ndarray], color: Tuple[int, int, int], thickness: int, op: float):
        if not sub_polys or thickness <= 0 or op <= 0:
            return
        tmp_mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
        for poly_pts in sub_polys:
            poly_roi = poly_pts.copy()
            poly_roi[:, 0, 0] -= min_x
            poly_roi[:, 0, 1] -= min_y
            cv2.polylines(tmp_mask, [poly_roi], isClosed=False, color=255, thickness=thickness, lineType=cv2.LINE_AA)

        # Fusionar con el buffer
        m = (tmp_mask.astype(np.float32) / 255.0) * op
        for c in range(3):
            contour_layer[:, :, c] = np.where(m > 0, color[c], contour_layer[:, :, c])
        np.maximum(alpha_layer, m, out=alpha_layer)

    # Helper para círculos ópticos de dotted
    def _draw_dots(center_pts: List[np.ndarray], color: Tuple[int, int, int], radius: int, op: float):
        if not center_pts or radius <= 0 or op <= 0:
            return
        tmp_mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
        for pt in center_pts:
            cx = int(round(pt[0] - min_x))
            cy = int(round(pt[1] - min_y))
            if 0 <= cx < roi_w and 0 <= cy < roi_h:
                cv2.circle(tmp_mask, (cx, cy), radius=radius, color=255, thickness=-1, lineType=cv2.LINE_AA)

        m = (tmp_mask.astype(np.float32) / 255.0) * op
        for c in range(3):
            contour_layer[:, :, c] = np.where(m > 0, color[c], contour_layer[:, :, c])
        np.maximum(alpha_layer, m, out=alpha_layer)

    int_pts_roi = np.round(pts_roi).astype(np.int32).reshape(-1, 1, 2)

    # Precomputar métricas y segmentos para estilos de línea discontinua / punteada
    pattern: List[float] = []
    pattern_period = 0.0
    dash_intervals: List[Tuple[float, float]] = []
    num_dots = 1
    dot_pitch = 1.0

    if line_style in ("dashed", "dotted", "dash_dot", "long_dash"):
        eff_scale = stroke.get("dashScale", 1.0)
        pattern = compute_dash_pattern(line_style, eff_w, eff_scale, total_len)
        pattern_period = sum(pattern) if pattern else 0.0

        if line_style == "dotted":
            pattern_period = pattern_period if pattern_period > 1e-4 else max(1.0, eff_w * 3.0)
            num_dots = max(1, int(round(total_len / pattern_period)))
            dot_pitch = total_len / num_dots
        else:
            if pattern:
                cur_off = 0.0
                seg_idx = 0
                while seg_idx < len(pattern):
                    d_len = pattern[seg_idx]
                    g_len = pattern[seg_idx + 1] if seg_idx + 1 < len(pattern) else 0.0
                    dash_intervals.append((cur_off, d_len))
                    cur_off += d_len + g_len
                    seg_idx += 2

    # Desfase perimétrico para animación march o gradient_march
    dash_offset = 0.0
    if anim_type in ("march", "gradient_march") and pattern_period > 1e-4:
        dash_offset = (phase * pattern_period) % pattern_period

    # Helper universal para dibujar cualquier intervalo de arco [s_start, s_end] respetando line_style
    def _draw_styled_interval(s_start: float, s_end: float, color: Tuple[int, int, int], thickness: int, op: float):
        if op <= 1e-4 or thickness <= 0 or s_end <= s_start:
            return

        arc_len = s_end - s_start
        if arc_len > total_len:
            arc_len = total_len

        if line_style == "solid":
            sub = slice_polyline_arc(pts_px, cum, total_len, s_start, s_start + arc_len)
            _draw_sub_arcs(sub, color, thickness=thickness, op=op)
        elif line_style == "double":
            sub = slice_polyline_arc(pts_px, cum, total_len, s_start, s_start + arc_len)
            th_out = max(3, int(round(thickness * 2.2)))
            th_in = max(1, int(round(thickness * 0.85)))
            _draw_double_arcs(sub, color, th_out, th_in, op, roi_w, roi_h, min_x, min_y, contour_layer, alpha_layer)
        elif line_style == "dotted":
            cur_radius = max(1, int(round(thickness * 0.55)))
            dot_pts = []
            eps = 1e-4
            d_min = int(math.floor((s_start - dash_offset) / dot_pitch)) - 1
            d_max = int(math.ceil((s_start + arc_len - dash_offset) / dot_pitch)) + 1
            for d in range(d_min, d_max + 1):
                s_dot = dash_offset + d * dot_pitch
                d_from_start = s_dot - s_start
                if arc_len >= total_len - eps:
                    is_in = (-eps <= d_from_start < total_len - eps)
                else:
                    is_in = (-eps <= d_from_start <= arc_len + eps)
                if is_in:
                    real_s = s_dot % total_len
                    dot_pts.append(interpolate_pt_at_s(pts_px, cum, real_s))
            _draw_dots(dot_pts, color, radius=cur_radius, op=op)
        else:
            # dashed, dash_dot, long_dash
            if not pattern or not dash_intervals or pattern_period <= 1e-4:
                sub = slice_polyline_arc(pts_px, cum, total_len, s_start, s_start + arc_len)
                _draw_sub_arcs(sub, color, thickness=thickness, op=op)
                return

            k_min = int(math.floor((s_start - dash_offset) / pattern_period)) - 1
            k_max = int(math.ceil((s_start + arc_len - dash_offset) / pattern_period)) + 1
            for k in range(k_min, k_max + 1):
                cycle_base = dash_offset + k * pattern_period
                for d_off, d_len in dash_intervals:
                    d_start = cycle_base + d_off
                    d_end = d_start + d_len
                    inter_start = max(s_start, d_start)
                    inter_end = min(s_start + arc_len, d_end)
                    if inter_end - inter_start > 1e-4:
                        sub = slice_polyline_arc(pts_px, cum, total_len, inter_start, inter_end)
                        _draw_sub_arcs(sub, color, thickness=thickness, op=op)

    # ==================== RENDERIZADO POR ANIMACIÓN Y ESTILO ====================

    is_comet = (anim_type == "laser_comet") or (anim_type == "dual_chase" and anim.get("chaseStyle") == "comet")
    is_clean_chaser = (anim_type == "trace_chaser") or (anim_type == "dual_chase" and anim.get("chaseStyle") != "comet")

    if is_comet:
        # COMETA LÁSER SUAVE CON SOPORTE COMPLETO DE ESTILOS DE LÍNEA Y CERO PUNTOS FALSOS
        comet_count = 1 if anim_type == "laser_comet" else 2
        seg_len_factor = stroke.get("segmentLength", 0.35)
        comet_len = total_len * (seg_len_factor if comet_count == 1 else (seg_len_factor * 0.75))
        num_segs = 16

        for c_idx in range(comet_count):
            c_phase = (phase + (0.5 * c_idx)) % 1.0
            head_s = c_phase * total_len

            for s_idx in range(num_segs):
                ratio0 = s_idx / num_segs
                ratio1 = (s_idx + 1) / num_segs
                seg_op = (ratio1**1.8) * opacity
                seg_thickness = max(1, int(round(eff_w * (0.5 + 0.7 * ratio1))))

                if anim["direction"] == "ccw":
                    seg_start = head_s + (1.0 - ratio1) * comet_len
                    seg_end = head_s + (1.0 - ratio0) * comet_len
                else:
                    seg_start = head_s - (1.0 - ratio0) * comet_len
                    seg_end = head_s - (1.0 - ratio1) * comet_len

                if s_idx >= num_segs - 2:
                    blend_w = 0.55 if s_idx == num_segs - 2 else 0.85
                    hot_color = tuple(int(round(bgr_color[c] * (1.0 - blend_w) + 255 * blend_w)) for c in range(3))
                    _draw_styled_interval(seg_start, seg_end, hot_color, thickness=seg_thickness, op=seg_op)
                else:
                    _draw_styled_interval(seg_start, seg_end, bgr_color, thickness=seg_thickness, op=seg_op)

            # Núcleo radiante en la cabeza
            tip_s = head_s if anim["direction"] != "ccw" else head_s
            core_arcs = slice_polyline_arc(pts_px, cum, total_len, tip_s - 3, tip_s + 3)
            hot_head = tuple(int(round(bgr_color[c] * 0.15 + 255 * 0.85)) for c in range(3))
            _draw_sub_arcs(core_arcs, hot_head, thickness=max(1, int(round(eff_w * 1.25))), op=min(1.0, opacity * 0.98))

    elif is_clean_chaser:
        # SEGMENTOS VIAJEROS DEFINIDOS (EL GUSANO Y DOBLE PERSECUCIÓN EN MODO FIJO)
        chaser_count = 1 if anim_type == "trace_chaser" else 2
        seg_len_factor = stroke.get("segmentLength", 0.35)
        seg_len = total_len * (seg_len_factor if chaser_count == 1 else (seg_len_factor * 0.75))
        th = max(1, int(round(eff_w)))

        for c_idx in range(chaser_count):
            c_phase = (phase + (0.5 * c_idx)) % 1.0
            head_s = c_phase * total_len

            if anim["direction"] == "ccw":
                start_s = head_s
                end_s = head_s + seg_len
            else:
                start_s = head_s - seg_len
                end_s = head_s

            _draw_styled_interval(start_s, end_s, bgr_color, thickness=th, op=opacity)

    elif anim_type == "pulse_wave":
        # ONDA PERIMETRAL: El contorno está a pleno color y una onda móvil lo desvanece suavemente a su paso
        wave_center_s = (phase * total_len) % total_len
        wave_width_s = total_len * stroke.get("segmentLength", 0.35)
        half_wave = max(4.0, wave_width_s * 0.5)

        num_steps = max(36, min(140, int(total_len / 8.0)))
        step_s = total_len / num_steps
        th = max(1, int(round(eff_w)))

        for i in range(num_steps):
            s0 = i * step_s
            s1 = (i + 1) * step_s
            mid_s = (s0 + s1) * 0.5
            dist = min(abs(mid_s - wave_center_s), total_len - abs(mid_s - wave_center_s))

            if dist < half_wave:
                # Modulación suave: en el centro desciende al 45% de intensidad y en los extremos regresa al 100%
                fade_norm = (1.0 - math.cos(math.pi * dist / half_wave)) * 0.5
                fade_factor = 0.45 + 0.55 * fade_norm
            else:
                fade_factor = 1.0

            seg_op = opacity * fade_factor
            _draw_styled_interval(s0, s1, bgr_color, thickness=th, op=seg_op)

    elif anim_type == "gradient_march":
        num_steps = max(36, min(128, int(total_len / 12.0)))
        step_s = total_len / num_steps
        dir_mult = -1.0 if anim["direction"] == "ccw" else 1.0
        th = max(1, int(round(eff_w)))
        for i in range(num_steps):
            s0 = i * step_s
            s1 = (i + 1) * step_s
            u = ((s0 / total_len) + (phase * dir_mult)) % 1.0
            hue_deg = (u * 360.0) % 360.0
            seg_bgr = hsl_shift_bgr(bgr_color, hue_deg)
            _draw_styled_interval(s0, s1, seg_bgr, thickness=th, op=opacity)

    elif anim_type == "edge_glitch":
        # Disrupción vectorial determinista por aristas modulada por velocidad
        spd = max(0.1, float(anim.get("speed", 1.0) or 1.0))
        if total_duration > 0:
            total_glitch_steps = max(1, int(round(total_duration * 22.0 * spd)))
            step_seed = int(math.floor((t_sec / total_duration) * total_glitch_steps))
        else:
            step_seed = int(math.floor(t_sec * 22.0 * spd))
        prng = lambda s: (abs(math.sin(s * 12.9898 + 78.233) * 43758.5453) % 1.0)
        burst = prng(step_seed)
        is_glitch = burst > 0.55
        is_flash = burst > 0.90
        th = max(1, int(round(eff_w)))

        if is_glitch and len(pts_px) >= 3:
            glitch_color = tuple(int(round(bgr_color[c] * 0.3 + 255 * 0.7)) for c in range(3)) if is_flash else bgr_color
            glitch_w = max(1, int(round(th * (1.35 if is_flash else 1.0))))
            glitch_op = min(1.0, opacity * (1.3 if is_flash else 1.0))
            n_pts = len(pts_px)

            for i in range(n_pts):
                p1 = pts_px[i]
                p2 = pts_px[(i + 1) % n_pts]
                dx = p2[0] - p1[0]
                dy = p2[1] - p1[1]
                edge_len = math.hypot(dx, dy)
                if edge_len < 1e-3:
                    continue
                nx = -dy / edge_len
                ny = dx / edge_len
                h = prng(step_seed * 17 + i * 31)

                if h < 0.40:
                    sub = [np.round(np.array([[p1], [p2]])).astype(np.int32)]
                elif h < 0.78:
                    dist = (4.0 + 14.0 * prng(step_seed * 7 + i)) * (1.0 if prng(step_seed * 5 + i) > 0.4 else -1.0)
                    q1 = p1 + np.array([nx * dist, ny * dist])
                    q2 = p2 + np.array([nx * dist, ny * dist])
                    sub = [np.round(np.array([[q1], [q2]])).astype(np.int32)]
                else:
                    mid_ratio = 0.35 + 0.30 * prng(step_seed * 9 + i)
                    mx = p1[0] + dx * mid_ratio
                    my = p1[1] + dy * mid_ratio
                    dist = (6.0 + 12.0 * prng(step_seed * 11 + i))
                    sub = [
                        np.round(np.array([[p1], [[mx, my]]])).astype(np.int32),
                        np.round(np.array([[[mx + nx * dist, my + ny * dist]], [[p2[0] + nx * dist, p2[1] + ny * dist]]])).astype(np.int32)
                    ]

                _draw_sub_arcs(sub, glitch_color, thickness=glitch_w, op=glitch_op)
        else:
            _draw_styled_interval(0.0, total_len, bgr_color, thickness=th, op=opacity)

    elif anim_type == "draw_wipe":
        if phase < 0.5:
            wipe_prog = phase * 2.0
            wipe_start = 0.0
            wipe_len = wipe_prog * total_len
        else:
            wipe_prog = (phase - 0.5) * 2.0
            wipe_start = wipe_prog * total_len
            wipe_len = (1.0 - wipe_prog) * total_len

        th = max(1, int(round(eff_w)))
        _draw_styled_interval(wipe_start, wipe_start + wipe_len, bgr_color, thickness=th, op=opacity)

    else:
        # anim_type in ("none", "march", "pulse", "deep_pulse", "color_flow")
        th = max(1, int(round(eff_w)))
        _draw_styled_interval(0.0, total_len, bgr_color, thickness=th, op=opacity)

    # ==================== RESPLANDOR ÓPTICO (GLOW) ====================
    if stroke["glow"] and np.any(alpha_layer > 0.02):
        k_size = int(round(base_w * 2.4)) * 2 + 1
        k_size = max(5, min(101, k_size))
        sigma = max(2.0, base_w * 1.1)

        glow_mask = cv2.GaussianBlur(alpha_layer, (k_size, k_size), sigma)
        glow_rgb = np.zeros((roi_h, roi_w, 3), dtype=np.float32)
        for c in range(3):
            glow_rgb[:, :, c] = glow_mask * (bgr_color[c] * 0.75)

        # Sumar resplandor al lienzo base
        canvas_roi_float = canvas[min_y:max_y, min_x:max_x].astype(np.float32)
        canvas_roi_float = np.clip(canvas_roi_float + glow_rgb, 0, 255)
        canvas[min_y:max_y, min_x:max_x] = canvas_roi_float.astype(np.uint8)

    # ==================== COMPOSICIÓN ALPHA SOBRE EL LIENZO ====================
    alpha_3d = np.clip(alpha_layer[:, :, np.newaxis], 0.0, 1.0)
    bg_roi = canvas[min_y:max_y, min_x:max_x].astype(np.float32)
    fg_roi = contour_layer.astype(np.float32)

    blended = fg_roi * alpha_3d + bg_roi * (1.0 - alpha_3d)
    canvas[min_y:max_y, min_x:max_x] = np.clip(blended, 0, 255).astype(np.uint8)


def _draw_double_arcs(
    sub_polys: List[np.ndarray],
    color: Tuple[int, int, int],
    th_out: int,
    th_in: int,
    op: float,
    roi_w: int,
    roi_h: int,
    min_x: int,
    min_y: int,
    contour_layer: np.ndarray,
    alpha_layer: np.ndarray,
):
    """Genera doble carril restando el núcleo interior de una máscara ancha."""
    mask_out = np.zeros((roi_h, roi_w), dtype=np.uint8)
    mask_in = np.zeros((roi_h, roi_w), dtype=np.uint8)

    for poly_pts in sub_polys:
        poly_roi = poly_pts.copy()
        poly_roi[:, 0, 0] -= min_x
        poly_roi[:, 0, 1] -= min_y
        cv2.polylines(mask_out, [poly_roi], isClosed=False, color=255, thickness=th_out, lineType=cv2.LINE_AA)
        cv2.polylines(mask_in, [poly_roi], isClosed=False, color=255, thickness=th_in, lineType=cv2.LINE_AA)

    double_mask = cv2.subtract(mask_out, mask_in)
    m = (double_mask.astype(np.float32) / 255.0) * op
    for c in range(3):
        contour_layer[:, :, c] = np.where(m > 0, color[c], contour_layer[:, :, c])
    np.maximum(alpha_layer, m, out=alpha_layer)
