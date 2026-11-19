# ORION v6 Mobile Android: plan de implementación

> **Para agentes ejecutores:** SUB-SKILL REQUERIDA: `superpowers:executing-plans` (o `superpowers:subagent-driven-development`). Los pasos usan checkboxes (`- [ ]`). **Se ejecuta UNA fase a la vez y se espera la autorización del usuario antes de pasar a la siguiente.**

**Objetivo:** Una app Android nativa que recibe el stream MJPEG del XIAO ESP32S3 Sense por la Zona Wi-Fi del celular, estima profundidad con MiDaS en el propio teléfono y avisa de obstáculos con vibración, audio estéreo y el buzzer del cinturón, también con la pantalla apagada.

**Arquitectura:** El XIAO se conecta como cliente a la Zona Wi-Fi del celular y anuncia su IP por UDP (puerto 9999). La app (Kotlin) descubre la IP, lee `http://<ip>:81/stream`, ejecuta `midas_small_256_fp16.tflite` con LiteRT (GPU → CPU) y pasa el mapa de profundidad a un analizador puro (sin Android) que decide la alerta. Todo vive dentro de un `ForegroundService` para que siga funcionando en el bolsillo.

**Stack:** Kotlin, Coroutines, LiteRT (TensorFlow Lite), OkHttp, Jetpack Compose (HUD), JUnit 4. Herramientas en PC: Python 3.13 + venv, `ai-edge-litert`, `pytest`. Firmware: Arduino IDE (ESP32 core).

**Spec de referencia:** la conversación de auditoría de viabilidad (Kotlin nativo elegido) y la lógica de la v5 en [`versions/v5_web_xiao_esp32s3/main.py`](../../../versions/v5_web_xiao_esp32s3/main.py) (`normalize_depth` L186, `analyze_depth` L202, `LocalAlarmSound.LEVELS` L256).

---

## Correcciones a lo que dije antes (léelas primero)

Al auditar el firmware y la red para hacer este plan encontré tres cosas que cambian lo que te recomendé:

