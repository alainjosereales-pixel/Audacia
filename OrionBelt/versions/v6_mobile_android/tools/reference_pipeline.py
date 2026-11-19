"""Pipeline de referencia MiDaS Small TFLite para ORION v6.

Define las funciones canónicas de preprocesamiento, inferencia y análisis de zonas
con paridad directa hacia la versión v5 de ORION.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

from fetch_model import DEFAULT_MODELS_DIR, MODEL_FILENAME, fetch_model

IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
TARGET_SIZE = (256, 256)


@dataclass
class ZoneResult:
    action: str  # "STOP" | "FORWARD"
    intensity: float  # 0.0 .. 1.0
    left: float
    center: float
    right: float
    overall: float
    nearest_zone: Optional[str]  # "LEFT" | "CENTER" | "RIGHT" | None


def load_interpreter(model_path: Optional[Path] = None) -> Interpreter:
    if model_path is None:
        model_path = DEFAULT_MODELS_DIR / MODEL_FILENAME
    if not model_path.exists():
        model_path = fetch_model(model_path)

    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    return interpreter


def preprocess(rgb_uint8: np.ndarray) -> np.ndarray:
    """Redimensiona bilinealmente a 256x256 y normaliza con estadísticas ImageNet.

    Args:
        rgb_uint8: array numpy de forma (H, W, 3) con dtype uint8 en RGB.

    Returns:
        array numpy de forma (1, 256, 256, 3) con dtype float32.
    """
    if not isinstance(rgb_uint8, np.ndarray) or rgb_uint8.dtype != np.uint8:
        raise ValueError("Se requiere un numpy ndarray uint8 en formato RGB")

    pil_img = Image.fromarray(rgb_uint8)
    if pil_img.size != TARGET_SIZE:
        pil_img = pil_img.resize(TARGET_SIZE, Image.BILINEAR)

    arr = np.asarray(pil_img, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    tensor = np.expand_dims(arr, axis=0)
    return tensor.astype(np.float32)


def normalize_depth(depth_raw: np.ndarray, invert: bool = False) -> np.ndarray:
    """Normalización min-max canónica idéntica a ORION v5.

    Si el rango dinámico es inferior a 1e-6 (escena plana / oscuridad),
    devuelve una matriz de ceros para evitar amplificar ruido térmico.
    """
    min_value = float(depth_raw.min())
    max_value = float(depth_raw.max())
    span = max_value - min_value

    if span < 1e-6:
        normalized = np.zeros_like(depth_raw, dtype=np.float32)
    else:
        normalized = (depth_raw - min_value) / span

    if invert:
        normalized = 1.0 - normalized

    return normalized.astype(np.float32)


def analyze_zones(
    depth_norm: np.ndarray,
    near_threshold: float = 0.60,
    sample_step: int = 4,
) -> ZoneResult:
    """Analiza la proximidad por tercios espaciales y calcula nivel de alarma.

    Replica la lógica de analyze_depth de v5 para 'action' e 'intensity',
    agregando descomposición de tercios (LEFT, CENTER, RIGHT).
    """
    depth = depth_norm[::sample_step, ::sample_step] if sample_step > 1 else depth_norm
    _, width = depth.shape[:2]
    third = width // 3

    left = depth[:, :third]
    center = depth[:, third : 2 * third]
    right = depth[:, 2 * third :]

    left_mean = float(left.mean()) if left.size > 0 else 0.0
    center_mean = float(center.mean()) if center.size > 0 else 0.0
    right_mean = float(right.mean()) if right.size > 0 else 0.0
    overall_mean = float(depth.mean()) if depth.size > 0 else 0.0

    action = "STOP" if overall_mean > 0.9 or center_mean > near_threshold else "FORWARD"

    if action == "STOP":
        reference = max(center_mean, overall_mean)
        span = max(1e-6, 1.0 - near_threshold)
        intensity = min(1.0, max(0.0, (reference - near_threshold) / span))
    else:
        intensity = 0.0

    candidates = [("LEFT", left_mean), ("CENTER", center_mean), ("RIGHT", right_mean)]
    best_zone, best_val = max(candidates, key=lambda x: x[1])
    nearest_zone = best_zone if best_val > near_threshold else None

    return ZoneResult(
        action=action,
        intensity=intensity,
        left=left_mean,
        center=center_mean,
        right=right_mean,
        overall=overall_mean,
        nearest_zone=nearest_zone,
    )


def infer_depth_tflite(interpreter: Interpreter, input_tensor: np.ndarray) -> np.ndarray:
    """Ejecuta inferencia con LiteRT y retorna el mapa de profundidad en 256x256."""
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    interpreter.set_tensor(input_details[0]["index"], input_tensor)
    interpreter.invoke()

    output = interpreter.get_tensor(output_details[0]["index"])
    # Salida esperada: shape (1, 256, 256) -> squeeze a (256, 256)
    if output.ndim == 3 and output.shape[0] == 1:
        output = output[0]
    elif output.ndim == 4 and output.shape[0] == 1 and output.shape[-1] == 1:
        output = output[0, :, :, 0]

    return output.astype(np.float32)
