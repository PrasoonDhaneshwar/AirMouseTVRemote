package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioTest {

    @Test
    fun `holds sound back until a whole payload is there`() {
        val chunker = VoiceChunker()
        assertTrue(chunker.add(ByteArray(VOICE_CHUNK_BYTES - 2)).isEmpty())
        val out = chunker.add(ByteArray(2))
        assertEquals(listOf(VOICE_CHUNK_BYTES), out.map { it.size })
        assertNull(chunker.finish())
    }

    @Test
    fun `every payload is exactly the size the TV recognises`() {
        val out = VoiceChunker().add(ByteArray(VOICE_CHUNK_BYTES * 2 + 1000))
        assertEquals(listOf(VOICE_CHUNK_BYTES, VOICE_CHUNK_BYTES), out.map { it.size })
    }

    @Test
    fun `pads the last payload with silence`() {
        val chunker = VoiceChunker()
        chunker.add(byteArrayOf(1, 2, 3, 4))
        val last = chunker.finish()!!
        assertEquals(VOICE_CHUNK_BYTES, last.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), last.copyOf(4))
        assertTrue(last.drop(4).all { it == 0.toByte() })
        assertNull(chunker.finish())
    }

    @Test
    fun `keeps the order of the sound across payloads`() {
        val chunker = VoiceChunker()
        val sound = ByteArray(VOICE_CHUNK_BYTES * 3 + 10) { (it % 251).toByte() }
        val out = chunker.add(sound.copyOfRange(0, 1000)) + chunker.add(sound.copyOfRange(1000, sound.size))
        val sent = out.fold(ByteArray(0)) { acc, b -> acc + b } + chunker.finish()!!
        assertArrayEquals(sound, sent.copyOf(sound.size))
    }

    @Test
    fun `a payload lasts 1280 ms at 8 kHz`() {
        assertEquals(1280L, VOICE_CHUNK_MS)
    }

    @Test
    fun `halving the rate averages each pair of samples`() {
        // Samples 100, 300, -200, -400 (little-endian) become 200 and -300
        val pcm = samples(100, 300, -200, -400)
        assertArrayEquals(samples(200, -300), halveSampleRate(pcm))
    }

    @Test
    fun `silence has no level and a full-scale tone the highest`() {
        assertEquals(0f, pcmLevel(ByteArray(800)), 0f)
        assertEquals(0f, pcmLevel(ByteArray(0)), 0f)
        val loud = samples(*IntArray(400) { if (it % 2 == 0) 32767 else -32768 })
        assertEquals(1f, pcmLevel(loud), 0.01f)
    }

    @Test
    fun `louder speech gives a higher level`() {
        val quiet = samples(*IntArray(400) { if (it % 2 == 0) 300 else -300 })
        val louder = samples(*IntArray(400) { if (it % 2 == 0) 3000 else -3000 })
        assertTrue(pcmLevel(louder) > pcmLevel(quiet))
    }

    private fun samples(vararg values: Int): ByteArray {
        val out = ByteArray(values.size * 2)
        values.forEachIndexed { i, v ->
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }
}
