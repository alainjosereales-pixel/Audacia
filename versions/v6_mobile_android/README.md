# 📱 ORION v6: Mobile Android + XIAO ESP32S3 Sense

**ORION v6** traslada todo el pipeline de visión artificial y asistencia espacial de ORION directamente a un teléfono **Android**, eliminando la necesidad de una computadora portátil durante el desplazamiento. El módulo **Seeed Studio XIAO ESP32S3 Sense** montado en el cinturón se conecta a la **Zona Wi-Fi (Hotspot)** del teléfono y transmite video MJPEG de baja latencia; la aplicación Android ejecuta el modelo **MiDaS v2.1 Small (FP32/FP16 en GPU/CPU)** localmente, segmenta el entorno en tres zonas espaciales (`LEFT`, `CENTER`, `RIGHT`) y guía al usuario mediante **vibración háptica**, **audio estéreo binaural** y **voz sintética (TTS)**, incluso con la pantalla apagada en el bolsillo.

---

## 📂 Estructura de la Versión

| Carpeta | Contenido | Descripción |
| :--- | :--- | :--- |
| `tools/` | Herramientas Python | Descarga verificada del modelo TFLite (`fetch_model.py`), pipeline de referencia (`reference_pipeline.py`), generación de fixtures y pruebas con `pytest`. |
| `firmware/XIAO_Camera_Orion_Mobile/` | Firmware Arduino (ESP32-S3) | Servidor MJPEG en Core 1 (`:81/stream`), telemetría/control en Core 0 (`:80/status`, `:80/led`, `:80/framesize`), beacon UDP `:4210`, reconexión exponencial, radio `WIFI_BW_HT20` y descarte activo de fotogramas (`SO_SNDTIMEO = 60 ms`). |
| `android/` | Proyecto Android (Kotlin + Compose) | App nativa con inferencia TFLite (GPU Delegate con afinidad de hilo dedicada + fallback CPU XNNPACK), lector MJPEG zero-allocation con Triple Buffer, auto-descubrimiento híbrido (UDP + tabla ARP + barrido HTTP), alertas multimodales y `ForegroundService` resiliente. |

---

## 🛠️ Requisitos Previos

1. **Hardware:**
   - Placa **Seeed Studio XIAO ESP32S3 Sense** (con módulo de cámara OV2640/OV3660 y antena Wi-Fi externa conectada).
   - Teléfono **Android 8.0+ (API 26+)** con soporte OpenGL ES 3.1+ para aceleración GPU (validado en **TECNO Camon 30S Pro CK7n / Android 14 / Helio G100 / Mali-G57 MC2**).
2. **Software en PC:**
   - **Python 3.10+** (para descargar el modelo TFLite y ejecutar los tests de validación).
   - **Arduino IDE 2.x** con el paquete de placas **esp32 by Espressif Systems** instalado.
   - **Android Studio Ladybug / Koala+** o **JDK 17** con Android SDK Platform 35.

---

## 📡 Paso 1: Configurar y Flashear el XIAO ESP32S3 Sense

