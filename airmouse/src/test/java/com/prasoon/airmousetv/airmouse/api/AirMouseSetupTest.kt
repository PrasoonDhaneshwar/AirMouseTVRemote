package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.AirMouseSetupStep
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class AirMouseSetupTest {

    @Test
    fun `the intro waits while the phone is being checked`() {
        assertEquals(AirMouseSetupStep.INTRO, nextSetupStep(AirMouseSetupStep.INTRO, PointerLinkState.IDLE))
        assertEquals(AirMouseSetupStep.INTRO, nextSetupStep(AirMouseSetupStep.INTRO, PointerLinkState.CONNECTING))
        assertEquals(AirMouseSetupStep.INTRO, nextSetupStep(AirMouseSetupStep.INTRO, PointerLinkState.BLUETOOTH_OFF))
    }

    @Test
    fun `the intro moves on once the phone turns out to be a mouse`() {
        assertEquals(AirMouseSetupStep.ON_TV, nextSetupStep(AirMouseSetupStep.INTRO, PointerLinkState.NEEDS_PAIRING))
    }

    @Test
    fun `a phone that can't be a mouse ends the setup from any step before the end`() {
        for (step in listOf(AirMouseSetupStep.INTRO, AirMouseSetupStep.ON_TV, AirMouseSetupStep.WAITING)) {
            assertEquals(AirMouseSetupStep.UNSUPPORTED, nextSetupStep(step, PointerLinkState.UNSUPPORTED))
            assertEquals(AirMouseSetupStep.UNSUPPORTED, nextSetupStep(step, PointerLinkState.BLOCKED))
        }
    }

    @Test
    fun `only the user moves from the TV instructions to waiting`() {
        assertEquals(AirMouseSetupStep.ON_TV, nextSetupStep(AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING))
        assertEquals(AirMouseSetupStep.WAITING, nextSetupStep(AirMouseSetupStep.WAITING, PointerLinkState.NEEDS_PAIRING))
    }

    @Test
    fun `a TV that connects finishes the setup from any step`() {
        // From the intro too: a TV paired before connects without the user doing anything
        for (step in listOf(AirMouseSetupStep.INTRO, AirMouseSetupStep.ON_TV, AirMouseSetupStep.WAITING)) {
            assertEquals(AirMouseSetupStep.DONE, nextSetupStep(step, PointerLinkState.READY))
        }
    }

    @Test
    fun `waiting stays while the TV has paired but not yet connected`() {
        assertEquals(AirMouseSetupStep.WAITING, nextSetupStep(AirMouseSetupStep.WAITING, PointerLinkState.CONNECTING))
    }

    @Test
    fun `a finished setup stays finished when the link drops`() {
        for (link in PointerLinkState.entries) {
            assertEquals(AirMouseSetupStep.DONE, nextSetupStep(AirMouseSetupStep.DONE, link))
        }
    }

    @Test
    fun `the unsupported screen stays until it is closed`() {
        for (link in PointerLinkState.entries) {
            assertEquals(AirMouseSetupStep.UNSUPPORTED, nextSetupStep(AirMouseSetupStep.UNSUPPORTED, link))
        }
    }
}
