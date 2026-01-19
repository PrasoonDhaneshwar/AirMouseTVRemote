package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TV_CACHE_PREFS = "tv_cache"
private const val TAG = "TvCacheManager"

@Singleton
class TvCacheManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(TV_CACHE_PREFS, Context.MODE_PRIVATE)

    fun saveWorkingPort(ip: String, port: Int) {
        prefs.edit(commit = true) {
            putInt("port_$ip", port)
        }
        Log.d(TAG, "💾 Cached port $port for $ip")
    }

    fun getCachedPort(ip: String): Int? {
        val port = prefs.getInt("port_$ip", -1)
        return if (port > 0) port else null
    }

    fun hasWorkingPort(ip: String): Boolean = getCachedPort(ip) != null

    fun clearAll() {
        prefs.edit(commit = true) {
            clear()
        }
        Log.i(TAG, "🗑️ Cleared all TV port cache")
    }
}