1. Copia el archivo de plantilla de credenciales Wi-Fi:
   ```powershell
   Copy-Item versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/secrets.example.h `
             versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/secrets.h
   ```
2. Edita `secrets.h` (este archivo está ignorado en Git para proteger tus claves):
   - Configura `HOTSPOT_SSID` y `HOTSPOT_PASS` con el nombre y contraseña de la **Zona Wi-Fi (Hotspot)** de tu teléfono Android (prioridad 1).
   - Opcionalmente configura `HOME_WIFI_SSID` y `HOME_WIFI_PASS` como red secundaria de pruebas en casa (prioridad 2).
3. Abre `versions/v6_mobile_android/firmware/XIAO_Camera_Orion_Mobile/XIAO_Camera_Orion_Mobile.ino` en **Arduino IDE** y configura:
   - **Board:** `XIAO_ESP32S3`
   - **PSRAM:** `OPI PSRAM` *(Obligatorio para habilitar los 8 MB de PSRAM y el triple buffer DMA)*
   - **Flash Size:** `8MB (64Mb)`
   - **Partition Scheme:** `Default 4MB with spiffs` (o `8M with spiffs`)
4. Compila y sube el sketch al XIAO ESP32S3 Sense.

---

## 🧠 Paso 2: Descargar el Modelo MiDaS TFLite

Si aún no tienes el archivo `midas_v2_1_small_256.tflite` (33 MB) en `android/app/src/main/assets/`, descárgalo automáticamente con el script de herramientas:

```powershell
cd versions/v6_mobile_android/tools
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements-tools.txt
.\.venv\Scripts\python fetch_model.py
```

El script verifica el tamaño e integridad del tensor de entrada (`[1, 256, 256, 3]`) y lo instala tanto en `tools/models/` como en `android/app/src/main/assets/midas_v2_1_small_256.tflite`.

---

## 📦 Paso 3: Compilar e Instalar la App Android

### Opción A: Desde Línea de Comandos (Windows PowerShell)
```powershell
cd versions/v6_mobile_android/android
.\gradlew.bat assembleDebug
```
El APK compilado quedará disponible en:
`versions/v6_mobile_android/android/app/build/outputs/apk/debug/app-debug.apk`

Con tu teléfono conectado por USB (y *Depuración por USB* activa), instálalo con:
```powershell
.\gradlew.bat installDebug
```

### Opción B: Desde Android Studio
1. Abre la carpeta `versions/v6_mobile_android/android` en Android Studio.
2. Espera a que finalice Gradle Sync.
3. Selecciona tu teléfono físico y presiona **Run ('app')**.

---

## 🚀 Uso Diario y Modo Bolsillo

1. **Activa la Zona Wi-Fi (Hotspot)** en tu teléfono Android (preferiblemente en la banda de **2.4 GHz**).
2. **Enciende el cinturón ORION** (XIAO ESP32S3 Sense conectado a su batería/powerbank).
3. **Abre la app ORION** en el teléfono:
   - El sistema descubrirá el XIAO automáticamente en 1–3 segundos mediante el beacon UDP (`:4210`), lectura de la tabla ARP del Hotspot o barrido rápido de subred (`:80/status`).
   - En la barra superior puedes alternar en vivo entre **GPU** y **CPU**, supervisar los **FPS**, la latencia en milisegundos y el estado térmico del teléfono.
4. **Ajustes Personalizados y Persistencia:**
   - En los botones inferiores puedes activar/desactivar **Háptica**, **Audio Estéreo** y **Voz TTS**.
   - Desde el menú superior (`⋮` -> **Ajustes de Detección e IP**) puedes ajustar el **Umbral de Cercanía** entre **40% y 90%** (por defecto **60%**), conectar manualmente a una IP fija o ejecutar el **Benchmark interactivo GPU vs CPU** con progreso por iteración en tiempo real. Todos tus ajustes se guardan automáticamente en el teléfono.
5. **Modo Bolsillo (Pantalla Apagada):**
   - Concede el permiso de *Sin restricciones de batería* cuando aparezca la tarjeta de sugerencia en la parte superior.
   - Bloquea la pantalla y guarda el teléfono en el bolsillo: el servicio en primer plano (`OrionForegroundService`) mantiene vivo el enlace Wi-Fi (`WIFI_MODE_FULL_LOW_LATENCY`), la inferencia en segundo plano y las alertas hápticas/auditivas continuas.
   - Para cerrar todo por completo, usa el botón **DETENER Y SALIR** de la notificación de Android o la opción **Cerrar ORION por completo** en el menú `⋮`.

---

## 🧪 Ejecución de Pruebas Automáticas

### Pruebas de Herramientas y Pipeline de Referencia (Python)
```powershell
versions\v6_mobile_android\tools\.venv\Scripts\python.exe -m pytest versions/v6_mobile_android/tools/tests
```

### Pruebas Unitarias de la App Android (JUnit)
```powershell
cd versions/v6_mobile_android/android
.\gradlew.bat testDebugUnitTest
```
