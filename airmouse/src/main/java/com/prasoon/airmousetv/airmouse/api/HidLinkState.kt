package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.PointerLinkState

/** How far the phone has got in taking the Bluetooth mouse role. */
enum class HidRegistration {
    /** Asked for; Android hasn't answered yet. */
    PENDING,

    /** The phone is a Bluetooth mouse for as long as the app stays in front. */
    REGISTERED,

    /** The phone has no Bluetooth mouse role: its maker left the profile out, or it never answered. */
    NO_PROFILE,

    /** The role exists but Android refused to give it to this app. */
    REFUSED,
}

/**
 * The state of the Bluetooth pointer link, from the facts the phone reports about it. [linked] is
 * whether a TV has been paired for the pointer, [connected] whether that TV is connected now.
 *
 * The order matters. Bluetooth being off is reported before anything about the mouse role, because
 * a phone can't be asked about its profiles while Bluetooth is off. A role still being asked for
 * counts as connecting, so nobody is told to pair a phone that then turns out not to support it.
 */
fun hidLinkState(
    started: Boolean,
    bluetoothOn: Boolean,
    registration: HidRegistration,
    linked: Boolean,
    connected: Boolean,
): PointerLinkState = when {
    !started -> PointerLinkState.IDLE
    !bluetoothOn -> PointerLinkState.BLUETOOTH_OFF
    registration == HidRegistration.NO_PROFILE -> PointerLinkState.UNSUPPORTED
    registration == HidRegistration.REFUSED -> PointerLinkState.BLOCKED
    registration == HidRegistration.PENDING -> PointerLinkState.CONNECTING
    connected -> PointerLinkState.READY
    linked -> PointerLinkState.CONNECTING
    else -> PointerLinkState.NEEDS_PAIRING
}
