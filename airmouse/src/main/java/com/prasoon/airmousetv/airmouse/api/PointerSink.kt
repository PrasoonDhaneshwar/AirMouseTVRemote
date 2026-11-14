package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the pointer sends to the TV. The pointer math and the air mouse pad only know this
 * interface, so the link underneath (Bluetooth today) can change, and tests can use a fake.
 */
interface PointerSink {
    val state: StateFlow<PointerLinkState>

    /** Moves the pointer by [dx], [dy] mouse counts, right and down being positive. Already filtered and scaled. */
    suspend fun move(dx: Float, dy: Float)

    /** Presses ([down]) or releases [button]. */
    suspend fun button(button: PointerButton, down: Boolean)

    /** Scrolls at the pointer by [dy] wheel notches, up being positive, and [dx] sideways notches. */
    suspend fun scroll(dx: Float, dy: Float)
}
