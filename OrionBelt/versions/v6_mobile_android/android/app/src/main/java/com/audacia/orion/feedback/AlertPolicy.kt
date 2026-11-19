package com.audacia.orion.feedback

import com.audacia.orion.vision.Action
import com.audacia.orion.vision.Zone
import com.audacia.orion.vision.ZoneResult

/**
 * Representa una alerta procesada lista para su emisión física (háptica, auditiva o buzzer).
 */
data class Alert(
    val level: Int,           // 0..3
    val zone: Zone,           // LEFT, CENTER, RIGHT
    val intervalMs: Long,     // 550, 380, 240, 150
    val intensity: Float      // 0.0 .. 1.0
)

/**
 * Cerebro de decisiones para la retroalimentación sensorial en ORION v6.
 *
 * Aplica:
 * 1. Mapeo de intensidad canónico de 4 niveles (0 a 3).
 * 2. Histéresis con margen de liberación para prevenir fluctuaciones erráticas.
 * 3. Filtrado de cadencia temporal (suprime ráfagas antes de que se cumpla el intervalo del nivel).
 * 4. Detección direccional para obstáculos laterales.
 */
class AlertPolicy(
    @Volatile var nearThreshold: Float = 0.60f,
    val releaseMargin: Float = 0.05f
) {
    private var inAlert = false
    private var lastAlertTimeMs: Long = 0L

    companion object {
        const val INTERVAL_LEVEL_0 = 550L
        const val INTERVAL_LEVEL_1 = 380L
        const val INTERVAL_LEVEL_2 = 240L
        const val INTERVAL_LEVEL_3 = 150L

        fun mapLevel(intensity: Float): Pair<Int, Long> {
            return when {
                intensity >= 0.85f -> Pair(3, INTERVAL_LEVEL_3)
                intensity >= 0.60f -> Pair(2, INTERVAL_LEVEL_2)
                intensity >= 0.30f -> Pair(1, INTERVAL_LEVEL_1)
                else -> Pair(0, INTERVAL_LEVEL_0)
            }
        }
    }

    fun update(result: ZoneResult, nowMs: Long): Alert? {
        val maxVal = maxOf(result.left, result.center, result.right)
        val maxZone = result.nearestZone ?: when {
            result.left >= result.center && result.left >= result.right -> Zone.LEFT
            result.right >= result.center && result.right >= result.left -> Zone.RIGHT
            else -> Zone.CENTER
        }

        // Histéresis
        val releaseThreshold = nearThreshold - releaseMargin
        if (inAlert) {
            if (maxVal < releaseThreshold) {
                inAlert = false
            }
        } else {
            if (result.action == Action.STOP || maxVal >= nearThreshold) {
                inAlert = true
            }
        }

        if (!inAlert) {
            return null
        }

        // Determinar intensidad efectiva
        val effectiveIntensity = if (result.action == Action.STOP && result.intensity > 0.0f) {
            result.intensity
        } else {
            val span = maxOf(1e-6f, 1.0f - nearThreshold)
            ((maxVal - nearThreshold) / span).coerceIn(0.0f, 1.0f)
        }

        val (level, intervalMs) = mapLevel(effectiveIntensity)

        // Comprobar cadencia temporal según el intervalo del nivel
        if (nowMs - lastAlertTimeMs >= intervalMs) {
            lastAlertTimeMs = nowMs
            return Alert(
                level = level,
                zone = maxZone,
                intervalMs = intervalMs,
                intensity = effectiveIntensity
            )
        }

        return null
    }

    fun reset() {
        inAlert = false
        lastAlertTimeMs = 0L
    }
}
