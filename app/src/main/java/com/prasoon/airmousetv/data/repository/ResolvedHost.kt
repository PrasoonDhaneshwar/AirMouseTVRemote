package com.prasoon.airmousetv.data.repository

import java.net.Inet4Address
import java.net.InetAddress

/**
 * The address to connect to, out of the ones a resolved service reports. A TV on a dual-stack
 * network can report an IPv6 address first, and a link-local one (fe80::) is only reachable
 * with the right network interface attached, so prefer IPv4, then any other IPv6 address. Null if
 * there is none of those: a link-local address alone is never listed, because the same TV shows up
 * again with its IPv4 address a moment later and would otherwise appear twice in the meantime.
 */
internal fun pickHost(addresses: List<InetAddress>): String? {
    val best = addresses.firstOrNull { it is Inet4Address }
        ?: addresses.firstOrNull { !it.isLinkLocalAddress }
    return best?.hostAddress
}
