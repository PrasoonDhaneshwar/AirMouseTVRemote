package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import androidx.core.content.edit
import com.prasoon.airmousetv.airmouse.api.linkedAddress
import com.prasoon.airmousetv.airmouse.api.tvLinkKeys

private const val PREFS = "air_mouse"

/**
 * Remembers the Bluetooth address the TV paired with the phone under, saved under each of the TV's
 * names (see [tvLinkKeys]). The Wi-Fi remote and the Bluetooth pointer know the same TV by different
 * names and addresses, and nothing in either protocol ties the two together, so the link is made
 * once, during setup, and kept here.
 */
class AirMouseLinkStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The Bluetooth address of the TV known by [keys], or null if it hasn't been set up for the air mouse. */
    fun addressFor(keys: List<String>): String? = linkedAddress(keys) { prefs.getString(it, null) }

    /** Saves [address] under every one of [keys], so the TV is found by whichever name it goes by next. */
    fun save(keys: List<String>, address: String) {
        prefs.edit { keys.forEach { putString(it, address) } }
    }

    /**
     * Forgets the link of the TV known by [keys], under whatever names it was saved: names it went
     * by earlier, which [keys] may no longer contain, would otherwise bring it back.
     */
    fun clear(keys: List<String>) {
        val address = addressFor(keys)
        prefs.edit {
            keys.forEach(::remove)
            if (address != null) prefs.all.filterValues { it == address }.keys.forEach(::remove)
        }
    }
}
