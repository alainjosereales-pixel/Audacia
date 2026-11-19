@echo off
setlocal EnableDelayedExpansion

REM =============================================================================
REM ORION | Optical Recognition & Intelligent Obstacle Navigation
REM Lanzador Automatizado de 1 Clic para Windows
REM =============================================================================

chcp 65001 > nul

echo =====================================================================
echo       ORION - Optical Recognition ^& Intelligent Obstacle Navigation
echo =====================================================================
echo.

REM 1. Verificar si Python esta disponible
where python >nul 2>nul
if errorlevel 1 (
    echo [ERROR] No se encontro Python en el sistema.
    echo Instala Python 3.10 o superior y asegurate de marcar "Add Python to PATH".
    echo.
    pause
    exit /b 1
)

REM 2. Verificar o crear entorno virtual
if not exist ".venv" (
    echo [INFO] Creando entorno virtual en .venv...
    python -m venv .venv
    if errorlevel 1 (
        echo [ERROR] Fallo al crear el entorno virtual.
        pause
        exit /b 1
    )
    echo [OK] Entorno virtual creado.
    echo.
)

REM 3. Activar entorno virtual si existe activate.bat
if exist ".venv\Scripts\activate.bat" (
    call .venv\Scripts\activate.bat
)

REM 4. Verificar dependencias basicas
python -c "import fastapi, cv2, torch" >nul 2>nul
if errorlevel 1 (
    echo [INFO] Instalando dependencias desde requirements.txt...
    python -m pip install --upgrade pip
    pip install -r requirements.txt
    if errorlevel 1 (
        echo [ERROR] Hubo un problema al instalar las dependencias.
        pause
        exit /b 1
    )
    echo [OK] Dependencias instaladas.
    echo.
)

REM 5. Asegurar existencia de archivo .env
if not exist ".env" (
    if exist ".env.example" (
        echo [INFO] Creando archivo .env a partir de .env.example...
        copy .env.example .env >nul
        echo [OK] Archivo .env inicializado.
        echo.
    )
)

REM 6. Lanzar aplicacion principal
echo [INICIANDO] Ejecutando ORION...
echo.
python main.py %*

if errorlevel 1 (
    echo.
    echo [AVISO] El programa se cerro con codigo de error %errorlevel%.
    pause
)

exit /b 0
