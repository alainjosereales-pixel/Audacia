package com.audacia.orion.resilience

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraWatchdogTest {

    @Test
    fun initialStateIsOk() {
        val watchdog = CameraWatchdog(timeoutMs = 3500L)
        assertEquals(WatchdogState.OK, watchdog.check(1000L))
    }

    @Test
    fun staysOkWithinTimeout() {
        val watchdog = CameraWatchdog(timeoutMs = 3500L)
        val startTime = 10000L

        watchdog.onFrame(startTime)
        // A los 1000 ms transcurridos
        assertEquals(WatchdogState.OK, watchdog.check(startTime + 1000L))
        // A los 3499 ms transcurridos (justo en el límite)
        assertEquals(WatchdogState.OK, watchdog.check(startTime + 3499L))
    }

    @Test
    fun lostAfter3500ms() {
        val watchdog = CameraWatchdog(timeoutMs = 3500L)
        val startTime = 10000L

        watchdog.onFrame(startTime)
        // Supera el umbral de 3500 ms (3501 ms)
        val state = watchdog.check(startTime + 3501L)
        assertEquals(WatchdogState.LOST, state)
    }

    @Test
    fun recoversOnFrame() {
        val watchdog = CameraWatchdog(timeoutMs = 3500L)
        val startTime = 10000L

        watchdog.onFrame(startTime)
        assertEquals(WatchdogState.LOST, watchdog.check(startTime + 4000L))

        // Llega un nuevo frame
        watchdog.onFrame(startTime + 4500L)
        assertEquals(WatchdogState.OK, watchdog.check(startTime + 4600L))
        assertEquals(WatchdogState.OK, watchdog.currentState)
    }

    @Test
    fun resetClearsState() {
        val watchdog = CameraWatchdog(timeoutMs = 3500L)
        watchdog.onFrame(1000L)
        assertEquals(WatchdogState.LOST, watchdog.check(5000L))

        watchdog.reset()
        assertEquals(WatchdogState.OK, watchdog.check(6000L))
    }
}
