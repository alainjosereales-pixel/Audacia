"""Launcher para el software de Projection Mapping.
Detecta monitores conectados en Windows, inicia el servidor local
y abre las ventanas de Control y Proyección.
"""

from __future__ import annotations

import ctypes
import os
import sys
import threading
import time
import webbrowser
from ctypes import wintypes
from pathlib import Path

from server import DEFAULT_PORT, DEFAULT_HOST, run_server


def get_windows_monitors():
    """Detecta los monitores conectados y sus coordenadas usando el Win32 API de Windows."""
    monitors = []

    if sys.platform != "win32":
        monitors.append({
            "device": "Pantalla Principal (Estándar)",
            "left": 0, "top": 0, "right": 1920, "bottom": 1080,
            "width": 1920, "height": 1080, "is_primary": True
        })
        return monitors
    
    # Callback tipo para EnumDisplayMonitors
    MONITORENUMPROC = ctypes.WINFUNCTYPE(
        ctypes.c_bool,
        wintypes.HMONITOR,
        wintypes.HDC,
        ctypes.POINTER(wintypes.RECT),
        wintypes.LPARAM
    )

    class MONITORINFOEXW(ctypes.Structure):
        _fields_ = [
            ("cbSize", wintypes.DWORD),
            ("rcMonitor", wintypes.RECT),
            ("rcWork", wintypes.RECT),
            ("dwFlags", wintypes.DWORD),
            ("szDevice", wintypes.WCHAR * 32),
        ]

    def _monitor_enum_callback(hMonitor, hdcMonitor, lprcMonitor, dwData):
        info = MONITORINFOEXW()
        info.cbSize = ctypes.sizeof(MONITORINFOEXW)
        if ctypes.windll.user32.GetMonitorInfoW(hMonitor, ctypes.byref(info)):
            rect = info.rcMonitor
            width = rect.right - rect.left
            height = rect.bottom - rect.top
            is_primary = bool(info.dwFlags & 1)
            monitors.append({
                "device": info.szDevice,
                "left": rect.left,
                "top": rect.top,
                "right": rect.right,
                "bottom": rect.bottom,
                "width": width,
                "height": height,
                "is_primary": is_primary
            })
        return True

    try:
        proc = MONITORENUMPROC(_monitor_enum_callback)
        ctypes.windll.user32.EnumDisplayMonitors(None, None, proc, 0)
    except Exception as e:
        print(f"[Launcher] Error al consultar monitores con Win32: {e}")

    # Fallback si Win32 no retorna datos
    if not monitors:
        monitors.append({
            "device": "Pantalla Principal",
            "left": 0, "top": 0, "right": 1920, "bottom": 1080,
            "width": 1920, "height": 1080, "is_primary": True
        })
    return monitors


def launch_app(port: int = DEFAULT_PORT, host: str = DEFAULT_HOST, open_browser: bool | None = None):
    monitors = get_windows_monitors()
    print("=" * 60)
    print("    PROJECTION MAPPER STUDIO - SISTEMA DE INICIO")
    print("=" * 60)
    print(f"Detectado(s) {len(monitors)} monitor(es):")
    for i, m in enumerate(monitors):
        tag = " [PRINCIPAL / LAPTOP]" if m["is_primary"] else " [SECUNDARIO / PROYECTOR]"
        print(f"  [{i+1}] {m['device']} - {m['width']}x{m['height']} en ({m['left']}, {m['top']}){tag}")
    print("-" * 60)

    # Iniciar servidor en hilo daemon
    server_thread = threading.Thread(target=run_server, args=(port, host), daemon=True)
    server_thread.start()
    time.sleep(0.5)

    display_host = "localhost" if host in ("127.0.0.1", "0.0.0.0") else host
    control_url = f"http://{display_host}:{port}/"
    projector_url = f"http://{display_host}:{port}/projector"

    if open_browser is None:
        auto_browser_env = os.environ.get("AUTO_OPEN_BROWSER", "true").strip().lower()
        open_browser = auto_browser_env in ("true", "1", "yes")

    if open_browser:
        print(f"\nAbriendo Ventana de Control en: {control_url}")
        webbrowser.open(control_url)
    else:
        print(f"\nServidor listo. Accede a la Ventana de Control en: {control_url}")

    if len(monitors) > 1:
        # Encontrar el proyector (el monitor no primario)
        secondary = next((m for m in monitors if not m["is_primary"]), monitors[1])
        print(f"\n¡Proyector detectado en posición X={secondary['left']}, Y={secondary['top']}!")
        print("Para máxima comodidad, puedes abrir la ventana de proyección")
        print("en el proyector pulsando el botón 'Abrir Proyector' desde la interfaz")
        print(f"o abriendo directamente: {projector_url} y pulsando F11.")
    else:
        print("\nNota: Solo se detectó 1 monitor activo.")
        print("Puedes abrir la ventana del proyector en otra pestaña o ventana:")
        print(f"-> {projector_url}")

    print("\nPresiona Ctrl+C en esta consola para detener el servidor.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nCerrando Projection Mapper...")


if __name__ == "__main__":
    launch_app()
