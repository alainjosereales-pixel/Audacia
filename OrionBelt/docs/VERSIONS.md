# 📜 Historial y Evolución de Versiones de ORION

El proyecto **ORION** ha evolucionado a través de 6 versiones principales, transitando desde un prototipo monolítico de escritorio hasta una plataforma distribuida en tiempo real y una aplicación móvil nativa para **Android** totalmente autónoma para uso de bolsillo.

---

## 📊 Matriz Comparativa de Versiones

| Característica | v1: Desktop Inicial | v2: Desktop Optimizado | v3: Web Local | v4: Web ESP32 | v5: Web XIAO ESP32S3 | v6: Mobile Android (Actual) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Entorno de Ejecución** | Ventana OpenCV | Ventana OpenCV + HUD | Navegador Web (PC) | Navegador Web (PC) | Navegador Web Reactivo (PC) | **App Nativa Android (Kotlin + Compose)** |
| **Fuente de Video** | Cámara Local / IP | Cámara Local / IP | Cámara Local / USB | ESP32-CAM (AI-Thinker) | XIAO ESP32S3 + Hot-Swap Mac | **XIAO ESP32S3 en Hotspot Móvil** |
| **Inferencia IA** | PyTorch CPU/GPU | PyTorch CPU/GPU | PyTorch CPU/GPU | PyTorch CPU/GPU | PyTorch MPS / CUDA / CPU | **TFLite On-Device (GPU OpenGL + CPU XNNPACK)** |
| **Tasa de Cuadros (FPS)** | 10 - 15 FPS | 20 - 25 FPS | 20 - 25 FPS | 8 - 15 FPS | 30 FPS (en Mac/PC) | **11 - 15 FPS sostenidos en móvil (0% drop)** |
| **Descubrimiento de IP** | ❌ Manual | ❌ Manual | N/A | ⚠️ Búsqueda lenta | ✅ Multicast / Subred | **✅ UDP Beacon `:4210` + ARP + Subred** |
| **Alertas al Usuario** | Visual | Audio simple | Audio + HUD Web | Audio + Buzzer ESP32 | Audio asíncrono + HUD Web | **✅ Háptica + Audio Estéreo Binaural + Voz TTS** |
| **Operación en Bolsillo** | ❌ Requiere PC | ❌ Requiere PC | ❌ Requiere PC | ❌ Requiere PC | ❌ Requiere Mac/PC servidor | **✅ ForegroundService (Pantalla Apagada)** |

---

## 🗂️ Detalle por Versión

### 🔹 Versión 1: Desktop Inicial (`versions/v1_desktop_initial/`)
* **Archivo principal:** [`versions/v1_desktop_initial/main.py`](../versions/v1_desktop_initial/main.py)
* **Objetivo:** Primer prototipo de validación de concepto de estimación de profundidad monocular usando MiDaS en Python.
* **Características:**
  - Pipeline síncrono: la captura, inferencia y renderizado se ejecutaban en el mismo hilo, reduciendo la fluidez a la velocidad de la red neuronal.
  - Renderizado mediante la función tradicional `cv2.imshow()`.
* **Cómo ejecutar:**
  ```bash
  python main.py --version 1
  ```

---

### 🔹 Versión 2: Desktop Optimizado (`versions/v2_desktop_optimized/`)
* **Archivo principal:** [`versions/v2_desktop_optimized/main.py`](../versions/v2_desktop_optimized/main.py)
* **Objetivo:** Maximizar el rendimiento en escritorio, desacoplando la inferencia de la tasa de refresco y agregando métricas visuales.
* **Características:**
  - Introducción del lector de último fotograma para eliminar la latencia acumulada.
  - HUD interactivo superpuesto en pantalla con histogramas, porcentaje de proximidad y FPS.
  - Implementación de audio de advertencia mediante `simpleaudio` / `winsound` / `afplay`.
* **Cómo ejecutar:**
  ```bash
  python main.py --version 2
  ```

---

### 🔹 Versión 3: Web Dashboard con Cámara Local (`versions/v3_web_local/`)
* **Archivo principal:** [`versions/v3_web_local/main.py`](../versions/v3_web_local/main.py)
* **Objetivo:** Migrar la interfaz gráfica desde ventanas nativas de escritorio hacia una arquitectura cliente-servidor accesible desde cualquier navegador.
* **Características:**
  - Servidor asíncrono con **FastAPI** y **Uvicorn**.
  - Transmisión de video comprimido mediante multipart MJPEG (`/video_feed`).
  - Telemetría en tiempo real a 25 Hz mediante **WebSockets** (`/ws/telemetry`).
  - Dashboard responsivo basado en HTML5, CSS moderno y JavaScript modular.
* **Cómo ejecutar:**
  ```bash
  python main.py --version 3
  ```

---

