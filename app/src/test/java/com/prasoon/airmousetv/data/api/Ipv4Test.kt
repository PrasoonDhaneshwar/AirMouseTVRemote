package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {

    private fun ip(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    @Test
    fun `parses a plain address and trims it`() {
        assertEquals("192.168.1.20", parseIpv4(" 192.168.1.20 "))
    }

    @Test
    fun `drops leading zeros`() {
        assertEquals("192.168.1.5", parseIpv4("192.168.001.005"))
    }

    @Test
    fun `rejects anything that is not four numbers`() {
        listOf("", "192.168.1", "192.168.1.2.3", "192.168.1.x", "192.168.1.256", "192.168..1", "1234.1.1.1", "-1.2.3.4")
            .forEach { assertNull(it, parseIpv4(it)) }
    }

    @Test
    fun `rejects addresses no TV can have`() {
        listOf("0.0.0.0", "127.0.0.1", "224.0.0.251", "255.255.255.255").forEach { assertNull(it, parseIpv4(it)) }
    }

    @Test
    fun `a slash 24 gives the other 253 hosts`() {
        val hosts = scanCandidates(ip(192, 168, 1, 40), 24)
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.40" in hosts)
    }

    @Test
    fun `a wider network is cut down to the slash 24 around the phone`() {
        val hosts = scanCandidates(ip(10, 0, 5, 9), 16)
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.0.5.") })
    }

    @Test
    fun `a narrower network is covered whole`() {
        val hosts = scanCandidates(ip(192, 168, 1, 130), 25)
        assertEquals(125, hosts.size)
        assertEquals("192.168.1.129", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
    }

    @Test
    fun `a network with no room for hosts gives nothing`() {
        assertTrue(scanCandidates(ip(192, 168, 1, 2), 31).isEmpty())
        assertTrue(scanCandidates(ip(192, 168, 1, 2), 32).isEmpty())
    }
}
