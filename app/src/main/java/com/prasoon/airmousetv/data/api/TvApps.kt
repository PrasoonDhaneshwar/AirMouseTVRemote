package com.prasoon.airmousetv.data.api

/** An app on the TV: its package, which is all the TV reports, and the name the Apps sheet shows. */
data class TvApp(val pkg: String, val name: String)

/**
 * What the app knows about TV apps without asking the TV, which has no way to list them: names for
 * common packages, the Popular set, which packages never belong in Recent, and how to open one.
 */
object TvApps {

    /** Most apps Recent keeps per TV: three rows of the sheet's four-across grid. */
    const val MAX_RECENT = 12

    /** How many regional apps follow the global ones in Popular, so the global ones still lead. */
    private const val MAX_REGIONAL = 3

    /** What is known about an app: its name, and the link that opens it, if one has been found. */
    private class Known(val name: String, val link: String? = null)

    /**
     * Known apps by package, TV builds only (Netflix on TV is `com.netflix.ninja`, not the phone's
     * `com.netflix.mediaclient`). Apps installed outside the Play Store are here too, since their
     * packages say little (`org.smarttube.beta` is SmartTube).
     *
     * The TV opens a link with whichever app claims it, so each app needs a link of its own: one that
     * no other app claims (`https://www.youtube.com`, not `vnd.youtube://`, which SmartTube claims too).
     * Links marked "checked" were confirmed on the Hisense Google TV (2026-11-27) with
     * `cmd package query-activities`. The rest are the app's web address, which an app usually
     * claims; if it doesn't, the TV shows its browser stub, which is noticed (see [STUB]). SmartTube
     * claims nothing of its own, so it has no link.
     */
    private val known = mapOf(
        "com.netflix.ninja" to Known("Netflix", "https://www.netflix.com/title"), // checked
        "com.google.android.youtube.tv" to Known("YouTube", "https://www.youtube.com"), // checked
        "com.google.android.youtube.tvkids" to Known("YouTube Kids"),
        "com.google.android.youtube.tvmusic" to Known("YouTube Music"),
        "com.amazon.amazonvideo.livingroom" to Known("Prime Video", "https://app.primevideo.com"), // checked
        "com.disney.disneyplus" to Known("Disney+", "https://www.disneyplus.com"),
        "com.spotify.tv.android" to Known("Spotify", "spotify://"),
        "com.apple.atve.androidtv.appletv" to Known("Apple TV", "https://tv.apple.com"),
        "com.wbd.stream" to Known("Max", "https://play.max.com"),
        "com.plexapp.android" to Known("Plex", "plex://"),
        "tv.twitch.android.app" to Known("Twitch", "https://www.twitch.tv"),
        "com.crunchyroll.crunchyroid" to Known("Crunchyroll", "https://www.crunchyroll.com"),
        "com.google.android.videos" to Known("Google TV"),
        "com.hulu.livingroomplus" to Known("Hulu", "https://www.hulu.com"),
        "com.cbs.ott" to Known("Paramount+", "https://www.paramountplus.com"),
        "com.peacocktv.peacockandroid" to Known("Peacock", "https://www.peacocktv.com"),
        "in.startv.hotstar" to Known("JioHotstar", "https://www.hotstar.com"), // checked
        "com.graymatrix.did" to Known("ZEE5", "https://www.zee5.com"), // checked
        "com.sonyliv" to Known("Sony LIV", "sonyliv://home"), // checked
        "com.jio.media.stb.ondemand" to Known("JioCinema", "jiocinema://"), // checked
        "uk.co.bbc.iplayer" to Known("BBC iPlayer", "https://www.bbc.co.uk/iplayer"),
        "com.channel4.ondemand" to Known("Channel 4", "https://www.channel4.com"),
        "com.globo.globotv" to Known("Globoplay", "https://globoplay.globo.com"),
        "org.xbmc.kodi" to Known("Kodi"),
        "org.videolan.vlc" to Known("VLC"),
        "org.smarttube.beta" to Known("SmartTube Beta"),
        "org.smarttube.stable" to Known("SmartTube"),
        "com.teamsmart.videomanager.tv" to Known("SmartTube"),
        "com.liskovsoft.smarttubetv.beta" to Known("SmartTube Beta"),
        "com.stremio.one" to Known("Stremio", "stremio://"), // checked
        "com.lagradost.cloudstream3" to Known("CloudStream", "cloudstreamapp://"),
        "com.lagradost.cloudstream3.prerelease" to Known("CloudStream", "cloudstreamapp://"), // checked
    )

