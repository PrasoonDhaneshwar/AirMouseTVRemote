package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

class FrameCodecTest {

    private fun encode(payload: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        FrameCodec.writeFrame(DataOutputStream(bytes), payload)
        return bytes.toByteArray()
    }

    private fun decode(wire: ByteArray): ByteArray =
        FrameCodec.readFrame(DataInputStream(ByteArrayInputStream(wire)))

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun roundTrip_preservesPayloadAtVarintBoundaries() {
        for (size in listOf(0, 1, 127, 128, 300, 16_384, FrameCodec.MAX_FRAME_BYTES)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            assertArrayEquals("size $size", payload, decode(encode(payload)))
        }
    }

    @Test
    fun writeFrame_usesOneByteLengthUpTo127() {
        val wire = encode(ByteArray(127))
        assertEquals(1 + 127, wire.size)
        assertEquals(127, wire[0].toInt())
    }

    @Test
    fun writeFrame_usesTwoByteVarintFrom128() {
        // 128 = 0b1000_0000 -> low 7 bits 0 with continuation flag (0x80), then 1
        val wire = encode(ByteArray(128))
        assertEquals(0x80, wire[0].toInt() and 0xFF)
        assertEquals(0x01, wire[1].toInt())
        assertEquals(2 + 128, wire.size)
    }

    @Test
    fun writeFrame_encodes300AsAC02() {
        val wire = encode(ByteArray(300))
        assertEquals(0xAC, wire[0].toInt() and 0xFF)
        assertEquals(0x02, wire[1].toInt())
    }

    @Test
    fun readFrame_readsTwoBackToBackFrames() {
        val stream = DataInputStream(ByteArrayInputStream(encode(bytes(1, 2, 3)) + encode(bytes(9))))
        assertArrayEquals(bytes(1, 2, 3), FrameCodec.readFrame(stream))
        assertArrayEquals(bytes(9), FrameCodec.readFrame(stream))
    }

    @Test
    fun readFrame_throwsOnEofInLengthPrefix() {
        assertThrows(IOException::class.java) { decode(ByteArray(0)) }
        // Continuation flag set but no following byte
        assertThrows(IOException::class.java) { decode(bytes(0x80)) }
    }

    @Test
    fun readFrame_throwsOnEofInBody() {
        // Claims 5 bytes, delivers 2
        assertThrows(EOFException::class.java) { decode(bytes(5, 1, 2)) }
    }

    @Test
    fun readFrame_rejectsLengthAboveLimit() {
        // 65537 = 0x10001 -> 81 80 04
        assertThrows(IOException::class.java) { decode(bytes(0x81, 0x80, 0x04)) }
        // 0x7FFFFFFF would have tried a ~2 GB allocation
        assertThrows(IOException::class.java) { decode(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x07)) }
    }

    @Test
    fun readFrame_rejectsVarintLongerThanFiveBytes() {
        assertThrows(IOException::class.java) { decode(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01)) }
    }

    @Test
    fun readFrame_rejectsNegativeLengthFromOverflow() {
        // Fifth byte 0x0F sets bit 31, which makes the Int negative
        assertThrows(IOException::class.java) { decode(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F)) }
    }

    @Test
    fun readFrame_acceptsLimitExactly() {
        val payload = ByteArray(FrameCodec.MAX_FRAME_BYTES)
        assertEquals(FrameCodec.MAX_FRAME_BYTES, decode(encode(payload)).size)
    }
}
