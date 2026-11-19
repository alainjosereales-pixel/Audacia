package com.audacia.orion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelAssetSmokeTest {

    @Test
    fun modelAssetExistsAndMatchesExpectedSizeBytes() {
        // En tests unitarios en JVM, la carpeta assets está en src/main/assets
        val assetDir = File("src/main/assets")
        val modelFile = File(assetDir, "midas_small_256_fp16.tflite")

        assertTrue("El archivo midas_small_256_fp16.tflite debe existir en assets", modelFile.exists())
        assertEquals(
            "El tamaño del modelo debe ser exactamente 33,507,904 bytes",
            33507904L,
            modelFile.length()
        )
    }

    @Test
    fun goldenFixturesArePresentAndValidJson() {
        val fixtures = listOf("flat", "center", "left", "right", "gradient", "portada")
        val classLoader = javaClass.classLoader
        assertNotNull("ClassLoader no disponible", classLoader)

        for (name in fixtures) {
            val resourceStream = classLoader?.getResourceAsStream("fixtures/$name.json")
            assertNotNull("Fixture $name.json debe existir en test resources", resourceStream)

            val content = resourceStream!!.bufferedReader().use { it.readText() }
            val json = JSONObject(content)

            assertEquals("El fixture debe reportar el nombre correcto", name, json.getString("name"))
            assertEquals("El ancho de la matriz debe ser 64", 64, json.getInt("width"))
            assertEquals("El alto de la matriz debe ser 64", 64, json.getInt("height"))
            assertTrue("Debe contener expected", json.has("expected"))

            val expected = json.getJSONObject("expected")
            assertTrue("Expected debe definir action", expected.has("action"))
        }
    }
}
