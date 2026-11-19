package com.audacia.orion.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Lector de stream MJPEG continuo desde el endpoint del XIAO ESP32S3.
 *
 * Utiliza OkHttp con timeout de lectura de 3500 ms (alineado con la tolerancia
 * de Hot-Swap de ORION). Extrae frames JPEG completos y los emite en un Flow.
 */
class MjpegStreamReader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .build(),
    private val parser: MjpegParser = MjpegParser()
) {
    fun streamFrames(endpoint: XiaoEndpoint): Flow<ByteArray> = flow {
        val request = Request.Builder()
            .url(endpoint.streamUrl)
            .header("Accept", "multipart/x-mixed-replace")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw java.io.IOException("HTTP ${response.code}: No se pudo abrir el stream en ${endpoint.streamUrl}")
        }

        val body = response.body ?: throw java.io.IOException("Cuerpo de respuesta vacío")
        val inputStream: InputStream = body.byteStream()
        val readBuffer = ByteArray(8 * 1024)

        try {
            while (coroutineContext.isActive) {
                val bytesRead = inputStream.read(readBuffer)
                if (bytesRead == -1) break

                val frames = parser.feed(readBuffer, bytesRead)
                for (frame in frames) {
                    emit(frame)
                }
            }
        } finally {
            inputStream.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)
}
