package com.prasoon.airmousetv.airmouse.api

import kotlin.math.ceil

/**
 * How far the pointer is pushed up and to the left to pin it in the corner, in mouse counts on each
 * axis. More than any TV is wide, 4K included, even if the TV slows the pointer down.
 */
internal const val RECENTER_SWEEP_COUNTS = 5000f

/** The size of one push towards the corner: a full report on both axes, so the sweep is over in a few frames. */
internal const val RECENTER_SWEEP_STEP = 127f

/**
 * The way back from the corner to about the middle, in mouse counts: half of a 1920 by 1080 screen at
 * the one pixel per count Android TV gives a mouse that moves slowly (measured on a Hisense Android
 * TV 9 with its pointer speed left at default). The numbers are about 11% under that half because at
 * the step size below the TV still moves the pointer about 1.12 times too far, so 960 by 540 ended
 * near (1080, 610).
 */
internal const val RECENTER_TO_MIDDLE_X = 850f
internal const val RECENTER_TO_MIDDLE_Y = 480f

/**
 * The size of one step of the way back. Small on purpose: the TV speeds the pointer up for fast mouse
 * movement, by an amount that changes from one run to the next. Measured on the Hisense, the pointer
 * ended about 1.55 times too far at 4 counts a step and about 1.3 times at 2; 3 is the compromise
 * that keeps the walk to about 4 seconds instead of 7.
 */
internal const val RECENTER_RETURN_STEP = 3f

/**
 * The movements that bring the pointer to about the middle of the screen, wherever it is.
 *
 * A mouse can only say "move by this much", so there is no "go to the middle". Instead the pointer
 * is pushed up and left by more than any screen is wide, which pins it in the corner whatever its
 * position was, and from that known place it is walked back to the middle. Where exactly it ends up
 * depends on the TV's pointer speed, so this is approximate.
 */
fun recenterMoves(): List<PointerMove> {
    val moves = ArrayList<PointerMove>()
    var swept = 0f
    while (swept < RECENTER_SWEEP_COUNTS) {
        moves += PointerMove(-RECENTER_SWEEP_STEP, -RECENTER_SWEEP_STEP)
        swept += RECENTER_SWEEP_STEP
    }
    // Both axes arrive together, so the pointer travels the diagonal instead of an L
    val steps = ceil(RECENTER_TO_MIDDLE_X / RECENTER_RETURN_STEP).toInt()
    repeat(steps) { moves += PointerMove(RECENTER_TO_MIDDLE_X / steps, RECENTER_TO_MIDDLE_Y / steps) }
    return moves
}

/** One count right, then one back: enough for the TV to show a pointer it had hidden, without moving it. */
fun wakeMoves(): List<PointerMove> = listOf(PointerMove(1f, 0f), PointerMove(-1f, 0f))