1. **IP estática: ya no es el plan principal.** Desde Android 11, muchas marcas cambian la subred de la Zona Wi-Fi (no siempre es `192.168.43.x`) y puede variar al reiniciar. Una IP fija en el XIAO podría quedar fuera de la subred y no conectarse nunca. **Plan:** el descubrimiento principal es el faro UDP que el firmware ya emite (`ORION_XIAO_CAM:<IP>:81`, [XIAO_Camera_Orion.ino L29-35](../../../versions/v5_web_xiao_esp32s3/firmware/XIAO_Camera_Orion/XIAO_Camera_Orion.ino#L29-L35)). La app guarda la última IP vista para reconectar al instante. La IP estática queda como opción del firmware, apagada por defecto, y solo la activamos si en la Fase 2 comprobamos que la subred de tu celular no cambia.
2. **Las cifras de rendimiento eran estimaciones.** Los FPS, la latencia y la comparación con Flutter no los medí. En las Fases 1 y 4 los mediremos en tu laptop y en tu celular, y el plan se ajusta según lo que salga.
3. **Hallazgo de seguridad:** la contraseña del Wi-Fi "Audacia" está escrita en el firmware ([L18-19](../../../versions/v5_web_xiao_esp32s3/firmware/XIAO_Camera_Orion/XIAO_Camera_Orion.ino#L18-L19)) y **ya está en el historial de Git y en GitHub**. En la Fase 0 las credenciales de la v6 pasan a un `secrets.h` ignorado por Git. Te recomiendo cambiar esa contraseña del router. No toco la v5 ni el historial sin que me lo pidas.

---

## Restricciones globales

- **Commits:** solo cuando el usuario lo indique explícitamente. Al cerrar cada fase propongo el mensaje de commit y espero.
- **Una fase a la vez:** al terminar una fase corro sus pruebas, te entrego un reporte y me detengo hasta que la autorices.
- La v1–v5 no se modifican. Todo lo nuevo vive en `versions/v6_mobile_android/`.
- Puertos del XIAO sin cambios: stream `81` (`/stream`), control `80` (`/buzzer`, `/status`, `/whoami`), faro UDP `9999`.
- Wi-Fi del XIAO: solo **2.4 GHz**. La Zona Wi-Fi del celular debe estar en 2.4 GHz o en "máxima compatibilidad".
- Paridad con v5: umbral `NEAR_THRESHOLD = 0.60`, pérdida de cámara declarada a los **3.5 s** sin frames, 4 niveles de alarma de `LocalAlarmSound.LEVELS`.
- Objetivo de rendimiento: **≥ 15 FPS sostenidos** de inferencia en el celular y **≤ 150 ms** desde que llega el frame hasta la alerta.
- Android: `minSdk 26`, `targetSdk` = el más alto del SDK instalado (37), paquete `com.audacia.orion`, Gradle Kotlin DSL.
- Ninguna credencial en archivos versionados (`secrets.h`, `local.properties` y `.env` van ignorados).
- Modelo: `midas_small_256_fp16.tflite` de `huggingface.co/litert-community/MiDaS-small` (33 507 904 bytes, verificado con HEAD 200). Entrada `1×256×256×3` float32 RGB normalizada con ImageNet (NHWC). La forma de la salida se confirma en la Fase 1.

## Puntos de riesgo a vigilar (Review Focus)

1. **El XIAO se apaga o sale de rango a mitad de camino.** El usuario debe recibir un aviso distinto al de obstáculo (vibración larga + voz "cámara desconectada") en ≤ 3.5 s, y la app debe reconectar sola. Prueba: Fase 6, Tarea 6.2.
2. **Frame JPEG corrupto o cortado** (Wi-Fi débil). Se descarta el frame sin cerrar la app. Prueba: Fase 4, `MjpegParserTest.corruptJpegIsSkipped`.
3. **Escena uniforme (pared lisa, oscuridad total).** El min-max de MiDaS amplifica el ruido. Si el rango es < 1e-6 el mapa es cero (igual que v5). Además, un rango muy bajo con media alta debe tratarse como "posible pared". Prueba: Fase 1 `test_flat_scene` y Fase 4 `DepthAnalyzerTest.flatScene`.
4. **Alertas que parpadean** cuando el obstáculo está justo en el umbral (vibración on/off sin parar). Se usa histéresis y tiempo mínimo entre alertas. Prueba: Fase 5, `AlertPolicyTest.hysteresisPreventsFlicker`.
5. **Android duerme la app o la limita por temperatura** en el bolsillo. Se usa un servicio en primer plano con wake lock, y si el teléfono se calienta se baja el FPS en vez de cerrar. Prueba: Fase 6, prueba de 30 min con pantalla apagada.

---

## Mapa de archivos

```
versions/v6_mobile_android/
├── README.md                         # Guía de uso y despliegue
├── tools/                            # Python en PC (Fase 1)
│   ├── requirements-tools.txt
│   ├── fetch_model.py                # Descarga + verificación de tamaño/SHA256
│   ├── inspect_model.py              # Imprime tensores de entrada/salida
│   ├── reference_pipeline.py         # preprocess / infer / normalize / analyze_zones
│   ├── make_fixtures.py              # Genera golden fixtures JSON para Kotlin
│   ├── benchmark_pc.py
│   └── tests/test_reference_pipeline.py
├── firmware/XIAO_Camera_Orion_Mobile/   # Copia de v5 adaptada (Fase 2)
│   ├── XIAO_Camera_Orion_Mobile.ino
│   ├── secrets.example.h             # Versionado (plantilla)
│   ├── secrets.h                     # IGNORADO por Git
│   ├── camera_pins.h, xiao_stream_httpd.{h,cpp}
└── android/                          # Proyecto Android Studio (Fase 3)
    └── app/src/
        ├── main/assets/midas_small_256_fp16.tflite   # IGNORADO (se descarga con fetch_model.py)
        ├── main/java/com/audacia/orion/
        │   ├── net/MjpegParser.kt, MjpegStreamReader.kt, XiaoDiscovery.kt, BuzzerClient.kt
        │   ├── vision/DepthEstimator.kt, DepthAnalyzer.kt, Preprocessor.kt
        │   ├── feedback/AlertPolicy.kt, HapticFeedback.kt, AudioFeedback.kt, VoiceAnnouncer.kt
        │   ├── service/OrionService.kt, CameraWatchdog.kt, ThermalGovernor.kt
        │   └── ui/MainActivity.kt, HudScreen.kt, SettingsScreen.kt
        ├── test/        # JUnit en JVM (no necesita celular)
        └── androidTest/ # Pruebas instrumentadas en el celular
```

---

## 🧑 Cuándo entras tú

| Momento | Fase | Qué haces | Tiempo aprox. |
| :--- | :--- | :--- | :--- |
| **A** | Fin de Fase 1 | Nada técnico: revisas el reporte de la IA y autorizas. | 5 min |
| **B** | Fase 2 | Configuras la Zona Wi-Fi del celular (2.4 GHz) y me dices el nombre y la contraseña **en el archivo `secrets.h`** (no en el chat). Abres el sketch en **Arduino IDE**, lo flasheas al XIAO y me pasas el log del Monitor Serie. | 20 min |
| **C** | **Inicio de Fase 3: aquí abres Android Studio por primera vez** | **New Project → Empty Activity** con los datos exactos de la Tarea 3.1. Esperas a que termine el *Gradle Sync* y me avisas. | 10 min |
| **D** | Fase 3 | Activas la depuración USB en el celular, lo conectas y aceptas el aviso de confianza. Luego presionas **Run ▶** en Android Studio la primera vez. | 10 min |
| **E** | Fases 4–6 | Pruebas de campo con el cinturón: caminar hacia una pared, apagar el XIAO, pantalla apagada 30 min. Te doy un protocolo con casillas. | 20–40 min por fase |

A partir de la Fase 3, **Android Studio se queda abierto** con el proyecto `versions/v6_mobile_android/android`. Yo edito los archivos desde aquí y compilo/instalo por terminal (`gradlew`); Android Studio detecta los cambios solo. Lo usas para ver el Logcat y para presionar Run cuando quieras.

---

## Fase 0: Preparación y blindaje

**Entregable:** la estructura v6 creada, un entorno Python aislado para las herramientas y las reglas de `.gitignore` para secretos y el modelo.

### Tarea 0.1: Estructura y `.gitignore`

**Archivos:** crear `versions/v6_mobile_android/README.md` (esqueleto); modificar `.gitignore`.

- [x] Agregar a `.gitignore`: `versions/v6_mobile_android/firmware/**/secrets.h`, `versions/v6_mobile_android/android/app/src/main/assets/*.tflite`, `versions/v6_mobile_android/tools/.venv/`, `versions/v6_mobile_android/tools/models/`, `local.properties`, `.gradle/`, `**/build/`, `*.apk`, `*.aab`, `*.keystore`, `*.jks`.
- [x] **Verificar:** `git check-ignore -v` sobre una ruta de prueba de cada patrón. Esperado: todas reportan la regla.

### Tarea 0.2: Entorno Python de herramientas

**Archivos:** crear `versions/v6_mobile_android/tools/requirements-tools.txt` con `ai-edge-litert`, `numpy`, `pillow`, `requests`, `pytest`.

- [x] Crear el venv en `tools/.venv` con el Python 3.13 del sistema e instalar los requisitos.
- [x] **Verificar:** `.venv\Scripts\python -c "from ai_edge_litert.interpreter import Interpreter; print('ok')"` imprime `ok`. Si `ai-edge-litert` no tiene wheel para Python 3.13 en Windows, se usa `tflite-runtime` o `tensorflow-cpu` como alternativa y se anota en el reporte.

**Cierre de Fase 0:** reporte + propuesta de commit `chore(v6): estructura inicial y blindaje de secretos`. **Espero tu autorización.**

---

## Fase 1: Modelo de IA y pipeline de referencia (PC)

**Entregable:** el modelo descargado y verificado, sus tensores documentados, un pipeline de referencia en Python probado y fixtures JSON que la app Kotlin usará para comprobar que calcula lo mismo.

### Tarea 1.1: Descarga e inspección del modelo

**Archivos:** `tools/fetch_model.py`, `tools/inspect_model.py`.

- [x] `fetch_model(dest: Path) -> Path`: descarga a `tools/models/` y copia a `android/app/src/main/assets/` si la carpeta existe. Falla si el tamaño ≠ 33 507 904 bytes. Guarda el SHA256 en `tools/models/MODEL_SHA256.txt`.
- [x] `inspect_model.py`: imprime nombre, forma, dtype y cuantización de cada tensor de entrada y salida.
- [x] **Verificar:** entrada `[1, 256, 256, 3] float32`. La forma de salida (se espera `[1, 256, 256]` o `[1, 256, 256, 1]`) se escribe en el reporte y fija `OUTPUT_SHAPE` para la Fase 4.

### Tarea 1.2: Pipeline de referencia con pruebas

**Archivos:** `tools/reference_pipeline.py`, `tools/tests/test_reference_pipeline.py`.

**Interfaces que produce (la Fase 4 las replica en Kotlin con los mismos nombres en camelCase):**
- `preprocess(rgb: np.ndarray[H,W,3] uint8) -> np.ndarray[1,256,256,3] float32`: redimensión bilineal a 256×256, `/255`, mean `(0.485, 0.456, 0.406)`, std `(0.229, 0.224, 0.225)`.
- `normalize_depth(raw: np.ndarray, invert: bool=False) -> np.ndarray`: copia exacta de v5 (rango < 1e-6 → ceros).
- `analyze_zones(depth_norm, near_threshold=0.60, sample_step=4) -> ZoneResult`, donde `ZoneResult` = `action: "STOP"|"FORWARD"`, `intensity: float`, `left: float`, `center: float`, `right: float`, `overall: float`, `nearest_zone: "LEFT"|"CENTER"|"RIGHT"|None`.
  - `action` e `intensity` se calculan **igual que `analyze_depth` de v5** (centro y media global). `left` y `right` son el tercio izquierdo y derecho. `nearest_zone` es la zona con mayor media si supera `near_threshold`, si no `None`.

- [x] Escribir las pruebas primero:
  - `test_normalize_matches_v5`: con la misma entrada, el resultado coincide con la función de v5 copiada en el test (`np.allclose`, atol 1e-6).
  - `test_flat_scene`: entrada constante → todo cero, `action == "FORWARD"`.
  - `test_center_obstacle_stops`: mapa con bloque 0.9 en el tercio central → `action == "STOP"`, `nearest_zone == "CENTER"`, `intensity > 0`.
  - `test_left_obstacle_zone`: bloque 0.9 solo en el tercio izquierdo → `nearest_zone == "LEFT"` y `action == "FORWARD"` (paridad v5: los laterales no detienen).
  - `test_preprocess_shape_and_stats`: imagen gris 128 → forma `(1,256,256,3)` y valores según la fórmula de ImageNet.
  - `test_model_runs_on_real_image`: `images/portada.jpeg` → salida finita con la forma de la Tarea 1.1.
- [x] Correr `pytest` y ver que fallan → implementar → que pasan.

### Tarea 1.3: Fixtures y benchmark

**Archivos:** `tools/make_fixtures.py`, `tools/benchmark_pc.py`; salida en `android/app/src/test/resources/fixtures/*.json` (la carpeta se crea en la Fase 3; mientras tanto se guardan en `tools/fixtures/`).

- [x] Generar 6 fixtures (`flat`, `center`, `left`, `right`, `gradient`, `portada`) con entrada `depth_norm` reducida a 64×64 y el `ZoneResult` esperado.
- [x] `benchmark_pc.py`: 100 inferencias en CPU, reporta la mediana y el p95 en ms.
- [x] **Verificar:** `pytest` en verde; benchmark impreso; mapa de `portada.jpeg` guardado en PNG coloreado (magma) para inspeccionarlo a simple vista.

### Tarea 1.4: Paridad TFLite vs PyTorch (aprobada por el usuario, temporal)

**Archivos:** `tools/parity_torch.py` (se borra al terminar).

- [x] Crear un venv aparte `tools/.venv-parity` con `torch` y `torchvision` **versión CPU** (índice `https://download.pytorch.org/whl/cpu`; unos 200–300 MB frente a los ~2.5 GB de la versión CUDA). Para comparar resultados no hace falta la GPU. Además `timm` y `opencv-python`.
- [x] Cargar `MiDaS_small` con `torch.hub` (igual que `load_midas_torch` de v5), procesar `images/portada.jpeg` y las imágenes sintéticas con ambos modelos y comparar `normalize_depth` de cada uno, redimensionados a 256×256.
- [x] **Criterio:** correlación de Pearson ≥ 0.98 y el mismo `action`/`nearest_zone` en todas las imágenes. Reportar el error absoluto medio.
- [x] **Limpieza:** borrar `tools/.venv-parity`, la caché de `torch.hub` (`%USERPROFILE%\.cache\torch\hub`, pesos de MiDaS y el repo clonado) y la caché de pip (`pip cache purge`). Reportar el espacio liberado comparando el disco libre antes y después.

**Cierre de Fase 1 (momento A):** reporte con tensores, latencia en PC, imagen de profundidad y pruebas. Propuesta de commit `feat(v6): pipeline de referencia MiDaS TFLite y fixtures`. **Espero tu autorización.**

---

## Fase 2: Firmware para Zona Wi-Fi del celular

**Entregable:** un sketch v6 que se conecta a la Zona Wi-Fi del celular, con credenciales fuera de Git, faro UDP activo y IP estática opcional.

### Tarea 2.1: Sketch v6 con secretos externos y multi-red con prioridad

**Archivos:** `versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/*`.

- [x] `secrets.example.h` define arreglo `KNOWN_NETWORKS` con prioridad estricta, `NUM_KNOWN_NETWORKS` y configuración estática opcional.
- [x] `secrets.h` activo local con las 3 redes configuradas (Celular principal 'a' en P1, Celular 2 en P2, Audacia en P3). Blindado e ignorado por Git.
- [x] El `.ino` implementa algoritmo híbrido Fast-Probe (1.5-2.5s a P1) + Escaneo de espectro en orden de prioridad para P2 y P3.
- [x] Faro UDP en puerto 9999: emite doble broadcast (`255.255.255.255` y `localIP | ~subnetMask`) cada 1 segundo cuando no hay clientes de stream para enlace instantáneo en Android.
- [x] Watchdog en `loop()`: detecta desconexión y reconecta automáticamente por prioridad sin colgar la cámara ni resetear el microcontrolador.
- [ ] Al perder el Wi-Fi, reintentar sin reiniciar la placa (ya existe `setAutoReconnect(true)`; se verifica en la prueba).

### Tarea 2.2: Validación con hardware (momento B)

- [ ] Tú: Zona Wi-Fi en 2.4 GHz, rellenas `secrets.h`, flasheas con Arduino IDE (Placa: *XIAO_ESP32S3*, PSRAM: *OPI PSRAM*) y me pegas el log serie.
- [ ] Desde el navegador del celular: `http://<ip>:81/stream` muestra video y `http://<ip>/whoami` responde JSON.
- [ ] Apagas y prendes la Zona Wi-Fi 3 veces y anotamos la IP de cada vez → decidimos si la IP estática es segura en tu modelo de celular.
- [ ] Mido desde la laptop conectada a la misma Zona Wi-Fi con `utils/xiao_discovery.py` que el faro llega.

**Cierre de Fase 2:** reporte con las IPs observadas, FPS del stream y decisión sobre IP estática. Commit propuesto `feat(v6): firmware XIAO para Zona Wi-Fi movil con secretos externos`. **Espero tu autorización.**

---

## Fase 3: Esqueleto Android y primer despliegue

**Entregable:** la app ORION instalada en tu celular desde Android Studio y desde terminal, con dependencias, permisos y el modelo dentro del APK.

### Tarea 3.1: Crear el proyecto (momento C, lo haces tú en Android Studio)

- [x] **File → New → New Project → Empty Activity** (Compose).
  - Name: `ORION` · Package name: `com.audacia.orion`
  - Save location: `C:\Users\jchrn\Documents\Trabajos\AudacIA\OrionBelt\versions\v6_mobile_android\android`
  - Language: Kotlin · Minimum SDK: **API 26** · Build configuration language: **Kotlin DSL**
- [x] Esperar el *Gradle Sync* (barra inferior) y avisarme. Esto deja el Gradle Wrapper y las versiones de AGP/Kotlin compatibles con tu Android Studio.

### Tarea 3.2: Configuración (lo hago yo)

**Archivos:** `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `AndroidManifest.xml`, `res/xml/network_security_config.xml`.

- [x] Dependencias: LiteRT/TFLite (`org.tensorflow:tensorflow-lite` + `tensorflow-lite-gpu`), OkHttp, `kotlinx-coroutines-android`, JUnit 4, `org.json` para tests en JVM. `androidResources { noCompress += "tflite" }`.
- [x] Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`.
- [x] `network_security_config.xml`: permitir HTTP sin cifrar (el XIAO no usa HTTPS). Desde Android 9 está bloqueado por defecto y sin esto el stream no abre.
- [x] Copiar el modelo a `assets/` y los fixtures de la Fase 1 a `app/src/test/resources/fixtures/`. Smoke test `ModelAssetSmokeTest` ejecutado y pasando.

### Tarea 3.3: Primer despliegue (momento D)

- [x] Tú: depuración USB activada, celular conectado, aceptas la huella RSA.
- [x] **Verificar:**
  - `adb devices` → tu celular aparece como `device` (TECNO CK7n, arm64-v8a, Android 14 API 34).
  - `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
  - `.\gradlew.bat testDebugUnitTest` → pasa (prueba de humo: el modelo existe en `assets/` y pesa 33 507 904 bytes).
  - `.\gradlew.bat installDebug` → la app abre en el celular y muestra "ORION v6" con el modelo, la versión de Android y el SoC detectado.
  - Desconectas el cable y la app sigue abriendo (comprueba que es independiente de la PC).

**Cierre de Fase 3:** reporte + commit ejecutado `feat(v6): esqueleto Android con TFLite, permisos y modelo`.

---

## Fase 4: Núcleo de visión (stream + IA + análisis)

**Entregable:** la app encuentra el XIAO sola, muestra el video y el mapa de profundidad en vivo con FPS y latencia, y calcula las zonas igual que el pipeline de referencia.

### Tarea 4.1: `MjpegParser` (Kotlin puro)

**Interfaz:** `class MjpegParser { fun feed(bytes: ByteArray, len: Int): List<ByteArray> }`. Devuelve los JPEG completos encontrados. Busca los marcadores SOI `FFD8` y EOI `FFD9` (no depende de `Content-Length`, que el firmware puede no enviar). Buffer máximo de 512 KB; si se supera, se reinicia el buffer.

- [ ] Pruebas JVM: `twoFramesInOneChunk`, `frameSplitAcrossChunks`, `corruptJpegIsSkipped` (SOI sin EOI seguido de otro frame válido → solo sale el válido), `oversizedBufferResets`.

### Tarea 4.2: `XiaoDiscovery` y `MjpegStreamReader`

**Interfaces:**
- `fun parseBeacon(msg: String): XiaoEndpoint?` donde `data class XiaoEndpoint(val ip: String, val streamPort: Int)`. Acepta solo `ORION_XIAO_CAM:<IPv4>:<puerto>`.
- `class XiaoDiscovery(context) { fun endpoints(): Flow<XiaoEndpoint> }`: escucha UDP 9999 con `MulticastLock`; prueba primero la última IP guardada (`GET /whoami`, timeout 800 ms).
- `class MjpegStreamReader(client: OkHttpClient) { fun frames(endpoint): Flow<ByteArray> }`: lee con timeout de 2 s y usa `MjpegParser`.

- [ ] Pruebas JVM: `parseBeacon` válido, con IP inválida, con prefijo distinto y con puerto fuera de rango. `MjpegStreamReader` contra `MockWebServer` de OkHttp sirviendo 3 JPEG → emite 3.

### Tarea 4.3: `Preprocessor`, `DepthEstimator` y `DepthAnalyzer`

**Interfaces:**
- `Preprocessor.toInput(bitmap: Bitmap, out: FloatBuffer)`: misma fórmula que `preprocess` de la Fase 1, sin crear memoria nueva por frame.
- `class DepthEstimator(context, backend: Backend = Backend.GPU) { fun infer(input: FloatBuffer): FloatArray; val activeBackend: Backend }` con `enum Backend { GPU, CPU }`. Si la GPU falla al iniciar, pasa a CPU con 4 hilos y lo informa en `activeBackend`. (NNAPI no se usa: Google lo marcó obsoleto en Android 15.)
- `object DepthAnalyzer { fun normalize(raw: FloatArray, invert: Boolean = false): FloatArray; fun analyze(depth: FloatArray, w: Int, h: Int, nearThreshold: Float = 0.60f, sampleStep: Int = 4): ZoneResult }`. `data class ZoneResult(val action: Action, val intensity: Float, val left: Float, val center: Float, val right: Float, val overall: Float, val nearestZone: Zone?)` con `enum class Action { STOP, FORWARD }` y `enum class Zone { LEFT, CENTER, RIGHT }`: los mismos campos que en Python.

- [ ] Pruebas JVM `DepthAnalyzerTest`: carga los 6 fixtures y compara con tolerancia 1e-4 (incluye `flatScene`).
- [ ] Prueba instrumentada `DepthEstimatorDeviceTest` (en el celular): el modelo carga, la salida tiene `OUTPUT_SHAPE`, y un benchmark de 50 inferencias reporta la mediana y el p95 para GPU y CPU.

### Tarea 4.4: Pipeline en vivo + HUD básico

- [ ] Coroutine: frames → decodificar (`BitmapFactory` con `inBitmap` reutilizable) → inferir → analizar. Si la inferencia va atrasada se descartan frames viejos (siempre se procesa el último, igual que `CAMERA_GRAB_LATEST`).
- [ ] HUD: video, mapa magma, FPS, ms por etapa, backend, IP del XIAO y barras izquierda/centro/derecha.
- [ ] **Verificar (momento E):** con el XIAO encendido la app lo encuentra sola en ≤ 5 s. **≥ 15 FPS** de mediana durante 2 min. Al acercar la mano al centro se marca `STOP`.

**Cierre de Fase 4:** reporte con FPS reales de tu celular por backend. Commit propuesto `feat(v6): stream MJPEG, inferencia LiteRT y analisis por zonas`. **Espero tu autorización.**

---

## Fase 5: Retroalimentación al usuario

**Entregable:** vibración, audio estéreo direccional, buzzer del cinturón y avisos de voz, con una política que no satura al usuario.

### Tarea 5.1: `AlertPolicy` (Kotlin puro, el cerebro de las alertas)

**Interfaz:** `class AlertPolicy(nearThreshold: Float = 0.60f, releaseMargin: Float = 0.05f) { fun update(result: ZoneResult, nowMs: Long): Alert? }` con `data class Alert(val level: Int /*0..3*/, val zone: Zone, val intervalMs: Long)`.
- Niveles e intervalos tomados de `LocalAlarmSound.LEVELS` de v5: intensidad ≥ 0.00 / 0.30 / 0.60 / 0.85 → nivel 0–3, intervalo 550 / 380 / 240 / 150 ms.
- Se entra en alerta con `center > 0.60` y se sale solo cuando `center < 0.55` (histéresis).

- [ ] Pruebas JVM: `hysteresisPreventsFlicker` (valores que oscilan 0.58↔0.62 producen un solo estado activo), `respectsIntervalPerLevel`, `levelMapping`, `sideObstacleGivesDirectionalCueOnly`.

### Tarea 5.2: Salidas

- `HapticFeedback.play(alert)`: `VibrationEffect.createWaveform` con amplitud según el nivel. Patrón distinto para izquierda, centro y derecha.
- `AudioFeedback.play(alert)`: tonos de 700/950/1200/1500 Hz (como v5) precargados en `SoundPool`; paneo estéreo L/C/R para audífonos.
- `BuzzerClient.beep(level)`: `GET http://<ip>/buzzer?beep=<ms>&duty=200&freq=<Hz>` (endpoint existente, [xiao_stream_httpd.cpp L221](../../../versions/v5_web_xiao_esp32s3/firmware/XIAO_Camera_Orion/xiao_stream_httpd.cpp#L221-L252)), sin bloquear y descartando la petición si la anterior sigue en vuelo.
- `VoiceAnnouncer`: TTS en español solo para cambios de estado ("ORION listo", "cámara desconectada", "cámara reconectada").

- [ ] **Verificar (momento E):** caminas hacia una pared y sientes que el ritmo sube por niveles. Un obstáculo a la izquierda suena en el oído izquierdo. El buzzer del cinturón suena sincronizado.

**Cierre de Fase 5:** commit propuesto `feat(v6): retroalimentacion haptica, audio estereo y buzzer`. **Espero tu autorización.**

---

## Fase 6: Modo bolsillo y resiliencia

**Entregable:** ORION funciona 30 min con la pantalla apagada, avisa si pierde la cámara, reconecta solo y se protege del calor.

### Tarea 6.1: `OrionService`

- [ ] `ForegroundService` con `foregroundServiceType="connectedDevice"`, notificación fija con Detener y `PARTIAL_WAKE_LOCK`. Todo el pipeline de las Fases 4–5 pasa del Activity al servicio. La UI solo observa un `StateFlow`.
- [ ] Pedir `POST_NOTIFICATIONS` y sugerir excluir la app de la optimización de batería (con explicación al usuario, sin forzarlo).

### Tarea 6.2: `CameraWatchdog` y reconexión

**Interfaz:** `class CameraWatchdog(timeoutMs: Long = 3500) { fun onFrame(nowMs: Long); fun check(nowMs: Long): WatchdogState }` con `enum WatchdogState { OK, LOST }`.
- [ ] Pruebas JVM: `lostAfter3500ms`, `recoversOnFrame`.
- [ ] En `LOST`: vibración larga + voz, y se reintenta con backoff 0.5 → 1 → 2 → 4 s (máx. 4 s), alternando la última IP y el faro UDP.

### Tarea 6.3: `ThermalGovernor`

- [ ] `PowerManager.addThermalStatusListener`: con `MODERATE` limitar a 12 FPS, con `SEVERE` a 8 FPS y avisar por voz. Nunca detener la detección.

### Verificación (momento E)

- [ ] 30 min con la pantalla apagada en el bolsillo: el servicio sigue vivo (`adb shell dumpsys activity services com.audacia.orion`) y las alertas siguen llegando.
- [ ] Apagar el XIAO → aviso en ≤ 3.5 s. Encenderlo → reconecta en ≤ 10 s sin tocar el celular.
- [ ] Apagar y prender la Zona Wi-Fi → la app se recupera sola.
- [ ] Batería: % consumido en 30 min (`adb shell dumpsys battery` antes y después) y temperatura máxima.

**Cierre de Fase 6:** commit propuesto `feat(v6): servicio en primer plano, watchdog y control termico`. **Espero tu autorización.**

---

## Fase 7: Ajustes, documentación y entrega

### Tarea 7.1: Pantalla de ajustes

- [ ] Umbral (0.40–0.90, por defecto 0.60), IP manual opcional, activar o desactivar vibración, audio, buzzer y voz, y backend GPU/CPU. Guardado con DataStore.

### Tarea 7.2: Documentación

- [ ] `versions/v6_mobile_android/README.md`: requisitos, flasheo, Zona Wi-Fi, instalación y uso diario.
- [ ] Actualizar `docs/VERSIONS.md` y `docs/ARCHITECTURE.md` con la v6.
- [ ] `main.py --version 6`: no ejecuta nada (la v6 corre en el celular); imprime cómo compilar e instalar.

### Tarea 7.3: Validación final

- [ ] `pytest` (tools) + `gradlew testDebugUnitTest` + `gradlew connectedDebugAndroidTest`, todo en verde.
- [ ] APK de release firmado con tu propio keystore (fuera de Git), para instalarlo sin la PC en otros celulares.
- [ ] Revisión de código final con la skill `requesting-code-review`.

> **Opcional, para decidir después:** usar la cámara trasera del celular como respaldo si se pierde el XIAO (el equivalente al Hot-Swap de v5). En el bolsillo no ve nada, así que solo sirve con el celular en la mano o en un soporte de pecho. Lo dejo fuera hasta que lo pidas.

**Cierre de Fase 7:** commit propuesto `docs(v6): guia de uso, ajustes y entrega`.

---

## Skills que se usarán

| Skill | Dónde |
| :--- | :--- |
| `executing-plans` | Ejecutar cada fase paso a paso |
| `test-driven-development` | Fases 1, 4, 5 y 6 (pruebas antes del código) |
| `systematic-debugging` | Ante cualquier fallo de hardware, red o compilación |
| `verification-before-completion` | Antes de declarar cerrada cualquier fase |
| `requesting-code-review` | Fase 7 |
