package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.NearbyDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTvTest {

    private val tvNames = listOf("Living Room TV", "living-room-tv")

    private fun device(name: String?, looksLikeTv: Boolean = false, address: String = "AA:BB:CC:00:00:${name.hashCode() and 0xFF}") =
        NearbyDevice(address, name, looksLikeTv)

    // ----- Class of device -----

    @Test
    fun `a TV, a set-top box and a video monitor announce themselves as TVs`() {
        assertTrue(isTvClass(0x043C))
        assertTrue(isTvClass(0x0424))
        assertTrue(isTvClass(0x0438))
    }

    @Test
    fun `the service bits above the device class don't matter`() {
        // A real TV: rendering, capturing and audio services set on top of "display with loudspeaker"
        assertTrue(isTvClass(0x2C043C))
    }

    @Test
    fun `headphones and speakers are not TVs, though they are audio and video devices too`() {
        for (audio in listOf(0x0404, 0x0418, 0x0414)) {
            assertFalse(isTvClass(audio))
        }
    }

    @Test
    fun `phones, computers and mice are not TVs`() {
        for (other in listOf(0x5A020C, 0x10010C, 0x002580)) {
            assertFalse(isTvClass(other))
        }
    }

    // ----- Names -----

    @Test
    fun `the TV is known by any of its network names`() {
        assertTrue(matchesTvName("Living Room TV", tvNames))
        assertTrue(matchesTvName("living-room-tv", tvNames))
    }

    @Test
    fun `case, spaces and punctuation don't count`() {
        assertTrue(matchesTvName("LIVING ROOM TV", tvNames))
        assertTrue(matchesTvName("LivingRoom_TV", tvNames))
    }

    @Test
    fun `a different name doesn't match, however close`() {
        assertFalse(matchesTvName("Living Room TV 2", tvNames))
        assertFalse(matchesTvName("Room TV", tvNames))
    }

    @Test
    fun `a device with no name, or none that has letters, never matches`() {
        assertFalse(matchesTvName(null, tvNames))
        assertFalse(matchesTvName("", tvNames))
        assertFalse(matchesTvName("—", listOf("…")))
    }

    // ----- Who to offer, and who to pair with unasked -----

    @Test
    fun `the device with the TV's name is paired with unasked`() {
        val tv = device("Living Room TV", looksLikeTv = true)
        assertEquals(tv, tvToPairUnasked(listOf(device("Soundbar"), tv), tvNames))
    }

    @Test
    fun `a name match is enough even if the device doesn't announce itself as a TV`() {
        // TV sticks and boxes often report a class of their own
        val stick = device("Living Room TV", looksLikeTv = false)
        assertEquals(stick, tvToPairUnasked(listOf(stick), tvNames))
    }

    @Test
    fun `two devices with the TV's name leave the choice to the user`() {
        val found = listOf(device("Living Room TV", address = "AA:00"), device("Living Room TV", address = "AA:01"))
        assertNull(tvToPairUnasked(found, tvNames))
        assertEquals(found, tvCandidates(found, tvNames))
    }

    @Test
    fun `nothing is paired with unasked when no name matches`() {
        assertNull(tvToPairUnasked(listOf(device("Bedroom TV", looksLikeTv = true)), tvNames))
        assertNull(tvToPairUnasked(emptyList(), tvNames))
    }

    @Test
    fun `devices with the TV's name are offered first, then other TVs`() {
        val other = device("Bedroom TV", looksLikeTv = true)
        val named = device("Living Room TV")
        assertEquals(listOf(named, other), tvCandidates(listOf(other, named), tvNames))
    }

    @Test
    fun `things that aren't TVs are never offered`() {
        val found = listOf(device("Headphones"), device("Laptop"), device(null))
        assertTrue(tvCandidates(found, tvNames).isEmpty())
    }

    @Test
    fun `a TV without a name is not offered, as there would be nothing to show for it`() {
        assertTrue(tvCandidates(listOf(device(null, looksLikeTv = true), device(" ", looksLikeTv = true)), tvNames).isEmpty())
    }
}
