package com.prasoon.airmousetv.airmouse.api

/**
 * The shortest time between two wheel turns sent to the TV. Some TV apps animate each turn of the
 * wheel, and a turn that arrives while the last one is still animating replaces it instead of
 * adding to it (seen in SmartTube on a Hisense Android TV 9: single notches a second apart scrolled
 * 128 pixels each, while seven notches in a quick drag scrolled 256 in all, and two notches 225 ms
 * apart both counted). Turns closer together than this are added up and sent as one bigger turn.
 */
const val WHEEL_GAP_MS = 250L

/**
 * Spaces out wheel turns so that each one reaches the TV whole: notches that come in faster than
 * [WHEEL_GAP_MS] are added up and sent together as one turn of several notches, which an app
 * handles as a single scroll of the full distance.
 *
 * The first turn after a pause goes out at once, so a single notch never waits. Give it the notches
 * with [add]; when that returns null, call [flush] once [nextDueMs] has come.
 */
class WheelPacer {
    private var pending = 0f
    private var lastSentMs: Long? = null

    /** When the notches held back may go out, or null if none are held back. */
    var nextDueMs: Long? = null
        private set

    /** Takes [notches] at [nowMs]. Returns what to send now, or null if they are held back for [flush]. */
    @Synchronized
    fun add(notches: Float, nowMs: Long): Float? {
        pending += notches
        val last = lastSentMs
        if (nextDueMs == null && (last == null || nowMs - last >= WHEEL_GAP_MS)) return takePending(nowMs)
        if (nextDueMs == null) nextDueMs = last!! + WHEEL_GAP_MS
        return null
    }

    /** What was held back, if [nextDueMs] has come by [nowMs]; null otherwise, or if nothing is held back. */
    @Synchronized
    fun flush(nowMs: Long): Float? {
        val due = nextDueMs ?: return null
        if (nowMs < due) return null
        nextDueMs = null
        // Turns that cancel out (a drag up and back down) leave nothing to send
        if (pending == 0f) return null
        return takePending(nowMs)
    }

    /** Forgets what is held back, as when the pointer link goes. */
    @Synchronized
    fun reset() {
        pending = 0f
        nextDueMs = null
        lastSentMs = null
    }

    private fun takePending(nowMs: Long): Float {
        val out = pending
        pending = 0f
        lastSentMs = nowMs
        return out
    }
}
