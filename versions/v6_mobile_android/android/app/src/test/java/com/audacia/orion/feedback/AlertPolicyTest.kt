package com.audacia.orion.feedback

import com.audacia.orion.vision.Action
import com.audacia.orion.vision.Zone
import com.audacia.orion.vision.ZoneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {

    @Test
    fun levelMapping() {
        val (lvl0, int0) = AlertPolicy.mapLevel(0.10f)
        assertEquals(0, lvl0)
        assertEquals(550L, int0)

        val (lvl1, int1) = AlertPolicy.mapLevel(0.35f)
        assertEquals(1, lvl1)
        assertEquals(380L, int1)

        val (lvl2, int2) = AlertPolicy.mapLevel(0.65f)
        assertEquals(2, lvl2)
        assertEquals(240L, int2)

        val (lvl3, int3) = AlertPolicy.mapLevel(0.90f)
        assertEquals(3, lvl3)
        assertEquals(150L, int3)
    }

    @Test
    fun hysteresisPreventsFlicker() {
        val policy = AlertPolicy(nearThreshold = 0.60f, releaseMargin = 0.05f)
        var time = 1000L

        // 1. Por debajo del umbral (0.58 < 0.60): Sin alerta
        val r1 = ZoneResult(Action.FORWARD, 0.0f, 0.2f, 0.58f, 0.2f, 0.3f, null)
        assertNull(policy.update(r1, time))

        // 2. Supera el umbral (0.62 >= 0.60): Se activa la alerta
        val r2 = ZoneResult(Action.STOP, 0.05f, 0.2f, 0.62f, 0.2f, 0.35f, Zone.CENTER)
        val alert2 = policy.update(r2, time)
        assertNotNull("Debe disparar alerta al superar 0.60", alert2)

        // 3. Cae a 0.58 (está entre 0.55 y 0.60): Gracias a la histéresis, la alerta SE MANTIENE
        time += 600L // Avanzar tiempo suficiente para el intervalo
        val r3 = ZoneResult(Action.FORWARD, 0.0f, 0.2f, 0.58f, 0.2f, 0.3f, Zone.CENTER)
        val alert3 = policy.update(r3, time)
        assertNotNull("La histéresis debe mantener activa la alerta en 0.58", alert3)

        // 4. Cae por debajo del margen de liberación (< 0.55): Se desactiva
        time += 600L
        val r4 = ZoneResult(Action.FORWARD, 0.0f, 0.2f, 0.54f, 0.2f, 0.25f, null)
        val alert4 = policy.update(r4, time)
        assertNull("Por debajo de 0.55 debe liberarse la alerta", alert4)
    }

    @Test
    fun respectsIntervalPerLevel() {
        val policy = AlertPolicy(nearThreshold = 0.60f)
        var time = 1000L

        // Alerta de nivel 3 (intensidad 0.90, intervalo 150 ms)
        val rHigh = ZoneResult(Action.STOP, 0.90f, 0.2f, 0.95f, 0.2f, 0.8f, Zone.CENTER)

        val a1 = policy.update(rHigh, time)
        assertNotNull("Primera alerta en t=1000 debe emitirse", a1)
        assertEquals(3, a1?.level)
        assertEquals(150L, a1?.intervalMs)

        // Intento a los 100 ms (t=1100): Debe descartarse porque 100 ms < 150 ms
        val a2 = policy.update(rHigh, time + 100L)
        assertNull("Debe suprimirse porque no ha pasado el intervalo de 150 ms", a2)

        // Intento a los 155 ms (t=1155): Debe emitirse
        val a3 = policy.update(rHigh, time + 155L)
        assertNotNull("Debe emitirse al cumplirse el intervalo de 150 ms", a3)
    }

    @Test
    fun sideObstacleGivesDirectionalCueOnly() {
        val policy = AlertPolicy(nearThreshold = 0.60f)
        val time = 1000L

        // Obstáculo lateral a la izquierda (0.75 en izquierda, centro libre 0.30)
        val rLeft = ZoneResult(Action.FORWARD, 0.0f, 0.75f, 0.30f, 0.20f, 0.40f, Zone.LEFT)
        val alert = policy.update(rLeft, time)

        assertNotNull(alert)
        assertEquals("La alerta debe apuntar a la zona izquierda", Zone.LEFT, alert?.zone)
    }

    @Test
    fun dynamicNearThresholdAdjustmentClampsAndTriggersCorrectly() {
        val policy = AlertPolicy(nearThreshold = 0.60f)
        val rMedium = ZoneResult(Action.FORWARD, 0.0f, 0.2f, 0.50f, 0.2f, 0.3f, null)

        // Con umbral 0.60, center=0.50 no dispara alerta
        assertNull(policy.update(rMedium, 1000L))

        // Al bajar el umbral a 0.45, center=0.50 sí dispara alerta
        policy.nearThreshold = 0.45f
        val alert = policy.update(rMedium, 2000L)
        assertNotNull("Con umbral 0.45 debe disparar alerta en 0.50", alert)
    }
}
