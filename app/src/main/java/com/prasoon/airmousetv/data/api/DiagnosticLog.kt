package com.prasoon.airmousetv.data.api

/**
 * The most recent [capacity] events, for the diagnostics a user sends without adb.
 *
 * The same event repeated back to back is kept as one line with a count, so scrolling a list with
 * the D-pad (a dozen identical key presses) doesn't push the connection events out of the log.
 */
class DiagnosticLog(private val capacity: Int) {

    private class Line(val firstAt: String, val event: String) {
        var lastAt = firstAt
        var count = 1
        override fun toString() =
            if (count == 1) "$firstAt $event" else "$firstAt $event ×$count, last at $lastAt"
    }

    private val lines = ArrayDeque<Line>()

    /** Adds [event], which happened at [time] (already formatted, e.g. "14:03:52"). */
    @Synchronized
    fun add(time: String, event: String) {
        val last = lines.lastOrNull()
        if (last != null && last.event == event) {
            last.count++
            last.lastAt = time
            return
        }
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(Line(time, event))
    }

    /** How many lines [report] holds. */
    @get:Synchronized
    val size get() = lines.size

    /** Every line, oldest first, one per row. */
    @Synchronized
    fun report(): String = lines.joinToString("\n")
}
