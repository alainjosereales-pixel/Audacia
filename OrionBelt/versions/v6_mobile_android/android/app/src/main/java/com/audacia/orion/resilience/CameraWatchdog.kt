package com.audacia.orion.resilience

enum class WatchdogState {
    OK,
    LOST
}

/**
 * Watchdog para monitorear la continuidad del stream de video del XIAO ESP32S3.
 *
 * Si no se recibe ningún frame en un intervalo mayor a [timeoutMs] (3500 ms por defecto),
 * el watchdog pasa a estado [WatchdogState.LOST], indicando pérdida de enlace o cuelgue del sensor.
 * Se recupera automáticamente a [WatchdogState.OK] en cuanto arriba un nuevo frame.
 */
class CameraWatchdog(val timeoutMs: Long = 3500L) {

    private var lastFrameTimeMs: Long = 0L
    private var state: WatchdogState = WatchdogState.OK

    /**
     * Notifica el arribo de un nuevo frame de video válido.
     */
    fun onFrame(nowMs: Long = System.currentTimeMillis()) {
        lastFrameTimeMs = nowMs
        state = WatchdogState.OK
    }

    /**
     * Verifica si el stream ha excedido la ventana de tolerancia de 3500 ms.
     */
    fun check(nowMs: Long = System.currentTimeMillis()): WatchdogState {
        if (lastFrameTimeMs == 0L) {
            return WatchdogState.OK
        }
        val elapsed = nowMs - lastFrameTimeMs
        state = if (elapsed > timeoutMs) {
            WatchdogState.LOST
        } else {
            WatchdogState.OK
        }
        return state
    }

    /**
     * Reinicia el estado del watchdog al iniciar o detener un stream.
     */
    fun reset() {
        lastFrameTimeMs = 0L
        state = WatchdogState.OK
    }

    val currentState: WatchdogState get() = state
}
