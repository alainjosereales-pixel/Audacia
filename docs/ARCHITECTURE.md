# 🏗️ Arquitectura del Sistema ORION

**ORION** (*Optical Recognition & Intelligent Obstacle Navigation*) es una plataforma de visión por computadora en tiempo real diseñada para la detección proactiva de obstáculos y asistencia de navegación mediante estimación de profundidad monocular y telemetría de baja latencia.

---

## 📐 Diagrama de Flujo General

```mermaid
flowchart TD
    subgraph Captura["Módulo de Entrada de Video"]
        CamXIAO["XIAO ESP32S3 Sense (WiFi MJPEG - Core 1)"]
        CamLocal["Cámara Integrada / USB (AVFoundation / DShow)"]
        SourceMgr["SourceManager (Máquina de Estados Hot-Swap)"]
        CamXIAO -->|Port 81 Stream| SourceMgr
        CamLocal -->|Fallback Seguro| SourceMgr
    end

    subgraph Pipeline["Motor de Procesamiento (OrionEngine)"]
        Reader["LatestFrameReader (Lector Desacoplado 1-Frame)"]
        WorkerAI["_ai_worker_loop (Hilo de Inferencia Asíncrono)"]
        MiDaS["Modelo MiDaS (Inferencia Torch / MPS / CUDA)"]
        PostProc["Debanding, Suavizado Temporal y Umbralización"]
        
        SourceMgr --> Reader
        Reader --> WorkerAI
        WorkerAI --> MiDaS
        MiDaS --> PostProc
    end

    subgraph Salidas["Módulos de Salida y Notificación"]
        Audio["LocalAlarmSound (afplay / winsound No Bloqueante)"]
        Telemetry["Telemetry Hub (WebSockets ~25 Hz)"]
        VideoStream["MJPEG HTTP Stream (/video_feed)"]
        
        PostProc --> Audio
        PostProc --> Telemetry
        PostProc --> VideoStream
    end

    subgraph Cliente["Frontend Web Dashboard"]
        Browser["Navegador Web (Canvas / HTML5 / JS WebSocket)"]
        Telemetry --> Browser
        VideoStream --> Browser
    end
```

---

## 1. Pipeline de Captura y Desacoplamiento de Latencia

Uno de los problemas más comunes en el procesamiento de video en tiempo real con redes neuronales es el desfase o retraso acumulativo (*frame lag*). Si la cámara entrega 30 FPS pero el modelo procesa a 15-20 FPS, los buffers convencionales de OpenCV (`cv2.VideoCapture`) acumulan fotogramas atrasados.

### LatestFrameReader (Lector de Último Fotograma)
* Corre en un hilo dedicado con prioridad alta.
* Consume constantemente el flujo entrante y **descarta inmediatamente cualquier fotograma viejo**, manteniendo únicamente el fotograma más reciente en memoria.
* Cuando el hilo de inferencia solicita un fotograma, obtiene exactamente el instante actual sin latencia acumulada (latencia < 33 ms).

---

## 2. Inferencia de Profundidad Monocular (MiDaS)

ORION emplea la arquitectura **MiDaS** (Ranftl et al., Intel ISL) para inferir mapas de profundidad densos a partir de una única cámara RGB:

1. **Preprocesamiento:**
   - Redimensión al ancho de procesamiento (`--proc-width`, por defecto 320 px o 480 px).
   - Conversión de espacio de color BGR a RGB y normalización mediante transforms de Torch Hub.
2. **Ejecución en Hardware:**
   - **Apple Silicon (Mac M1/M2/M3):** Aceleración por Metal Performance Shaders (`mps`) con fallback automático habilitado (`PYTORCH_ENABLE_MPS_FALLBACK=1`).
   - **NVIDIA GPU:** Aceleración por CUDA con `torch.backends.cudnn.benchmark = True`.
   - **CPU:** Optimización multihilo configurando `torch.set_num_threads`.
