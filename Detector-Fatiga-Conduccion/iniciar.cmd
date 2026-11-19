@echo off
setlocal
title Detector de Fatiga y Somnolencia - Audacia
cd /d "%~dp0"

echo ==========================================================
echo    DETECTOR DE FATIGA Y SOMNOLENCIA - INICIO (WINDOWS)
echo ==========================================================

REM Verificar y activar entorno virtual si existe
if exist ".venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (.venv)...
    call .venv\Scripts\activate.bat
) else if exist "venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (venv)...
    call venv\Scripts\activate.bat
) else (
    echo [AVISO] No se detecto .venv o venv. Ejecutando con Python del sistema.
    echo [TIP] Si faltan dependencias, ejecuta: pip install -r requirements.txt
)

echo [INFO] Abriendo navegador en http://localhost:8000...
start "" cmd /c "timeout /t 3 /nobreak >nul & start http://localhost:8000"

echo [INFO] Iniciando servidor FastAPI / Uvicorn en http://localhost:8000...
echo [INFO] Presiona Ctrl+C en esta consola para detener el servidor.
echo ==========================================================
python web_app\server.py

pause

