package com.prasoon.airmousetv.data.model

/** How a key is pressed: a complete tap, or the two halves of a hold. */
enum class KeyAction {
    TAP,
    /** Key goes down and stays down (repeats on the TV) until [LONG_END]. */
    LONG_START,
    /** Releases a key held with [LONG_START]; only valid after it. */
    LONG_END
}
