@echo off
setlocal
title Indutronica SHM-ML - Monitor de Vibracion Ferroviaria
cd /d "%~dp0"

echo ==========================================================
echo  INDUTRONICA SHM-ML - MONITOR EN TIEMPO REAL (WINDOWS)
echo ==========================================================
echo Directorio: %~dp0

REM 1. Verificar y activar entorno virtual
if exist ".venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (.venv)...
    call .venv\Scripts\activate.bat
) else if exist "venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual (venv)...
    call venv\Scripts\activate.bat
) else (
    echo [AVISO] No se detecto .venv o venv. Usando Python del sistema.
    echo [TIP] Si faltan dependencias, ejecuta: pip install -r requirements.txt
)

set PORT=%1
if "%PORT%"=="" (
    echo [*] Buscando puerto USB Arduino automaticamente...
    echo [*] Si conoces el puerto COM, puedes ejecutar: start_monitor.cmd COM3
) else (
    echo [*] Usando puerto COM especificado: %PORT%
)

REM 2. Abrir navegador en http://localhost:5000 tras breve espera
start "" cmd /c "timeout /t 3 /nobreak >nul & start http://localhost:5000"

REM 3. Iniciar servidor de monitoreo
echo [INFO] Iniciando servidor en http://localhost:5000...
echo [INFO] Presiona Ctrl+C en esta consola para detener el servidor.
echo ==========================================================
if "%PORT%"=="" (
    python indutronica_monitor_server.py
) else (
    python indutronica_monitor_server.py %PORT%
)

pause

