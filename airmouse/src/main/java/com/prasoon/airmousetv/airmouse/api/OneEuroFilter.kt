package com.prasoon.airmousetv.airmouse.api

import kotlin.math.PI
import kotlin.math.abs

/** Cutoff, in Hz, of the low-pass on the signal's rate of change. 1 Hz is the value the filter's authors recommend. */
private const val DERIVATIVE_CUTOFF_HZ = 1f

/**
 * A One Euro filter (Casiez, Roussel and Vogel, 2012): a low-pass whose cutoff rises with how fast
 * the signal is changing. A hand held still gets a low cutoff, which removes tremor; a quick sweep
 * gets a high one, so the pointer doesn't trail behind the hand. A fixed low-pass can only do one
 * of the two.
 *
 * [minCutoffHz] is the cutoff at rest: lower is steadier but laggier. [beta] is how quickly the
 * cutoff opens up as the signal changes faster: higher means less lag in fast moves.
 */
class OneEuroFilter(private val minCutoffHz: Float, private val beta: Float) {
    private var previous: Float? = null
    private var derivative = 0f

    /** The smoothed value for [value], which arrived [dtSeconds] after the one before it. */
    fun filter(value: Float, dtSeconds: Float): Float {
        val last = previous
        if (last == null || dtSeconds <= 0f) {
            previous = value
            return value
        }
        val rate = (value - last) / dtSeconds
        derivative += smoothing(DERIVATIVE_CUTOFF_HZ, dtSeconds) * (rate - derivative)
        val cutoff = minCutoffHz + beta * abs(derivative)
        val smoothed = last + smoothing(cutoff, dtSeconds) * (value - last)
        previous = smoothed
        return smoothed
    }

    /** Forgets the signal so far; the next value passes through unchanged. */
    fun reset() {
        previous = null
        derivative = 0f
    }

    /** How much of a new sample a low-pass with [cutoffHz] lets through, for samples [dtSeconds] apart. */
    private fun smoothing(cutoffHz: Float, dtSeconds: Float): Float {
        val r = 2f * PI.toFloat() * cutoffHz * dtSeconds
        return r / (r + 1f)
    }
}