### 🔹 Versión 4: Web con ESP32-CAM (`versions/v4_web_esp32/`)
* **Archivo principal:** [`versions/v4_web_esp32/main.py`](../versions/v4_web_esp32/main.py)
* **Lanzador rápido:** [`versions/v4_web_esp32/start.py`](../versions/v4_web_esp32/start.py)
* **Firmware:** [`versions/v4_web_esp32/firmware/CameraWebServer/`](../versions/v4_web_esp32/firmware/CameraWebServer/)
* **Objetivo:** Integrar la primera cámara inalámbrica basada en el SoC ESP32 estándar (placa AI-Thinker).
* **Características:**
  - Firmware Arduino basado en el ejemplo clásico de Espressif con resolución SVGA/VGA.
  - Escaneo de subred para localizar el ESP32 en la red WiFi.
  - Limitaciones: Tasa de cuadros limitada (8 a 15 FPS) y sin mecanismo de respaldo si la cámara perdía conexión WiFi.
* **Cómo ejecutar:**
  ```bash
  python main.py --version 4
  # o bien:
  python versions/v4_web_esp32/start.py
  ```

---

### 🔹 Versión 5: Web XIAO ESP32S3 Sense (Servidor Mac/PC) (`versions/v5_web_xiao_esp32s3/`)
* **Archivo principal:** [`versions/v5_web_xiao_esp32s3/main.py`](../versions/v5_web_xiao_esp32s3/main.py)
* **Lanzador rápido:** [`versions/v5_web_xiao_esp32s3/start.py`](../versions/v5_web_xiao_esp32s3/start.py)
* **Firmware:** [`versions/v5_web_xiao_esp32s3/firmware/XIAO_Camera_Orion/`](../versions/v5_web_xiao_esp32s3/firmware/XIAO_Camera_Orion/)
* **Objetivo:** Solución de escritorio/servidor de alta velocidad: hardware XIAO ESP32S3 Sense a 30 FPS, tolerancia a fallos de red y arranque autónomo como servicio en macOS.
* **Características Destacadas:**
  - **Firmware dedicado para XIAO ESP32S3:** Servidor HTTP en Núcleo 1, puerto dedicado 81, triple buffer DMA en PSRAM y eliminación del bloqueo TCP `SO_SNDTIMEO`.
  - **Hot-Swap y Rollback Autónomo:** Si el XIAO se apaga, la cámara de la Mac entra en acción inmediatamente; cuando el XIAO vuelve a encenderse, conmuta de regreso sin tocar nada.
  - **Soporte Servicio macOS:** Generación del archivo `.orion_port` en arranque para sincronización con scripts automáticos `.sh`.
  - **Seguridad contra caídas en macOS:** Cierre ordenado de subprocesos `afplay` y de buffers de captura para prevenir `SIGSEGV: 11`.
* **Cómo ejecutar:**
  ```bash
  python main.py --version 5
  # o con opciones:
  python main.py --version 5 --xiao-ip 172.16.121.4 --device mps
  ```

---

### 🔹 Versión 6: Mobile Android + XIAO ESP32S3 Sense (Versión Móvil Autónoma) (`versions/v6_mobile_android/`)
* **Guía y documentación:** [`versions/v6_mobile_android/README.md`](../versions/v6_mobile_android/README.md)
* **Proyecto Android:** [`versions/v6_mobile_android/android/`](../versions/v6_mobile_android/android/)
* **Firmware Móvil:** [`versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/`](../versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/)
* **Herramientas Python:** [`versions/v6_mobile_android/tools/`](../versions/v6_mobile_android/tools/)
* **Objetivo:** Independencia total de computadoras portátiles. El cinturón ORION con XIAO ESP32S3 Sense se conecta a la Zona Wi-Fi (Hotspot) del teléfono Android, ejecutando toda la visión profunda y el sistema de alertas directamente en el bolsillo del usuario.
* **Características Destacadas:**
  - **Inferencia TFLite On-Device con Afinidad de Hilo GPU:** Ejecuta `midas_v2_1_small_256.tflite` sobre un hilo único dedicado (`OrionInferenceThread`) con `GpuDelegate` (FP16 + `SUSTAINED_SPEED`) y conmutación en caliente a CPU (`XNNPACK`, 4 hilos).
  - **Streaming MJPEG Zero-Allocation y Radio HT20:** El XIAO transmite frames desde PSRAM sin fragmentación HTTP chunked, con ancho de banda Wi-Fi fijado en `WIFI_BW_HT20` y descarte activo a `60 ms` (`SO_SNDTIMEO`) para erradicar bloqueos de cola TCP; la app decodifica mediante un **Triple Buffer** (`inBitmap`) libre de pausas de Garbage Collector y sin colisiones con el VSYNC de la UI.
  - **Navegación Espacial por 3 Zonas y Alertas Multimodales:** Divide el mapa de profundidad en `LEFT`, `CENTER` y `RIGHT` (banda central vertical del 20% al 80%), emitiendo patrones hápticos diferenciados, tonos estéreo binaurales (`AudioTrack` PCM sintetizado) y comandos de voz en español (`TextToSpeech`).
  - **Modo Bolsillo y Resiliencia Térmica:** Servicio en primer plano (`OrionForegroundService`) con `WakeLock` parcial y `WifiLock` de baja latencia, `StreamWatchdog` de reconexión automática y `ThermalController` que ajusta la cadencia si el teléfono se calienta.
  - **Ajustes Persistentes:** Control deslizable de umbral de cercanía (`40%` a `90%`, por defecto `60%`), conexión manual por IP opcional y persistencia automática en `SharedPreferences`.
* **Cómo consultar instrucciones de compilación/instalación:**
  ```bash
  python main.py --version 6
  ```
