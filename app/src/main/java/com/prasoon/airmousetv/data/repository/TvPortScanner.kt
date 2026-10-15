package com.prasoon.airmousetv.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_SCANNER = "TvPortScanner"
private const val PROBE_TIMEOUT_MS = 1500
private const val MAX_NAME_LENGTH = 64

/**
 * Performs HTTP GETs against known TV ports and extracts friendlyName/deviceName.
 */
@Singleton
class TvPortScanner @Inject constructor(
    private val cache: TvCacheManager
) {

    private companion object {
        private val TV_PORTS = listOf(8008, 8009, 7675, 8060, 5353, 80, 8080)
    }

    /**
     * Suspends while scanning; returns a friendly name if found, or null.
     * The ports are probed in parallel, so the wait is one probe timeout, not one per port.
     */
    suspend fun fetchFriendlyName(host: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG_SCANNER, "🔍 Scanning $host on TV ports...")

        // STEP 1: try cached port
        cache.getCachedPort(host)?.let { cachedPort ->
            queryPort(host, cachedPort)?.let {
                Log.i(TAG_SCANNER, "✅ Cache HIT: '$it'")
                return@withContext it
            }
            Log.d(TAG_SCANNER, "💥 Cached port $cachedPort failed, full scanning...")
        }

        // STEP 2: full scan, all ports at once; the first port in list order that answers wins
        val found = coroutineScope {
            TV_PORTS
                .map { port -> async { queryPort(host, port)?.let { port to it } } }
                .awaitAll()
                .firstNotNullOfOrNull { it }
        }
        if (found == null) {
            Log.d(TAG_SCANNER, "❌ No friendly name found via HTTP")
            return@withContext null
        }
        cache.saveWorkingPort(host, found.first)
        Log.i(TAG_SCANNER, "✅ Found '${found.second}' on port ${found.first}! CACHED.")
        found.second
    }

    /** Executes GET / on the given port and tries to parse a friendly name; null if the port is closed or has none. */
    private fun queryPort(host: String, port: Int): String? = try {
        Socket().use { socket ->
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)

            // Write, then read on the same socket: closing the output stream would close the socket itself
            socket.getOutputStream().apply {
                write("GET / HTTP/1.1\r\nHost: $host:$port\r\nUser-Agent: Android/Remote\r\n\r\n".toByteArray(Charsets.UTF_8))
                flush()
            }

            val buffer = ByteArray(2048)
            val bytes = socket.getInputStream().read(buffer)
            if (bytes > 0) String(buffer, 0, bytes, Charsets.UTF_8).findFriendlyName() else null
        }
    } catch (e: Exception) {
        Log.d(TAG_SCANNER, "Port $port failed: ${e.message}")
        null
    }
}

private val FRIENDLY_NAME_PATTERNS: List<Regex> = listOf(
    // JSON
    "\"friendlyName\"\\s*:\\s*\"([^\"]+)\"",
    "\"deviceName\"\\s*:\\s*\"([^\"]+)\"",
    "\"name\"\\s*:\\s*\"([^\"]+)\"",
    // XML
    "<friendlyName[^>]*>([^<]+)</friendlyName>",
    "<deviceName[^>]*>([^<]+)</deviceName>",
    // Header; the class has no newline, so the value ends at the end of the line
    "FRIENDLY[ \\t]*:[ \\t]*([\\w\\- +.]+)"
).map { Regex(it, RegexOption.IGNORE_CASE) }

/** A name taken from the network: any device on the Wi-Fi can choose it, so strip control characters and cap the length. */
internal fun String.cleanDeviceName(): String =
    filterNot { it.isISOControl() }.trim().take(MAX_NAME_LENGTH)

/** Extracts friendlyName/deviceName from JSON, XML, or headers, trying them in that order. */
internal fun String.findFriendlyName(): String? =
    FRIENDLY_NAME_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.find(this)?.groupValues?.getOrNull(1)
            ?.cleanDeviceName()
            ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }
