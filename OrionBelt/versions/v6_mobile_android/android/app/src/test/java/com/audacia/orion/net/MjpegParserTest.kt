package com.audacia.orion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MjpegParserTest {

    private lateinit var parser: MjpegParser

    @Before
    fun setUp() {
        parser = MjpegParser()
    }

    private fun makeJpeg(payload: ByteArray): ByteArray {
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        return soi + payload + eoi
    }

    @Test
    fun twoFramesInOneChunk() {
        val frame1 = makeJpeg(byteArrayOf(1, 2, 3))
        val frame2 = makeJpeg(byteArrayOf(4, 5, 6, 7))
        val chunk = frame1 + frame2

        val extracted = parser.feed(chunk, chunk.size)
        assertEquals(2, extracted.size)
        assertTrue(frame1.contentEquals(extracted[0]))
        assertTrue(frame2.contentEquals(extracted[1]))
    }

    @Test
    fun frameSplitAcrossChunks() {
        val frame = makeJpeg(ByteArray(100) { it.toByte() })
        val half = frame.size / 2

        val part1 = frame.copyOfRange(0, half)
        val part2 = frame.copyOfRange(half, frame.size)

        val res1 = parser.feed(part1, part1.size)
        assertEquals(0, res1.size)

        val res2 = parser.feed(part2, part2.size)
        assertEquals(1, res2.size)
        assertTrue(frame.contentEquals(res2[0]))
    }

    @Test
    fun corruptJpegIsSkipped() {
        // Un SOI sin EOI seguido de un frame completo y válido
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 10, 20, 30) // sin EOI
        val validFrame = makeJpeg(byteArrayOf(100, 101, 102))
        val chunk = corrupt + validFrame

        val extracted = parser.feed(chunk, chunk.size)
        assertEquals(1, extracted.size)
        assertTrue(validFrame.contentEquals(extracted[0]))
    }

    @Test
    fun oversizedBufferResets() {
        // Alimentar más de MAX_BUFFER_SIZE (512 KB) de basura sin SOI/EOI
        val garbage = ByteArray(300 * 1024) { 0x55.toByte() }
        parser.feed(garbage, garbage.size)
        parser.feed(garbage, garbage.size) // Total 600 KB > 512 KB

        // Luego alimentar un frame válido. Debe recuperarse y extraer el frame válido
        val valid = makeJpeg(byteArrayOf(9, 8, 7))
        val res = parser.feed(valid, valid.size)
        assertEquals(1, res.size)
        assertTrue(valid.contentEquals(res[0]))
    }
}
