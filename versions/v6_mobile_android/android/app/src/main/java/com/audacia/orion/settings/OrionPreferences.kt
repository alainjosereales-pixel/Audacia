package com.audacia.orion.settings

import android.content.Context
import com.audacia.orion.feedback.FeedbackSettings
import com.audacia.orion.vision.InferenceBackend

/**
 * Almacenamiento persistente de ajustes de usuario para ORION v6.
 *
 * Guarda:
 * - Umbral de proximidad (nearThreshold: 0.40 a 0.90, defecto 0.60).
 * - Estado de los canales sensoriales (Háptica, Audio, Voz).
 * - Backend de inferencia preferido (GPU o CPU).
 */
class OrionPreferences(private val context: Context) {

    companion object {
        const val PREFS_NAME = "orion_user_settings"
        const val KEY_NEAR_THRESHOLD = "near_threshold"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_AUDIO_ENABLED = "audio_enabled"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_PREFERRED_BACKEND = "preferred_backend"

        const val DEFAULT_NEAR_THRESHOLD = 0.60f
        const val MIN_NEAR_THRESHOLD = 0.40f
        const val MAX_NEAR_THRESHOLD = 0.90f

        fun clampThreshold(value: Float): Float =
            value.coerceIn(MIN_NEAR_THRESHOLD, MAX_NEAR_THRESHOLD)
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getNearThreshold(): Float {
        return try {
            clampThreshold(prefs.getFloat(KEY_NEAR_THRESHOLD, DEFAULT_NEAR_THRESHOLD))
        } catch (e: Exception) {
            DEFAULT_NEAR_THRESHOLD
        }
    }

    fun saveNearThreshold(threshold: Float) {
        try {
            prefs.edit()
                .putFloat(KEY_NEAR_THRESHOLD, clampThreshold(threshold))
                .apply()
        } catch (ignored: Exception) {}
    }

    fun getFeedbackSettings(): FeedbackSettings {
        return try {
            FeedbackSettings(
                hapticEnabled = prefs.getBoolean(KEY_HAPTIC_ENABLED, true),
                audioEnabled = prefs.getBoolean(KEY_AUDIO_ENABLED, true),
                voiceEnabled = prefs.getBoolean(KEY_VOICE_ENABLED, true)
            )
        } catch (e: Exception) {
            FeedbackSettings()
        }
    }

    fun saveFeedbackSettings(settings: FeedbackSettings) {
        try {
            prefs.edit()
                .putBoolean(KEY_HAPTIC_ENABLED, settings.hapticEnabled)
                .putBoolean(KEY_AUDIO_ENABLED, settings.audioEnabled)
                .putBoolean(KEY_VOICE_ENABLED, settings.voiceEnabled)
                .apply()
        } catch (ignored: Exception) {}
    }

    fun getPreferredBackend(): InferenceBackend {
        return try {
            val raw = prefs.getString(KEY_PREFERRED_BACKEND, InferenceBackend.GPU.name)
            if (raw == InferenceBackend.CPU.name) InferenceBackend.CPU else InferenceBackend.GPU
        } catch (e: Exception) {
            InferenceBackend.GPU
        }
    }

    fun savePreferredBackend(backend: InferenceBackend) {
        try {
            prefs.edit()
                .putString(KEY_PREFERRED_BACKEND, backend.name)
                .apply()
        } catch (ignored: Exception) {}
    }
}
