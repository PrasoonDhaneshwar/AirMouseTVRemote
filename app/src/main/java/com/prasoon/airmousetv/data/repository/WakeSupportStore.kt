package com.prasoon.airmousetv.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "tv_wake"

/**
 * Remembers, per TV name, the TVs seen leaving the network when switched off. The app can't turn
 * those back on (typically TVs with an IR remote and no network standby), so it warns before
 * switching them off. The protocol has no way to ask the TV this up front.
 */
@Singleton
class WakeSupportStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True if the TV called [name] was last seen leaving the network when switched off. */
    fun cannotWake(name: String): Boolean = prefs.getBoolean(name, false)

    /** Records whether the TV called [name] leaves the network when switched off. */
    fun setCannotWake(name: String, cannotWake: Boolean) {
        prefs.edit { if (cannotWake) putBoolean(name, true) else remove(name) }
    }
}
