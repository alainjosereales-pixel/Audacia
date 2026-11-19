package com.audacia.orion.net

import java.io.ByteArrayOutputStream

/**
 * Parser de streams MJPEG ultra-ligero y robusto ante caídas de red o bytes corruptos.
 *
 * Escanea marcadores SOI (0xFFD8) y EOI (0xFFD9) sin depender de cabeceras HTTP Content-Length.
 * Protegido contra sobrecarga de memoria mediante un límite estricto de buffer (512 KB).
 */
class MjpegParser(
    private val maxBufferSize: Int = 512 * 1024 // 512 KB
) {
    private var buffer = ByteArrayOutputStream(64 * 1024)

    fun feed(bytes: ByteArray, len: Int): List<ByteArray> {
        if (len <= 0) return emptyList()

        buffer.write(bytes, 0, len)

        if (buffer.size() > maxBufferSize) {
            val data = buffer.toByteArray()
            // Protección contra desbordamiento: buscar si hay algún SOI reciente en los últimos 64 KB
            val lastSoi = findLastSoi(data)
            buffer.reset()
            if (lastSoi != -1 && (data.size - lastSoi) < (maxBufferSize / 2)) {
                buffer.write(data, lastSoi, data.size - lastSoi)
            } else {
                return emptyList()
            }
        }

        val currentData = buffer.toByteArray()
        val frames = mutableListOf<ByteArray>()
        var searchStart = 0

        while (searchStart < currentData.size - 1) {
            val soiIdx = findSoi(currentData, searchStart)
            if (soiIdx == -1) break

            // Verificar si hay otro SOI antes de encontrar un EOI (frame truncado previo)
            val nextSoiIdx = findSoi(currentData, soiIdx + 2)

            val eoiIdx = findEoi(currentData, soiIdx + 2)
            if (eoiIdx == -1) {
                // Si no hay EOI pero hay otro SOI más adelante, el primer frame está roto: saltar al siguiente SOI
                if (nextSoiIdx != -1) {
                    searchStart = nextSoiIdx
                    continue
                }
                // Si aún no ha llegado el EOI, esperar más datos en el siguiente feed
                searchStart = soiIdx
                break
            }

            // Si hay un siguiente SOI que ocurre ANTES del EOI encontrado, el primer frame estaba corrupto
            if (nextSoiIdx != -1 && nextSoiIdx < eoiIdx) {
                searchStart = nextSoiIdx
                continue
            }

            // Frame válido completo encontrado entre soiIdx y eoiIdx + 2
            val frameEnd = eoiIdx + 2
            val frame = currentData.copyOfRange(soiIdx, frameEnd)
            frames.add(frame)
            searchStart = frameEnd
        }

        // Conservar en el buffer los bytes remanentes que aún no forman un frame completo
        buffer.reset()
        if (searchStart < currentData.size) {
            buffer.write(currentData, searchStart, currentData.size - searchStart)
        }

        return frames
    }

    private fun findSoi(data: ByteArray, start: Int): Int {
        for (i in start until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findEoi(data: ByteArray, start: Int): Int {
        for (i in start until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findLastSoi(data: ByteArray): Int {
        for (i in data.size - 2 downTo 0) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }
}
