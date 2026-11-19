[Español](#español) | [English](#english)

---

<div id="español"></div>

# Vibración Trenes (SHM)

Sistema de monitoreo de salud estructural (*Structural Health Monitoring*) para la evaluación del apriete o aflojamiento en uniones apernadas de vías y estructuras ferroviarias mediante análisis de vibraciones por impacto.

## Funcionamiento

El sensor piezoeléctrico conectado a un Arduino captura la onda mecánica generada al golpear la unión con un martillo percutor:
1. Se procesa la señal en el dominio de la frecuencia mediante FFT.
2. Se evalúa el índice de resonancia `r_high` (energía en altas frecuencias vs bajas frecuencias).
   - `r_high < 0.018`: Perno apretado / firme (energía concentrada en frecuencias bajas).
   - `r_high >= 0.018`: Perno aflojado / con holgura (resonancias en frecuencias agudas).
3. Se aplica un consenso por mayoría sobre los últimos 3 impactos (Agg3) para emitir el dictamen final.

## Requisitos

- Python 3.10 o 3.11
- Placa Arduino (UNO, Nano o compatible) conectada por USB
- Sensor piezoeléctrico de vibración / impacto
- Martillo de impacto manual
- Dependencias listadas en `requirements.txt`

## Instalación

1. Navegar a la carpeta del proyecto:
```bash
cd Vibracion-Trenes
```

2. Crear entorno virtual e instalar dependencias:
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

3. Cargar el firmware en Arduino:
Abrir el archivo `dumb_daq.ino` en el IDE de Arduino y cargarlo a la placa conectada.

## Instrucciones de Inicio

### En Windows (Inicio Rápido)
- Haz doble clic en el archivo `start_monitor.cmd` (o ejecútalo desde cmd/PowerShell).
- Detecta el entorno virtual, abre el navegador en `http://localhost:5000` y busca automáticamente el puerto COM del Arduino.
- Si deseas especificar un puerto COM específico:
  ```cmd
  start_monitor.cmd COM3
  ```

### En macOS (Inicio Rápido)
- Haz doble clic en el archivo `start_monitor.command`.
- Detecta el puerto USB del Arduino y abre Google Chrome en modo pantalla completa.

### En Linux (Inicio Rápido)
```bash
chmod +x start_monitor.sh
./start_monitor.sh
```

### Inicio Manual (Cualquier Sistema Operativo)
1. Conectar el Arduino por USB.
2. Con el entorno virtual activado, iniciar el servidor:
```bash
python indutronica_monitor_server.py
```
O indicando el puerto serial manualmente:
```bash
python indutronica_monitor_server.py COM3    # En Windows
python indutronica_monitor_server.py /dev/ttyUSB0  # En Linux
```
3. Abrir en el navegador: [http://localhost:5000](http://localhost:5000)

Al iniciar, el sistema calibra el nivel de ruido basal durante 2 segundos y queda listo para registrar impactos.

## Estructura del Proyecto

```text
Vibracion-Trenes/
├── start_monitor.cmd             # Script de inicio rápido para Windows
├── start_monitor.command         # Script de inicio rápido para macOS
├── start_monitor.sh              # Script de inicio para Linux / bash
├── indutronica_monitor_server.py # Servidor web FastAPI / WebSocket y procesamiento FFT en tiempo real
├── realtime_runner.py            # Motor de inferencia y extracción espectral en tiempo real
├── realtime_model_config.json    # Configuración de bandas espectrales y umbrales de diagnóstico
├── dumb_daq.ino                  # Firmware Arduino de adquisición ADC a alta velocidad (10 kHz)
├── requirements.txt              # Dependencias de Python
├── README.md                     # Documentación e instrucciones del proyecto
└── static/                       # Frontend local para monitoreo en vivo (sin conexión a Internet)
    ├── favicon.ico
    ├── favicon.png
    ├── favicon.svg
    ├── css/
    │   └── fonts.css             # Estilos de tipografías locales
    ├── js/
    │   └── tailwindcss.js        # Librería Tailwind CSS local
    └── fonts/                    # Archivos de fuentes tipográficas para soporte offline
```

---

<div id="english"></div>

# Railway Vibration Monitoring (SHM)

Structural Health Monitoring (SHM) system to diagnose bolt tightness or looseness in railway tracks, bogies, and structures via impact vibration response analysis.

## How It Works

A piezoelectric vibration sensor wired to an Arduino captures the acoustic and structural wave produced when striking the joint with an impact hammer:
1. The signal is transformed into the frequency domain via FFT.
2. The resonance metric `r_high` (high-frequency energy vs. low-frequency baseline energy) is calculated:
   - `r_high < 0.018`: Tight / firm joint (solid low-frequency dissipation).
   - `r_high >= 0.018`: Loose / slack joint (high-frequency ringing and harmonics).
3. A rolling majority consensus over the last 3 valid strikes (Agg3) confirms the final diagnostic.

## Requirements

- Python 3.10 or 3.11
- Arduino board (UNO, Nano, or compatible) via USB
- Piezoelectric vibration/impact sensor
- Manual percussive/impact hammer
- Dependencies listed in `requirements.txt`

## Installation

1. Navigate to the project directory:
```bash
cd Vibracion-Trenes
```

2. Set up virtual environment and install dependencies:
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

3. Flash the firmware:
Open `dumb_daq.ino` in the Arduino IDE and flash it to your connected board.

## How to Start

### On Windows (Quick Launch)
- Double-click `start_monitor.cmd` (or run from cmd/PowerShell).
- Detects the virtual environment, opens your browser at `http://localhost:5000`, and auto-connects to the Arduino COM port.
- To specify a particular COM port:
  ```cmd
  start_monitor.cmd COM3
  ```

### On macOS (Quick Launch)
- Double-click `start_monitor.command`.
- Auto-detects the Arduino serial port and launches Google Chrome in fullscreen mode.

### On Linux (Quick Launch)
```bash
chmod +x start_monitor.sh
./start_monitor.sh
```

### Manual Launch (Cross-platform)
1. Connect the Arduino over USB.
2. With the virtual environment activated, run:
```bash
python indutronica_monitor_server.py
```
Or specify the port manually:
```bash
python indutronica_monitor_server.py COM3         # On Windows
python indutronica_monitor_server.py /dev/ttyUSB0  # On Linux
```
3. Open in your browser: [http://localhost:5000](http://localhost:5000)

The system executes a 2-second baseline electrical noise calibration upon startup and is immediately ready to process impact tests.

## Project Structure

```text
Vibracion-Trenes/
├── start_monitor.cmd             # Windows quick start script
├── start_monitor.command         # macOS quick start script
├── start_monitor.sh              # Linux bash start script
├── indutronica_monitor_server.py # FastAPI / WebSocket server and real-time FFT processing
├── realtime_runner.py            # Real-time feature extraction and inference runner
├── realtime_model_config.json    # Spectral band thresholds and model configuration
├── dumb_daq.ino                  # Arduino firmware for high-speed ADC sampling (10 kHz)
├── requirements.txt              # Python dependencies
├── README.md                     # Project documentation and guide
└── static/                       # Local offline UI assets
    ├── favicon.ico
    ├── favicon.png
    ├── favicon.svg
    ├── css/
    │   └── fonts.css             # Local font styling
    ├── js/
    │   └── tailwindcss.js        # Local Tailwind CSS bundle
    └── fonts/                    # Font files for complete offline operation
```