3. **Postprocesamiento:**
   - **Debanding:** Reducción de artefactos de gradiente mediante filtros de desenfoque gaussianos de bajo costo.
   - **Filtro Temporal IIR:** Suavizado exponencial entre fotogramas consecutivos ($D_t = \alpha D_t + (1-\alpha) D_{t-1}$) para eliminar parpadeo (*flicker*).
   - **Cálculo de Proximidad:** Segmentación por percentiles y evaluación contra `--near-threshold`.

---

## 3. Máquina de Estados de Conmutación en Caliente (Hot-Swap)

El sistema implementa una arquitectura híbrida de alta disponibilidad:

```mermaid
stateDiagram-v2
    [*] --> Init
    Init --> XIAO_ACTIVE: Intento de conexión XIAO
    Init --> LOCAL_ACTIVE: XIAO no disponible al arranque

    state XIAO_ACTIVE {
        [*] --> Streaming_XIAO
        Streaming_XIAO --> Timeout_Check: Heartbeat cada 1s
    }

    state LOCAL_ACTIVE {
        [*] --> Streaming_Local
        Streaming_Local --> Auto_Discovery: Escaneo en segundo plano cada 3s
    }

    XIAO_ACTIVE --> LOCAL_ACTIVE: Fallo de stream / Socket cerrado (Rollback < 1s)
    LOCAL_ACTIVE --> XIAO_ACTIVE: XIAO detectado y stream verificado (Hot-Swap instantáneo)
```

### Transición Segura entre Dispositivos de Captura
* **Prevención de `SIGSEGV: 11` (macOS AVFoundation):** Al cambiar de la cámara web integrada a la cámara de red, el hilo de lectura se detiene (`thread.join()`) **antes** de liberar el puntero nativo de OpenCV (`cap.release()`), evitando accesos a memoria liberada (*use-after-free*) en los drivers multimedia de macOS.
* **Descubrimiento Dinámico de IP:** Si el router DHCP cambia la IP del XIAO, el subsistema [`XiaoDiscoveryManager`](../utils/xiao_discovery.py) escanea la subred en segundo plano, actualiza la dirección en tiempo real y conmuta la fuente de captura sin interrumpir el servidor HTTP ni la conexión de los clientes web.

---

## 4. Sistema de Audio Asíncrono sin Bloqueo

Para alertar al usuario de obstáculos críticos sin degradar los FPS de inferencia ni de video:

* **macOS:** Se utiliza el comando nativo `afplay` en un subproceso desacoplado. Para prevenir la acumulación de procesos zombis, se ejecuta un monitor ligero que limpia los procesos terminados mediante llamadas no bloqueantes `poll()`.
* **Windows:** Se emplea síntesis de onda senoidal en memoria mediante el módulo estándar `wave` y `winsound.PlaySound(..., SND_ASYNC | SND_MEMORY)`.
* **Cooldown de Alarma:** Intervalo mínimo configurable (por defecto 0.8 s) para evitar saturación acústica continua.

---

## 5. Arquitectura Móvil Autónoma (ORION v6: Android + XIAO ESP32S3)

En **ORION v6** (`versions/v6_mobile_android/`), toda la computación se traslada al teléfono Android del usuario, operando sobre la **Zona Wi-Fi (Hotspot)** local del dispositivo sin depender de servidores externos ni conexión a Internet.

