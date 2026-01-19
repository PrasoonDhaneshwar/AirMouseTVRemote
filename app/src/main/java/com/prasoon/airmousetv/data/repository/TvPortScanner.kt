package com.prasoon.airmousetv.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_SCANNER = "TvPortScanner"

/**
 * Performs HTTP GETs against known TV ports and extracts friendlyName/deviceName.
 * Keeps all original log messages from your previous fetchFriendlyName() implementation.
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
     * This is a pure function from host -> String? (no state except via cache).
     */
    suspend fun fetchFriendlyName(host: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG_SCANNER, "🔍 Scanning $host on TV ports...")

        // STEP 1: try cached port
        cache.getCachedPort(host)?.let { cachedPort ->
            Log.d(TAG_SCANNER, "💾 Using cached port $cachedPort for $host")
            try {
                val result = queryPort(host, cachedPort, isCached = true)
                if (result != null) {
                    Log.i(TAG_SCANNER, "✅ Cache HIT: '$result'")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.d(TAG_SCANNER, "💥 Cached port $cachedPort failed, full scanning...")
            }
        }

        // STEP 2: full scan
        for (port in TV_PORTS) {
            try {
                val result = queryPort(host, port, isCached = false)
                if (result != null) {
                    cache.saveWorkingPort(host, port)
                    Log.i(TAG_SCANNER, "✅ Found '$result' on port $port! CACHED.")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.d(TAG_SCANNER, "Port $port failed: ${e.message}")
            }
        }

        Log.d(TAG_SCANNER, "❌ No friendly name found via HTTP")
        null
    }

    /**
     * Executes GET / on the given port and tries to parse a friendly name.
     */
    private fun queryPort(host: String, port: Int, isCached: Boolean): String? {
        val socket = Socket()
        socket.soTimeout = 1500

        socket.connect(InetSocketAddress(host, port), 1500)

        socket.getOutputStream().use { out ->
            val request =
                "GET / HTTP/1.1\r\nHost: $host:$port\r\nUser-Agent: Android/Remote\r\n\r\n"
            out.write(request.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        socket.getInputStream().use { input ->
            val buffer = ByteArray(2048)
            val bytes = input.read(buffer)
            if (bytes > 0) {
                val response = String(buffer, 0, bytes, Charsets.UTF_8)
                if (isCached) {
                    Log.d(TAG_SCANNER, "📄 Cached port $port: ${response.take(100)}...")
                } else {
                    Log.d(TAG_SCANNER, "📄 Port $port response: ${response.take(200)}...")
                }
                val name = response.findFriendlyName()
                if (name != null) return name
            }
        }

        socket.close()
        return null
    }
}

/**
 * Extracts friendlyName/deviceName from JSON, XML, or headers.
 * This is the same logic you had, just isolated.
 */
private fun String.findFriendlyName(): String? = try {
    // JSON patterns
    "\"friendlyName\"\\s*:.*?\"([^\"]+)\"".toRegex()
        .find(this)?.groupValues?.getOrNull(1)
        ?: "\"deviceName\"\\s*:.*?\"([^\"]+)\"".toRegex()
            .find(this)?.groupValues?.getOrNull(1)
        ?: "\"name\"\\s*:.*?\"([^\"]+)\"".toRegex()
            .find(this)?.groupValues?.getOrNull(1)

        // XML patterns
        ?: "<friendlyName[^>]*>([^<]+)</friendlyName>".toRegex(RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)
        ?: "<deviceName[^>]*>([^<]+)</deviceName>".toRegex(RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)

        // Header pattern
        ?: "FRIENDLY\\s*:\\s*([\\w\\s\\-\\+\\.]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.length > 2 && it != "unknown" }
} catch (e: Exception) {
    Log.e(TAG_SCANNER, "Regex failed", e)
    null
}
