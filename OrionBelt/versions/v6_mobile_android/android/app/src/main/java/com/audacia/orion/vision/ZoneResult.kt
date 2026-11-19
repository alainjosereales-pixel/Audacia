package com.audacia.orion.vision

enum class Action {
    STOP,
    FORWARD
}

enum class Zone {
    LEFT,
    CENTER,
    RIGHT
}

data class ZoneResult(
    val action: Action,
    val intensity: Float,
    val left: Float,
    val center: Float,
    val right: Float,
    val overall: Float,
    val nearestZone: Zone?
)