```mermaid
flowchart TD
    subgraph Cinturon["Cinturón ORION (XIAO ESP32S3 Sense)"]
        OV2640["Sensor OV2640 + Triple Buffer DMA PSRAM"]
        Core1["Core 1: Servidor MJPEG (:81/stream) - HT20 + SO_SNDTIMEO 60ms"]
        Core0["Core 0: Control HTTP (:80/status, /led, /framesize) + Beacon UDP :4210"]
        OV2640 --> Core1
    end

    subgraph AndroidApp["Teléfono Android (OrionForegroundService)"]
        Discovery["XiaoDiscovery (UDP :4210 + Tabla ARP + Barrido Subred)"]
        MjpegReader["MjpegStreamReader (Triple Buffer inBitmap Zero-Allocation)"]
        Channel["Channel(CONFLATED) - Descarte de Frames Antiguos"]
        GpuThread["OrionInferenceThread (Hilo Dedicado OpenGL / CPU XNNPACK)"]
        DepthEst["DepthEstimator (MiDaS v2.1 Small 256x256 + Filtro Temporal IIR)"]
        Zones["ZoneAnalyzer (LEFT / CENTER / RIGHT - Percentil 90)"]
        Policy["AlertPolicy (Umbral Configurable 40%-90% + Histéresis + Cooldown)"]
        Feedback["FeedbackController (Háptica + AudioTrack Binaural + Voz TTS)"]
        Resilience["StreamWatchdog + ThermalController"]

        Core0 -.->|Descubrimiento 1-3s| Discovery
        Core1 -->|TCP_NODELAY Direct Send| MjpegReader
        Discovery --> MjpegReader
        MjpegReader --> Channel
        Channel --> GpuThread
        GpuThread --> DepthEst
        DepthEst --> Zones
        Zones --> Policy
        Policy --> Feedback
        Resilience --> MjpegReader
        Resilience --> GpuThread
    end
```

### Pilares Técnicos de ORION v6

1. **Transmisión MJPEG sin Bloqueos (`WIFI_BW_HT20` + Envío Directo + `SO_SNDTIMEO = 60 ms`):**
   - El firmware del XIAO fija el ancho de banda de radio en **HT20 (20 MHz)** para maximizar la relación señal/ruido en entornos de 2.4 GHz y copia el JPEG del buffer DMA de la cámara a un buffer contiguo en PSRAM (`s_tx_buf`), liberando el DMA de inmediato.
   - Se eliminó la codificación HTTP `Transfer-Encoding: chunked` en favor de un único `httpd_send()` por fotograma con `TCP_NODELAY = 1` y un timeout estricto de socket `SO_SNDTIMEO = 60 ms`. Si el aire presenta interferencia momentánea, el fotograma se descarta limpiamente sin saturar la cola `TCP_SND_QUEUELEN` de LwIP.
2. **Pipeline Android Zero-Allocation con Triple Buffer y Afinidad de Hilo GPU:**
   - `MjpegStreamReader` reutiliza un buffer de bytes preasignado y decodifica en un anillo de **3 `Bitmap` mutables (`inBitmap`)**, evitando colisiones con el VSYNC de Jetpack Compose y eliminando pausas del Garbage Collector (`GC freed`).
   - `DepthEstimator` ejecuta el intérprete TFLite y su `GpuDelegate` exclusivamente sobre un único hilo dedicado (`OrionInferenceThread`) con `INFERENCE_PREFERENCE_SUSTAINED_SPEED` y precisión FP16, garantizando que el contexto OpenGL ES nunca cambie de hilo nativo.
3. **Segmentación Espacial en 3 Zonas y Retroalimentación Multimodal:**
   - `ZoneAnalyzer` recorta la franja vertical útil (20% a 80% de la altura para ignorar techo y piso inmediato), divide el ancho en tres tercios (`LEFT`, `CENTER`, `RIGHT`) y calcula el percentil 90 de cercanía normalizada.
   - `AlertPolicy` evalúa las zonas frente al umbral ajustable por el usuario (`nearThreshold` de `0.40` a `0.90`, persistido en `OrionPreferences`) y dispara alertas diferenciadas mediante vibración (`HapticFeedback`), tonos estéreo panoramizados (`AudioFeedback` sobre `AudioTrack` PCM) y síntesis de voz en español (`VoiceFeedback`).
4. **Modo Bolsillo y Control Térmico (`OrionForegroundService`):**
   - Mantiene activo un `PARTIAL_WAKE_LOCK` y un `WIFI_MODE_FULL_LOW_LATENCY` para procesar sin interrupciones con la pantalla bloqueada en el bolsillo, mientras `ThermalController` monitoriza la temperatura del teléfono para modular la cadencia si se detecta sobrecalentamiento.
