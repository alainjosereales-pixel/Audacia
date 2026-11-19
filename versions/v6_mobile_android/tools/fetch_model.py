"""Descarga y verificación de midas_small_256_fp16.tflite para ORION v6."""

from __future__ import annotations

import hashlib
import shutil
import sys
from pathlib import Path

import requests

MODEL_URL = (
    "https://huggingface.co/litert-community/MiDaS-small/resolve/main/"
    "midas_small_256_fp16.tflite"
)
MODEL_FILENAME = "midas_small_256_fp16.tflite"
EXPECTED_SIZE_BYTES = 33_507_904

TOOLS_DIR = Path(__file__).resolve().parent
DEFAULT_MODELS_DIR = TOOLS_DIR / "models"
ANDROID_ASSETS_DIR = TOOLS_DIR.parent / "android" / "app" / "src" / "main" / "assets"


def compute_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_model(dest: Path | None = None) -> Path:
    """Descarga el modelo a dest (por defecto tools/models/midas_small_256_fp16.tflite),
    verifica su tamaño exacto, guarda el SHA256 y lo copia a android assets si existe."""
    if dest is None:
        dest = DEFAULT_MODELS_DIR / MODEL_FILENAME
    elif dest.is_dir():
        dest = dest / MODEL_FILENAME

    dest.parent.mkdir(parents=True, exist_ok=True)

    if dest.exists() and dest.stat().st_size == EXPECTED_SIZE_BYTES:
        print(f"[fetch_model] Modelo ya existe y tiene tamaño válido: {dest}")
    else:
        tmp_path = dest.with_suffix(".tflite.part")
        print(f"[fetch_model] Descargando {MODEL_URL} -> {dest} ...")
        with requests.get(MODEL_URL, stream=True, timeout=120) as resp:
            resp.raise_for_status()
            with tmp_path.open("wb") as f:
                for chunk in resp.iter_content(chunk_size=1024 * 512):
                    if chunk:
                        f.write(chunk)
        actual_size = tmp_path.stat().st_size
        if actual_size != EXPECTED_SIZE_BYTES:
            tmp_path.unlink(missing_ok=True)
            raise ValueError(
                f"Tamaño descargado inválido: {actual_size} bytes "
                f"(esperado: {EXPECTED_SIZE_BYTES} bytes)"
            )
        tmp_path.replace(dest)
        print(f"[fetch_model] Descarga completa ({actual_size:,} bytes).")

    sha256_hex = compute_sha256(dest)
    sha_file = dest.parent / "MODEL_SHA256.txt"
    sha_file.write_text(f"{sha256_hex}  {dest.name}\n", encoding="utf-8")
    print(f"[fetch_model] SHA256: {sha256_hex}")

    if ANDROID_ASSETS_DIR.exists():
        asset_dest = ANDROID_ASSETS_DIR / MODEL_FILENAME
        shutil.copy2(dest, asset_dest)
        print(f"[fetch_model] Copiado a Android assets: {asset_dest}")

    return dest


if __name__ == "__main__":
    try:
        fetch_model()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
