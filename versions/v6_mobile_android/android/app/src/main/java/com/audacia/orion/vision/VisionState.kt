package com.audacia.orion.vision

import android.graphics.Bitmap
import com.audacia.orion.feedback.Alert
import com.audacia.orion.feedback.FeedbackSettings
import com.audacia.orion.net.XiaoEndpoint
import com.audacia.orion.resilience.ThermalThrottleLevel
import com.audacia.orion.resilience.WatchdogState

enum class ConnectionStatus {
    IDLE,
    SEARCHING,
    CONNECTING,
    STREAMING,
    ERROR
}

data class BenchmarkMetrics(
    val gpuAvailable: Boolean,
    val gpuMedianMs: Double,
    val gpuP95Ms: Double,
    val gpuFps: Double,
    val cpuMedianMs: Double,
    val cpuP95Ms: Double,
    val cpuFps: Double,
    val iterations: Int,
    val completedAtTime: String
)

data class VisionState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val endpoint: XiaoEndpoint? = null,
    val cameraBitmap: Bitmap? = null,
    val depthBitmap: Bitmap? = null,
    val zoneResult: ZoneResult? = null,
    val inferenceMs: Long = 0,
    val totalLatencyMs: Long = 0,
    val fps: Float = 0.0f,
    val activeBackend: InferenceBackend = InferenceBackend.CPU,
    val framesProcessed: Long = 0,
    val activeAlert: Alert? = null,
    val feedbackSettings: FeedbackSettings = FeedbackSettings(),
    val nearThreshold: Float = 0.60f,
    val statusMessage: String = "Listo para iniciar",
    val isBenchmarkRunning: Boolean = false,
    val benchmarkProgress: Float = 0f,
    val benchmarkPhase: String = "",
    val benchmarkMetrics: BenchmarkMetrics? = null,
    val benchmarkSummary: String? = null,
    val watchdogState: WatchdogState = WatchdogState.OK,
    val thermalLevel: ThermalThrottleLevel = ThermalThrottleLevel.NOMINAL
)
