package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.model.TvKey
import kotlin.math.abs
import kotlin.math.roundToInt

/** The most keys one scroll movement turns into, so a fast flick can't bury the TV in key presses. */
const val MAX_SCROLL_KEYS_PER_MOVE = 6

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
