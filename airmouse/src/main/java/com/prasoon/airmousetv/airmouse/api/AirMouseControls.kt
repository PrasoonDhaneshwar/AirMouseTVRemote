package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.VolumeButtons
import kotlin.math.pow

/** The slowest and fastest the sensitivity slider goes, as a multiple of the default speed. The default sits in the middle. */
internal const val LOWEST_SENSITIVITY = 0.5f
internal const val HIGHEST_SENSITIVITY = 2f

/** What a press of one of the phone's volume buttons does to the TV. */
enum class VolumeAction {
    /** A click at the pointer, held for as long as the button is. */
    CLICK,

    /** The TV's Back key. */
    BACK,

    /** Nothing special: the button changes the TV's volume, like on the rest of the remote. */
    VOLUME,
}

/**
 * The pointer settings behind the user's slider positions. The sensitivity slider is geometric,
 * half speed at one end and double at the other, because that is how a change of speed feels: each
 * step the same amount faster than the last, not the same number of counts.
 */
fun AirMouseSettings.toPointerSettings(): PointerSettings {
    val position = sensitivity.coerceIn(0f, 1f)
    val factor = LOWEST_SENSITIVITY * (HIGHEST_SENSITIVITY / LOWEST_SENSITIVITY).pow(position)
    return PointerSettings(sensitivity = factor, smoothing = smoothAir.coerceIn(0f, 1f))
}

/**
 * What the volume button does: [volumeUp] for the upper one. The buttons only stand in for click
 * and Back while [pointing], that is with the pad showing, its link up and air pointing on. With
 * the pad used as a mouse pad the thumb is on the screen already and taps there; anywhere else in
 * the app the buttons are the TV's volume.
 */
fun volumeAction(volumeUp: Boolean, settings: AirMouseSettings, pointing: Boolean): VolumeAction = when {
    !pointing || !settings.airPointing -> VolumeAction.VOLUME
    settings.volumeButtons == VolumeButtons.TV_VOLUME -> VolumeAction.VOLUME
    // The upper button clicks unless swapped
    volumeUp == (settings.volumeButtons == VolumeButtons.CLICK_AND_BACK) -> VolumeAction.CLICK
    else -> VolumeAction.BACK
}
