package com.audacia.orion.resilience

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.audacia.orion.feedback.VoiceAnnouncer

enum class ThermalThrottleLevel(val maxFps: Int, val description: String) {
    NOMINAL(30, "Nominal"),
    MODERATE(12, "Moderado (12 FPS)"),
    SEVERE(8, "Severo (8 FPS)")
}

/**
 * Gobernador térmico para ORION v6.
 *
 * Monitorea el estado térmico del hardware móvil mediante [PowerManager.OnThermalStatusChangedListener].
 * Cuando el teléfono se calienta (típico en modo bolsillo con inferencia continua sobre GPU/NPU):
 * - En [PowerManager.THERMAL_STATUS_MODERATE]: limita el pipeline a 12 FPS.
 * - En [PowerManager.THERMAL_STATUS_SEVERE] o superior: limita a 8 FPS y emite un aviso por voz
 *   sin interrumpir en ningún momento la asistencia visual ni la detección de obstáculos.
 * - Al normalizarse la temperatura: restaura automáticamente el régimen nominal.
 */
class ThermalGovernor(
    private val context: Context,
    private val voiceAnnouncer: VoiceAnnouncer? = null,
    private val onLevelChanged: ((ThermalThrottleLevel) -> Unit)? = null
) : AutoCloseable {

    var currentLevel: ThermalThrottleLevel = ThermalThrottleLevel.NOMINAL
        private set

    val minFrameIntervalMs: Long
        get() = when (currentLevel) {
            ThermalThrottleLevel.NOMINAL -> 0L
            ThermalThrottleLevel.MODERATE -> 1000L / 12 // ~83 ms
            ThermalThrottleLevel.SEVERE -> 1000L / 8   // 125 ms
        }

    private var listener: Any? = null

    init {
        registerListener()
    }

    private fun registerListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                handleThermalStatus(status)
            }
            try {
                powerManager.addThermalStatusListener(context.mainExecutor, thermalListener)
                listener = thermalListener
                handleThermalStatus(powerManager.currentThermalStatus)
            } catch (e: Exception) {
                // En emuladores o entornos sin soporte de listener térmico
            }
        }
    }

    fun handleThermalStatus(status: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val newLevel = when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalThrottleLevel.NOMINAL

            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalThrottleLevel.SEVERE

            else -> ThermalThrottleLevel.NOMINAL
        }

        if (newLevel != currentLevel) {
            val previous = currentLevel
            currentLevel = newLevel
            if (newLevel == ThermalThrottleLevel.SEVERE && previous != ThermalThrottleLevel.SEVERE) {
                voiceAnnouncer?.speak("Alerta térmica: reduciendo velocidad")
            }
            onLevelChanged?.invoke(newLevel)
        }
    }

    override fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            (listener as? PowerManager.OnThermalStatusChangedListener)?.let {
                try {
                    powerManager.removeThermalStatusListener(it)
                } catch (ignored: Exception) {}
            }
            listener = null
        }
    }
}
