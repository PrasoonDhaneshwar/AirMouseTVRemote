package com.prasoon.airmousetv.data.api

/** Narrowest prefix a subnet scan covers: a /24 is 254 hosts, which probes in a couple of seconds. */
private const val MIN_SCAN_PREFIX = 24

/**
 * Returns [text] as a plain dotted IPv4 address a TV could have (leading zeros dropped), or null if
 * it is anything else. Loopback, multicast, broadcast and 0.x.x.x are refused because no TV is there.
 */
fun parseIpv4(text: String): String? {
    val parts = text.trim().split(".")
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
        part.toInt().takeIf { it <= 255 } ?: return null
    }
    if (octets[0] == 0 || octets[0] == 127 || octets[0] >= 224) return null
    return octets.joinToString(".")
}

/**
 * Every other host address on the network of [own] (4 bytes), for a scan. A network wider than a /24
 * is cut down to the /24 around [own], so the scan stays short; a narrower one is covered whole.
 */
fun scanCandidates(own: ByteArray, prefixLength: Int): List<String> {
    if (own.size != 4) return emptyList()
    val prefix = prefixLength.coerceIn(MIN_SCAN_PREFIX, 32)
    if (prefix >= 31) return emptyList()
    val address = own.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
    val hostMask = (1L shl (32 - prefix)) - 1
    val network = address and hostMask.inv()
    return (1 until hostMask)
        .map { network + it }
        .filter { it != address }
        .map { "${it shr 24 and 0xFF}.${it shr 16 and 0xFF}.${it shr 8 and 0xFF}.${it and 0xFF}" }
}
