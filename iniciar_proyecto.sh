#!/usr/bin/env bash
# =============================================================================
# 🌌 ORION | Optical Recognition & Intelligent Obstacle Navigation
# Lanzador Automatizado de 1 Clic para macOS / Linux
# =============================================================================

set -e

echo "====================================================================="
echo "      🌌 ORION - Optical Recognition & Intelligent Obstacle Navigation"
echo "====================================================================="
echo ""

# 1. Localizar interprete de Python 3
if command -v python3 &>/dev/null; then
    PY_BIN="python3"
elif command -v python &>/dev/null; then
    PY_BIN="python"
else
    echo "❌ [ERROR] No se encontró Python 3 instalado en el sistema."
    echo "Por favor instala Python 3.10 o superior."
    exit 1
fi

# 2. Gestionar Entorno Virtual (.venv)
if [ ! -d ".venv" ]; then
    echo "ℹ️  [INFO] Creando entorno virtual en .venv..."
    $PY_BIN -m venv .venv
    echo "✅ [OK] Entorno virtual creado."
    echo ""
fi

# 3. Activar Entorno Virtual
source .venv/bin/activate

# 4. Verificar e instalar dependencias
if ! python -c "import fastapi, cv2, torch" &>/dev/null; then
    echo "ℹ️  [INFO] Instalando dependencias necesarias..."
    pip install --upgrade pip
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if [ -f "requirements-mac.txt" ]; then
            pip install -r requirements-mac.txt
        else
            pip install -r requirements.txt
        fi
    else
        pip install -r requirements.txt
    fi
    echo "✅ [OK] Dependencias instaladas."
    echo ""
fi

# 5. Gestionar archivo .env
if [ ! -f ".env" ] && [ -f ".env.example" ]; then
    echo "⚠️  [AVISO] Archivo .env no encontrado. Copiando desde .env.example..."
    cp .env.example .env
    echo "✅ [OK] Archivo .env inicializado con valores predeterminados."
    echo ""
fi

# 6. Ejecutar ORION
echo "🚀 [INICIANDO] Arrancando sistema ORION..."
echo ""
python main.py "$@"
