package com.prasoon.airmousetv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ResolvedHostTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int) =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    /** 2001:db8::1, a global address */
    private val global6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0x20; it[1] = 0x01; it[2] = 0x0d; it[3] = 0xb8.toByte(); it[15] = 1 })

    /** fe80::1, link-local */
    private val linkLocal6 = InetAddress.getByAddress(ByteArray(16).also { it[0] = 0xfe.toByte(); it[1] = 0x80.toByte(); it[15] = 1 })

    @Test
    fun aSingleIpv4Address_isUsed() {
        assertEquals("192.168.2.182", pickHost(listOf(v4(192, 168, 2, 182))))
    }

    @Test
    fun ipv4_isPreferred_evenWhenIpv6IsReportedFirst() {
        assertEquals("192.168.2.182", pickHost(listOf(linkLocal6, global6, v4(192, 168, 2, 182))))
    }

    @Test
    fun theFirstIpv4Address_winsAmongSeveral() {
        assertEquals("10.0.0.5", pickHost(listOf(v4(10, 0, 0, 5), v4(192, 168, 2, 182))))
    }

    @Test
    fun withoutIpv4_aGlobalIpv6Address_beatsALinkLocalOne() {
        val host = pickHost(listOf(linkLocal6, global6))
        assertNotNull(host)
        assertTrue(host!!.startsWith("2001"))
    }

    @Test
    fun onlyALinkLocalAddress_givesNull() {
        assertNull(pickHost(listOf(linkLocal6)))
    }

    @Test
    fun noAddresses_givesNull() {
        assertNull(pickHost(emptyList()))
    }
}
