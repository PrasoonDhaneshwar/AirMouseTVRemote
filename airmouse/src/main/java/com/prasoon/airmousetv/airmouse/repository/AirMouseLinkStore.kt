package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import androidx.core.content.edit

private const val PREFS = "air_mouse"

/**
 * Remembers, per TV name, the Bluetooth address the TV paired with the phone under. The Wi-Fi
 * remote and the Bluetooth pointer know the same TV by different names and addresses, and nothing
 * in either protocol ties the two together, so the link is made once, during setup, and kept here.
 */
class AirMouseLinkStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The Bluetooth address of the TV called [tvName], or null if it hasn't been set up for the air mouse. */
    fun addressFor(tvName: String): String? = prefs.getString(tvName, null)

    fun save(tvName: String, address: String) {
        prefs.edit { putString(tvName, address) }
    }

    fun clear(tvName: String) {
        prefs.edit { remove(tvName) }
    }
}
