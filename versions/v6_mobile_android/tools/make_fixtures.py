"""Genera golden fixtures en JSON para validación cruzada con Kotlin (JUnit)."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

import numpy as np
from PIL import Image

from reference_pipeline import (
    analyze_zones,
    infer_depth_tflite,
    load_interpreter,
    normalize_depth,
    preprocess,
)

TOOLS_DIR = Path(__file__).resolve().parent
FIXTURES_DIR = TOOLS_DIR / "fixtures"
OUTPUT_IMG_DIR = TOOLS_DIR / "output_test"
REPO_ROOT = TOOLS_DIR.parents[2]


def save_colorized_magma(depth_norm_256: np.ndarray, out_path: Path):
    """Guarda una visualización PNG usando una rampa de color Magma/Inferno."""
    # Tabla LUT estilo magma (256 colores RGB)
    # Rampa: negro/morado oscuro -> magenta -> naranja -> amarillo brillante
    x = np.linspace(0.0, 1.0, 256)
    r = np.clip(1.5 * x, 0.0, 1.0)
    g = np.clip(1.8 * (x - 0.35), 0.0, 1.0)
    b = np.clip(np.where(x < 0.4, 2.5 * x, 1.0 - (x - 0.4) * 1.6), 0.0, 1.0)

    lut = np.zeros((256, 3), dtype=np.uint8)
    lut[:, 0] = (r * 255).astype(np.uint8)
    lut[:, 1] = (g * 255).astype(np.uint8)
    lut[:, 2] = (b * 255).astype(np.uint8)

    depth_uint8 = np.clip(depth_norm_256 * 255.0, 0, 255).astype(np.uint8)
    rgb_mapped = lut[depth_uint8]

    out_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgb_mapped).save(out_path)
    print(f"[make_fixtures] Mapa de profundidad guardado: {out_path}")


def generate_fixtures():
    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_IMG_DIR.mkdir(parents=True, exist_ok=True)

    scenarios = {}

    # 1. Flat (escena vacía/plana)
    flat = np.zeros((64, 64), dtype=np.float32)
    scenarios["flat"] = flat

    # 2. Center (obstáculo cercano al frente)
    center = np.zeros((64, 64), dtype=np.float32)
    third = 64 // 3
    center[:, third : 2 * third] = 0.85
    scenarios["center"] = center

    # 3. Left (obstáculo lateral izquierdo)
    left = np.zeros((64, 64), dtype=np.float32)
    left[:, :third] = 0.85
    scenarios["left"] = left

    # 4. Right (obstáculo lateral derecho)
    right = np.zeros((64, 64), dtype=np.float32)
    right[:, 2 * third :] = 0.85
    scenarios["right"] = right

    # 5. Gradient (rampa de profundidad suave de izquierda a derecha)
    grad = np.tile(np.linspace(0.1, 0.9, 64, dtype=np.float32), (64, 1))
    scenarios["gradient"] = grad

    # 6. Portada real (inferencia MiDaS sobre images/portada.jpeg)
    portada_path = REPO_ROOT / "images" / "portada.jpeg"
    if portada_path.exists():
        img = Image.open(portada_path).convert("RGB")
        interpreter = load_interpreter()
        inp = preprocess(np.array(img, dtype=np.uint8))
        raw_depth = infer_depth_tflite(interpreter, inp)
        norm_depth = normalize_depth(raw_depth)

        # Guardar imagen coloreada
        save_colorized_magma(norm_depth, OUTPUT_IMG_DIR / "portada_depth_magma.png")

        # Reducir a 64x64 para el fixture
        downsampled = norm_depth[::4, ::4].astype(np.float32)
        scenarios["portada"] = downsampled

    # Guardar cada fixture
    for name, depth_arr in scenarios.items():
        result = analyze_zones(depth_arr, near_threshold=0.60, sample_step=1)
        fixture_data = {
            "name": name,
            "width": int(depth_arr.shape[1]),
            "height": int(depth_arr.shape[0]),
            "sample_step": 1,
            "near_threshold": 0.60,
            "depth_norm": [round(float(v), 5) for v in depth_arr.flatten()],
            "expected": asdict(result),
        }

        fixture_file = FIXTURES_DIR / f"{name}.json"
        with fixture_file.open("w", encoding="utf-8") as f:
            json.dump(fixture_data, f, indent=2)
        print(f"[make_fixtures] Fixture generado: {fixture_file.name} (action={result.action}, zone={result.nearest_zone})")


if __name__ == "__main__":
    generate_fixtures()
