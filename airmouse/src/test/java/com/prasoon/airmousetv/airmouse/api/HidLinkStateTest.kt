package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class HidLinkStateTest {

    private fun state(
        started: Boolean = true,
        bluetoothOn: Boolean = true,
        registration: HidRegistration = HidRegistration.REGISTERED,
        linked: Boolean = true,
        connected: Boolean = false,
    ) = hidLinkState(started, bluetoothOn, registration, linked, connected)

    @Test
    fun `nothing is reported before the air mouse is started`() {
        assertEquals(PointerLinkState.IDLE, state(started = false, connected = true))
    }

    @Test
    fun `Bluetooth being off comes before anything about the mouse role`() {
        // With Bluetooth off the phone can't be asked about its profiles, so "unsupported" would be a guess
        assertEquals(PointerLinkState.BLUETOOTH_OFF, state(bluetoothOn = false, registration = HidRegistration.NO_PROFILE))
        assertEquals(PointerLinkState.BLUETOOTH_OFF, state(bluetoothOn = false, registration = HidRegistration.PENDING))
    }

    @Test
    fun `a phone without the profile is unsupported`() {
        assertEquals(PointerLinkState.UNSUPPORTED, state(registration = HidRegistration.NO_PROFILE))
    }

    @Test
    fun `a role Android wouldn't hand over is blocked, not unsupported`() {
        assertEquals(PointerLinkState.BLOCKED, state(registration = HidRegistration.REFUSED))
    }

    @Test
    fun `while the role is being asked for nobody is told to pair`() {
        assertEquals(PointerLinkState.CONNECTING, state(registration = HidRegistration.PENDING, linked = false))
        assertEquals(PointerLinkState.CONNECTING, state(registration = HidRegistration.PENDING, linked = true))
    }

    @Test
    fun `a phone that is a mouse with no TV paired needs pairing`() {
        assertEquals(PointerLinkState.NEEDS_PAIRING, state(linked = false))
    }

    @Test
    fun `a paired TV that isn't connected is being connected to`() {
        assertEquals(PointerLinkState.CONNECTING, state(linked = true, connected = false))
    }

    @Test
    fun `a connected TV is ready`() {
        assertEquals(PointerLinkState.READY, state(linked = true, connected = true))
    }
}
