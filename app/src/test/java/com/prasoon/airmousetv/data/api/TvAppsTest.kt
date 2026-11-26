package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAppsTest {

    @Test
    fun knownPackagesUseTheirName() {
        assertEquals("Netflix", TvApps.nameFor("com.netflix.ninja"))
        assertEquals("SmartTube", TvApps.nameFor("com.teamsmart.videomanager.tv"))
    }

    @Test
    fun unknownPackagesGetANameFromTheirLongestTellingPart() {
        assertEquals("Stremio", TvApps.nameFromPackage("com.stremio.one"))
        assertEquals("Cloudstream", TvApps.nameFromPackage("com.lagradost.cloudstream3"))
        assertEquals("Jellyfin", TvApps.nameFromPackage("org.jellyfin.androidtv"))
    }

    @Test
    fun aPackageOfOnlyGenericPartsStillGetsAName() {
        assertEquals("Tv", TvApps.nameFromPackage("com.app.tv"))
    }

    @Test
    fun launchersSettingsAndTheStoreAreSystem() {
        assertTrue(TvApps.isSystem("com.google.android.tvlauncher"))
        assertTrue(TvApps.isSystem("com.google.android.apps.tv.launcherx"))
        assertTrue(TvApps.isSystem("com.android.tv.settings"))
        assertTrue(TvApps.isSystem("com.android.vending"))
        assertFalse(TvApps.isSystem("com.netflix.ninja"))
    }

    @Test
    fun addRecentPutsTheAppFirstOnceAndCapsTheList() {
        val recent = listOf("a.one", "b.two", "c.three")
        assertEquals(listOf("c.three", "a.one", "b.two"), TvApps.addRecent(recent, "c.three"))
        val full = (1..TvApps.MAX_RECENT).map { "app.n$it" }
        val added = TvApps.addRecent(full, "app.new")
        assertEquals(TvApps.MAX_RECENT, added.size)
        assertEquals("app.new", added.first())
        assertFalse("app.n${TvApps.MAX_RECENT}" in added)
    }

    @Test
    fun addRecentIgnoresSystemScreens() {
        val recent = listOf("com.netflix.ninja")
        assertEquals(recent, TvApps.addRecent(recent, "com.google.android.tvlauncher"))
    }

    @Test
    fun popularAddsRegionalAppsAfterTheGlobalOnes() {
        val india = TvApps.popular("in")
        assertEquals("com.netflix.ninja", india.first())
        assertTrue("in.startv.hotstar" in india)
        assertFalse("com.hulu.livingroomplus" in india)
        assertEquals(TvApps.popular("ZZ").size + 3, india.size)
    }

    @Test
    fun popularLeavesOutRecentApps() {
        assertFalse("com.netflix.ninja" in TvApps.popular("US", exclude = listOf("com.netflix.ninja")))
    }

    @Test
    fun knownAppsOpenWithTheirOwnLink() {
        assertEquals("stremio://", TvApps.launchLink("com.stremio.one"))
        // Not vnd.youtube://, which SmartTube claims too and would bring up a chooser
        assertEquals("https://www.youtube.com", TvApps.launchLink("com.google.android.youtube.tv"))
        assertFalse(TvApps.needsStoreLaunch("com.stremio.one"))
    }

    @Test
    fun appsWithoutALinkAreAskedOfThePlayStore() {
        assertEquals("market://launch?id=org.example.player", TvApps.launchLink("org.example.player"))
        assertTrue(TvApps.needsStoreLaunch("org.example.player"))
        assertTrue(TvApps.needsStoreLaunch("org.smarttube.beta"))
        assertEquals("SmartTube Beta", TvApps.nameFor("org.smarttube.beta"))
    }

    @Test
    fun theBrowserStubIsNeverRecent() {
        assertTrue(TvApps.isSystem(TvApps.STUB))
    }
}
