package com.audacia.orion.service

import android.content.Context
import com.audacia.orion.vision.VisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Gestor singleton de sesión de ORION v6.
 *
 * Mantiene una única instancia canónica de [VisionPipeline] compartida entre
 * [OrionService] (ForegroundService en segundo plano) y [HudViewModel] (interfaz Compose).
 * Permite que la inferencia y las alertas sensoriales continúen ininterrumpidas en el bolsillo
 * cuando la actividad pasa a segundo plano o se apaga la pantalla.
 */
object OrionSession {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var pipelineInstance: VisionPipeline? = null

    fun getPipeline(context: Context): VisionPipeline {
        return pipelineInstance ?: synchronized(this) {
            pipelineInstance ?: VisionPipeline(context.applicationContext, sessionScope).also {
                pipelineInstance = it
            }
        }
    }

    fun stop() {
        pipelineInstance?.stop()
    }

    fun close() {
        synchronized(this) {
            pipelineInstance?.close()
            pipelineInstance = null
        }
    }
}
