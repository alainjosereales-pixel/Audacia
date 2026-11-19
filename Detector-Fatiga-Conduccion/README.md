[Español](#español) | [English](#english)

---

<div id="español"></div>

# Detector de Fatiga y Somnolencia

Sistema de visión artificial para la detección en tiempo real de signos tempranos de fatiga al volante, caída de párpados (microsueños), cabeceo y bostezos mediante cámara web.

## Características

- Malla facial de 478 puntos tridimensionales con MediaPipe Face Mesh (compatible con lentes de montura normal).
- Cálculo de EAR (*Eye Aspect Ratio*) en tiempo real para medición de apertura de ojos y detección de microsueños.
- Cálculo de MAR (*Mouth Aspect Ratio*) para detección de bostezos sostenidos.
- Estimación de ángulos de orientación de la cabeza (*pitch/yaw*) para detectar cabeceo por sueño o distracciones laterales.
- Alarma acústica integrada con reproducción en el navegador.
- Servidor web FastAPI con *streaming* de video y gráficas dinámicas locales con Chart.js.

## Requisitos

- Python 3.10 o 3.11
- Cámara web USB o integrada
- Dependencias indicadas en `requirements.txt`

## Instalación

1. Navegar a la carpeta del proyecto:
```bash
cd Detector-Fatiga-Conduccion
```

2. Crear entorno virtual e instalar librerías:
```bash
# macOS / Linux
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Windows
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Instrucciones de Inicio

### En Windows (Inicio Rápido)
- Haz doble clic en el archivo `iniciar.cmd` (o ejecútalo desde la terminal de Windows).
- El script activará el entorno virtual si existe, abrirá automáticamente la interfaz web en tu navegador (`http://localhost:8000`) y pondrá en marcha el servidor de detección.

### En macOS (Inicio Rápido)
- Haz doble clic en el archivo `iniciar.command`.
- Inicia el servidor en segundo plano, abre Google Chrome en pantalla completa y activa el canal de audio.

### Inicio Manual (Cualquier Sistema Operativo)
Con el entorno virtual activado:
```bash
python web_app/server.py
```
Luego abre tu navegador en: [http://localhost:8000](http://localhost:8000)

## Estructura del Proyecto

```text
Detector-Fatiga-Conduccion/
├── iniciar.cmd               # Script de inicio rápido para Windows
├── iniciar.command           # Script de inicio rápido para macOS
├── requirements.txt          # Lista de dependencias de Python
├── LEEME.txt                 # Guía rápida en texto plano
├── README.md                 # Documentación del proyecto
├── scripts/
│   └── simulate_click.py     # Script para simular clic físico y activar audio en navegador
├── src/
│   ├── __init__.py
│   └── fatigue_analyzer.py   # Motor de análisis de fatiga (cálculo de EAR, MAR y Head Pose)
└── web_app/
    ├── server.py             # Servidor web FastAPI / WebSockets para streaming de video
    ├── better_detector.py    # Detector facial basado en MediaPipe Face Landmarker
    ├── config.yaml           # Parámetros y umbrales de detección de fatiga
    ├── face_landmarker.task  # Modelo binario oficial de MediaPipe Face Landmarker
    └── static/               # Assets frontend (HTML, JavaScript, CSS, fuentes y audio)
        ├── index.html        # Dashboard visual con indicadores de fatiga
        ├── script.js         # Lógica del cliente WebSocket y disparo de alarmas
        ├── style.css         # Estilos visuales del dashboard
        ├── chart.js          # Biblioteca de gráficos dinámicos local
        ├── music.wav         # Audio de la alarma acústica
        └── fonts/            # Tipografías locales (Inter, Outfit)
```

## Configuración

Los parámetros y umbrales pueden ajustarse en `web_app/config.yaml`:
- `FATIGUE_EAR_THRESHOLD`: Umbral de ojos cerrados (por defecto: `0.22`).
- `FATIGUE_EYE_CLOSED_CONSEC_FRAMES`: Cuadros consecutivos de ojos cerrados para disparar alarma (por defecto: `50`, ~2.5s a 20 FPS).
- `FATIGUE_MAR_THRESHOLD`: Umbral de apertura de boca para bostezo (por defecto: `0.80`).
- `FATIGUE_HEAD_PITCH_DOWN_THRESHOLD`: Inclinación hacia abajo para detectar cabeceo (por defecto: `32` grados).

---

<div id="english"></div>

# Driver Fatigue and Drowsiness Detector

Real-time computer vision system designed to monitor and detect early signs of driver fatigue, eyelid closure (microsleep), head nodding, and yawning using a standard webcam.

## Features

- 478 3D facial landmarks powered by MediaPipe Face Mesh (resilient to standard prescription glasses).
- Real-time EAR (*Eye Aspect Ratio*) computation to monitor eye closure and trigger microsleep alerts.
- Real-time MAR (*Mouth Aspect Ratio*) computation to detect sustained yawning.
- Head pose estimation (*pitch/yaw* angles) to detect drowsiness nodding and lateral distractions.
- Built-in audible cabin alarm.
- FastAPI web server streaming live video and telemetry with local Chart.js visualizations.

## Requirements

- Python 3.10 or 3.11
- USB or integrated webcam
- Dependencies listed in `requirements.txt`

## Installation

1. Navigate to the project folder:
```bash
cd Detector-Fatiga-Conduccion
```

2. Create virtual environment and install dependencies:
```bash
# macOS / Linux
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Windows
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## How to Start

### On Windows (Quick Launch)
- Double-click `iniciar.cmd` (or run it from the command prompt).
- The script automatically detects the virtual environment, launches the web interface at `http://localhost:8000`, and starts the detection server.

### On macOS (Quick Launch)
- Double-click `iniciar.command`.
- Automatically launches the background server and opens Google Chrome maximized with active audio.

### Manual Launch (Cross-platform)
With the virtual environment activated:
```bash
python web_app/server.py
```
Open in your browser: [http://localhost:8000](http://localhost:8000)

## Project Structure

```text
Detector-Fatiga-Conduccion/
├── iniciar.cmd               # Quick start script for Windows
├── iniciar.command           # Quick start script for macOS
├── requirements.txt          # Python dependencies
├── LEEME.txt                 # Quick guide in plain text
├── README.md                 # Project documentation
├── scripts/
│   └── simulate_click.py     # Script to simulate click and unlock browser audio
├── src/
│   ├── __init__.py
│   └── fatigue_analyzer.py   # Fatigue analysis engine (EAR, MAR, Head Pose)
└── web_app/
    ├── server.py             # FastAPI / WebSockets video streaming server
    ├── better_detector.py    # MediaPipe Face Landmarker wrapper
    ├── config.yaml           # Detection thresholds and parameters
    ├── face_landmarker.task  # MediaPipe Face Landmarker model binary
    └── static/               # Frontend assets (HTML, JavaScript, CSS, fonts, alarm audio)
        ├── index.html        # Real-time web dashboard
        ├── script.js         # WebSocket client logic and alert triggering
        ├── style.css         # Dashboard styles
        ├── chart.js          # Local Chart.js library
        ├── music.wav         # Acoustic alarm audio
        └── fonts/            # Local web fonts (Inter, Outfit)
```

## Configuration

Thresholds can be adjusted inside `web_app/config.yaml`:
- `FATIGUE_EAR_THRESHOLD`: Eye closure threshold (default: `0.22`).
- `FATIGUE_EYE_CLOSED_CONSEC_FRAMES`: Consecutive closed-eye frames to trigger alarm (default: `50`, ~2.5s at 20 FPS).
- `FATIGUE_MAR_THRESHOLD`: Mouth openness threshold for yawning (default: `0.80`).
- `FATIGUE_HEAD_PITCH_DOWN_THRESHOLD`: Downward tilt angle for nodding detection (default: `32` degrees).
