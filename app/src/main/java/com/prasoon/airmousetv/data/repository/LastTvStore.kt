package com.prasoon.airmousetv.data.repository

import android.content.Context
import androidx.core.content.edit
import com.prasoon.airmousetv.data.model.DiscoveredTv
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "last_tv"

/** Remembers the TV we last connected to, so the app can reconnect to it on launch. */
@Singleton
class LastTvStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(tv: DiscoveredTv) {
        prefs.edit {
            putString("name", tv.name)
            putString("friendlyName", tv.friendlyName)
            putString("host", tv.host)
            putInt("port", tv.port)
            putString("mac", tv.macAddress)
            putString("serviceType", tv.serviceType)
        }
    }

    fun load(): DiscoveredTv? {
        val host = prefs.getString("host", null) ?: return null
        return DiscoveredTv(
            name = prefs.getString("name", null) ?: host,
            friendlyName = prefs.getString("friendlyName", null),
            host = host,
            port = prefs.getInt("port", 0),
            macAddress = prefs.getString("mac", null),
            serviceType = prefs.getString("serviceType", "") ?: ""
        )
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
