package com.audacia.orion.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.audacia.orion.vision.Zone
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Motor de audio estéreo direccional de ultra-baja latencia basado en SoundPool.
 *
 * Emite tonos puros (700 Hz, 950 Hz, 1200 Hz, 1500 Hz) con paneo estéreo
 * binaural para audífonos o altavoces (paneo lateral L/C/R).
 *
 * Genera automáticamente los tonos PCM canónicos en caché para evitar
 * dependencias de activos externos y asegurar arranque instantáneo.
 */
class AudioFeedback(context: Context) : AutoCloseable {

    var isEnabled: Boolean = true

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = IntArray(4)

    init {
        // Frecuencias canónicas de ORION: 700 Hz (N0), 950 Hz (N1), 1200 Hz (N2), 1500 Hz (N3)
        val frequencies = intArrayOf(700, 950, 1200, 1500)
        val durationsMs = intArrayOf(100, 90, 80, 70)

        for (i in 0..3) {
            val wavFile = File(context.cacheDir, "orion_tone_lvl_$i.wav")
            if (!wavFile.exists() || wavFile.length() < 100) {
                generateWavTone(wavFile, frequencies[i], durationsMs[i])
            }
            soundIds[i] = soundPool.load(wavFile.absolutePath, 1)
        }
    }

    fun play(alert: Alert) {
        if (!isEnabled) return

        val levelIndex = alert.level.coerceIn(0, 3)
        val soundId = soundIds[levelIndex]
        if (soundId == 0) return

        // Paneo estéreo direccional
        val (leftVol, rightVol) = when (alert.zone) {
            Zone.LEFT -> Pair(1.0f, 0.15f)
            Zone.RIGHT -> Pair(0.15f, 1.0f)
            Zone.CENTER -> Pair(1.0f, 1.0f)
        }

        soundPool.play(soundId, leftVol, rightVol, 1, 0, 1.0f)
    }

    private fun generateWavTone(file: File, freqHz: Int, durationMs: Int) {
        val sampleRate = 16000
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val dataSize = numSamples * 2
        val totalSize = 36 + dataSize

        val pcm = ShortArray(numSamples)
        val fadeSamples = (numSamples * 0.08).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i * freqHz / sampleRate
            var sample = (sin(angle) * Short.MAX_VALUE * 0.85).toInt()

            // Atenuación suave en los bordes para eliminar clicks / pops de audio
            if (i < fadeSamples) {
                sample = (sample * (i.toDouble() / fadeSamples)).toInt()
            } else if (i > numSamples - fadeSamples) {
                sample = (sample * ((numSamples - i).toDouble() / fadeSamples)).toInt()
            }

            pcm[i] = sample.toShort()
        }

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (16 para PCM)
            header.putShort(1) // AudioFormat (1 = PCM)
            header.putShort(1) // NumChannels (1 = Mono)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // ByteRate
            header.putShort(2) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(dataSize)

            out.write(header.array())

            val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) {
                buffer.putShort(s)
            }
            out.write(buffer.array())
        }
    }

    override fun close() {
        soundPool.release()
    }
}