    /** Popular everywhere, in this order: the apps most TVs have. */
    private val global = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
    )

    /**
     * Popular in one country, by ISO 3166 code. Kept short and only where the TV package is known;
     * a wrong guess costs little, since a tap on an app the TV lacks opens its store page.
     */
    private val regional = mapOf(
        "US" to listOf("com.hulu.livingroomplus", "com.peacocktv.peacockandroid", "com.cbs.ott"),
        "IN" to listOf("in.startv.hotstar", "com.graymatrix.did", "com.sonyliv"),
        "GB" to listOf("uk.co.bbc.iplayer", "com.channel4.ondemand"),
        "BR" to listOf("com.globo.globotv"),
    )

    /**
     * System screens the TV reports like any app, which nobody wants to "open" from a list: home
     * screens, settings, the store, the assistant, the screensaver.
     */
    private val system = setOf(
        "android",
        "com.android.vending",
        "com.android.systemui",
        "com.android.tv.settings",
        "com.google.android.katniss",
        "com.google.android.apps.tv.dreamx",
        "com.google.android.backdrop",
        "com.google.android.tungsten.setupwraith",
        STUB,
    )

    /** Package parts that say nothing about which app it is, skipped when making a name from one. */
    private val genericParts = setOf(
        "com", "org", "net", "io", "tv", "co", "uk", "in", "de", "fr", "app", "apps", "android",
        "androidtv", "atv", "googletv", "leanback", "mobile", "client", "player", "ott", "one",
        "livingroom", "release", "beta", "prerelease", "free", "pro", "main",
    )

    /** True for packages that are part of the TV rather than an app to open: kept out of Recent. */
    fun isSystem(pkg: String): Boolean =
        pkg in system || "launcher" in pkg || pkg.endsWith(".settings") || pkg.endsWith(".setupwizard")

    /** The name to show for [pkg]: the known one, or one made from the package ([nameFromPackage]). */
    fun nameFor(pkg: String): String = known[pkg]?.name ?: nameFromPackage(pkg)

    /**
     * A readable guess at an app's name from its package alone: the longest part that isn't generic,
     * without trailing digits, capitalised. `com.lagradost.cloudstream3` gives "Cloudstream", and
     * `com.stremio.one` gives "Stremio". Odd guesses can be renamed from the sheet.
     */
    fun nameFromPackage(pkg: String): String {
        val parts = pkg.split('.', '_')
            .map { it.trimEnd { c -> c.isDigit() } }
            .filter { it.length > 1 && it.lowercase() !in genericParts }
        val best = parts.maxByOrNull { it.length } ?: pkg.substringAfterLast('.').ifBlank { pkg }
        return best.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Popular for a phone set to [country]: the global apps, then up to [MAX_REGIONAL] regional ones,
     * leaving out any in [exclude] (those already in Recent).
     */
    fun popular(country: String, exclude: Collection<String> = emptyList()): List<String> =
        (global + regional[country.uppercase()].orEmpty().take(MAX_REGIONAL)).distinct().filter { it !in exclude }

    /**
     * [recent] with [pkg] moved to the front, capped at [MAX_RECENT]. System packages leave it
     * unchanged.
     */
    fun addRecent(recent: List<String>, pkg: String): List<String> =
        if (isSystem(pkg) || pkg.isBlank()) recent else (listOf(pkg) + (recent - pkg)).take(MAX_RECENT)

    /**
     * The link that opens [pkg] on the TV: its own link if known, otherwise a request to the TV's Play
     * Store to launch it. Not every Play Store build takes that request (the Hisense's doesn't, and the
     * TV then closes the connection), so the remote stops sending it to a TV that turned it down.
     */
    fun launchLink(pkg: String): String = known[pkg]?.link ?: storeLaunchLink(pkg)

    /** Asks the TV's Play Store to open [pkg]. */
    fun storeLaunchLink(pkg: String): String = "market://launch?id=$pkg"

    /** True if [pkg] can only be opened through [storeLaunchLink]. */
    fun needsStoreLaunch(pkg: String): Boolean = known[pkg]?.link == null

    /** [pkg]'s page in the TV's Play Store, where it can be installed, or opened if it is installed. */
    fun storePageLink(pkg: String): String = "market://details?id=$pkg"

    /** The TV's Play Store. */
    const val PLAY_STORE = "com.android.vending"

    /** What a Google TV shows for a web address no app claims: "no browser". */
    const val STUB = "com.google.android.tv.frameworkpackagestubs"
}
