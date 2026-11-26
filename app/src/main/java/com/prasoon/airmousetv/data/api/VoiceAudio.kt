package com.prasoon.airmousetv.data.api

import kotlin.math.sqrt

/**
 * Sample rate the TV's assistant expects in voice payloads: 8 kHz, 16-bit, mono. The TV's remote
 * service logs "Voice config ... 8000 16 2 0" for each session. 16 kHz was tried on the Hisense and
 * came back empty, as it did for others (androidtvremote2 issue #49).
 */
const val VOICE_SAMPLE_RATE = 8000

/**
 * Size of every voice payload. On the Hisense, 8 KB payloads reached the assistant but were always
 * recognised as empty; a Sharp and a Sony did the same (androidtvremote2 issues #49 and #51), and
 * all three recognise speech sent in 20 KB payloads. At 8 kHz this is 1.28 s of sound.
 */
const val VOICE_CHUNK_BYTES = 20 * 1024

/** How long one [VOICE_CHUNK_BYTES] payload lasts, so silence can be sent at the pace sound would be. */
const val VOICE_CHUNK_MS = VOICE_CHUNK_BYTES * 1000L / (VOICE_SAMPLE_RATE * 2)

/** Collects recorded PCM and hands it out in payloads of exactly [VOICE_CHUNK_BYTES]. */
class VoiceChunker {
    private var pending = ByteArray(0)

    /** Adds [pcm] and returns the payloads that are now full, in order. */
    fun add(pcm: ByteArray): List<ByteArray> {
        pending += pcm
        val out = mutableListOf<ByteArray>()
        while (pending.size >= VOICE_CHUNK_BYTES) {
            out += pending.copyOfRange(0, VOICE_CHUNK_BYTES)
            pending = pending.copyOfRange(VOICE_CHUNK_BYTES, pending.size)
        }
        return out
    }

    /** The rest, padded with silence to [VOICE_CHUNK_BYTES]; null when nothing is left. */
    fun finish(): ByteArray? {
        if (pending.isEmpty()) return null
        val last = pending.copyOf(VOICE_CHUNK_BYTES)
        pending = ByteArray(0)
        return last
    }
}

/**
 * Halves the sample rate of 16-bit little-endian mono [pcm] by averaging each pair of samples, for a
 * phone that can't record at [VOICE_SAMPLE_RATE] directly and records at twice that. Averaging is a
 * crude low-pass filter, enough for speech: the assistant only needs what is below 4 kHz.
 */
fun halveSampleRate(pcm: ByteArray): ByteArray {
    val pairs = pcm.size / 4
    val out = ByteArray(pairs * 2)
    for (i in 0 until pairs) {
        val a = sampleAt(pcm, i * 4)
        val b = sampleAt(pcm, i * 4 + 2)
        val mean = (a + b) / 2
        out[i * 2] = (mean and 0xFF).toByte()
        out[i * 2 + 1] = (mean shr 8).toByte()
    }
    return out
}

/**
 * How loud 16-bit little-endian [pcm] is, from 0 (silence) to 1, for the sound-level bars. The RMS is
 * square-rooted again so normal speech, which sits far below full scale, still moves the bars well.
 */
fun pcmLevel(pcm: ByteArray): Float {
    val samples = pcm.size / 2
    if (samples == 0) return 0f
    var sum = 0.0
    for (i in 0 until samples) {
        val s = sampleAt(pcm, i * 2).toDouble()
        sum += s * s
    }
    val rms = sqrt(sum / samples) / 32768.0
    return sqrt(rms).toFloat().coerceIn(0f, 1f)
}

private fun sampleAt(pcm: ByteArray, offset: Int): Int =
    (pcm[offset].toInt() and 0xFF) or (pcm[offset + 1].toInt() shl 8)
