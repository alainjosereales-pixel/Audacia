"""Pruebas unitarias para el pipeline de referencia MiDaS TFLite (ORION v6)."""

from pathlib import Path
import numpy as np
import pytest

from reference_pipeline import (
    preprocess,
    normalize_depth,
    analyze_zones,
    infer_depth_tflite,
    load_interpreter,
    ZoneResult,
)


def v5_normalize_depth(depth_raw: np.ndarray, invert: bool) -> np.ndarray:
    """Implementación exacta tomada de versions/v5_web_xiao_esp32s3/main.py L186."""
    min_value = float(depth_raw.min())
    max_value = float(depth_raw.max())
    span = max_value - min_value

    if span < 1e-6:
        normalized = np.zeros_like(depth_raw, dtype=np.float32)
    else:
        normalized = (depth_raw - min_value) / span

    if invert:
        normalized = 1.0 - normalized

    return normalized


def test_normalize_matches_v5():
    """Valida paridad matemática bit a bit con la versión v5."""
    rng = np.random.default_rng(42)
    raw = rng.uniform(10.0, 500.0, size=(256, 256)).astype(np.float32)

    res_ref = normalize_depth(raw, invert=False)
    res_v5 = v5_normalize_depth(raw, invert=False)
    assert np.allclose(res_ref, res_v5, atol=1e-6)

    res_ref_inv = normalize_depth(raw, invert=True)
    res_v5_inv = v5_normalize_depth(raw, invert=True)
    assert np.allclose(res_ref_inv, res_v5_inv, atol=1e-6)


def test_flat_scene():
    """Escena uniforme (pared lisa / oscuridad): span < 1e-6 -> ceros y FORWARD."""
    flat_raw = np.full((256, 256), 42.0, dtype=np.float32)
    norm = normalize_depth(flat_raw)
    assert np.all(norm == 0.0)

    res = analyze_zones(norm, near_threshold=0.60)
    assert res.action == "FORWARD"
    assert res.intensity == 0.0
    assert res.center == 0.0
    assert res.left == 0.0
    assert res.right == 0.0
    assert res.overall == 0.0
    assert res.nearest_zone is None


def test_center_obstacle_stops():
    """Obstáculo frontal en el tercio central que excede near_threshold genera STOP."""
    depth = np.zeros((256, 256), dtype=np.float32)
    third = 256 // 3
    # Bloque cercano en zona central
    depth[:, third : 2 * third] = 0.90

    res = analyze_zones(depth, near_threshold=0.60)
    assert res.action == "STOP"
    assert res.nearest_zone == "CENTER"
    assert res.center > 0.60
    assert res.intensity > 0.0


def test_left_obstacle_zone():
    """Obstáculo lateral en tercio izquierdo identifica zona LEFT pero mantiene FORWARD (paridad v5)."""
    depth = np.zeros((256, 256), dtype=np.float32)
    third = 256 // 3
    # Llenar holgadamente dentro del tercio izquierdo
    depth[:, : third - 8] = 0.90

    res = analyze_zones(depth, near_threshold=0.60)
    assert res.nearest_zone == "LEFT"
    assert res.left > 0.60
    assert res.center == 0.0
    assert res.action == "FORWARD"
    assert res.intensity == 0.0


def test_preprocess_shape_and_stats():
    """Verifica dimensión (1, 256, 256, 3), dtype float32 y estandarización ImageNet."""
    # Imagen sintética gris plano 128
    dummy = np.full((120, 160, 3), 128, dtype=np.uint8)
    tensor = preprocess(dummy)

    assert tensor.shape == (1, 256, 256, 3)
    assert tensor.dtype == np.float32

    # pixel normalizado = (128/255.0 - mean) / std
    expected_r = ((128.0 / 255.0) - 0.485) / 0.229
    expected_g = ((128.0 / 255.0) - 0.456) / 0.224
    expected_b = ((128.0 / 255.0) - 0.406) / 0.225

    assert np.isclose(tensor[0, 0, 0, 0], expected_r, atol=1e-3)
    assert np.isclose(tensor[0, 0, 0, 1], expected_g, atol=1e-3)
    assert np.isclose(tensor[0, 0, 0, 2], expected_b, atol=1e-3)


def test_model_runs_on_real_image():
    """Ejecuta inferencia completa sobre images/portada.jpeg y comprueba salida finita."""
    portada_path = Path(__file__).resolve().parents[4] / "images" / "portada.jpeg"
    assert portada_path.exists(), f"No se encontró portada.jpeg en {portada_path}"

    from PIL import Image
    img = Image.open(portada_path).convert("RGB")
    rgb_arr = np.array(img, dtype=np.uint8)

    interpreter = load_interpreter()
    input_tensor = preprocess(rgb_arr)
    raw_depth = infer_depth_tflite(interpreter, input_tensor)

    assert raw_depth.shape == (256, 256)
    assert raw_depth.dtype == np.float32
    assert np.all(np.isfinite(raw_depth))

    norm = normalize_depth(raw_depth)
    assert 0.0 <= norm.min() and norm.max() <= 1.0

    zone_res = analyze_zones(norm, near_threshold=0.60)
    assert zone_res.action in ("STOP", "FORWARD")
    assert 0.0 <= zone_res.intensity <= 1.0
