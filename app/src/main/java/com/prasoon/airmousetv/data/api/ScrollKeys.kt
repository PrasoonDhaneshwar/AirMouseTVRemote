package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.model.TvKey
import kotlin.math.abs
import kotlin.math.roundToInt

/** The most keys one scroll movement turns into, so a fast flick can't bury the TV in key presses. */
const val MAX_SCROLL_KEYS_PER_MOVE = 6

/**
 * Distance dragged, in dp, that counts as one scroll step: one D-pad key on the touchpad, one notch
 * on the air mouse pad at its default scroll speed. The two share it so a swipe scrolls the same
 * distance on both.
 */
const val SCROLL_STEP_DP = 40f

/**
 * Keeps a drag scrolling along one axis, the way the touchpad steps along the dominant one, so a
 * slightly diagonal finger doesn't also send sideways keys. The axis is chosen once the finger has
 * travelled [decideAfter] (in any unit, as long as [add] uses the same) and kept until [reset]; what
 * was dragged meanwhile is handed out with the first decision, so nothing is lost.
 */
class ScrollAxisLock(private val decideAfter: Float) {
    private var sumX = 0f
    private var sumY = 0f
    private var horizontal: Boolean? = null

    /** The part of the drag [dx], [dy] to scroll with: all of it on the locked axis and none on the other, or nothing before an axis is chosen. */
    fun add(dx: Float, dy: Float): Pair<Float, Float> {
        horizontal?.let { return if (it) dx to 0f else 0f to dy }
        sumX += dx
        sumY += dy
        if (abs(sumX) < decideAfter && abs(sumY) < decideAfter) return 0f to 0f
        val chosen = abs(sumX) >= abs(sumY)
        horizontal = chosen
        return if (chosen) sumX to 0f else 0f to sumY
    }

    /** Starts afresh for the next drag. */
    fun reset() {
        sumX = 0f
        sumY = 0f
        horizontal = null
    }
}

/**
 * Turns scrolling into D-pad keys, for TV apps that scroll their lists by focus and handle a mouse
 * wheel badly (the touchpad scrolls well for that reason). Scrolling up sends Up, as scrolling
 * right sends Right. Fractions of a notch are carried over, so a slow drag still adds up to keys.
 */
class ScrollKeys {
    private var carryX = 0f
    private var carryY = 0f

    /** The keys for [dx] notches sideways (right positive) and [dy] of the wheel (up positive); empty while under one notch. */
    @Synchronized
    fun add(dx: Float, dy: Float): List<TvKey> {
        carryX += dx
        carryY += dy
        val x = carryX.roundToInt()
        val y = carryY.roundToInt()
        carryX -= x
        carryY -= y
        val keys = ArrayList<TvKey>(1)
        repeat(minOf(abs(y), MAX_SCROLL_KEYS_PER_MOVE)) { keys += if (y > 0) TvKey.UP else TvKey.DOWN }
        repeat(minOf(abs(x), MAX_SCROLL_KEYS_PER_MOVE)) { keys += if (x > 0) TvKey.RIGHT else TvKey.LEFT }
        return keys
    }

    /** Forgets carried fractions, as when the pad goes away. */
    @Synchronized
    fun reset() {
        carryX = 0f
        carryY = 0f
    }
}
