package com.audacia.orion.vision

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DepthAnalyzerTest {

    private fun loadFixture(name: String): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$name.json")
        assertNotNull("Fixture fixtures/$name.json no encontrado", stream)
        val text = stream!!.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    private fun runFixtureTest(fixtureName: String) {
        val json = loadFixture(fixtureName)
        val width = json.getInt("width")
        val height = json.getInt("height")
        val sampleStep = json.getInt("sample_step")
        val nearThreshold = json.getDouble("near_threshold").toFloat()

        val depthJsonArr = json.getJSONArray("depth_norm")
        val depthNorm = FloatArray(depthJsonArr.length()) { i ->
            depthJsonArr.getDouble(i).toFloat()
        }

        val expected = json.getJSONObject("expected")
        val expectedAction = expected.getString("action")
        val expectedIntensity = expected.getDouble("intensity").toFloat()
        val expectedLeft = expected.getDouble("left").toFloat()
        val expectedCenter = expected.getDouble("center").toFloat()
        val expectedRight = expected.getDouble("right").toFloat()
        val expectedOverall = expected.getDouble("overall").toFloat()
        val expectedNearestZone = if (expected.isNull("nearest_zone")) null else expected.getString("nearest_zone")

        val result = DepthAnalyzer.analyze(
            depthNorm = depthNorm,
            width = width,
            height = height,
            nearThreshold = nearThreshold,
            sampleStep = sampleStep
        )

        assertEquals("[$fixtureName] Acción no coincide", expectedAction, result.action.name)
        assertEquals("[$fixtureName] Intensidad difiere", expectedIntensity, result.intensity, 1e-4f)
        assertEquals("[$fixtureName] Media Left difiere", expectedLeft, result.left, 1e-4f)
        assertEquals("[$fixtureName] Media Center difiere", expectedCenter, result.center, 1e-4f)
        assertEquals("[$fixtureName] Media Right difiere", expectedRight, result.right, 1e-4f)
        assertEquals("[$fixtureName] Media Overall difiere", expectedOverall, result.overall, 1e-4f)

        if (expectedNearestZone == null) {
            assertNull("[$fixtureName] nearestZone debió ser null", result.nearestZone)
        } else {
            assertEquals("[$fixtureName] nearestZone no coincide", expectedNearestZone, result.nearestZone?.name)
        }
    }

    @Test
    fun testFlatFixture() {
        runFixtureTest("flat")
    }

    @Test
    fun testCenterFixture() {
        runFixtureTest("center")
    }

    @Test
    fun testLeftFixture() {
        runFixtureTest("left")
    }

    @Test
    fun testRightFixture() {
        runFixtureTest("right")
    }

    @Test
    fun testGradientFixture() {
        runFixtureTest("gradient")
    }

    @Test
    fun testPortadaFixture() {
        runFixtureTest("portada")
    }

    @Test
    fun normalizeReusesProvidedOutputBufferWithoutAllocatingNewArray() {
        val raw = floatArrayOf(10f, 20f, 30f, 50f)
        val reusable = FloatArray(4)
        val result = DepthAnalyzer.normalize(raw, invert = false, outArray = reusable)

        assertSame("Debe reutilizar la misma referencia de FloatArray", reusable, result)
        assertEquals(0.0f, result[0], 1e-5f)
        assertEquals(0.25f, result[1], 1e-5f)
        assertEquals(0.50f, result[2], 1e-5f)
        assertEquals(1.0f, result[3], 1e-5f)
    }
}
