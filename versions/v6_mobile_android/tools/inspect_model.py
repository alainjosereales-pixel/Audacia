"""Inspecciona los tensores de entrada y salida de midas_small_256_fp16.tflite."""

from __future__ import annotations

import json
from pathlib import Path

from ai_edge_litert.interpreter import Interpreter

from fetch_model import DEFAULT_MODELS_DIR, MODEL_FILENAME, fetch_model


def inspect_model(model_path: Path | None = None) -> dict:
    if model_path is None:
        model_path = DEFAULT_MODELS_DIR / MODEL_FILENAME
    if not model_path.exists():
        model_path = fetch_model(model_path)

    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()

    inputs = [
        {
            "index": int(d["index"]),
            "name": d["name"],
            "shape": [int(x) for x in d["shape"]],
            "dtype": d["dtype"].__name__,
            "quantization": [float(d["quantization"][0]), int(d["quantization"][1])],
        }
        for d in interpreter.get_input_details()
    ]
    outputs = [
        {
            "index": int(d["index"]),
            "name": d["name"],
            "shape": [int(x) for x in d["shape"]],
            "dtype": d["dtype"].__name__,
            "quantization": [float(d["quantization"][0]), int(d["quantization"][1])],
        }
        for d in interpreter.get_output_details()
    ]
    info = {
        "model": model_path.name,
        "size_bytes": model_path.stat().st_size,
        "inputs": inputs,
        "outputs": outputs,
    }
    return info


if __name__ == "__main__":
    details = inspect_model()
    print(json.dumps(details, indent=2))
