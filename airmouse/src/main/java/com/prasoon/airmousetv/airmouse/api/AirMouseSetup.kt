package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.AirMouseSetupStep
import com.prasoon.airmousetv.airmouse.model.PointerLinkState

/**
 * The setup step to show once the pointer link reports [link] while [current] is showing.
 *
 * The link moves the setup forward by itself where it can: off the intro once the phone is known
 * to support the mouse role, and to the end as soon as the TV has connected, from whichever step,
 * since a TV that was paired before connects without the user doing anything. The step from "open
 * Pair accessory" to waiting is the user's, as only they can see the TV. The two end states stay:
 * a link that drops after setup finished doesn't undo it.
 */
fun nextSetupStep(current: AirMouseSetupStep, link: PointerLinkState): AirMouseSetupStep = when {
    current == AirMouseSetupStep.DONE || current == AirMouseSetupStep.UNSUPPORTED -> current
    link == PointerLinkState.UNSUPPORTED || link == PointerLinkState.BLOCKED -> AirMouseSetupStep.UNSUPPORTED
    link == PointerLinkState.READY -> AirMouseSetupStep.DONE
    link == PointerLinkState.NEEDS_PAIRING && current == AirMouseSetupStep.INTRO -> AirMouseSetupStep.ON_TV
    else -> current
}
