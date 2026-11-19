package com.audacia.orion.vision

import android.graphics.Bitmap

/**
 * Colorizador Magma/Inferno de alta velocidad para mapas de profundidad normalizados.
 *
 * Precalcula una tabla LUT de 256 colores ARGB correspondientes a la rampa:
 * Negro/Morado -> Magenta -> Naranja -> Amarillo brillante.
 *
 * Diseñado con cero asignación dinámica de memoria en el bucle de renderizado.
 */
object MagmaColorizer {
    val lut = IntArray(256)

    init {
        for (i in 0..255) {
            val x = i / 255.0f
            val r = (1.5f * x).coerceIn(0.0f, 1.0f)
            val g = (1.8f * (x - 0.35f)).coerceIn(0.0f, 1.0f)
            val b = (if (x < 0.4f) 2.5f * x else 1.0f - (x - 0.4f) * 1.6f).coerceIn(0.0f, 1.0f)

            val redInt = (r * 255).toInt()
            val greenInt = (g * 255).toInt()
            val blueInt = (b * 255).toInt()

            lut[i] = (0xFF shl 24) or (redInt shl 16) or (greenInt shl 8) or blueInt
        }
    }

    /**
     * Mapea un arreglo de profundidad normalizado [0.0 .. 1.0] a un buffer de píxeles ARGB.
     */
    fun colorize(
        depthNorm: FloatArray,
        width: Int = 256,
        height: Int = 256,
        outPixels: IntArray? = null
    ): IntArray {
        val total = width * height
        val pixels = outPixels ?: IntArray(total)
        val limit = minOf(depthNorm.size, total)

        for (i in 0 until limit) {
            val v = depthNorm[i]
            val index = (v * 255.0f).toInt().coerceIn(0, 255)
            pixels[i] = lut[index]
        }
        return pixels
    }

    /**
     * Genera o reutiliza un Bitmap ARGB_8888 con el mapa de calor aplicado.
     */
    fun toBitmap(
        depthNorm: FloatArray,
        width: Int = 256,
        height: Int = 256,
        reuseBitmap: Bitmap? = null,
        reusablePixels: IntArray? = null
    ): Bitmap {
        val pixels = colorize(depthNorm, width, height, reusablePixels)
        val bitmap = if (reuseBitmap != null &&
            reuseBitmap.width == width &&
            reuseBitmap.height == height &&
            !reuseBitmap.isRecycled
        ) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
