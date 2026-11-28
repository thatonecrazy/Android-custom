package app.veneer

import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veneer.data.IslandConfig
import app.veneer.data.QuickTile
import app.veneer.system.IslandEvent
import app.veneer.system.IslandEvents
import app.veneer.system.IslandFrame
import app.veneer.system.IslandPanel
import app.veneer.system.LyricLine
import app.veneer.system.Lyrics
import app.veneer.system.QuickActions
import app.veneer.system.TileKind
import app.veneer.system.TileOutcome
import app.veneer.system.islandWindowFlags
import app.veneer.system.kind
import app.veneer.ui.island.IslandContent
import app.veneer.ui.island.IslandHost
import app.veneer.ui.island.IslandView
import app.veneer.ui.island.LyricsState
import app.veneer.ui.island.LyricsView
import app.veneer.ui.island.MusicControls
import app.veneer.ui.island.PlayPauseIcon
import app.veneer.ui.island.QuickPanel
import app.veneer.ui.island.QuickPanelActions
import app.veneer.ui.island.QuickPanelState
import app.veneer.ui.island.animationsOff
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCameraCharacteristics
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
class IslandTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var app: VeneerApp
    private val green = 0xFF3DDC84.toInt()
    private val config = IslandConfig(enabled = true, background = Color.BLACK, content = Color.WHITE, accent = green)
    private val accent = ComposeColor(green)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.updater.autoCheck = false
        IslandPanel.close()
    }

    @After
    fun tearDown() {
        IslandPanel.close()
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
    }

    // --- Helpers -------------------------------------------------------------------------------

    private fun solid(color: Int): ImageBitmap = createBitmap(64, 64).apply { eraseColor(color) }.asImageBitmap()
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    private fun width(tag: String) = node(tag).fetchSemanticsNode().size.width
    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot
    private fun capture(tag: String): Bitmap = node(tag).captureToImage().asAndroidBitmap()
    private fun Bitmap.at(fx: Float, fy: Float) = getPixel((width * fx).toInt().coerceIn(0, width - 1), (height * fy).toInt().coerceIn(0, height - 1))
    private fun near(c: Int, r: Int, g: Int, b: Int, tol: Int = 26) =
        abs(Color.red(c) - r) <= tol && abs(Color.green(c) - g) <= tol && abs(Color.blue(c) - b) <= tol
    private fun Bitmap.count(test: (Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (test(getPixel(x, y))) n++
        return n
    }
    private fun greenish(c: Int) = Color.green(c) > 120 && Color.green(c) > Color.red(c) + 40
    private fun exists(tag: String) = runCatching { node(tag).fetchSemanticsNode() }.isSuccess

    /**
     * Moves the clock on [ms] one frame at a time and lets the UI catch up after each frame, the way
     * a phone draws: state changed outside the UI (a press, a new song) is picked up on the next frame.
     */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(16L, left)
            rule.mainClock.advanceTimeBy(step)
            rule.waitForIdle()
            left -= step
        }
    }

    /** Advances the clock a frame at a time until [tag] is on screen; returns the time it took. */
    private fun advanceUntilShown(tag: String, limitMs: Long = 600): Long {
        var t = 0L
        while (!exists(tag) && t < limitMs) {
            advance(16)
            t += 16
        }
        assertTrue("$tag never appeared", exists(tag))
        return t
    }

    private fun music(title: String, art: ImageBitmap?, open: Boolean = false, playing: Boolean = true, controls: MusicControls? = null, lyrics: LyricsView? = null) =
        IslandContent.Music(title, "Some Artist", art, playing, open, controls, lyrics)

    /** Made-up lines, timed four seconds apart. */
    private val madeUp = Lyrics(
        listOf(
            LyricLine(0, "made-up line zero"),
            LyricLine(4_000, "made-up line one"),
            LyricLine(8_000, "made-up line two"),
            LyricLine(12_000, "made-up line three"),
            LyricLine(16_000, "made-up line four"),
            LyricLine(20_000, "made-up line five"),
            LyricLine(24_000, "made-up line six"),
        ),
        synced = true,
    )

    // --- Media: cover art ------------------------------------------------------------------------

    @Test
    fun coverArtFillsThePillDarkenedAboutThirtyPercent() {
        rule.setContent { IslandView(config, accent, music("Song", solid(Color.RED)), null) }
        rule.waitForIdle()
        val pill = capture("musicPill")
        // Red art under a 30% black layer: about 70% red, across the pill and up to its top edge.
        assertTrue(near(pill.at(0.25f, 0.5f), 178, 0, 0))
        assertTrue(near(pill.at(0.5f, 0.06f), 178, 0, 0))
        // The music bars stay visible on top, in the highlight color.
        assertTrue(pill.count { greenish(it) } > 20)
    }

    @Test
    fun theOpenCardUsesTheSameCoverBlurredAsItsBackground() {
        rule.setContent { IslandView(config, accent, music("Song", solid(Color.BLUE), open = true), null) }
        rule.waitForIdle()
        node("blurredCover").assertExists()
        val card = capture("musicCard")
        // Bottom corner, away from text and buttons: blue under the 45% darkening.
        assertTrue(near(card.at(0.985f, 0.5f), 0, 0, 140, tol = 30))
    }

    @Test
    fun aNewSongSlidesTheOldCoverOutAndTheNewOneIn() {
        var content by mutableStateOf<IslandContent>(music("One", solid(Color.RED)))
        rule.setContent { IslandView(config, accent, content, null) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        content = music("Two", solid(Color.BLUE))
        advance(144)
        val mid = capture("musicPill")
        val left = mid.at(0.25f, 0.1f)
        val right = mid.at(0.9f, 0.1f)
        assertTrue("old cover still on the left", Color.red(left) > Color.blue(left))
        assertTrue("new cover coming in on the right", Color.blue(right) > Color.red(right) + 60)
        advance(500)
        val end = capture("musicPill")
        assertTrue(near(end.at(0.25f, 0.5f), 0, 0, 178))
        assertTrue(near(end.at(0.04f, 0.1f), 0, 0, 178))
    }

    @Test
    fun playAndPauseMorphIntoEachOther() {
        var playing by mutableStateOf(false)
        rule.setContent {
            Box(Modifier.testTag("pp").background(ComposeColor.Black)) {
                PlayPauseIcon(playing, ComposeColor.White, 96.dp, reduceMotion = false)
            }
        }
        rule.waitForIdle()
        fun whites() = capture("pp").count { Color.red(it) > 200 }
        val play = capture("pp")
        // Top right of the right pause bar is empty while it shows play.
        assertTrue(Color.red(play.at(16f / 24f, 6f / 24f)) < 60)
        val playWhites = whites()
        rule.mainClock.autoAdvance = false
        playing = true
        advance(110)
        val midWhites = whites()
        advance(300)
        val pause = capture("pp")
        assertTrue(Color.red(pause.at(16f / 24f, 6f / 24f)) > 200)
        val pauseWhites = whites()
        assertTrue("halfway is between the two shapes", midWhites != playWhites && midWhites != pauseWhites)
    }

    // --- Animations ------------------------------------------------------------------------------

    @Test
    fun resizeWaitsForTheFadeOvershootsThenShowsTheNewContents() {
        var content by mutableStateOf<IslandContent>(IslandContent.Idle)
        rule.setContent { IslandView(config, accent, content, null) }
        rule.waitForIdle()
        val idle = width("islandBody")
        rule.mainClock.autoAdvance = false
        content = IslandContent.Charging(76)
        val widths = mutableListOf<Int>()
        var greenAt150 = -1
        for (step in 1..30) {
            advance(16)
            widths += width("islandBody")
            if (step == 9) greenAt150 = capture("islandBody").count { greenish(it) }
        }
        val final = widths.last()
        assertEquals("keeps its size while the old contents fade out", idle, widths[2])
        assertTrue("grows past its new size before settling", widths.max() > final)
        assertTrue(final > idle * 2)
        assertEquals("new contents stay hidden while it grows", 0, greenAt150)
        assertTrue("new contents show once it's grown", capture("islandBody").count { greenish(it) } > 50)

        // Shrinking: the contents fade first, then the island shrinks.
        advance(300)
        val settled = width("islandBody")
        assertEquals(final.toFloat(), settled.toFloat(), 2f)
        content = IslandContent.Idle
        advance(48)
        assertEquals("contents fade before it shrinks", settled, width("islandBody"))
        advance(500)
        assertEquals(idle.toFloat(), width("islandBody").toFloat(), 2f)
    }

    @Test
    fun pressingSqueezesTheIslandAndReleasingSpringsBack() {
        rule.setContent { IslandView(config, accent, IslandContent.Idle, null) }
        rule.waitForIdle()
        val normal = bounds("islandBody").width
        rule.mainClock.autoAdvance = false
        node("islandBody").performTouchInput { down(center) }
        advance(256)
        val squeezed = bounds("islandBody").width / normal
        assertTrue("squeezed to about 95%: $squeezed", squeezed in 0.93f..0.97f)
        node("islandBody").performTouchInput { up() }
        advance(400)
        assertEquals(normal, bounds("islandBody").width, 0.5f)
    }

    @Test
    fun chargingPulsesTheBoltOnceAndSlidesTheFillUp() {
        var content by mutableStateOf<IslandContent>(IslandContent.Idle)
        rule.setContent { IslandView(config, accent, content, null) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        content = IslandContent.Charging(76)
        advanceUntilShown("chargeFill")
        val fills = mutableListOf<Int>()
        val bolts = mutableListOf<Float>()
        repeat(40) {
            advance(16)
            fills += width("chargeFill")
            bolts += rule.onNodeWithContentDescription("Charging", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        }
        val box = width("islandBody")
        assertTrue("the fill starts low", fills.first() < box * 0.3f)
        assertTrue("and slides up", fills.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(box * 0.76f, fills.last().toFloat(), box * 0.03f)
        val restSize = bolts.last()
        assertTrue("the bolt pulses", bolts.max() > restSize * 1.25f)
        assertEquals("then rests", restSize, bolts.takeLast(3).first(), 0.5f)
    }

    @Test
    fun notificationsDropWithABounceAndTheIconPopsInLast() {
        var content by mutableStateOf<IslandContent>(IslandContent.Idle)
        rule.setContent { IslandView(config, accent, content, null) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        content = IslandContent.Notification("Messages", "Sam", "made-up message", null)
        val tops = mutableListOf<Float>()
        var textAt = -1
        var iconAt = -1
        for (step in 1..36) {
            advance(16)
            tops += bounds("islandBody").top
            if (textAt < 0 && capture("islandBody").count { Color.red(it) > 200 } > 30) textAt = step
            if (iconAt < 0 && exists("notificationIcon") && bounds("notificationIcon").width > 1f) iconAt = step
        }
        val rest = tops.last()
        assertTrue("it drops in from above (highest ${tops.min()}, rests at $rest)", tops.min() < rest - 20f)
        assertTrue("with a small bounce past where it stops", tops.max() > rest + 0.5f)
        assertTrue("text shows first ($textAt), the icon last ($iconAt)", textAt in 1 until iconAt)
    }

    @Test
    fun theDropDownUnfoldsDownwardAndTilesBounceAndFillFromTheCenter() {
        var open by mutableStateOf(false)
        var on by mutableStateOf(mapOf<QuickTile, Boolean?>(QuickTile.Flashlight to false))
        val tiles = listOf(QuickTile.Wifi, QuickTile.Bluetooth, QuickTile.Flashlight, QuickTile.DoNotDisturb)
        rule.setContent {
            IslandView(
                config, accent, IslandContent.Idle, null,
                panel = if (open) {
                    {
                        QuickPanel(
                            config, accent, QuickPanelState(tiles, on, 0.5f, false, 0.5f),
                            QuickPanelActions(onTile = { t -> on = on + (t to !(on[t] ?: false)) }),
                            reduceMotion = false,
                        )
                    }
                } else null,
            )
        }
        rule.waitForIdle()
        val islandBottom = bounds("islandBody").bottom
        rule.mainClock.autoAdvance = false
        open = true
        advanceUntilShown("quickPanel")
        while (bounds("quickPanel").height < 1f) advance(16)
        val early = bounds("quickPanel")
        advance(500)
        val done = bounds("quickPanel")
        assertTrue("it unfolds: shorter at first", early.height < done.height * 0.8f)
        assertTrue("from the island downward", done.top >= islandBottom)

        // A tap bounces the tile, and its color fills from the center out.
        val full = bounds("tileCircle-Flashlight").width
        node("tileCircle-Flashlight").performClick()
        advance(32)
        assertTrue(bounds("tileCircle-Flashlight").width < full * 0.97f)
        advance(112)
        val filling = capture("tileCircle-Flashlight")
        assertTrue("the center fills first", greenish(filling.at(0.5f, 0.25f)))
        assertFalse("the edge is still grey", greenish(filling.at(0.5f, 0.03f)))
        advance(400)
        assertTrue("then the whole tile", greenish(capture("tileCircle-Flashlight").at(0.5f, 0.06f)))
        assertEquals(full, bounds("tileCircle-Flashlight").width, 0.5f)
    }

    @Test
    fun withRemoveAnimationsEverythingJumpsStraightToTheEnd() {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 0f)
        assertTrue(animationsOff())
        var content by mutableStateOf<IslandContent>(IslandContent.Idle)
        rule.setContent { IslandView(config, accent, content, null, reduceMotion = animationsOff()) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        content = IslandContent.Charging(76)
        advance(96)
        val now = width("islandBody")
        assertTrue("already at full size", now > 400)
        assertTrue("contents already visible", capture("islandBody").count { greenish(it) } > 50)
        advance(600)
        assertEquals("no spring, no fade", width("islandBody"), now)
        assertEquals(width("islandBody") * 0.76f, width("chargeFill").toFloat(), 3f)
    }

    // --- Lyrics ----------------------------------------------------------------------------------

    @Test
    fun theLyricsButtonTogglesATallerCardAndTheControlsKeepWorking() {
        var lyricsOpen by mutableStateOf(false)
        var state by mutableStateOf<LyricsState>(LyricsState.Loading)
        var playPresses = 0
        val controls = MusicControls({}, { playPresses++ }, {}, {}, toggleLyrics = { lyricsOpen = !lyricsOpen })
        rule.setContent {
            IslandView(config, accent, music("Song", solid(Color.RED), open = true, controls = controls, lyrics = LyricsView(lyricsOpen, state, 0)), null)
        }
        rule.waitForIdle()
        val short = node("musicCard").fetchSemanticsNode().size.height
        node("lyricsButton").performClick()
        rule.waitForIdle()
        assertTrue(lyricsOpen)
        val tall = node("musicCard").fetchSemanticsNode().size.height
        assertTrue("the card gets taller ($short -> $tall)", tall > short + 300)
        rule.onNodeWithText("Looking for lyrics...").assertExists()
        node("playPause").performClick()
        assertEquals(1, playPresses)
        state = LyricsState.NotFound
        rule.waitForIdle()
        rule.onNodeWithText("No lyrics found for this song").assertExists()
        state = LyricsState.Offline
        rule.waitForIdle()
        rule.onNodeWithText("Can't reach the lyrics service").assertExists()
        node("playPause").performClick()
        assertEquals(2, playPresses)
        node("lyricsButton").performClick()
        rule.waitForIdle()
        assertFalse(lyricsOpen)
        node("lyrics").assertDoesNotExist()
    }

    @Test
    fun timedLyricsFollowTheSongAndTappingALineSeeksThere() {
        var position by mutableStateOf(8_500L)
        val seeks = mutableListOf<Long>()
        val controls = MusicControls({}, {}, {}, {}, seekTo = { seeks += it })
        rule.setContent {
            IslandView(config, accent, music("Song", null, open = true, controls = controls, lyrics = LyricsView(true, LyricsState.Found(madeUp), position)), null)
        }
        rule.waitForIdle()
        fun current() = node("lyricCurrent").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        fun centered() {
            val line = bounds("lyricCurrent")
            val pane = bounds("lyrics")
            assertEquals((pane.top + pane.bottom) / 2, (line.top + line.bottom) / 2, 24f)
        }
        assertEquals("made-up line two", current())
        centered()
        position = 20_100
        rule.waitForIdle()
        assertEquals("made-up line five", current())
        centered()
        node("lyric3").performClick()
        assertEquals(listOf(12_000L), seeks)
    }

    @Test
    fun untimedLyricsScrollByHand() {
        val plain = Lyrics((1..30).map { LyricLine(null, "made-up plain line $it") }, synced = false)
        rule.setContent {
            IslandView(config, accent, music("Song", null, open = true, controls = MusicControls({}, {}, {}, {}), lyrics = LyricsView(true, LyricsState.Found(plain), 0)), null)
        }
        rule.waitForIdle()
        node("lyricCurrent").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("made-up plain line 30"))
        rule.onNodeWithText("made-up plain line 30").assertExists()
    }

    // --- Quick actions: opening and closing --------------------------------------------------------

    @Test
    fun tappingSwipingAndTheWindowFlags() {
        var taps = 0
        var swipes = 0
        var content by mutableStateOf<IslandContent>(IslandContent.Idle)
        rule.setContent { IslandView(config, accent, content, null, onTap = { taps++ }, onSwipeDown = { swipes++ }) }
        rule.waitForIdle()
        node("islandBody").performClick()
        assertEquals(1, taps)
        // A swipe down works from any state, here while music plays.
        content = music("Song", solid(Color.RED))
        rule.waitForIdle()
        node("islandBody").performTouchInput { swipe(center, center + Offset(0f, 300f), 120) }
        rule.waitForIdle()
        assertEquals(1, swipes)
        assertEquals("a swipe isn't a tap", 1, taps)

        // The window hears taps outside it, never blocks taps meant for other apps, and takes focus only while open.
        val closed = islandWindowFlags(panelOpen = false)
        val opened = islandWindowFlags(panelOpen = true)
        for (flags in listOf(closed, opened)) {
            assertTrue(flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH != 0)
            assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        }
        assertTrue(closed and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(opened and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0)

        // ACTION_OUTSIDE and Back reach the frame's callbacks.
        var outside = 0
        var back = 0
        val frame = IslandFrame(app).apply {
            onOutside = { outside++ }
            onBack = { back++ }
        }
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0))
        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        assertEquals(1, outside)
        assertEquals(1, back)
    }

    @Test
    fun theLiveIslandOpensAndClosesItsDropDown() {
        app.store.update { it.copy(island = config) }
        rule.setContent { IslandHost() }
        rule.waitForIdle()
        // Tapping the resting island opens the drop-down.
        node("islandBody").performClick()
        rule.waitForIdle()
        assertTrue(IslandPanel.open.value)
        node("quickPanel").assertExists()
        // Swiping up on it closes it.
        node("quickPanel").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertFalse(IslandPanel.open.value)
        node("quickPanel").assertDoesNotExist()
        // A swipe down opens it even while charging shows.
        IslandEvents.post(IslandEvent.Charging(40))
        rule.waitForIdle()
        node("chargeFill").assertExists()
        node("islandBody").performTouchInput { swipe(center, center + Offset(0f, 300f), 120) }
        rule.waitForIdle()
        assertTrue(IslandPanel.open.value)
        // Closing from outside (what ACTION_OUTSIDE and Back do) folds it away.
        IslandPanel.close()
        rule.waitForIdle()
        node("quickPanel").assertDoesNotExist()
    }

    // --- Quick actions: tiles, sliders, permissions ------------------------------------------------

    private fun setCanWriteSettings(allowed: Boolean) {
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_WRITE_SETTINGS, Process.myUid(), app.packageName,
            if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED,
        )
    }

    @Test
    fun tilesReadTheRealState() {
        val quick = QuickActions(app)
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        assertEquals(true, quick.isOn(QuickTile.AutoRotate))
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        assertEquals(false, quick.isOn(QuickTile.AutoRotate))
        Settings.Global.putInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        assertEquals(true, quick.isOn(QuickTile.Airplane))
        @Suppress("DEPRECATION")
        app.getSystemService(WifiManager::class.java).isWifiEnabled = false
        assertEquals(false, quick.isOn(QuickTile.Wifi))
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        assertEquals(true, quick.isOn(QuickTile.PowerSaving))
        app.getSystemService(NotificationManager::class.java).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertEquals(true, quick.isOn(QuickTile.DoNotDisturb))
        // Tiles that only do something once have no on/off.
        listOf(QuickTile.Screenshot, QuickTile.LockScreen, QuickTile.PowerMenu).forEach { assertEquals(null, quick.isOn(it)) }
    }

    @Test
    fun directTilesSwitchOrSayWhatTheyNeed() {
        val quick = QuickActions(app)
        // Auto-rotate needs "Modify system settings".
        setCanWriteSettings(false)
        val needs = quick.tap(QuickTile.AutoRotate) as TileOutcome.NeedsPermission
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, needs.intent.action)
        setCanWriteSettings(true)
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        assertEquals(TileOutcome.Done, quick.tap(QuickTile.AutoRotate))
        assertEquals(1, Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION))

        // Do not disturb needs Do Not Disturb access.
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, (quick.tap(QuickTile.DoNotDisturb) as TileOutcome.NeedsPermission).intent.action)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertEquals(TileOutcome.Done, quick.tap(QuickTile.DoNotDisturb))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)

        // No flashlight: a message, not a crash. With one: it switches on.
        assertTrue(QuickActions(app).tap(QuickTile.Flashlight) is TileOutcome.Failed)
        val camera = app.getSystemService(CameraManager::class.java)
        val characteristics = ShadowCameraCharacteristics.newCameraCharacteristics()
        shadowOf(characteristics).set(CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
        shadowOf(characteristics).set(CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK)
        shadowOf(camera).addCamera("0", characteristics)
        val withTorch = QuickActions(app)
        assertEquals(TileOutcome.Done, withTorch.tap(QuickTile.Flashlight))
        assertTrue(shadowOf(camera).getTorchMode("0"))
        assertEquals(true, withTorch.isOn(QuickTile.Flashlight))

        // Screenshot, lock and power menu need the accessibility service; without it they say so.
        listOf(QuickTile.Screenshot, QuickTile.LockScreen, QuickTile.PowerMenu).forEach {
            assertTrue(quick.tap(it) is TileOutcome.Failed)
        }
    }

    @Test
    fun otherTilesOpenAndroidsOwnPopUpOrPage() {
        val quick = QuickActions(app)
        val started = shadowOf(app as Application)
        fun opens(tile: QuickTile): String? {
            assertEquals(tile.label, TileOutcome.Opened, quick.tap(tile))
            val intent = started.nextStartedActivity
            return intent.action ?: intent.component?.className
        }
        assertEquals(Settings.Panel.ACTION_WIFI, opens(QuickTile.Wifi))
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, opens(QuickTile.MobileData))
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, opens(QuickTile.Airplane))
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, opens(QuickTile.Bluetooth))
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, opens(QuickTile.Location))
        assertEquals("com.android.settings.TetherSettings", opens(QuickTile.Hotspot))
        assertEquals(Settings.ACTION_BATTERY_SAVER_SETTINGS, opens(QuickTile.PowerSaving))
        // Every one of these carries the small arrow, and every tile answers without throwing.
        QuickTile.entries.forEach { tile -> assertNotNull(quick.tap(tile)) }
        assertEquals(
            listOf(QuickTile.Wifi, QuickTile.Bluetooth, QuickTile.MobileData, QuickTile.Location, QuickTile.PowerSaving, QuickTile.Airplane, QuickTile.Hotspot),
            QuickTile.entries.filter { it.kind == TileKind.OpensSystem },
        )
    }

    @Test
    fun brightnessAndVolumeSliders() {
        val quick = QuickActions(app)
        setCanWriteSettings(false)
        assertTrue(quick.setBrightness(0.5f) is TileOutcome.NeedsPermission)
        setCanWriteSettings(true)
        Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        assertEquals(TileOutcome.Done, quick.setBrightness(0.5f))
        assertEquals(127, Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertFalse(quick.autoBrightness())
        assertEquals(0.5f, quick.brightness(), 0.01f)
        assertEquals(TileOutcome.Done, quick.setAutoBrightness(true))
        assertTrue(quick.autoBrightness())

        val audio = app.getSystemService(AudioManager::class.java)
        assertEquals(TileOutcome.Done, quick.setVolume(0.5f))
        assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(0.5f, quick.volume(), 0.1f)
    }

    @Test
    fun theDropDownShowsTilesFourAcrossWithLiveStateAndPermissionLines() {
        setCanWriteSettings(false)
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        app.store.update { it.copy(island = config) }
        rule.setContent { IslandHost() }
        rule.waitForIdle()
        IslandPanel.open.value = true
        rule.waitForIdle()
        // Four across: the first four tiles share a row, the fifth starts the next one.
        val tops = QuickTile.defaults.map { bounds("tile-${it.name}").top }
        assertEquals(1, tops.take(4).distinct().size)
        assertTrue(tops[4] > tops[0])
        rule.onAllNodesWithContentDescription("Opens Android's settings", useUnmergedTree = true)
            .assertCountEquals(QuickTile.defaults.count { it.kind == TileKind.OpensSystem })
        fun state(tile: QuickTile) = node("tile-${tile.name}").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("Off", state(QuickTile.AutoRotate))
        // The real setting changes behind the panel's back: the tile follows within a moment.
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        advance(900)
        rule.waitForIdle()
        assertEquals("On", state(QuickTile.AutoRotate))

        // A tile that needs a permission says why, with a button to the right page.
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        node("tileCircle-AutoRotate").performClick()
        rule.waitForIdle()
        node("panelMessage").assertExists()
        rule.onNodeWithText("Auto-rotate needs permission for Veneer to modify system settings.").assertExists()
        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, shadowOf(app as Application).nextStartedActivity.action)
        assertFalse(IslandPanel.open.value)
    }

    @Test
    fun screenshotsOfTheIslandStates() {
        val dir = System.getProperty("screenshots.dir") ?: return
        var content by mutableStateOf<IslandContent>(music("One", solid(0xFFFF6B6B.toInt())))
        rule.setContent {
            Box(Modifier.testTag("frame").padding(24.dp).background(ComposeColor(0xFF2A2A33)).padding(24.dp)) {
                IslandView(config, accent, content, null)
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        content = IslandContent.Charging(76)
        val frames = (0..9).map { i -> advance(if (i == 0) 0 else 45); capture("frame") }
        val w = frames.maxOf { it.width }
        val h = frames.maxOf { it.height }
        val strip = createBitmap(w, h * frames.size)
        val canvas = android.graphics.Canvas(strip)
        frames.forEachIndexed { i, f -> canvas.drawBitmap(f, 0f, (i * h).toFloat(), null) }
        File(dir).mkdirs()
        File(dir, "island-frames-charging.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
