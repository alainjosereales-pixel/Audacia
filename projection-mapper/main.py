"""main.py - Punto de entrada principal y lanzador unificado para Projection Mapper Studio.
Provee interfaz de línea de comandos (CLI) con banderas de configuración,
verificación automática de dependencias y arranque del estudio.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def check_dependencies() -> bool:
    """Comprueba la disponibilidad de las dependencias esenciales de Python."""
    missing = []
    try:
        import cv2  # noqa: F401
    except ImportError:
        missing.append("opencv-python (cv2)")

    try:
        import numpy  # noqa: F401
    except ImportError:
        missing.append("numpy")

    if missing:
        print("\n" + "=" * 60)
        print("[ERROR] Faltan dependencias esenciales para ejecutar Projection Mapper:")
        for pkg in missing:
            print(f"  • {pkg}")
        print("-" * 60)
        print("Para instalarlas en tu entorno virtual ejecuta:")
        print("    pip install -r requirements.txt")
        print("=" * 60 + "\n")
        return False
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Projection Mapper Studio - Estudio profesional de Video Mapping y Deformación Homográfica",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--port", "-p",
        type=int,
        default=None,
        help="Puerto TCP para el servidor local (anula variable PORT de .env)",
    )
    parser.add_argument(
        "--host", "-H",
        type=str,
        default=None,
        help="Dirección IP o interfaz de red de enlace (anula variable HOST de .env)",
    )
    parser.add_argument(
        "--no-browser",
        action="store_true",
        help="Inicia el servidor en modo sin cabeza (headless), sin abrir navegador automáticamente",
    )
    parser.add_argument(
        "--check-deps",
        action="store_true",
        help="Verifica dependencias del entorno y sale sin iniciar el servidor",
    )
    parser.add_argument(
        "--version", "-v",
        action="version",
        version="Projection Mapper Studio v2.9.2",
    )

    args = parser.parse_args()

    # Verificación de dependencias requeridas
    deps_ok = check_dependencies()
    if args.check_deps:
        if deps_ok:
            print("[OK] Todas las dependencias esenciales (OpenCV, NumPy) están correctamente instaladas.")
            sys.exit(0)
        else:
            sys.exit(1)

    if not deps_ok:
        sys.exit(1)

    # Importar e invocar el lanzador
    from launcher import launch_app
    from server import DEFAULT_PORT, DEFAULT_HOST

    target_port = args.port if args.port is not None else DEFAULT_PORT
    target_host = args.host if args.host is not None else DEFAULT_HOST
    open_browser = False if args.no_browser else None

    launch_app(port=target_port, host=target_host, open_browser=open_browser)


if __name__ == "__main__":
    main()
