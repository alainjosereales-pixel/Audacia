package com.audacia.orion.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Preprocesador de imágenes optimizado con cero asignación dinámica de memoria por frame.
 *
 * Reutiliza un único Bitmap escalado (256x256) y Canvas con filtrado bilineal y normaliza
 * con las constantes canónicas de ImageNet:
 * Mean: [0.485, 0.456, 0.406], Std: [0.229, 0.224, 0.225].
 */
class Preprocessor(
    val targetWidth: Int = 256,
    val targetHeight: Int = 256
) {
    companion object {
        private const val MEAN_R = 0.485f
        private const val MEAN_G = 0.456f
        private const val MEAN_B = 0.406f

        private const val INV_STD_R = 1.0f / 0.229f
        private const val INV_STD_G = 1.0f / 0.224f
        private const val INV_STD_B = 1.0f / 0.225f

        private const val INV_255 = 1.0f / 255.0f
    }

    private val intValues = IntArray(targetWidth * targetHeight)
    private val reusableScaledBitmap: Bitmap by lazy {
        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }
    private val scaleCanvas: Canvas by lazy { Canvas(reusableScaledBitmap) }
    private val scalePaint: Paint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val dstRect: Rect by lazy { Rect(0, 0, targetWidth, targetHeight) }

    val inputBuffer: FloatBuffer = ByteBuffer.allocateDirect(1 * targetWidth * targetHeight * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun process(bitmap: Bitmap): FloatBuffer {
        val sourceForPixels = if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            bitmap
        } else {
            scaleCanvas.drawBitmap(bitmap, null, dstRect, scalePaint)
            reusableScaledBitmap
        }

        sourceForPixels.getPixels(intValues, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        inputBuffer.rewind()
        for (i in 0 until targetWidth * targetHeight) {
            val pixel = intValues[i]

            val r = ((pixel shr 16 and 0xFF) * INV_255 - MEAN_R) * INV_STD_R
            val g = ((pixel shr 8 and 0xFF) * INV_255 - MEAN_G) * INV_STD_G
            val b = ((pixel and 0xFF) * INV_255 - MEAN_B) * INV_STD_B

            inputBuffer.put(r)
            inputBuffer.put(g)
            inputBuffer.put(b)
        }
        inputBuffer.rewind()

        return inputBuffer
    }
}
