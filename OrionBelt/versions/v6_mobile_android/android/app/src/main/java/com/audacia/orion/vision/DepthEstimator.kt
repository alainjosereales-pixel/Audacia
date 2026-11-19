package com.audacia.orion.vision

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

enum class InferenceBackend {
    GPU,
    CPU
}

/**
 * Motor de inferencia para MiDaS Small cuantizado en FP16 (ORION v6).
 *
 * Utiliza aceleración por GPU móvil si el hardware es compatible (CompatibilityList).
 * En caso contrario, realiza fallback suave a 4 hilos en CPU con XNNPACK.
 */
class DepthEstimator(
    context: Context,
    modelAssetName: String = "midas_small_256_fp16.tflite",
    preferredBackend: InferenceBackend = InferenceBackend.GPU
) : AutoCloseable {

    var activeBackend: InferenceBackend = InferenceBackend.CPU
        private set

    private var gpuDelegate: GpuDelegate? = null
    private val interpreter: Interpreter

    val outputWidth = 256
    val outputHeight = 256

    private val outputBuffer: FloatBuffer = ByteBuffer.allocateDirect(1 * outputWidth * outputHeight * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val rawArray = FloatArray(outputWidth * outputHeight)

    init {
        val modelBuffer = loadModelFile(context, modelAssetName)
        var activeDelegate: GpuDelegate? = null
        var activeInterpreter: Interpreter? = null
        var backendResolved = InferenceBackend.CPU

        if (preferredBackend == InferenceBackend.GPU) {
            try {
                // Si CompatibilityList reconoce el dispositivo, usar sus opciones sugeridas.
                // Si el dispositivo es un modelo reciente fuera de la whitelist estática de TFLite
                // (ej: Motorola Edge 50/60 Fusion, Adreno/Mali modernos), NO bloquear la GPU:
                // instanciar opciones estándar con precisión FP16 y velocidad sostenida.
                val delegateOptions = try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        compatList.bestOptionsForThisDevice
                    } else {
                        GpuDelegateFactory.Options()
                    }
                } catch (t: Throwable) {
                    GpuDelegateFactory.Options()
                }.apply {
                    setPrecisionLossAllowed(true) // FP16 nativo para duplicar velocidad en shaders GPU
                    setInferencePreference(GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                }

                val delegate = GpuDelegate(delegateOptions)
                val gpuOptions = Interpreter.Options().apply {
                    addDelegate(delegate)
                }

                // 1. Instanciar intérprete con aceleración GPU
                val testInterpreter = Interpreter(modelBuffer.duplicate(), gpuOptions)

                // 2. Verificación de ejecución: forzar compilación de shaders en GPU con un buffer de prueba
                val testInput = ByteBuffer.allocateDirect(1 * outputWidth * outputHeight * 3 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                testInterpreter.run(testInput, outputBuffer)
                outputBuffer.rewind()

                activeDelegate = delegate
                activeInterpreter = testInterpreter
                backendResolved = InferenceBackend.GPU
            } catch (t: Throwable) {
                // Fallback elegante a CPU si el driver GPU falla
                try {
                    activeDelegate?.close()
                } catch (ignored: Throwable) {}
                activeDelegate = null
                try {
                    activeInterpreter?.close()
                } catch (ignored: Throwable) {}
                activeInterpreter = null
                backendResolved = InferenceBackend.CPU
            }
        }

        // Si se seleccionó CPU o si la GPU no pudo compilar los shaders, inicializar CPU con XNNPACK
        if (activeInterpreter == null) {
            val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val cpuOptions = Interpreter.Options().apply {
                setUseXNNPACK(true)
                setNumThreads(cpuThreads)
            }
            activeInterpreter = Interpreter(modelBuffer.duplicate(), cpuOptions)
            backendResolved = InferenceBackend.CPU
        }

        gpuDelegate = activeDelegate
        interpreter = activeInterpreter
        activeBackend = backendResolved
    }

    private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(assetName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun infer(inputBuffer: FloatBuffer): FloatArray {
        inputBuffer.rewind()
        outputBuffer.rewind()

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        outputBuffer.get(rawArray)
        return rawArray
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }
}
