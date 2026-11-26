package com.prasoon.airmousetv.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "recent_apps"
private const val RECENT = "recent:"
private const val NAME = "name:"
private const val STORE_REFUSED = "store_refused:"

/**
 * The apps each TV has reported on screen, newest first, kept per TV name because each TV has its
 * own apps; and the names the user gave apps, kept per package since an app is the same on every TV.
 * Stays on the phone, like the rest of the app's data (backup is off).
 */
@Singleton
class RecentAppsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The packages seen on the TV called [tv], newest first. */
    fun recent(tv: String): List<String> =
        prefs.getString(RECENT + tv, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun setRecent(tv: String, packages: List<String>) {
        prefs.edit { putString(RECENT + tv, packages.joinToString("\n")) }
    }

    /**
     * True once the TV called [tv] turned down a request to its Play Store to launch an app (see
     * [com.prasoon.airmousetv.data.api.TvApps.storeLaunchLink]). Each refusal costs a dropped
     * connection, so it isn't asked again.
     */
    fun storeLaunchRefused(tv: String): Boolean = prefs.getBoolean(STORE_REFUSED + tv, false)

    fun setStoreLaunchRefused(tv: String) {
        prefs.edit { putBoolean(STORE_REFUSED + tv, true) }
    }

    /** The name the user gave [pkg], or null if they haven't renamed it. */
    fun customName(pkg: String): String? = prefs.getString(NAME + pkg, null)

    /** Renames [pkg]; a blank [name] goes back to the app's own name. */
    fun setCustomName(pkg: String, name: String) {
        prefs.edit { if (name.isBlank()) remove(NAME + pkg) else putString(NAME + pkg, name.trim()) }
    }
}
