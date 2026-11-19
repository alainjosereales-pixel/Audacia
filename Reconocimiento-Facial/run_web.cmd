@echo off
setlocal
title Audacia FR2000 - Reconocimiento Facial
cd /d "%~dp0"

echo ==================================================================
echo           AUDACIA FR2000 - DASHBOARD BIOMETRICO LOCAL            
echo ==================================================================
echo Directorio: %~dp0

REM 1. Verificar y activar entorno virtual
if exist ".venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (.venv)...
    call .venv\Scripts\activate.bat
) else if exist "venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (venv)...
    call venv\Scripts\activate.bat
) else (
    echo.
    echo ------------------------------------------------------------------
    echo  [AVISO] No se detecto entorno virtual configurado (.venv).
    echo  Para configurarlo, ejecuta:
    echo    python -m venv .venv
    echo    .venv\Scripts\activate
    echo    pip install -r requirements.txt
    echo  Ejecutando con Python del sistema...
    echo ------------------------------------------------------------------
)

set PORT=8000
set URL=http://localhost:%PORT%

REM 2. Abrir navegador en segundo plano tras un breve retardo
start "" cmd /c "timeout /t 3 /nobreak >nul & start %URL%"

REM 3. Iniciar servidor FastAPI
echo [INFO] Iniciando servidor web en %URL%...
echo [INFO] Presiona Ctrl+C en esta consola para detener el servidor.
echo ==================================================================
echo.

python run_web.py --port %PORT%

pause

