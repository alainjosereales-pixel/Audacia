# Projection Mapper Studio

> **Estudio profesional de Video Mapping arquitectónico, deformación geométrica homográfica y exportación de video acelerada por hardware (GPU).**

[![Python Version](https://img.shields.io/badge/Python-3.9%20%7C%203.10%20%7C%203.11%20%7C%203.12%20%7C%203.13-blue.svg)](https://www.python.org/)
[![OpenCV](https://img.shields.io/badge/OpenCV-5.0.0-green.svg)](https://opencv.org/)
[![NumPy](https://img.shields.io/badge/NumPy-2.0+-orange.svg)](https://numpy.org/)
[![FFmpeg](https://img.shields.io/badge/FFmpeg-Hardware%20Accelerated-red.svg)](https://ffmpeg.org/)
[![License](https://img.shields.io/badge/Licencia-AudacIA%20Research-purple.svg)](#licencia-y-créditos)

---

## 📋 Tabla de Contenidos

1. [Descripción General y Problema que Resuelve](#-descripción-general-y-problema-que-resuelve)
2. [Tecnologías Principales](#-tecnologías-principales)
3. [Estructura del Proyecto](#-estructura-del-proyecto)
4. [Requisitos Previos del Sistema](#-requisitos-previos-del-sistema)
5. [Guía de Instalación Paso a Paso](#-guía-de-instalación-paso-a-paso)
6. [Instrucciones de Ejecución](#-instrucciones-de-ejecución)
   - [Modo 1 Clic (Windows)](#modo-1-clic-windows)
   - [Modo Consola / CLI con Parámetros](#modo-consola--cli-con-parámetros)
7. [Variables de Entorno (.env)](#-variables-de-entorno-env)
8. [Atajos de Teclado y Flujo Operativo](#-atajos-de-teclado-y-flujo-operativo)
9. [Especificación de la API REST y Protocolos](#-especificación-de-la-api-rest-y-protocolos)
10. [Batería de Pruebas y Control de Calidad](#-batería-de-pruebas-y-control-de-calidad)
11. [Documentación Técnica Detallada](#-documentación-técnica-detallada)
12. [Licencia y Créditos](#-licencia-y-créditos)

---

## 🎯 Descripción General y Problema que Resuelve

El software tradicional de Video Mapping comercial (como Resolume Arena, MadMapper o HeavyM) presenta dos grandes barreras:
1. **Costos prohibitivos y licencias restrictivas:** Licencias que oscilan entre $400 y $900 USD por máquina.
2. **Dependencia crítica de hardware en vivo:** Para exposiciones museísticas, vitrinas comerciales, fachadas arquitectónicas o eventos continuos, el operador está obligado a dejar una computadora costosa ($1,500+ USD) conectada y encendida toda la noche exclusivamente para alimentar el proyector.

**Projection Mapper Studio** resuelve este problema mediante un flujo de trabajo híbrido de doble propósito:
* **Calibración y Proyección en Vivo (0 ms):** Permite calibrar geometrías poligonales y curvas Bézier sobre objetos reales en tiempo real mediante una arquitectura desacoplada de doble ventana (Ventana de Control del Operador + Ventana de Proyector a pantalla completa) comunicadas mediante `BroadcastChannel API`.
* **Exportador Standalone MP4 Pre-Mapeado (Autonomía Total):** Renderiza fotograma a fotograma la escena calibrada, aplicando la matriz de homografía 3x3 exacta, máscaras de recorte y contornos vectoriales animados directamente en un archivo de video `.mp4` codificado por hardware GPU en bucle continuo perfecto (*seamless loop*). El video resultante se copia a una memoria USB y se conecta directamente al proyector, operando de forma 100% autónoma sin requerir computadoras conectadas.

---

## 🛠️ Tecnologías Principales

| Componente | Tecnología | Propósito |
|---|---|---|
| **Backend HTTP & REST** | Python 3 (ThreadingHTTPServer) | Servidor multihilo local, API REST de persistencia y streaming de video HTTP 206 por rangos de bytes. |
| **Visión por Computador** | OpenCV (cv2) | Transformaciones de perspectiva 3x3 (`getPerspectiveTransform`, `warpPerspective`), rasterizado y manejo de canal alfa transparente en PNG. |
| **Cálculo Matricial** | NumPy | Operaciones vectorizadas sobre arreglos numéricos, cálculo de longitud de arco y cuantización periódica de fase temporal. |
| **Codificación de Video** | FFmpeg | Aceleración por hardware GPU (NVIDIA NVENC, AMD AMF, Intel QuickSync, Windows Media Foundation) con fallback CPU (`libx264`). |
| **Frontend de Control** | HTML5 / CSS3 / Vanilla JS | Interfaz de calibración modular, panel inspector, gestor de capas y visualizador de canvas SVG dinámico. |
| **Ventana de Proyección** | CSS 3D Transforms + Canvas | Proyección a pantalla completa sin elementos de interfaz, con negro puro (`#000000`) y latencia cero. |
| **Sincronización** | BroadcastChannel API | Canal IPC nativo entre pestañas/ventanas del navegador sin sobrecarga de red. |

---

## 📁 Estructura del Proyecto

```text
projection-mapper/
├── INICIAR.bat              # Lanzador 1-clic para Windows (auto-detecta Python / venv)
├── DETENER.bat              # Script de apagado limpio y liberación de puertos
├── main.py                  # Punto de entrada unificado multiplataforma con soporte CLI
├── launcher.py              # Detección de monitores/proyector y arranque asistido
├── server.py                # Servidor HTTP multihilo, streaming de video y API REST
├── video_exporter.py        # Motor de composición y renderizado de video acelerado por GPU
├── contour_exporter.py      # Motor determinista de trazado vectorial y animaciones de contorno
├── requirements.txt         # Dependencias de producción Python organizadas
├── pyproject.toml           # Especificación de empaquetado estándar (PEP 517 / PEP 621)
├── package.json             # Metadatos y scripts para pruebas con Node.js
├── .env.example             # Plantilla completa de variables de entorno comentadas
├── .gitignore               # Reglas de exclusión para Git y control de versiones
├── README.md                # Documentación principal del repositorio
│
├── bin/                     # Binarios portables (ej. ffmpeg.exe local en versión portable)
├── documentation/           # Manuales técnicos y guías operativas
│   ├── GUIA_DE_USUARIO.md       # Manual operativo para usuarios y técnicos de eventos
│   └── DOCUMENTACION_TECNICA.md # Especificación matemática y arquitectura interna
│
├── media/                   # Contenedor de archivos multimedia
│   ├── images/              # Imágenes importadas (.png, .jpg, .svg)
│   ├── videos/              # Clips de video asignados a figuras (.mp4, .webm)
│   └── exports/             # Videos renderizados (.mp4) listos para USB
│
├── scenes/                  # Persistencia de escenas (.json) y paquetes portables (.pmap)
├── tests/                   # Suite de pruebas unitarias y de integración
│   ├── test_contour_exporter.py # Pruebas del motor de renderizado Python y homografía
│   └── test_contour_model.mjs   # Pruebas del modelo geométrico y motor SVG en Node.js
│
└── static/                  # Frontend de la aplicación web
    ├── index.html           # Vista del Operador (Ventana de Control)
    ├── projector.html       # Vista del Proyector (Salida de Proyección Limpia)
    ├── css/                 # Hojas de estilo modulares
    │   ├── control.css      # Estilos de la interfaz de control y temas
    │   └── projector.css    # Estilos de la ventana de proyección a pantalla completa
    └── js/                  # Módulos JavaScript (ES6 Modules)
        ├── app_env.js       # Detección de entorno (Desktop Local vs Demo Web)
        ├── contour_engine.js# Motor vectorial de contornos y animaciones perimetrales
        ├── editor_canvas.js # Motor interactivo del lienzo SVG de diseño
        ├── export_modal.js  # Interfaz y monitor de progreso de renderizado de video
        ├── icons.js         # Catálogo de iconos vectoriales SVG
        ├── math_warp.js     # Algoritmos de homografía 3D y evaluación de curvas Bézier
        ├── polygon_model.js # Modelos de datos para polígonos, capas y escenas
        ├── projector_view.js# Motor de renderizado del proyector (CSS 3D + SVG)
        ├── scene_manager.js # Gestor de escenas, empaquetador .pmap e importador
        ├── sync.js          # Bus de sincronización BroadcastChannel (0 ms)
        └── ui_controller.js # Controlador principal de eventos, atajos y paneles
```

---

## 💻 Requisitos Previos del Sistema

### Entorno de Ejecución
* **Sistema Operativo:**
  * Windows 10 u 11 (64-bit) (Recomendado para aceleración por GPU y detección automática de monitores).
  * Linux (Ubuntu 20.04+, Debian, Fedora) o macOS (12+) para entornos de desarrollo y servidor.
* **Python:** Versión 3.9 o superior (3.9, 3.10, 3.11, 3.12, 3.13 de 64-bit).
* **Node.js (Opcional):** Versión 18.0 o superior (solo requerido para ejecutar la suite de pruebas del frontend `test_contour_model.mjs`).

### Hardware Recomendado
* **Procesador:** Intel Core i5 / AMD Ryzen 5 o superior.
* **Memoria RAM:** 8 GB mínimo (16 GB recomendado para escenas complejas con múltiples videos 4K).
* **Tarjeta Gráfica (GPU):**
  * NVIDIA GeForce (GTX 1050 o superior) con soporte NVENC.
  * AMD Radeon con soporte AMF.
  * Gráficos integrados Intel con soporte QuickSync (QSV).
* **Pantallas:** Configuración de doble monitor (Pantalla 1: Laptop/Control; Pantalla 2: Proyector extendido).

---

## 🚀 Guía de Instalación Paso a Paso

Si trabajas con el código fuente desde el repositorio Git, sigue estos pasos:

### 1. Clonar el Repositorio
```bash
git clone https://github.com/jhanhrndz/projection-mapper.git
cd projection-mapper
```

### 2. Crear y Activar el Entorno Virtual
* **En Windows (PowerShell / CMD):**
  ```powershell
  python -m venv .venv
  .\.venv\Scripts\activate
  ```
* **En Linux / macOS:**
  ```bash
  python3 -m venv .venv
  source .venv/bin/activate
  ```

### 3. Instalar Dependencias de Producción
```bash
pip install -r requirements.txt
```

### 4. Configurar Variables de Entorno
Copia la plantilla `.env.example` a un archivo local `.env`:
* **En Windows:**
  ```cmd
  copy .env.example .env
  ```
* **En Linux / macOS:**
  ```bash
  cp .env.example .env
  ```
*(Opcional: edita el archivo `.env` con un editor de texto si deseas cambiar el puerto por defecto `8085` o forzar un códec de video).*

### 5. Verificar Instalación
Ejecuta la verificación automática de dependencias:
```bash
python main.py --check-deps
```
Si todo está correcto, responderá:
`[OK] Todas las dependencias esenciales (OpenCV, NumPy) están correctamente instaladas.`

---

## ⚡ Instrucciones de Ejecución

### Modo 1 Clic (Windows)
1. **Iniciar el Software:** Haz doble clic sobre [`INICIAR.bat`](./INICIAR.bat).  
   *Detecta el entorno virtual, instala dependencias si faltan, libera el puerto si estaba bloqueado, inicia el servidor en segundo plano y abre la Ventana de Control en tu navegador.*
2. **Detener el Software:** Haz doble clic sobre [`DETENER.bat`](./DETENER.bat) o utiliza el botón **"Apagar Servidor"** desde el menú contextual (`⋮`) de la interfaz.

---

### Modo Consola / CLI con Parámetros

Puedes iniciar el sistema desde la terminal utilizando `main.py` con diversos argumentos:

```bash
# Inicio estándar con configuración por defecto
python main.py

# Iniciar en un puerto específico (ej. 9000)
python main.py --port 9000

# Enlazar a todas las interfaces de red para acceso desde red local (LAN)
python main.py --host 0.0.0.0 --port 8085

# Modo sin cabeza (Headless, no abre el navegador automáticamente)
python main.py --no-browser

# Ver versión instalada
python main.py --version
```

#### Parámetros Disponibles en `main.py`:
| Parámetro | Abreviatura | Descripción | Valor por Defecto |
|---|---|---|---|
| `--port` | `-p` | Puerto TCP donde escuchará el servidor HTTP | `8085` (o valor en `.env`) |
| `--host` | `-H` | Dirección IP de enlace de red | `127.0.0.1` (o valor en `.env`) |
| `--no-browser` | | Evita abrir el navegador automáticamente al arrancar | `False` |
| `--check-deps` | | Comprueba las dependencias requeridas y finaliza | N/A |
| `--version` | `-v` | Muestra la versión actual de la aplicación | `v2.9.2` |

---

## 🌐 Variables de Entorno (.env)

El archivo `.env` permite configurar el comportamiento del servidor y del motor de renderizado sin tocar el código fuente:

| Variable | Tipo | Valor por Defecto | Descripción |
|---|---|---|---|
| `PORT` | Entero | `8085` | Puerto TCP de la interfaz web y la API REST. |
| `HOST` | String | `127.0.0.1` | Dirección IP de escucha (`127.0.0.1` solo local, `0.0.0.0` para red LAN). |
| `APP_ENV` | String | `production` | Entorno de ejecución (`production`, `development`, `portable`). |
| `MEDIA_DIR` | Ruta | `./media` | Carpeta de almacenamiento de videos e imágenes subidos. |
| `SCENES_DIR` | Ruta | `./scenes` | Carpeta de persistencia de escenas `.json` y paquetes `.pmap`. |
| `EXPORTS_DIR` | Ruta | `./media/exports` | Carpeta de destino de los videos renderizados `.mp4`. |
| `FFMPEG_PATH` | Ruta | *(Auto-detectado)* | Ruta al ejecutable de FFmpeg (si no está en `./bin` o en el PATH). |
| `FFMPEG_ENCODER_OVERRIDE` | String | `auto` | Forzar codificador: `auto`, `h264_nvenc`, `h264_amf`, `h264_qsv`, `h264_mf`, `libx264`. |
| `AUTO_OPEN_BROWSER` | Booleano | `true` | Abrir el navegador automáticamente al iniciar (`true` / `false`). |
| `DEBUG` | Booleano | `false` | Mostrar registros detallados de depuración en consola (`true` / `false`). |

---

## ⌨️ Atajos de Teclado y Flujo Operativo

| Atajo | Acción | Contexto |
|---|---|---|
| <kbd>V</kbd> | Modo Selección / Edición de Vértices | Lienzo de diseño |
| <kbd>P</kbd> | Herramienta Dibujar Polígono Recto Libre | Creación geométrica |
| <kbd>C</kbd> | Herramienta Dibujar Polígono Curvo Bézier | Creación geométrica |
| <kbd>Espacio</kbd> o <kbd>M</kbd> | Alternar entre Modo Calibración y Modo Presentación | Lienzo de diseño |
| <kbd>Enter</kbd> | Cerrar y completar el polígono en dibujo | Creación geométrica |
| <kbd>Esc</kbd> | Cancelar trazado actual / Deseleccionar figura | General |
| <kbd>Ctrl + S</kbd> | Guardado rápido de la escena actual | Persistencia |
| <kbd>Ctrl + Shift + S</kbd> | Guardar escena como nuevo archivo | Persistencia |
| <kbd>Ctrl + O</kbd> | Abrir Gestor de Escenas y Paquetes | Persistencia |
| <kbd>Ctrl + Z</kbd> | Deshacer última modificación | Historial de edición |
| <kbd>Ctrl + Y</kbd> | Rehacer modificación deshecha | Historial de edición |
| <kbd>Supr</kbd> / <kbd>Del</kbd> | Eliminar vértice seleccionado o figura activa | Edición |
| <kbd>Shift + Clic</kbd> | Selección múltiple de vértices | Edición |
| <kbd>Flechas (↑ ↓ ← →)</kbd> | Micro-ajuste milimétrico de vértices (1 píxel) | Calibración fina |
| <kbd>Shift + Flechas</kbd> | Ajuste rápido de vértices (10 píxeles) | Calibración fina |
| <kbd>?</kbd> o <kbd>F1</kbd> | Abrir modal de ayuda interactiva | General |
| <kbd>F</kbd> o <kbd>F11</kbd> | Activar / Desactivar pantalla completa | Ventana del Proyector |

---

## 🔌 Especificación de la API REST y Protocolos

El servidor Python provee endpoints REST nativos bajo formato JSON:

### Gestión de Escenas y Proyectos
* `GET /api/scenes` - Lista todas las escenas guardadas en el directorio `scenes/`.
* `POST /api/scenes/save` - Guarda o actualiza una escena en formato JSON.
* `POST /api/scenes/load` - Carga una escena específica y la transmite a la interfaz.
* `POST /api/scenes/delete` - Elimina un archivo de escena del disco.
* `GET /api/project/export?name={nombre}` - Empaqueta la escena y **todos sus archivos de video e imágenes asociados** en un único archivo comprimido portable `.pmap`.
* `POST /api/project/import` - Descomprime un archivo `.pmap`, registra los medios en `media/` y carga la escena lista para proyectar.

### Renderizado y Exportación de Video
* `POST /api/export` - Inicia un proceso asíncrono de renderizado de video MP4 pre-mapeado con homografía por hardware.
* `GET /api/export/status` - Consulta el progreso porcentual (`0.0%` a `100.0%`), fotograma actual y tiempo estimado restante (ETA).
* `POST /api/export/cancel` - Cancela el proceso de renderizado y purga archivos temporales incompletos.
* `GET /api/exports` - Lista los archivos de video `.mp4` disponibles en `media/exports/`.

### Control del Sistema
* `POST /api/system/shutdown` - Apaga ordenadamente el servidor Python y libera los sockets de red.
* `GET /media/*` - Streaming de video e imágenes con soporte para cabeceras HTTP `Range` (HTTP 206 Partial Content) para permitir rebobinado instantáneo.

---

## 🧪 Batería de Pruebas y Control de Calidad

El proyecto cuenta con suites de pruebas automatizadas que cubren tanto el motor de backend (Python) como la lógica matemática del frontend (Node.js).

### Ejecutar Pruebas de Backend (Python)
Valida la homografía de perspectiva, cálculo de arcos, canal alfa transparente en PNG, bucles continuos (seamless loops) y las 66 combinaciones de estilos y animaciones:
```bash
python -m unittest discover -s tests -v
```

### Ejecutar Pruebas de Frontend (Node.js)
Valida el modelo de polígonos, cuantización de trazos, curvas Bézier y emulación del motor SVG en entorno sin navegador:
```bash
node --test tests/test_contour_model.mjs
# O utilizando npm:
npm test
```

---

## 📚 Documentación Técnica Detallada

Para profundizar en la arquitectura matemática y operativa del sistema:
* 📘 [**Guía de Usuario**](./documentation/GUIA_DE_USUARIO.md): Manual paso a paso para operadores, configuración de proyectores, calibración de figuras y técnicas para shows en vivo.
* 🛠️ [**Documentación Técnica Oficial**](./documentation/DOCUMENTACION_TECNICA.md): Fundamentos matemáticos de matrices de perspectiva 3x3, algoritmo De Casteljau para curvas Bézier, cuantización temporal de fases y arquitectura de renderizado.

---

## 📄 Licencia y Créditos

Desarrollado para **AudacIA** y el centro de investigación.  
Uso autorizado para proyectos de investigación, producciones visuales interactivas, eventos culturales y espectáculos de video mapping arquitectónico.
