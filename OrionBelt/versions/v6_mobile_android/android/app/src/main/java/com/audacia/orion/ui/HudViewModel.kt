package com.audacia.orion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audacia.orion.feedback.FeedbackSettings
import com.audacia.orion.service.OrionService
import com.audacia.orion.service.OrionSession
import com.audacia.orion.vision.ConnectionStatus
import com.audacia.orion.vision.InferenceBackend
import com.audacia.orion.vision.VisionPipeline
import com.audacia.orion.vision.VisionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HudViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline: VisionPipeline = OrionSession.getPipeline(application)

    val state: StateFlow<VisionState> = pipeline.state

    init {
        // Iniciar servicio en primer plano para asegurar persistencia en bolsillo con pantalla apagada
        OrionService.start(application)
        if (pipeline.state.value.connectionStatus == ConnectionStatus.IDLE) {
            startAutoDiscovery()
        }
    }

    fun startAutoDiscovery() {
        pipeline.startAutoDiscovery()
    }

    fun connectDirect(ip: String) {
        pipeline.connectDirect(ip)
    }

    fun stop() {
        pipeline.stop()
    }

    fun switchBackend(backend: InferenceBackend) {
        pipeline.switchBackend(backend)
    }

    fun updateFeedbackSettings(settings: FeedbackSettings) {
        pipeline.updateFeedbackSettings(settings)
    }

    fun updateNearThreshold(threshold: Float) {
        pipeline.updateNearThreshold(threshold)
    }

    fun getLastKnownIp(): String? = pipeline.getLastKnownIp()

    fun runBenchmark(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = pipeline.runDeviceBenchmark(25)
            onDone(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No cerramos el pipeline aquí si OrionService está activo en modo bolsillo
        if (OrionService.instance == null) {
            OrionSession.close()
        }
    }
}
