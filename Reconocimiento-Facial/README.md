[Español](#español) | [English](#english)

---

<div id="español"></div>

# Reconocimiento Facial

Sistema de visión por computador, reconocimiento biométrico y síntesis de voz offline para interacción en tiempo real. Utiliza YuNet para detección facial, SFace para reconocimiento biométrico (embeddings de 128 dimensiones) y Kokoro-82M (ONNX) para síntesis de voz local en español.

## Requisitos

- Python 3.10 o 3.11
- Cámara web (USB o integrada)
- macOS / Linux / Windows
- `espeak-ng` (en macOS: `brew install espeak-ng`, requerido para el motor fonético de Kokoro)

## Instalación

1. Navegar a la carpeta del proyecto:
```bash
cd Reconocimiento-Facial
```

2. Crear y activar entorno virtual:
```bash
# macOS / Linux
python3 -m venv .venv
source .venv/bin/activate

# Windows
python -m venv .venv
.venv\Scripts\activate
```

3. Instalar dependencias:
```bash
pip install -r requirements.txt
```

4. Descargar el modelo de voz Kokoro (opcional si usas síntesis Kokoro):
El modelo `kokoro-v1.0.onnx` no está incluido en el repositorio por temas de peso. Para descargarlo:
```bash
python scripts/download_models.py
```

## Instrucciones de Inicio

### En Windows (Inicio Rápido)
- Haz doble clic en el archivo `run_web.cmd` (o ejecútalo desde cmd/PowerShell).
- Activa el entorno virtual si existe, abre automáticamente el navegador en `http://localhost:8000` e inicia el dashboard biométrico.

### En macOS (Inicio Rápido)
- Haz doble clic en el archivo `run_web.command`.
- Inicia el servidor FastAPI / Uvicorn y abre el navegador por defecto cuando la cámara y la API estén listas.

### Inicio Manual

#### 1. Panel Web (FastAPI)
Para iniciar el servidor con streaming de video y panel de administración web:
```bash
python run_web.py
```
O especificando el puerto:
```bash
python run_web.py --port 8000
```
Disponible en: [http://localhost:8000](http://localhost:8000)

#### 2. Modo Visor Directo (OpenCV)
Para correr el sistema en una ventana de escritorio nativa con métricas HUD y cuadros de seguimiento:
```bash
python main.py
```

Controles de teclado en el visor:
- `q` / `ESC`: Salir
- `s`: Guardar captura del frame actual
- `b`: Imprimir telemetría en consola
- `+` / `-`: Subir o bajar umbral de detección

### Registro de Personas (Enrolamiento)
Para enrolar un nuevo rostro en la base de datos local usando la cámara web:
```bash
python scripts/register_person.py --name "Nombre"
```
Para listar las personas registradas:
```bash
python scripts/register_person.py --list
```

## Estructura del Proyecto

```text
Reconocimiento-Facial/
├── run_web.cmd               # Script de inicio rápido para Windows
├── run_web.command           # Script de inicio rápido para macOS
├── run_web.py                # Lanzador del servidor web FastAPI
├── main.py                   # Visor nativo OpenCV con HUD y control de umbrales
├── requirements.txt          # Dependencias de Python con soporte multiplataforma
├── README.md                 # Documentación e instrucciones del proyecto
├── audacia/app/              # Arquitectura modular del pipeline de visión e interacción
│   ├── attention/            # Estimación de atención y pose de cabeza (pitch/yaw/roll)
│   ├── audio/                # Síntesis TTS (Kokoro ONNX / pyttsx3 fallback) y cola de audio
│   ├── camera/               # Captura multihilo optimizada para streaming
│   ├── core/                 # Registro de eventos, timers y estructuras base
│   ├── detection/            # Detección de rostros y landmarks con YuNet
│   ├── diagnostics/          # Medición de FPS, latencia y consumo de memoria
│   ├── interaction/          # Lógica conversacional, saludos y control de sesiones
│   ├── motion/               # Detección de movimiento en el encuadre
│   ├── recognition/          # Reconocimiento y cálculo de similitud coseno con SFace
│   ├── storage/              # Persistencia SQLite para identidades y embeddings
│   └── tracking/             # Algoritmo de seguimiento continuo Kalman / SORT
├── config/
│   └── audacia_config.yaml   # Configuración de cámara, umbrales y frases
├── data/                     # Base de datos SQLite, caché de audio y snapshots
├── models/                   # Pesos ONNX para YuNet, SFace y voces de Kokoro
├── scripts/                  # Utilidades auxiliares (enrolamiento, benchmark, descarga)
└── web/                      # Backend FastAPI y frontend interactivo
    ├── app.py                # Definición de rutas REST y WebSockets
    ├── pipeline_bridge.py    # Enlace asíncrono entre el pipeline de OpenCV y la web
    ├── static/               # CSS y JavaScript del dashboard en tiempo real
    └── templates/            # Plantilla HTML con gráficos y controles
```

## Configuración

Parámetros de tracking, saludos y tiempos de espera disponibles en `config/audacia_config.yaml`.

---

<div id="english"></div>

# Facial Recognition

Computer vision system for face detection, tracking, biometric recognition, and offline text-to-speech synthesis for real-time human-robot interaction. Powered by YuNet for face detection, SFace for biometric recognition (128-dimensional embeddings), and Kokoro-82M (ONNX) for local Spanish voice synthesis.

## Requirements

- Python 3.10 or 3.11
- USB or integrated webcam
- macOS / Linux / Windows
- `espeak-ng` (on macOS: `brew install espeak-ng`, required by the phonemizer)

## Installation

1. Navigate to the project directory:
```bash
cd Reconocimiento-Facial
```

2. Create and activate a virtual environment:
```bash
# macOS / Linux
python3 -m venv .venv
source .venv/bin/activate

# Windows
python -m venv .venv
.venv\Scripts\activate
```

3. Install dependencies:
```bash
pip install -r requirements.txt
```

4. Download the Kokoro voice model (optional if using Kokoro TTS):
The model file `kokoro-v1.0.onnx` is not tracked in the repository due to file size limits. Download it by running:
```bash
python scripts/download_models.py
```

## How to Start

### On Windows (Quick Launch)
- Double-click `run_web.cmd` (or execute from cmd/PowerShell).
- Automatically activates virtual environment, opens your default browser at `http://localhost:8000`, and starts the server.

### On macOS (Quick Launch)
- Double-click `run_web.command`.
- Launches the FastAPI / Uvicorn server and automatically opens the browser once the camera and API are ready.

### Manual Launch

#### 1. Web Dashboard (FastAPI)
Starts the video streaming server and web dashboard:
```bash
python run_web.py
```
Or specify a custom port:
```bash
python run_web.py --port 8000
```
Access URL: [http://localhost:8000](http://localhost:8000)

#### 2. Direct HUD Window (OpenCV)
Runs the standalone desktop interface with real-time metrics:
```bash
python main.py
```

Keyboard shortcuts:
- `q` / `ESC`: Quit
- `s`: Save frame snapshot
- `b`: Print telemetry benchmarks to console
- `+` / `-`: Increase or decrease detection threshold

### Enrolling New Faces
To enroll a new identity using the webcam:
```bash
python scripts/register_person.py --name "Name"
```
To list all registered identities:
```bash
python scripts/register_person.py --list
```

## Project Structure

```text
Reconocimiento-Facial/
├── run_web.cmd               # Windows quick-launch script
├── run_web.command           # macOS quick-launch script
├── run_web.py                # FastAPI web server entrypoint
├── main.py                   # Desktop OpenCV viewer with HUD and keyboard shortcuts
├── requirements.txt          # Python dependencies with cross-platform markers
├── README.md                 # Project documentation and guide
├── audacia/app/              # Vision pipeline and interaction architecture
│   ├── attention/            # Attention estimation and head pose (pitch/yaw/roll)
│   ├── audio/                # TTS synthesis (Kokoro ONNX / pyttsx3 fallback) and queue
│   ├── camera/               # Multi-threaded camera capture
│   ├── core/                 # Event logging, timers, and foundational structs
│   ├── detection/            # Face detection and landmarks with YuNet
│   ├── diagnostics/          # Performance benchmarks (FPS, latency, memory)
│   ├── interaction/          # Conversational flow, greetings, and session state
│   ├── motion/               # Frame motion detection
│   ├── recognition/          # Biometric feature extraction and matching with SFace
│   ├── storage/              # SQLite persistence for identities and embeddings
│   └── tracking/             # Continuous tracking with Kalman filter / SORT
├── config/
│   └── audacia_config.yaml   # Camera, threshold, and greeting configurations
├── data/                     # SQLite database, audio cache, and snapshots
├── models/                   # ONNX weights for YuNet, SFace, and Kokoro voices
├── scripts/                  # Utility scripts (enrolment, benchmarks, model download)
└── web/                      # FastAPI backend and interactive frontend
    ├── app.py                # REST endpoints and WebSocket definitions
    ├── pipeline_bridge.py    # Asynchronous bridge between vision engine and web UI
    ├── static/               # Real-time dashboard CSS and JavaScript
    └── templates/            # HTML template with live gauges and controls
```

## Configuration

Detection thresholds, custom greeting phrases, and interaction cooldowns can be customized in `config/audacia_config.yaml`.
