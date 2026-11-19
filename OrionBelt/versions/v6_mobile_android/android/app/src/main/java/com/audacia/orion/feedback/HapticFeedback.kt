package com.audacia.orion.feedback

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.audacia.orion.vision.Zone

/**
 * Motor de retroalimentación háptica direccional de alta precisión para ORION v6.
 *
 * Utiliza VibratorManager en Android 12+ (API 31+) y Vibrator clásico en versiones previas.
 * Modula la amplitud según el nivel de alerta (0 a 3) y el patrón temporal según la zona (L / C / R).
 */
class HapticFeedback(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isEnabled: Boolean = true

    fun play(alert: Alert) {
        if (!isEnabled || vibrator == null || !vibrator.hasVibrator()) return

        val amplitude = when (alert.level) {
            3 -> 255
            2 -> 190
            1 -> 130
            else -> 70
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (alert.zone) {
                Zone.LEFT -> {
                    // Doble pulso direccional izquierdo
                    val timings = longArrayOf(0, 35, 45, 35)
                    val amplitudes = intArrayOf(0, amplitude, 0, amplitude)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                Zone.RIGHT -> {
                    // Triple pulso ultra-rápido direccional derecho
                    val timings = longArrayOf(0, 25, 30, 25, 30, 25)
                    val amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                Zone.CENTER -> {
                    // Pulso firme frontal centrado
                    val duration = when (alert.level) {
                        3 -> 100L
                        2 -> 80L
                        1 -> 60L
                        else -> 45L
                    }
                    VibrationEffect.createOneShot(duration, amplitude)
                }
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val duration = (40L + alert.level * 20L)
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    /**
     * Emite una vibración larga y distintiva de advertencia cuando se pierde el enlace con la cámara.
     * Patrón de dos pulsos largos (300 ms pulso - 100 ms pausa - 300 ms pulso a máxima intensidad).
     */
    fun playCameraLost() {
        if (!isEnabled || vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 300, 100, 300)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 300), -1)
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}
