package com.prasoon.airmousetv.presentation

import com.prasoon.airmousetv.data.api.VOICE_CHUNK_BYTES
import com.prasoon.airmousetv.data.api.VOICE_CHUNK_MS
import com.prasoon.airmousetv.data.api.VoiceChunker
import com.prasoon.airmousetv.data.api.pcmLevel
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.model.VoiceError
import com.prasoon.airmousetv.data.model.VoiceState
import com.prasoon.airmousetv.data.repository.MicBusyException
import com.prasoon.airmousetv.data.repository.MicSource
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** How long the TV gets to answer the search key with a voice session; a TV on Wi-Fi answers in well under a second. */
internal const val VOICE_BEGIN_TIMEOUT_MS = 3000L

/** Longest a voice search listens. The TV's assistant stops listening by itself well before this; it is only a safety net. */
internal const val MAX_LISTEN_MS = 15_000L

/** How long the "Sent" card shows before the pad comes back. */
internal const val SENT_SHOWN_MS = 2000L

/** How long an error stays in the voice panel if nobody closes it. */
internal const val ERROR_SHOWN_MS = 6000L

/** How many sound levels the panel draws as bars: one per recorded frame, about a second of sound. */
internal const val LEVEL_BARS = 21

/**
 * How long after the key is let go the session waits for the TV to say it heard the end of speech.
 * Its assistant reports it a second or two after the voice stops; this only bounds a TV that never does.
 */
internal const val SPEECH_END_WAIT_MS = 10_000L

/**
 * How long the session stays open after the TV heard the end of speech. Ending it sooner cancelled
 * the search on a Sharp (androidtvremote2 issue #49); about 3 s worked there.
 */
internal const val AFTER_SPEECH_END_MS = 3000L

/** What the voice search needs from the TV. */
internal interface TvVoicePort {
    /** Presses the search key and returns the voice session the TV opens, or null if it opened none. */
    suspend fun beginVoice(): Int?
    /** Sends a chunk of voice; throws [IOException] if the TV can no longer be reached. */
    suspend fun sendVoice(sessionId: Int, samples: ByteArray)
    /** Returns once the TV has said it heard the end of speech in [sessionId]. */
    suspend fun awaitSpeechEnd(sessionId: Int)
    suspend fun endVoice(sessionId: Int)
    suspend fun pressBack()
    fun note(event: String)
}

/** [TvVoicePort] over the app's remote session. */
internal class SessionTvVoicePort(
    private val repository: RemoteRepository,
    private val session: RemoteSessionManager
) : TvVoicePort {
    override suspend fun beginVoice() = session.beginVoice(VOICE_BEGIN_TIMEOUT_MS)
    override suspend fun sendVoice(sessionId: Int, samples: ByteArray) = session.sendVoice(sessionId, samples)
    override suspend fun awaitSpeechEnd(sessionId: Int) = session.awaitSpeechEnd(sessionId)
    override suspend fun endVoice(sessionId: Int) = session.endVoice(sessionId)
    override suspend fun pressBack() {
        repository.sendKey(TvKey.BACK)
    }
    override fun note(event: String) = session.note(event)
}

/**
 * Runs one voice search at a time: the microphone streams to the TV's assistant from [start] until
 * [finish] sends it or [cancel] drops it.
 *
 * Recording starts at once, while the TV is still being asked for a voice session, and whatever is
 * said meanwhile is kept and sent once the session is open, so the first word isn't lost. Nothing is
 * recognised on the phone and nothing is kept: the sound goes to the TV and is then gone.
 *
 * Letting go turns the microphone off, but the session stays open, fed with silence at the pace of
 * sound, until the TV has heard the end of speech and [AFTER_SPEECH_END_MS] more: the assistant
 * cancels the search if its session ends sooner.
 */
