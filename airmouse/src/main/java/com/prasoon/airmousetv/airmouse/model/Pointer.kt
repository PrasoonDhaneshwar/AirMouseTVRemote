package com.prasoon.airmousetv.airmouse.model

/** A button of the pointer on the TV. */
enum class PointerButton {
    /** The left mouse button: a click at the pointer. */
    PRIMARY,

    /** The right mouse button. What it does is up to the TV; Android usually treats it as Back. */
    SECONDARY,
}

/** Where the link that carries the pointer to the TV stands. */
enum class PointerLinkState {
    /** Not started: the air mouse isn't showing. */
    IDLE,

    /** This phone can't act as a Bluetooth mouse, so there is nothing to set up. */
    UNSUPPORTED,

    /**
     * The phone has the Bluetooth mouse role but wouldn't hand it to this app. Only one app can
     * hold it at a time, so this is usually another "phone as keyboard or mouse" app.
     */
    BLOCKED,

    /** Bluetooth is switched off on the phone. */
    BLUETOOTH_OFF,

    /** The phone can be a mouse, but no TV has been paired with it for the pointer yet. */
    NEEDS_PAIRING,

    /** The paired TV is being connected to, or the link dropped and is being restored. */
    CONNECTING,

    /** The TV takes pointer input. */
    READY,
}

/** What the remote screen shows about the air mouse. */
data class AirMouseUiState(
    /** False on phones that can never be a Bluetooth mouse (older than Android 9, or no Bluetooth), where nothing about the air mouse is shown. */
    val available: Boolean = false,
    /** True once the connected TV has been paired for the pointer, which is when the Air mouse pad mode appears. */
    val linked: Boolean = false,
    val link: PointerLinkState = PointerLinkState.IDLE,
    /** False on phones without a gyroscope, where sliding the thumb is the only way to point. */
    val hasGyroscope: Boolean = false,
    /** The step of the one-time setup that is showing, or null when it isn't open. */
    val setup: AirMouseSetupStep? = null,
    /** The name the TV lists this phone under while pairing, or null if it isn't known. */
    val phoneName: String? = null,
)

/** The steps of the one-time Bluetooth setup, in order. */
enum class AirMouseSetupStep {
    /** What the air mouse is, and the button that starts the setup. */
    INTRO,

    /** The user opens the TV's "Pair accessory" screen. */
    ON_TV,

    /** The phone is visible and waits for the TV to pair with it. */
    WAITING,

    /** Paired and connected. */
    DONE,

    /** This phone can't be a Bluetooth mouse. */
    UNSUPPORTED,
}
