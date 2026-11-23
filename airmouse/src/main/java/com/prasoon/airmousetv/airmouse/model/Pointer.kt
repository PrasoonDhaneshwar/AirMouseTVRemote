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
    /**
     * True once this phone is known to lack the Bluetooth mouse role, which is the only time the air mouse
     * isn't advertised. Unknown counts as present: finding out needs the Bluetooth permission, which a new
     * install doesn't have yet, and the setup's own first step finds out for certain.
     */
    val roleMissing: Boolean = false,
    val settings: AirMouseSettings = AirMouseSettings(),
    /** True while the gyroscope is being calibrated and the phone has to lie still. */
    val calibrating: Boolean = false,
    /** How the last calibration ended: true if it was taken, false if the phone moved, null if none was run since the sheet opened. */
    val calibrated: Boolean? = null,
    /** During setup: what the phone's search for the TV over Bluetooth has turned up. */
    val search: TvSearch = TvSearch(),
)

/** A Bluetooth device seen in a scan. [looksLikeTv] is true if it announces itself as a TV or TV stick. */
data class NearbyDevice(val address: String, val name: String?, val looksLikeTv: Boolean)

/** Where the phone's own search for the TV stands, while the air mouse is being set up. */
data class TvSearch(
    /** False where the phone can't look for the TV itself (before Android 12, or scanning not allowed), and the TV has to find the phone. */
    val possible: Boolean = false,
    /** Devices found that could be the TV, likeliest first; the user picks when the app can't tell. */
    val candidates: List<NearbyDevice> = emptyList(),
    /** The name of the device being paired with right now, or null when none is. */
    val pairingWith: String? = null,
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

/**
 * What scrolling on the pad sends to the TV. Most TV apps scroll their lists by focus and handle a
 * mouse wheel badly (SmartTube drops notches, some ignore it), so D-pad keys are the default; the
 * wheel is for apps that scroll by it, such as browsers and maps.
 */
enum class ScrollWith {
    /** D-pad keys over Wi-Fi, as the touchpad sends them. */
    TV_KEYS,

    /** The Bluetooth mouse wheel, and its sideways wheel. */
    MOUSE_WHEEL,
}

/** What the phone's volume buttons do while air pointing is on. */
enum class VolumeButtons {
    /** Volume up clicks at the pointer, volume down is Back. */
    CLICK_AND_BACK,

    /** The other way round, for whichever button sits under the user's finger. */
    SWAPPED,

    /** The buttons change the TV's volume, as they do on the rest of the remote. */
    TV_VOLUME,
}

/**
 * How the user has set the air mouse up to feel. [sensitivity] and [smoothAir] are slider positions
 * from 0 to 1, with 0.5 in the middle being the default.
 */
data class AirMouseSettings(
    val sensitivity: Float = 0.5f,
    val smoothAir: Float = 0.5f,
    val volumeButtons: VolumeButtons = VolumeButtons.CLICK_AND_BACK,
    /** True if moving the phone moves the pointer; false if the pad is used as a mouse pad. */
    val airPointing: Boolean = true,
    val scrollWith: ScrollWith = ScrollWith.TV_KEYS,
    /** A slider position like [sensitivity]: how far a finger has to drag to scroll, half as far at one end and twice as far at the other. */
    val scrollSpeed: Float = 0.5f,
)
