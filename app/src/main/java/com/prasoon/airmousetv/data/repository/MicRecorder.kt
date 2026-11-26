package com.prasoon.airmousetv.data.repository

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.prasoon.airmousetv.data.api.VOICE_SAMPLE_RATE
import com.prasoon.airmousetv.data.api.halveSampleRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MicRecorder"

/** Length of each recorded frame. Short enough for lively sound-level bars, long enough not to wake the CPU needlessly. */
private const val FRAME_MS = 50

/** The microphone can't be used: another app holds it, or the phone refused to record. */
class MicBusyException(message: String) : Exception(message)

/** Where a voice search gets its sound. */
interface MicSource {
    /**
     * Records until the collector stops, emitting 16-bit little-endian mono PCM at [VOICE_SAMPLE_RATE].
     * Throws [MicBusyException] if the microphone can't be used. Needs the RECORD_AUDIO permission.
     */
    fun record(): Flow<ByteArray>
}

/** [MicSource] on the phone's microphone, through [AudioRecord]. */
@Singleton
class AudioRecordMic @Inject constructor() : MicSource {

    /** An open recorder, and whether it records at twice [VOICE_SAMPLE_RATE] and has to be halved. */
    private class Opened(val recorder: AudioRecord, val halve: Boolean, val frameBytes: Int)

    @SuppressLint("MissingPermission") // the voice key asks for the permission before it records
    override fun record(): Flow<ByteArray> = flow {
        val opened = open() ?: throw MicBusyException("The microphone couldn't be opened")
        val recorder = opened.recorder
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicBusyException("The microphone didn't start")
            }
            val frame = ByteArray(opened.frameBytes)
            var checkedSilenced = false
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, 0, frame.size)
                if (read < 0) throw MicBusyException("Reading the microphone failed ($read)")
                if (read == 0) continue
                // Since Android 10 a second app gets silence instead of an error while another
                // (a call, for one) has the microphone, so ask once the recording is running
                if (!checkedSilenced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkedSilenced = true
                    if (recorder.activeRecordingConfiguration?.isClientSilenced == true) {
                        throw MicBusyException("Another app is using the microphone")
                    }
                }
                val pcm = frame.copyOf(read)
                emit(if (opened.halve) halveSampleRate(pcm) else pcm)
            }
        } finally {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Recorder was not recording", e)
            }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Opens a recorder at [VOICE_SAMPLE_RATE], or at twice that if the phone can't do 8 kHz (only
     * 44.1 kHz is guaranteed, but every phone tried takes 8 or 16). The voice-recognition source is
     * tuned for speech: no noise suppression that clips words, the same input the phone's own assistant uses.
     */
    @SuppressLint("MissingPermission")
    private fun open(): Opened? {
        for (rate in listOf(VOICE_SAMPLE_RATE, VOICE_SAMPLE_RATE * 2)) {
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) continue
            val frameBytes = rate * FRAME_MS / 1000 * 2
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, frameBytes * 4)
                )
            } catch (e: Exception) {
                Log.w(TAG, "No recorder at $rate Hz", e)
                continue
            }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return Opened(recorder, rate != VOICE_SAMPLE_RATE, frameBytes)
            recorder.release()
        }
        return null
    }
}
