package com.prasoon.airmousetv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPortStuckTest {

    @Test
    fun `silent pairing port next to an open remote port is stuck`() {
        assertTrue(pairingPortStuck(remote = HostProbe.OPEN, pairing = HostProbe.UNREACHABLE))
    }

    @Test
    fun `a refusing pairing port is a service still starting, not stuck`() {
        assertFalse(pairingPortStuck(remote = HostProbe.OPEN, pairing = HostProbe.REFUSED))
    }

    @Test
    fun `a TV that answers on neither port is off, not stuck`() {
        assertFalse(pairingPortStuck(remote = HostProbe.UNREACHABLE, pairing = HostProbe.UNREACHABLE))
    }

    @Test
    fun `an open pairing port is not stuck`() {
        assertFalse(pairingPortStuck(remote = HostProbe.OPEN, pairing = HostProbe.OPEN))
    }
}
