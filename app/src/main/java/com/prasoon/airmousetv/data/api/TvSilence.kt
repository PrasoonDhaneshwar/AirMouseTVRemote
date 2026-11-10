package com.prasoon.airmousetv.data.api

/**
 * Silence from a TV that pings longer than this makes it suspect: TVs that ping do so every ~5 s,
 * so missing one ping and a bit is already unusual.
 */
const val SILENCE_SUSPECT_MS = 8_000L

/**
 * After a Power tap, a TV still heard from this long afterwards stayed on the network (it is on,
 * or in network standby). Until then it may be shutting down and dropping off Wi-Fi.
 */
const val STILL_TALKING_AFTER_POWER_MS = 15_000L

/**
 * Power taps closer together than this are dropped. A TV that is switching off can keep its Wi-Fi
 * up for a moment, and a second Power press that reaches it then is stored by the network layer
 * and acted on when the TV is next switched on, which switches it straight back off.
 */
const val POWER_REPEAT_GUARD_MS = 4_000L

/**
 * True when a key press should wait for a check that the TV is still on the network, because it
 * may have gone to sleep. A press written to a sleeping TV isn't lost: the phone keeps resending it
 * and the TV acts on it when it wakes, so presses must not be written blindly.
 *
 * All times are on one monotonic clock, in ms. [lastHeardAt] is when the TV last sent anything,
 * [lastPowerAt] when Power was last tapped (null if never this run).
 */
fun needsAwakeCheck(now: Long, lastHeardAt: Long, tvPings: Boolean, lastPowerAt: Long?): Boolean {
    val silentTooLong = tvPings && now - lastHeardAt > SILENCE_SUSPECT_MS
    return silentTooLong || mayBeSwitchingOff(lastHeardAt, lastPowerAt)
}

/** True when Power was tapped and the TV hasn't been heard from long enough since to know it stayed on the network. */
fun mayBeSwitchingOff(lastHeardAt: Long, lastPowerAt: Long?): Boolean =
    lastPowerAt != null && lastHeardAt < lastPowerAt + STILL_TALKING_AFTER_POWER_MS

/**
 * How long after a Power tap a lost connection still counts as the TV switching off. Covers the
 * slowest way the loss is noticed: a TV that never pinged is only given up after 90 s of silence,
 * followed by about 20 s of reconnect attempts.
 */
const val POWER_OFF_DROP_WINDOW_MS = 180_000L

/**
 * True when a connection lost at [now] was most likely lost because the TV switched off after the
 * Power tap at [lastPowerAt]: the tap was recent and the TV went quiet soon after it. If the TV is
 * also unreachable then, it leaves the network when off, and the app can't turn it back on.
 */
fun lostAfterPowerOff(now: Long, lastHeardAt: Long, lastPowerAt: Long?): Boolean =
    lastPowerAt != null && now - lastPowerAt <= POWER_OFF_DROP_WINDOW_MS &&
        mayBeSwitchingOff(lastHeardAt, lastPowerAt)

/** True when a Power tap at [now] comes too soon after the one at [lastPowerAt] and should be dropped. */
fun isRepeatPowerTap(now: Long, lastPowerAt: Long?): Boolean =
    lastPowerAt != null && now - lastPowerAt < POWER_REPEAT_GUARD_MS
