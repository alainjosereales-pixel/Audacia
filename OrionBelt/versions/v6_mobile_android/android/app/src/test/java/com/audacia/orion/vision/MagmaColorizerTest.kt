package com.audacia.orion.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagmaColorizerTest {

    @Test
    fun testLutInitializedAndBounds() {
        assertEquals("LUT debe tener 256 entradas", 256, MagmaColorizer.lut.size)

        // El valor 0 debe ser completamente opaco (Alpha = 0xFF) y negro o casi negro
        val firstColor = MagmaColorizer.lut[0]
        val alpha0 = (firstColor ushr 24) and 0xFF
        val r0 = (firstColor ushr 16) and 0xFF
        val g0 = (firstColor ushr 8) and 0xFF
        val b0 = firstColor and 0xFF

        assertEquals(255, alpha0)
        assertEquals(0, r0)
        assertEquals(0, g0)
        assertEquals(0, b0)

        // El valor 255 debe ser amarillo brillante de alta intensidad
        val lastColor = MagmaColorizer.lut[255]
        val r255 = (lastColor ushr 16) and 0xFF
        val g255 = (lastColor ushr 8) and 0xFF

        assertTrue("Rojo en 255 debe estar cerca del máximo", r255 >= 240)
        assertTrue("Verde en 255 debe estar cerca del máximo", g255 >= 240)
    }

    @Test
    fun testColorizeMapping() {
        val depth = floatArrayOf(0.0f, 0.5f, 1.0f, -0.5f, 1.5f)
        val pixels = MagmaColorizer.colorize(depth, width = 5, height = 1)

        assertEquals(5, pixels.size)
        // -0.5f debe clampearse a 0
        assertEquals(MagmaColorizer.lut[0], pixels[3])
        // 1.5f debe clampearse a 255
        assertEquals(MagmaColorizer.lut[255], pixels[4])
        // 0.0f mapea a index 0
        assertEquals(MagmaColorizer.lut[0], pixels[0])
        // 1.0f mapea a index 255
        assertEquals(MagmaColorizer.lut[255], pixels[2])
    }
}
