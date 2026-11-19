package com.audacia.orion.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.audacia.orion.feedback.FeedbackCoordinator
import com.audacia.orion.feedback.FeedbackSettings
import com.audacia.orion.net.MjpegStreamReader
import com.audacia.orion.net.XiaoDiscovery
import com.audacia.orion.net.XiaoEndpoint
import com.audacia.orion.resilience.CameraWatchdog
import com.audacia.orion.resilience.ThermalGovernor
import com.audacia.orion.resilience.WatchdogState
import com.audacia.orion.settings.OrionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Pipeline integral de visión en tiempo real para ORION v6.
 *
 * Conecta:
 * 1. Autodescubrimiento UDP del XIAO ESP32S3.
 * 2. Consumo de stream MJPEG sobre HTTP.
 * 3. Buffer de descarte dinámico (Channel.CONFLATED) para latencia cero.
 * 4. Preprocesamiento ImageNet bilineal.
 * 5. Inferencia TFLite acelerada por GPU móvil o CPU multi-hilo.
 * 6. Normalización, segmentación por zonas y generación de mapa Magma.
 */
class VisionPipeline(
    private val context: Context,
    private val scope: CoroutineScope
) : AutoCloseable {

    private val preferences = OrionPreferences(context)
    private val _state = MutableStateFlow(
        VisionState(
            feedbackSettings = preferences.getFeedbackSettings(),
            nearThreshold = preferences.getNearThreshold()
        )
    )
    val state: StateFlow<VisionState> = _state.asStateFlow()

    private var preferredBackend = preferences.getPreferredBackend()
    @Volatile
    private var currentNearThreshold: Float = preferences.getNearThreshold()
    private var depthEstimator: DepthEstimator? = null
    private val preprocessor = Preprocessor()
    private val discovery = XiaoDiscovery(context)
    private val streamReader = MjpegStreamReader()
    val feedback = FeedbackCoordinator(context).apply {
        settings = _state.value.feedbackSettings
        alertPolicy.nearThreshold = currentNearThreshold
    }
    val watchdog = CameraWatchdog(timeoutMs = 3500L)
    val thermalGovernor = ThermalGovernor(context, feedback.voice) { level ->
        _state.update { it.copy(thermalLevel = level) }
    }

    private var pipelineJob: Job? = null
    @Volatile
    private var pauseInferenceForBenchmark: Boolean = false
    private val frameChannel = Channel<ByteArray>(capacity = Channel.CONFLATED)

    // Hilo único dedicado de alta prioridad para mantener la afinidad del contexto EGL/OpenCL
    // del GpuDelegate de TensorFlow Lite y evitar penalizaciones de 30-50 ms por cambio de hilo.
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            } catch (ignored: Throwable) {}
            runnable.run()
        }, "OrionInferenceThread").apply { isDaemon = true }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()

    // Buffers pre-asignados con triple buffer (A/B/C) para cero presión sobre el Garbage Collector
    // y cero colisiones con el VSYNC de la pantalla (evita que un Bitmap se sobreescriba en el Paso 1
    // mientras aún está visible o en tránsito hacia la interfaz Compose).
    private val ringBufferSize = 3
    private val reusablePixels = IntArray(256 * 256)
    private val reusableNormDepth = FloatArray(256 * 256)
    private val cameraBitmaps = arrayOfNulls<Bitmap>(ringBufferSize)
    private val depthBitmaps: Array<Bitmap> by lazy {
        Array(ringBufferSize) {
            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        }
    }

    init {
        scope.launch(inferenceDispatcher) {
            initEstimator(preferredBackend)
        }
    }

    private fun initEstimator(backend: InferenceBackend) {
        depthEstimator?.close()
        depthEstimator = try {
            val estimator = DepthEstimator(context, preferredBackend = backend)
            _state.update {
                it.copy(
                    activeBackend = estimator.activeBackend,
                    statusMessage = "IA Lista (${estimator.activeBackend})"
                )
            }
            estimator
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    statusMessage = "Error al iniciar TFLite: ${e.message}",
                    connectionStatus = ConnectionStatus.ERROR
                )
            }
            null
        }
    }

    fun switchBackend(backend: InferenceBackend) {
        preferredBackend = backend
        preferences.savePreferredBackend(backend)
        scope.launch(inferenceDispatcher) {
            initEstimator(backend)
        }
    }

    fun updateFeedbackSettings(settings: FeedbackSettings) {
        feedback.settings = settings
        preferences.saveFeedbackSettings(settings)
        _state.update { it.copy(feedbackSettings = settings) }
    }

    fun updateNearThreshold(threshold: Float) {
        val clamped = OrionPreferences.clampThreshold(threshold)
        currentNearThreshold = clamped
        feedback.alertPolicy.nearThreshold = clamped
        preferences.saveNearThreshold(clamped)
        _state.update { it.copy(nearThreshold = clamped) }
    }

    fun getLastKnownIp(): String? = discovery.getLastKnownEndpoint()?.ip

    /**
     * Inicia el autodescubrimiento continuo y supervisor de reconexión.
     * Combina en paralelo el sondeo rápido HTTP (a la última IP conocida) y la escucha
     * de faros UDP en el puerto 9999, logrando enlaces instantáneos (<1.5s).
     */
    fun startAutoDiscovery() {
        stop(silent = true)
        pipelineJob = scope.launch(Dispatchers.IO) {
            var backoffMs = 500L
            while (isActive) {
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.SEARCHING,
                        statusMessage = "Buscando cámara (sonda rápida o faro UDP)..."
                    )
                }

                // 1. Descubrir endpoint
                val endpoint = try {
                    findEndpoint()
                } catch (e: CancellationException) {
                    break
                }

                if (!isActive) break

                // 2. Transmitir stream desde el endpoint descubierto
                val streamOk = try {
                    streamFromEndpoint(endpoint)
                } catch (e: CancellationException) {
                    break
                }

                // 3. Si el stream se interrumpió y seguimos activos, esperar con backoff exponencial (0.5 -> 1 -> 2 -> 4s)
                if (isActive) {
                    if (streamOk) {
                        backoffMs = 500L
                    } else {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(4000L)
                    }
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.SEARCHING,
                            statusMessage = "Reintentando autoconexión (${backoffMs}ms)..."
                        )
                    }
                }
            }
        }
    }

    /**
     * Conexión directa a un endpoint conocido (por IP y puerto de stream).
     */
    fun connectDirect(ip: String, streamPort: Int = 81) {
        stop(silent = true)
        val endpoint = XiaoEndpoint(ip = ip.trim(), streamPort = streamPort)
        discovery.saveLastKnownEndpoint(endpoint)
        pipelineJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    streamFromEndpoint(endpoint)
                } catch (e: CancellationException) {
                    break
                }
                if (isActive) {
                    delay(1500)
                }
            }
        }
    }

    /**
     * Localiza el endpoint de la cámara combinando:
     * - Sondeo HTTP inmediato a la última IP conocida.
     * - Sondeo periódico concurrente (cada 1200 ms).
     * - Escucha pasiva continua de faros UDP (puerto 9999).
     */
    private suspend fun findEndpoint(): XiaoEndpoint = coroutineScope {
        val last = discovery.getLastKnownEndpoint()
        if (last != null && discovery.probe(last)) {
            return@coroutineScope last
        }

        val foundChannel = Channel<XiaoEndpoint>(Channel.CONFLATED)

        // Tarea A: Sondeo periódico de la última IP
        val probeJob = if (last != null) {
            launch(Dispatchers.IO) {
                while (isActive) {
                    delay(1200)
                    if (discovery.probe(last)) {
                        foundChannel.trySend(last)
                        break
                    }
                }
            }
        } else null

        // Tarea B: Escucha de faros UDP emitidos por el firmware
        val beaconJob = launch(Dispatchers.IO) {
            try {
                discovery.listenBeacons().collect { endpoint ->
                    foundChannel.trySend(endpoint)
                }
            } catch (e: CancellationException) {
                // Cancelación normal
            } catch (ignored: Exception) {
            }
        }

        try {
            foundChannel.receive()
        } finally {
            probeJob?.cancel()
            beaconJob.cancel()
        }
    }

    /**
     * Abre el flujo MJPEG desde el endpoint.
     * Anuncia "Cámara conectada" ÚNICAMENTE cuando se recibe exitosamente el primer frame de video,
     * eliminando cualquier bucle o falso positivo cuando la cámara está apagándose.
     */
    private suspend fun streamFromEndpoint(endpoint: XiaoEndpoint): Boolean {
        discovery.saveLastKnownEndpoint(endpoint)
        watchdog.reset()
        _state.update {
            it.copy(
                endpoint = endpoint,
                connectionStatus = ConnectionStatus.CONNECTING,
                watchdogState = WatchdogState.OK,
                statusMessage = "Conectando stream en ${endpoint.streamUrl}..."
            )
        }

        // Iniciar consumidor de inferencia sobre el hilo dedicado (preserva afinidad EGL de GpuDelegate)
        val consumerJob = scope.launch(inferenceDispatcher) {
            runInferenceLoop(endpoint)
        }

        var hasDeliveredFrame = false
        var watchdogTriggered = false

        // Supervisor activo del watchdog: detecta congelamiento del socket sin cierre TCP (>3500ms)
        val watchdogJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(400)
                if (hasDeliveredFrame) {
                    val ws = watchdog.check()
                    if (ws == WatchdogState.LOST) {
                        watchdogTriggered = true
                        feedback.onCameraLost()
                        _state.update {
                            it.copy(
                                watchdogState = WatchdogState.LOST,
                                statusMessage = "Watchdog: Sin frames en > 3.5s"
                            )
                        }
                        break
                    }
                }
            }
        }

        try {
            // Flujo productor de frames desde MJPEG
            streamReader.streamFrames(endpoint).collect { frameBytes ->
                if (watchdogTriggered) {
                    throw CancellationException("Watchdog: timeout de 3500 ms alcanzado")
                }
                watchdog.onFrame()
                if (!hasDeliveredFrame) {
                    hasDeliveredFrame = true
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.STREAMING,
                            watchdogState = WatchdogState.OK,
                            statusMessage = "Stream activo (${endpoint.ip})"
                        )
                    }
                    feedback.onCameraConnected()
                } else if (_state.value.watchdogState == WatchdogState.LOST) {
                    _state.update { it.copy(watchdogState = WatchdogState.OK) }
                }
                // Channel.CONFLATED descarta automáticamente frames acumulados si el consumidor está ocupado
                frameChannel.trySend(frameBytes)
            }
            return !watchdogTriggered
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hasDeliveredFrame && !watchdogTriggered) {
                feedback.onCameraDisconnected()
            }
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    statusMessage = "Stream interrumpido: ${e.message}"
                )
            }
            return false
        } finally {
            watchdogJob.cancel()
            consumerJob.cancel()
        }
    }

    /**
     * Bucle de procesamiento de frames en tiempo real (ejecutado siempre en OrionInferenceThread).
     */
    private suspend fun runInferenceLoop(endpoint: XiaoEndpoint?) {
        var frameCount = 0L
        var fpsLastTime = System.currentTimeMillis()
        var fpsFrameCounter = 0
        var currentFps = 0f

        while (scope.isActive) {
            if (pauseInferenceForBenchmark) {
                delay(40)
                continue
            }
            val frameBytes = frameChannel.receive()
            if (pauseInferenceForBenchmark) {
                continue
            }
            val totalStart = System.nanoTime()
            val bufIdx = (frameCount % ringBufferSize).toInt()

            // 1. Decodificar JPEG reutilizando Bitmap pre-asignado (triple buffer A/B/C para cero GC y sin parpadeo)
            val decodeOptions = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inBitmap = cameraBitmaps[bufIdx]
            }
            val cameraBitmap = try {
                BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size, decodeOptions)
            } catch (e: Exception) {
                try {
                    BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                } catch (e2: Exception) {
                    null
                }
            } ?: continue
            cameraBitmaps[bufIdx] = cameraBitmap

            // 2. Preprocesamiento ImageNet (cero asignaciones de Bitmap)
            val inputBuffer = preprocessor.process(cameraBitmap)

            // 3. Inferencia MiDaS (mismo hilo nativo donde se inicializó GpuDelegate)
            val estimator = depthEstimator ?: continue
            val inferStart = System.nanoTime()
            val rawDepth = estimator.infer(inputBuffer)
            val inferEnd = System.nanoTime()
            val inferMs = (inferEnd - inferStart) / 1_000_000

            // 4. Normalización y segmentación espacial (reutilizando reusableNormDepth)
            val normDepth = DepthAnalyzer.normalize(rawDepth, outArray = reusableNormDepth)
            val zoneResult = DepthAnalyzer.analyze(normDepth, 256, 256, nearThreshold = currentNearThreshold)

            // 5. Retroalimentación sensorial (háptica, audio estéreo, voz)
            val alert = feedback.processFrameResult(zoneResult, endpoint)

            // 6. Generación de mapa de calor Magma en triple buffer (A/B/C)
            val colorizedBitmap = MagmaColorizer.toBitmap(
                depthNorm = normDepth,
                width = 256,
                height = 256,
                reuseBitmap = depthBitmaps[bufIdx],
                reusablePixels = reusablePixels
            )

            cameraBitmap.prepareToDraw()
            colorizedBitmap.prepareToDraw()

            // 7. Cálculo de FPS y latencia total
            val totalEnd = System.nanoTime()
            val totalLatencyMs = (totalEnd - totalStart) / 1_000_000
            frameCount++
            fpsFrameCounter++

            // Control térmico de velocidad de frames (ThermalGovernor)
            val minInterval = thermalGovernor.minFrameIntervalMs
            if (minInterval > 0) {
                val computeMs = totalLatencyMs
                val remainingDelay = minInterval - computeMs
                if (remainingDelay > 0) {
                    delay(remainingDelay)
                }
            }

            val now = System.currentTimeMillis()
            val elapsedMs = now - fpsLastTime
            if (elapsedMs >= 1000) {
                currentFps = (fpsFrameCounter * 1000f) / elapsedMs
                fpsFrameCounter = 0
                fpsLastTime = now
            }

            _state.update {
                it.copy(
                    cameraBitmap = cameraBitmap,
                    depthBitmap = colorizedBitmap,
                    zoneResult = zoneResult,
                    activeAlert = alert,
                    inferenceMs = inferMs,
                    totalLatencyMs = totalLatencyMs,
                    fps = currentFps,
                    framesProcessed = frameCount,
                    activeBackend = estimator.activeBackend
                )
            }
        }
    }

    /**
     * Ejecuta el benchmark de inferencia en GPU y CPU directamente en el dispositivo móvil,
     * emitiendo progreso iteración por iteración a la interfaz de usuario sin congelarla.
     */
    suspend fun runDeviceBenchmark(iterations: Int = 25): String {
        if (_state.value.isBenchmarkRunning) {
            return _state.value.benchmarkSummary.orEmpty()
        }

        // Actualización instantánea (0 ms) antes de cambiar al hilo de inferencia
        pauseInferenceForBenchmark = true
        _state.update {
            it.copy(
                isBenchmarkRunning = true,
                benchmarkProgress = 0.02f,
                benchmarkPhase = "Preparando motor GPU OpenGL (Warmup)...",
                benchmarkMetrics = null,
                benchmarkSummary = null,
                statusMessage = "Ejecutando Benchmark GPU/CPU..."
            )
        }

        return try {
            withContext(inferenceDispatcher) {
                val testBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(testBitmap)
                canvas.drawColor(android.graphics.Color.GRAY)
                val input = preprocessor.process(testBitmap)

                suspend fun benchmarkBackend(
                    backend: InferenceBackend,
                    label: String,
                    baseProgress: Float
                ): Pair<Double, Double> {
                    _state.update {
                        it.copy(
                            benchmarkPhase = "Inicializando $label (3 iteraciones de calentamiento)..."
                        )
                    }
                    val estimator = DepthEstimator(context, preferredBackend = backend)
                    if (backend == InferenceBackend.GPU && estimator.activeBackend != InferenceBackend.GPU) {
                        estimator.close()
                        return Pair(-1.0, -1.0)
                    }

                    // Warmup (3 iteraciones)
                    repeat(3) {
                        estimator.infer(input)
                    }

                    val times = DoubleArray(iterations)
                    for (i in 0 until iterations) {
                        val start = System.nanoTime()
                        estimator.infer(input)
                        val durationMs = (System.nanoTime() - start) / 1_000_000.0
                        times[i] = durationMs

                        val stepProgress = baseProgress + ((i + 1).toFloat() / (iterations * 2f)) * 0.96f
                        _state.update {
                            it.copy(
                                benchmarkProgress = stepProgress.coerceIn(0.02f, 0.99f),
                                benchmarkPhase = "Evaluando $label: iteración ${i + 1} / $iterations (${String.format("%.1f", durationMs)} ms)"
                            )
                        }
                        kotlinx.coroutines.yield()
                    }
                    estimator.close()
                    times.sort()
                    val median = times[iterations / 2]
                    val p95 = times[(iterations * 0.95).toInt().coerceAtMost(iterations - 1)]
                    return Pair(median, p95)
                }

                val (gpuMed, gpuP95) = try {
                    benchmarkBackend(InferenceBackend.GPU, "GPU (OpenGL FP16)", 0.02f)
                } catch (e: Exception) {
                    Pair(-1.0, -1.0)
                }

                val (cpuMed, cpuP95) = benchmarkBackend(InferenceBackend.CPU, "CPU (XNNPACK 4 Hilos)", 0.50f)

                // Restaurar backend preferido del usuario
                _state.update {
                    it.copy(
                        benchmarkProgress = 0.99f,
                        benchmarkPhase = "Restaurando motor de inferencia..."
                    )
                }
                initEstimator(preferredBackend)

                val gpuAvail = gpuMed > 0
                val gpuFpsVal = if (gpuAvail) 1000.0 / gpuMed else 0.0
                val cpuFpsVal = if (cpuMed > 0) 1000.0 / cpuMed else 0.0

                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val metrics = BenchmarkMetrics(
                    gpuAvailable = gpuAvail,
                    gpuMedianMs = gpuMed,
                    gpuP95Ms = gpuP95,
                    gpuFps = gpuFpsVal,
                    cpuMedianMs = cpuMed,
                    cpuP95Ms = cpuP95,
                    cpuFps = cpuFpsVal,
                    iterations = iterations,
                    completedAtTime = timeStr
                )

                val gpuStr = if (gpuAvail) {
                    "GPU: Mediana ${String.format("%.1f", gpuMed)} ms (p95: ${String.format("%.1f", gpuP95)} ms, ~${String.format("%.1f", gpuFpsVal)} FPS)"
                } else {
                    "GPU: No disponible en este dispositivo"
                }
                val cpuStr = "CPU (4 hilos): Mediana ${String.format("%.1f", cpuMed)} ms (p95: ${String.format("%.1f", cpuP95)} ms, ~${String.format("%.1f", cpuFpsVal)} FPS)"
                val resultSummary = "$gpuStr\n$cpuStr"

                _state.update {
                    it.copy(
                        isBenchmarkRunning = false,
                        benchmarkProgress = 1.0f,
                        benchmarkPhase = "Benchmark completado ($timeStr)",
                        benchmarkMetrics = metrics,
                        benchmarkSummary = resultSummary,
                        statusMessage = "Benchmark completado ($timeStr)"
                    )
                }
                resultSummary
            }
        } finally {
            pauseInferenceForBenchmark = false
            if (_state.value.isBenchmarkRunning) {
                _state.update { it.copy(isBenchmarkRunning = false) }
            }
        }
    }

    fun stop(silent: Boolean = false) {
        val wasConnected = _state.value.connectionStatus == ConnectionStatus.STREAMING ||
                _state.value.connectionStatus == ConnectionStatus.CONNECTING
        pipelineJob?.cancel()
        pipelineJob = null
        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.IDLE,
                statusMessage = "Detenido"
            )
        }
        if (wasConnected && !silent) {
            feedback.onCameraDisconnected()
        }
    }

    override fun close() {
        stop(silent = true)
        thermalGovernor.close()
        feedback.close()
        try {
            inferenceExecutor.submit {
                depthEstimator?.close()
                depthEstimator = null
            }.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (ignored: Exception) {
            depthEstimator?.close()
        }
        inferenceDispatcher.close()
    }
}
