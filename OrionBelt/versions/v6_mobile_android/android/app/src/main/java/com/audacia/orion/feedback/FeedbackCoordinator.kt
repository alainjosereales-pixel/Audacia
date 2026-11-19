package com.audacia.orion.feedback

import android.content.Context
import com.audacia.orion.net.XiaoEndpoint
import com.audacia.orion.vision.ZoneResult

data class FeedbackSettings(
    val hapticEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val voiceEnabled: Boolean = true
)

/**
 * Coordinador central de todos los canales de retroalimentación sensorial de ORION v6:
 * - Háptica direccional en el teléfono (vibración).
 * - Audio estéreo binaural en el teléfono (auriculares/altavoz).
 * - Avisos de voz TTS para estados del sistema.
 */
class FeedbackCoordinator(
    context: Context,
    val alertPolicy: AlertPolicy = AlertPolicy(),
    val haptic: HapticFeedback = HapticFeedback(context),
    val audio: AudioFeedback = AudioFeedback(context),
    val voice: VoiceAnnouncer = VoiceAnnouncer(context)
) : AutoCloseable {

    var settings: FeedbackSettings = FeedbackSettings()
        set(value) {
            field = value
            haptic.isEnabled = value.hapticEnabled
            audio.isEnabled = value.audioEnabled
            voice.isEnabled = value.voiceEnabled
        }

    init {
        haptic.isEnabled = settings.hapticEnabled
        audio.isEnabled = settings.audioEnabled
        voice.isEnabled = settings.voiceEnabled
    }

    fun processFrameResult(
        result: ZoneResult,
        endpoint: XiaoEndpoint? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Alert? {
        val alert = alertPolicy.update(result, nowMs)
        if (alert != null) {
            // 1. Vibración háptica direccional en el celular
            haptic.play(alert)

            // 2. Audio estéreo direccional en el celular (binaural)
            audio.play(alert)
        }
        return alert
    }

    fun onCameraConnected() {
        if (settings.voiceEnabled) {
            voice.announceCameraConnected()
        }
    }

    fun onCameraDisconnected() {
        if (settings.voiceEnabled) {
            voice.announceCameraDisconnected()
        }
    }

    /**
     * Notificación cuando el CameraWatchdog detecta interrupción del stream (> 3500 ms).
     * Emite vibración larga de alerta y anuncio por voz.
     */
    fun onCameraLost() {
        haptic.playCameraLost()
        if (settings.voiceEnabled) {
            voice.announceCameraDisconnected()
        }
    }

    fun onOrionReady() {
        if (settings.voiceEnabled) {
            voice.announceReady()
        }
    }

    override fun close() {
        haptic.cancel()
        audio.close()
        voice.close()
    }
}
