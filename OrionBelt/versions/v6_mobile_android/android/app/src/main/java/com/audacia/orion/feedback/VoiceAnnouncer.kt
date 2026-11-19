package com.audacia.orion.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Anunciador por voz en español para eventos y transiciones críticas del sistema ORION v6.
 *
 * Diseñado para mantenerse en silencio durante la marcha normal para no interferir
 * con las señales continuas de audio y vibración, activándose solo en cambios de estado.
 */
class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {

    var isEnabled: Boolean = true

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private var lastSpokenText: String? = null
    private var lastSpokenTimeMs: Long = 0L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("es-ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback al idioma por defecto del dispositivo
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.1f)
            isInitialized = true
        }
    }

    fun speak(text: String, minIntervalMs: Long = 2500L) {
        if (!isEnabled || !isInitialized || tts == null) return
        val now = System.currentTimeMillis()
        // Suprimir repeticiones del mismo texto dentro de la ventana de enfriamiento
        if (text == lastSpokenText && (now - lastSpokenTimeMs) < minIntervalMs) {
            return
        }
        // Suprimir cambios oscilatorios rápidos (< 1200 ms)
        if ((now - lastSpokenTimeMs) < 1200L) {
            return
        }
        lastSpokenText = text
        lastSpokenTimeMs = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orion_tts_$now")
    }

    fun announceReady() = speak("ORION listo")
    fun announceCameraConnected() = speak("Cámara conectada")
    fun announceCameraDisconnected() = speak("Cámara desconectada")

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