internal class VoiceSearchController(
    private val scope: CoroutineScope,
    private val tv: TvVoicePort,
    private val mic: MicSource
) {
    /** How a search ended, decided by whichever comes first: the user, the microphone, the TV or the time limit. */
    private enum class Outcome { SEND, CANCEL, MIC_BUSY, NO_ANSWER, LOST }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** The running search; also the one that later clears its "Sent" card or error. */
    private var job: Job? = null

    /** Completed with the outcome of the running search. */
    private var ending: CompletableDeferred<Outcome>? = null

    /** Keeps a sent search's session open until the TV is done with it; see [handOver]. */
    private var handOverJob: Job? = null

    /**
     * Counts searches. A cancelled search may still be tidying up the TV after a new one has started,
     * so each search changes the state only while it is still the latest.
     */
    private var generation = 0

    /** True while a search is listening or still handing its voice over. */
    val busy: Boolean get() = _state.value is VoiceState.Listening

    /** Turns the microphone on and starts a search. [tapMode] keeps it going until [finish], for TalkBack, where holding is hard. */
    fun start(tapMode: Boolean) {
        if (busy) return
        // A sent search still being handed over is ended now, so its session is closed before the new one opens
        handOverJob?.cancel()
        // A cancelled search isn't cancelled again: it may still be closing the TV's assistant, and it
        // no longer touches the state once this one has started (see generation)
        val end = CompletableDeferred<Outcome>()
        ending = end
        val search = ++generation
        _state.value = VoiceState.Listening(tapMode = tapMode)
        job = scope.launch { run(end, search) }
    }

    /** The finger left the key (true) or came back onto it (false): letting go now cancels, or sends. */
    fun setCancelArmed(armed: Boolean) {
        _state.update { if (it is VoiceState.Listening) it.copy(cancelArmed = armed) else it }
    }

    /** Sends what was said: the key was let go, or Stop and send was tapped. */
    fun finish() {
        ending?.complete(Outcome.SEND)
    }

    /** Drops the search without sending anything. */
    fun cancel() {
        ending?.complete(Outcome.CANCEL)
    }

    /** The connection to the TV dropped: a search that is listening ends with nothing sent. */
    fun connectionLost() {
        ending?.complete(Outcome.LOST)
    }

    /** Closes the "Sent" card or an error at once. */
    fun dismiss() {
        if (_state.value is VoiceState.Sent || _state.value is VoiceState.Failed) {
            job?.cancel()
            _state.value = VoiceState.Idle
        }
    }

    private suspend fun run(end: CompletableDeferred<Outcome>, search: Int) {
        val outcome = try {
            listenAndStream(end, search)
        } catch (e: IOException) {
            tv.note("voice search lost the TV: ${e.message}")
            Outcome.LOST
        }
        if (search != generation) return
        ending = null
        when (outcome) {
            Outcome.SEND -> show(VoiceState.Sent, SENT_SHOWN_MS)
            Outcome.CANCEL -> _state.value = VoiceState.Idle
            Outcome.MIC_BUSY -> show(VoiceState.Failed(VoiceError.MIC_BUSY), ERROR_SHOWN_MS)
            Outcome.NO_ANSWER -> show(VoiceState.Failed(VoiceError.TV_NO_ANSWER), ERROR_SHOWN_MS)
            Outcome.LOST -> show(VoiceState.Failed(VoiceError.WIFI_LOST), ERROR_SHOWN_MS)
        }
    }

    /** Shows [state] for [forMs], then the key at rest again, unless something else has replaced it meanwhile. */
    private suspend fun show(state: VoiceState, forMs: Long) {
        _state.value = state
        delay(forMs)
        _state.compareAndSet(state, VoiceState.Idle)
    }

    /**
     * Records and streams until [end] is completed, then closes the TV's voice session the way the
     * outcome asks for. Returns the outcome; throws [IOException] if the TV can't be reached.
     */
    private suspend fun listenAndStream(end: CompletableDeferred<Outcome>, search: Int): Outcome = coroutineScope {
        val audio = Channel<ByteArray>(Channel.UNLIMITED)
        val recording = launch {
            try {
                mic.record().collect { pcm ->
                    addLevel(pcmLevel(pcm))
                    audio.send(pcm)
                }
            } catch (e: MicBusyException) {
                tv.note("microphone busy: ${e.message}")
                end.complete(Outcome.MIC_BUSY)
            } finally {
                audio.close()
            }
        }
        val session = async {
            tv.beginVoice().also { if (it == null) end.complete(Outcome.NO_ANSWER) }
        }
        // Sends as the voice comes in; once recording stops it sends the rest, padded, and returns
        val streaming = launch {
            val id = session.await() ?: return@launch
            val chunker = VoiceChunker()
            for (pcm in audio) chunker.add(pcm).forEach { tv.sendVoice(id, it) }
            chunker.finish()?.let { tv.sendVoice(id, it) }
        }
        val limit = launch {
            delay(MAX_LISTEN_MS)
            end.complete(Outcome.SEND)
        }

        val outcome = end.await()
        limit.cancel()
        recording.cancel()
        // Lets the user see the key at rest at once; the TV is tidied up below
        if (outcome == Outcome.CANCEL && search == generation) _state.value = VoiceState.Idle
        when (outcome) {
            Outcome.SEND -> {
                val id = session.await()
                if (id == null) {
                    streaming.cancel()
                    return@coroutineScope Outcome.NO_ANSWER
                }
                recording.join()
                streaming.join()
                tv.note("voice search sent")
                // Outlives this search's own job, so closing the "Sent" card doesn't end the session early
                handOverJob = scope.launch { handOver(id) }
                Outcome.SEND
            }
            Outcome.NO_ANSWER -> Outcome.NO_ANSWER
            Outcome.LOST -> {
                streaming.cancel()
                session.cancel()
                Outcome.LOST
            }
            Outcome.CANCEL, Outcome.MIC_BUSY -> {
                streaming.cancel()
                // The TV's assistant is already on screen and listening: end its session and close it,
                // so the TV isn't left waiting for a voice that will never come
                withContext(NonCancellable) {
                    session.await()?.let { id ->
                        tv.endVoice(id)
                        tv.pressBack()
                    }
                }
                outcome
            }
        }
    }

    /**
     * Keeps session [id] open after the voice has gone: silence is sent at the pace of sound until the
     * TV says it heard the end of speech, then for [AFTER_SPEECH_END_MS] more, and the session is ended.
     * Cancelled (a new search starting), it ends the session at once.
     */
    private suspend fun handOver(id: Int) {
        try {
            coroutineScope {
                val silence = launch {
                    val quiet = ByteArray(VOICE_CHUNK_BYTES)
                    while (true) {
                        delay(VOICE_CHUNK_MS)
                        tv.sendVoice(id, quiet)
                    }
                }
                val heard = withTimeoutOrNull(SPEECH_END_WAIT_MS) { tv.awaitSpeechEnd(id) } != null
                if (!heard) tv.note("TV didn't say it heard the end of speech in session $id")
                delay(AFTER_SPEECH_END_MS)
                silence.cancel()
            }
        } catch (e: IOException) {
            // The voice already went; a connection lost now only cuts the wait short
            tv.note("voice session $id: connection lost while waiting for the TV (${e.message})")
        } finally {
            withContext(NonCancellable) { tv.endVoice(id) }
        }
    }

    /** Adds one frame's sound level to the bars, dropping the oldest. */
    private fun addLevel(level: Float) {
        _state.update {
            if (it is VoiceState.Listening) it.copy(levels = (it.levels + level).takeLast(LEVEL_BARS)) else it
        }
    }
}
