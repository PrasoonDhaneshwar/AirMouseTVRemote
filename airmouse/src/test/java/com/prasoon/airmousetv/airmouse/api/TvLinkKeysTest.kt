package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLinkKeysTest {

    private val discovered = tvLinkKeys("HiSmartTV-A4-4a5812b2", "Android TV", "192.168.0.222")

    @Test
    fun `a TV is known by its network name, then the name people see, then its address`() {
        assertEquals(listOf("HiSmartTV-A4-4a5812b2", "Android TV", "192.168.0.222"), discovered)
    }

    @Test
    fun `a TV added by its address has that address as its only name`() {
        // Added by IP, the remote has nothing but the address to call it
        assertEquals(listOf("192.168.0.222"), tvLinkKeys("192.168.0.222", null, "192.168.0.222"))
    }

    @Test
    fun `blank names are left out`() {
        assertEquals(listOf("tv", "10.0.0.5"), tvLinkKeys("tv", "  ", "10.0.0.5"))
    }

    @Test
    fun `a link saved when the TV was added by address is found once discovery names it`() {
        // The case seen after the TV restarted: set up while added by IP, found again by its network name
        val saved = mapOf("192.168.0.222" to "7C:B3:7B:BA:D2:B3")
        assertEquals("7C:B3:7B:BA:D2:B3", linkedAddress(discovered, saved::get))
    }

    @Test
    fun `a link saved under the name people see is found under a new network name`() {
        val saved = mapOf("Android TV" to "7C:B3:7B:BA:D2:B3")
        assertEquals("7C:B3:7B:BA:D2:B3", linkedAddress(discovered, saved::get))
    }

    @Test
    fun `the network name wins when the names disagree`() {
        // The address may have been another TV's once; the network name is this TV's own
        val saved = mapOf("192.168.0.222" to "OLD-TV", "HiSmartTV-A4-4a5812b2" to "THIS-TV")
        assertEquals("THIS-TV", linkedAddress(discovered, saved::get))
    }

    @Test
    fun `a TV that was never set up has no link`() {
        assertNull(linkedAddress(discovered, mapOf("Bedroom TV" to "X")::get))
        assertNull(linkedAddress(emptyList()) { "X" })
    }
}
