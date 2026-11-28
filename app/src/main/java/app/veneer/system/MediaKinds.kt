package app.veneer.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import app.veneer.data.MediaKind
import app.veneer.veneer

/**
 * Decides whether what's playing is music (the island shows the cover art and bars) or a video
 * (the island stays as it is). In order: your own choice for the app, then a built-in list of
 * well-known apps, then the media info itself.
 */
object MediaKinds {

    /** How a decision was made, shown in the "Music or video?" list. */
    enum class Reason { YourChoice, KnownApp, Browser, MediaInfo }

    data class Decision(val kind: MediaKind, val reason: Reason)

    /** Package name to app name, for well-known music apps. */
    val musicApps = mapOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.apple.android.music" to "Apple Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.soundcloud.android" to "SoundCloud",
        "deezer.android.app" to "Deezer",
        "com.aspiro.tidal" to "Tidal",
        "com.pandora.android" to "Pandora",
        "com.sec.android.app.music" to "Samsung Music",
        "com.maxmpz.audioplayer" to "Poweramp",
    )

    /** Package name to app name, for well-known video apps. YouTube is not YouTube Music. */
    val videoApps = mapOf(
        "com.google.android.youtube" to "YouTube",
        "com.netflix.mediaclient" to "Netflix",
        "com.disney.disneyplus" to "Disney+",
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
        "com.amazon.avod" to "Prime Video",
        "tv.twitch.android.app" to "Twitch",
        "com.hulu.plus" to "Hulu",
        "com.wbd.stream" to "Max",
        "com.hbo.hbonow" to "Max",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
    )

    /** Common web browsers. Any other app that opens web links counts too (see [isBrowser]). */
    val browsers = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus", "org.mozilla.fenix",
        "com.brave.browser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "com.android.browser", "com.mi.globalbrowser", "com.UCMobile.intl",
    )

    /** Media this long or longer counts as video when an app isn't on either list. */
    const val MUSIC_MAX_MS = 15 * 60 * 1000L

    /**
     * The decision itself. [durationMs] is 0 or less when the app doesn't say how long the media is;
     * that doesn't count as "under 15 minutes".
     */
    fun decide(
        packageName: String,
        album: String?,
        artist: String?,
        durationMs: Long,
        userChoice: MediaKind?,
        isBrowser: Boolean,
    ): Decision {
        if (userChoice != null) return Decision(userChoice, Reason.YourChoice)
        if (packageName in musicApps) return Decision(MediaKind.Music, Reason.KnownApp)
        if (packageName in videoApps) return Decision(MediaKind.Video, Reason.KnownApp)
        if (isBrowser || packageName in browsers) return Decision(MediaKind.Video, Reason.Browser)
        val described = !album.isNullOrBlank() || !artist.isNullOrBlank()
        val short = durationMs in 1 until MUSIC_MAX_MS
        return Decision(if (described && short) MediaKind.Music else MediaKind.Video, Reason.MediaInfo)
    }

    /** [decide] with the user's choices and the phone's browsers looked up. */
    fun decide(context: Context, packageName: String, album: String?, artist: String?, durationMs: Long): Decision =
        decide(packageName, album, artist, durationMs, context.veneer.store.current.island.mediaKinds[packageName], isBrowser(context, packageName))

    /** What would be decided without the user's choice, for "Automatic" in the settings list. */
    fun automatic(context: Context, packageName: String): String = when {
        packageName in musicApps -> "Automatic: music (a known music app)"
        packageName in videoApps -> "Automatic: video (a known video app)"
        packageName in browsers || isBrowser(context, packageName) -> "Automatic: video (a web browser)"
        else -> "Automatic: music when it has an album or artist and is under 15 minutes"
    }

    @Volatile
    private var browserCache: Set<String>? = null

    /** Apps that open any web link are browsers. */
    fun isBrowser(context: Context, packageName: String): Boolean {
        val known = browserCache ?: runCatching {
            val web = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
            context.packageManager.queryIntentActivities(web, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet()).also { browserCache = it }
        return packageName in known
    }

    /** Adds an app to the "Music or video?" list the first time it plays something. */
    fun noteApp(context: Context, packageName: String) {
        val store = context.veneer.store
        if (packageName.isBlank() || store.current.island.mediaApps.firstOrNull() == packageName) return
        store.update { s ->
            s.copy(island = s.island.copy(mediaApps = (listOf(packageName) + s.island.mediaApps.filter { it != packageName }).take(20)))
        }
    }
}
