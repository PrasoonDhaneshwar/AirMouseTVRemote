package com.prasoon.airmousetv.presentation

import com.prasoon.airmousetv.data.api.VOICE_CHUNK_BYTES
import com.prasoon.airmousetv.data.api.VOICE_CHUNK_MS
import com.prasoon.airmousetv.data.model.VoiceError
import com.prasoon.airmousetv.data.model.VoiceState
import com.prasoon.airmousetv.data.repository.MicBusyException
import com.prasoon.airmousetv.data.repository.MicSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSearchControllerTest {

    /**
     * A TV that answers the search key with session 5 after [answerAfterMs], says it heard the end of
     * speech once [speechEnd] completes, and logs what it is sent.
     */
    private class FakeTv : TvVoicePort {
        val log = mutableListOf<String>()
        var answerAfterMs = 100L
        var answer: Int? = 5
        var failSending = false
        val speechEnd = CompletableDeferred<Unit>()
        val voiceChunks get() = log.count { it.startsWith("voice") }

        override suspend fun beginVoice(): Int? {
            log += "search"
            delay(answerAfterMs)
            return answer
        }

        override suspend fun sendVoice(sessionId: Int, samples: ByteArray) {
            if (failSending) throw IOException("gone")
            assertEquals(VOICE_CHUNK_BYTES, samples.size)
            log += "voice $sessionId"
        }

        override suspend fun awaitSpeechEnd(sessionId: Int) = speechEnd.await()

        override suspend fun endVoice(sessionId: Int) {
            log += "end $sessionId"
        }

        override suspend fun pressBack() {
            log += "back"
        }

        override fun note(event: String) = Unit
    }

    /** A microphone that hands out whatever [say] puts in, and is open while recording. */
    private class FakeMic : MicSource {
        private val frames = Channel<ByteArray>(Channel.UNLIMITED)
        var busy = false
        var open = false

        fun say(bytes: Int, level: Byte = 40) {
            frames.trySend(ByteArray(bytes) { if (it % 2 == 1) 0 else level })
        }

        override fun record(): Flow<ByteArray> = flow {
            if (busy) throw MicBusyException("in use")
            open = true
            try {
                for (frame in frames) emit(frame)
            } finally {
                open = false
            }
        }
    }

    private class Rig(val tv: FakeTv, val mic: FakeMic, val voice: VoiceSearchController)

    private fun TestScope.rig(): Rig {
        val tv = FakeTv()
        val mic = FakeMic()
        return Rig(tv, mic, VoiceSearchController(backgroundScope, tv, mic))
    }

    @Test
    fun `what is said before the TV answers is still sent`() = runTest {
        val r = rig()
        r.tv.answerAfterMs = 500
        r.voice.start(tapMode = false)
        r.mic.say(VOICE_CHUNK_BYTES / 2)
        runCurrent()
        assertEquals(listOf("search"), r.tv.log)

        advanceTimeBy(600)
        r.mic.say(VOICE_CHUNK_BYTES)
        runCurrent()
        r.voice.finish()
        runCurrent()

        // One full payload while talking, then the rest padded into a second
        assertEquals(listOf("search", "voice 5", "voice 5"), r.tv.log)
        assertEquals(VoiceState.Sent, r.voice.state.value)
        assertFalse(r.mic.open)
    }

    @Test
    fun `after letting go the session stays open with silence until the TV is done`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.mic.say(1000)
        runCurrent()
        r.voice.finish()
        runCurrent()
        assertEquals(1, r.tv.voiceChunks)

        // Silence keeps going at the pace of sound while the TV is still listening
        advanceTimeBy(VOICE_CHUNK_MS * 2 + 1)
        assertEquals(3, r.tv.voiceChunks)
        assertFalse(r.tv.log.contains("end 5"))

        // Heard: the session ends a little later, not at once
        r.tv.speechEnd.complete(Unit)
        advanceTimeBy(AFTER_SPEECH_END_MS - 100)
        assertFalse(r.tv.log.contains("end 5"))
        advanceTimeBy(200)
        assertEquals("end 5", r.tv.log.last())
    }

    @Test
    fun `a TV that never says it heard the end still gets its session ended`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.voice.finish()
        advanceTimeBy(SPEECH_END_WAIT_MS + AFTER_SPEECH_END_MS + 1)
        assertEquals("end 5", r.tv.log.last())
    }

    @Test
    fun `closing the Sent card doesn't cut the session short`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.voice.finish()
        runCurrent()
        r.voice.dismiss()
        assertEquals(VoiceState.Idle, r.voice.state.value)
        assertFalse(r.tv.log.contains("end 5"))
        r.tv.speechEnd.complete(Unit)
        advanceTimeBy(AFTER_SPEECH_END_MS + 1)
        assertEquals("end 5", r.tv.log.last())
    }

    @Test
    fun `the Sent card goes by itself`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.voice.finish()
        runCurrent()
        assertEquals(VoiceState.Sent, r.voice.state.value)
        advanceTimeBy(SENT_SHOWN_MS + 1)
        assertEquals(VoiceState.Idle, r.voice.state.value)
    }

    @Test
    fun `cancelling sends no voice, ends the TV's session and closes its assistant`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        r.mic.say(4000)
        advanceTimeBy(200)
        r.voice.setCancelArmed(true)
        assertTrue((r.voice.state.value as VoiceState.Listening).cancelArmed)
        r.voice.cancel()
        runCurrent()

        assertEquals(VoiceState.Idle, r.voice.state.value)
        assertEquals(listOf("search", "end 5", "back"), r.tv.log)
        assertFalse(r.mic.open)
    }

    @Test
    fun `a TV that doesn't open a voice session is reported at once`() = runTest {
        val r = rig()
        r.tv.answer = null
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        assertEquals(VoiceState.Failed(VoiceError.TV_NO_ANSWER), r.voice.state.value)
        assertFalse(r.mic.open)
        advanceTimeBy(ERROR_SHOWN_MS + 1)
        assertEquals(VoiceState.Idle, r.voice.state.value)
    }

    @Test
    fun `a busy microphone is reported and the TV's assistant is closed again`() = runTest {
        val r = rig()
        r.mic.busy = true
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        assertEquals(VoiceState.Failed(VoiceError.MIC_BUSY), r.voice.state.value)
        assertEquals(listOf("search", "end 5", "back"), r.tv.log)
    }

    @Test
    fun `losing the connection while talking sends nothing more`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.voice.connectionLost()
        runCurrent()
        assertEquals(VoiceState.Failed(VoiceError.WIFI_LOST), r.voice.state.value)
        assertFalse(r.tv.log.contains("end 5"))
        assertFalse(r.mic.open)
    }

    @Test
    fun `a send that fails while talking counts as a lost connection`() = runTest {
        val r = rig()
        r.tv.failSending = true
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.mic.say(VOICE_CHUNK_BYTES)
        runCurrent()
        assertEquals(VoiceState.Failed(VoiceError.WIFI_LOST), r.voice.state.value)
        assertFalse(r.mic.open)
    }

    @Test
    fun `listening stops by itself after the longest time allowed and sends`() = runTest {
        val r = rig()
        r.voice.start(tapMode = true)
        assertTrue((r.voice.state.value as VoiceState.Listening).tapMode)
        advanceTimeBy(MAX_LISTEN_MS + 1)
        runCurrent()
        assertEquals(VoiceState.Sent, r.voice.state.value)
    }

    @Test
    fun `the sound level feeds the bars, keeping only the latest`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        repeat(LEVEL_BARS + 5) { r.mic.say(800, level = if (it == LEVEL_BARS + 4) 100 else 0) }
        runCurrent()
        val levels = (r.voice.state.value as VoiceState.Listening).levels
        assertEquals(LEVEL_BARS, levels.size)
        assertTrue(levels.last() > 0f)
        assertEquals(0f, levels.first(), 0f)
    }

    @Test
    fun `a new search ends the previous one's session first`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        r.voice.finish()
        runCurrent()
        r.voice.start(tapMode = false)
        runCurrent()
        assertEquals(listOf("search", "end 5", "search"), r.tv.log.filter { it != "voice 5" })
    }

    @Test
    fun `a new search can start while a cancelled one is still closing the TV's assistant`() = runTest {
        val r = rig()
        r.voice.start(tapMode = false)
        runCurrent()
        // Cancelled before the TV has answered: tidying up waits for that answer
        r.voice.cancel()
        runCurrent()
        assertEquals(VoiceState.Idle, r.voice.state.value)
        r.voice.start(tapMode = false)
        advanceTimeBy(200)
        assertTrue(r.voice.state.value is VoiceState.Listening)
        r.voice.finish()
        runCurrent()
        assertEquals(VoiceState.Sent, r.voice.state.value)
        // The first search still closed the assistant it had opened
        assertTrue(r.tv.log.contains("back"))
    }
}
