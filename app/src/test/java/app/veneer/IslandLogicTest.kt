package app.veneer

import app.veneer.data.MediaKind
import app.veneer.system.LrcParser
import app.veneer.system.LyricsRepository
import app.veneer.system.LyricsResult
import app.veneer.system.MediaKinds
import app.veneer.ui.island.lyricsAllowed
import app.veneer.ui.island.showsMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class IslandLogicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- Music or video -------------------------------------------------------------------------

    private fun decide(pkg: String, album: String? = null, artist: String? = null, ms: Long = 0, choice: MediaKind? = null, browser: Boolean = false) =
        MediaKinds.decide(pkg, album, artist, ms, choice, browser)

    @Test
    fun yourChoiceComesFirst() {
        assertEquals(MediaKinds.Decision(MediaKind.Video, MediaKinds.Reason.YourChoice), decide("com.spotify.music", choice = MediaKind.Video))
        assertEquals(MediaKinds.Decision(MediaKind.Music, MediaKinds.Reason.YourChoice), decide("com.google.android.youtube", choice = MediaKind.Music))
    }

    @Test
    fun knownAppsByPackageName() {
        val music = listOf(
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music", "com.amazon.mp3",
            "com.soundcloud.android", "deezer.android.app", "com.aspiro.tidal", "com.pandora.android",
            "com.sec.android.app.music", "com.maxmpz.audioplayer",
        )
        val video = listOf(
            "com.google.android.youtube", "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
            "tv.twitch.android.app", "com.hulu.plus", "com.wbd.stream", "com.zhiliaoapp.musically",
        )
        // Even a long, undescribed track from a music app is music, and a short song-like clip from a video app is video.
        music.forEach { assertEquals(it, MediaKinds.Decision(MediaKind.Music, MediaKinds.Reason.KnownApp), decide(it, ms = 3_600_000)) }
        video.forEach { assertEquals(it, MediaKinds.Decision(MediaKind.Video, MediaKinds.Reason.KnownApp), decide(it, album = "A", artist = "B", ms = 180_000)) }
        // YouTube and YouTube Music are different apps.
        assertEquals(MediaKind.Video, decide("com.google.android.youtube").kind)
        assertEquals(MediaKind.Music, decide("com.google.android.apps.youtube.music").kind)
    }

    @Test
    fun browsersAreVideo() {
        assertEquals(MediaKinds.Decision(MediaKind.Video, MediaKinds.Reason.Browser), decide("com.android.chrome", album = "A", artist = "B", ms = 200_000))
        assertEquals(MediaKinds.Decision(MediaKind.Video, MediaKinds.Reason.Browser), decide("com.example.somebrowser", artist = "B", ms = 200_000, browser = true))
    }

    @Test
    fun otherAppsGoByTheMediaInfo() {
        assertEquals(MediaKind.Music, decide("com.example.player", artist = "Someone", ms = 3 * 60_000).kind)
        assertEquals(MediaKind.Music, decide("com.example.player", album = "Some album", ms = 15 * 60_000 - 1).kind)
        assertEquals(MediaKind.Video, decide("com.example.player", artist = "Someone", ms = 15 * 60_000).kind) // not under 15 minutes
        assertEquals(MediaKind.Video, decide("com.example.player", ms = 3 * 60_000).kind) // no album or artist
        assertEquals(MediaKind.Video, decide("com.example.player", artist = "Someone", ms = 0).kind) // length unknown
        assertEquals(MediaKinds.Reason.MediaInfo, decide("com.example.player").reason)
    }

    @Test
    fun onlyMusicChangesTheIslandAndGetsLyrics() {
        assertTrue(showsMusic(MediaKind.Music, playing = true, cardOpen = false))
        assertTrue(showsMusic(MediaKind.Music, playing = false, cardOpen = true)) // paused from the open card
        assertFalse(showsMusic(MediaKind.Music, playing = false, cardOpen = false))
        assertFalse(showsMusic(MediaKind.Video, playing = true, cardOpen = false))
        assertFalse(showsMusic(MediaKind.Video, playing = true, cardOpen = true))
        assertTrue(lyricsAllowed(setting = true, kind = MediaKind.Music))
        assertFalse(lyricsAllowed(setting = true, kind = MediaKind.Video))
        assertFalse(lyricsAllowed(setting = false, kind = MediaKind.Music))
    }

    // --- Lyrics (every line here is made up for the test) ---------------------------------------

    private val lrc = """
        [ar:Test Artist]
        [ti:Test Song]
        [00:01.50] first made-up line
        [00:04.2]second made-up line
        [00:07.250] third made-up line
        [00:09.00][00:20.00] a line used twice
        no time on this line
    """.trimIndent()

    @Test
    fun readsTimedLyrics() {
        val lines = LrcParser.parse(lrc)
        assertEquals(listOf(1_500L, 4_200L, 7_250L, 9_000L, 20_000L), lines.map { it.timeMs })
        assertEquals("first made-up line", lines[0].text)
        assertEquals("a line used twice", lines[4].text)
        assertEquals(-1, LrcParser.currentIndex(lines, 1_000))
        assertEquals(0, LrcParser.currentIndex(lines, 1_500))
        assertEquals(2, LrcParser.currentIndex(lines, 8_000))
        assertEquals(4, LrcParser.currentIndex(lines, 60_000))
    }

    private fun record(synced: String?, plain: String?, instrumental: Boolean = false): String {
        fun q(s: String?) = s?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"
        return """{"id":1,"trackName":"T","artistName":"A","albumName":"B","duration":200,"instrumental":$instrumental,"plainLyrics":${q(plain)},"syncedLyrics":${q(synced)}}"""
    }

    @Test
    fun usesTimedLyricsAndAsksWithTheRightFields() = runBlocking {
        val asked = mutableListOf<String>()
        val repo = LyricsRepository(tmp.newFolder()) { url ->
            asked += url
            LyricsRepository.Http(200, record("[00:01.00] made-up timed line", "made-up plain line"))
        }
        val result = repo.lookup("My Song", "Some Artist", "An Album", 200_400)
        val lyrics = (result as LyricsResult.Found).lyrics
        assertTrue(lyrics.synced)
        assertEquals("made-up timed line", lyrics.lines.single().text)
        assertEquals(
            "https://lrclib.net/api/get?track_name=My+Song&artist_name=Some+Artist&album_name=An+Album&duration=200",
            asked.single(),
        )
    }

    @Test
    fun fallsBackToPlainLyrics() = runBlocking {
        val repo = LyricsRepository(tmp.newFolder()) { LyricsRepository.Http(200, record(null, "made-up line one\nmade-up line two")) }
        val lyrics = (repo.lookup("S", "A", "B", 180_000) as LyricsResult.Found).lyrics
        assertFalse(lyrics.synced)
        assertEquals(listOf("made-up line one", "made-up line two"), lyrics.lines.map { it.text })
    }

    @Test
    fun noLyricsIsSavedSoTheSongIsNeverAskedForTwice() = runBlocking {
        val dir = tmp.newFolder()
        var calls = 0
        val fetch: (String) -> LyricsRepository.Http = { calls++; LyricsRepository.Http(404, """{"code":404,"name":"TrackNotFound"}""") }
        assertEquals(LyricsResult.NotFound, LyricsRepository(dir, fetch).lookup("S", "A", "B", 180_000))
        assertEquals(LyricsResult.NotFound, LyricsRepository(dir, fetch).lookup("S", "A", "B", 180_000)) // a new start of the app
        assertEquals(1, calls)
        // Found lyrics are saved the same way.
        var found = 0
        val dir2 = tmp.newFolder()
        val ok: (String) -> LyricsRepository.Http = { found++; LyricsRepository.Http(200, record("[00:02.00] made-up saved line", null)) }
        LyricsRepository(dir2, ok).lookup("S", "A", "B", 180_000)
        val again = LyricsRepository(dir2, ok).lookup("S", "A", "B", 180_000) as LyricsResult.Found
        assertEquals(1, found)
        assertEquals(2_000L, again.lyrics.lines.single().timeMs)
    }

    @Test
    fun offlineIsNotSavedAndIsTriedAgain() = runBlocking {
        var calls = 0
        val repo = LyricsRepository(tmp.newFolder()) { calls++; throw IOException("no network") }
        assertEquals(LyricsResult.Offline, repo.lookup("S", "A", "B", 180_000))
        assertEquals(LyricsResult.Offline, repo.lookup("S", "A", "B", 180_000))
        assertEquals(2, calls)
    }

    @Test
    fun instrumentalAndEmptyMeanNoLyrics() = runBlocking {
        val repo = LyricsRepository(tmp.newFolder()) { LyricsRepository.Http(200, record(null, null, instrumental = true)) }
        assertEquals(LyricsResult.NotFound, repo.lookup("S", "A", "B", 180_000))
    }

    @Test
    fun withoutALengthItSearchesByName() = runBlocking {
        val asked = mutableListOf<String>()
        val repo = LyricsRepository(tmp.newFolder()) { url ->
            asked += url
            LyricsRepository.Http(200, "[" + record(null, null) + "," + record("[00:03.00] made-up found line", null) + "]")
        }
        val lyrics = (repo.lookup("S", "A", "", 0) as LyricsResult.Found).lyrics
        assertEquals("made-up found line", lyrics.lines.single().text)
        assertTrue(asked.single().startsWith("https://lrclib.net/api/search?track_name=S&artist_name=A"))
    }
}
