package app.veneer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veneer.data.AppOverride
import app.veneer.data.BarIcons
import app.veneer.data.BuiltinKind
import app.veneer.data.GestureAction
import app.veneer.data.IconFinish
import app.veneer.data.IconShape
import app.veneer.data.IconRef
import app.veneer.data.IconStyle
import app.veneer.data.IslandConfig
import app.veneer.data.MediaKind
import app.veneer.data.QuickTile
import app.veneer.data.ShuffleInterval
import app.veneer.data.ShuffleTarget
import app.veneer.data.ThemeConfig
import app.veneer.data.DOCK_PAGE
import app.veneer.data.ItemContent
import app.veneer.data.LayoutOps
import app.veneer.data.LayoutOps.Spot
import app.veneer.data.WallpaperSpec
import app.veneer.data.WallpaperStyle
import app.veneer.system.CurrentWallpaper
import app.veneer.system.WallpaperTarget
import app.veneer.system.Wallpapers
import app.veneer.ui.common.GlowBus
import app.veneer.ui.customize.matchWallpaper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import kotlin.math.abs
import app.veneer.ui.launcher.HomeController
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** The previews at the top of the Customize pages. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
class PreviewsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var app: VeneerApp

    /** Stands in for the phone's wallpaper behind the see-through window. */
    private val backdrop = WallpaperSpec(WallpaperStyle.Aurora, 0xFF6C4BFF.toInt(), 0xFFFF4D9A.toInt(), 0xFF1FC8FF.toInt())

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.updater.autoCheck = false
        TestApps.install(app)
        app.icons.iconSource = { entry, _ -> TestApps.icon(entry.label) }
        app.apps.refresh()
        val deadline = System.currentTimeMillis() + 10_000
        while (app.apps.apps.value.size < TestApps.samples.size && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals(TestApps.samples.size, app.apps.apps.value.size)
        app.store.update { start ->
            var s = start.copy(items = emptyList(), pageCount = 2, firstRunDone = true)
            val clock = BuiltinKind.ClockDate
            s = LayoutOps.addAt(s, ItemContent.Builtin(clock, HomeController.defaultStyle(clock)), Spot(0, 0, 0), 4, 2)
            listOf("Calendar", "Maps", "Weather", "Notes").forEachIndexed { i, l -> s = LayoutOps.addAt(s, appContent(l), Spot(0, i, 3), 1, 1) }
            listOf("Photos", "Music", "Mail", "Files").forEachIndexed { i, l -> s = LayoutOps.addAt(s, appContent(l), Spot(0, i, 4), 1, 1) }
            val search = BuiltinKind.Search
            s = LayoutOps.addAt(s, ItemContent.Builtin(search, HomeController.defaultStyle(search)), Spot(0, 0, 5), 4, 1)
            listOf("Phone", "Messages", "Browser", "Camera", "Store").forEachIndexed { i, l ->
                s = LayoutOps.addAt(s, appContent(l), Spot(DOCK_PAGE, i, 0), 1, 1)
            }
            s
        }
    }

    private fun appContent(label: String): ItemContent.App {
        val entry = app.apps.apps.value.first { it.label == label }
        return ItemContent.App(entry.key.component.flattenToString(), entry.key.userSerial)
    }

    private fun settle() {
        repeat(8) {
            compose.waitForIdle()
            Thread.sleep(60)
        }
        compose.waitForIdle()
    }

    private fun open(section: String): ActivityScenario<CustomizeActivity> {
        val intent = Intent(app, CustomizeActivity::class.java).putExtra(CustomizeActivity.EXTRA_SECTION, section)
        return ActivityScenario.launch<CustomizeActivity>(intent).also { settle() }
    }

    /** Saves the whole window drawn over [wallpaper], as the phone shows the wallpaper behind it. */
    private fun shot(name: String, wallpaper: WallpaperSpec? = backdrop) {
        settle()
        val dir = System.getProperty("screenshots.dir") ?: return
        val image = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        val out = createBitmap(image.width, image.height)
        val canvas = Canvas(out)
        if (wallpaper != null) canvas.drawBitmap(Wallpapers.render(wallpaper, image.width, image.height, app), 0f, 0f, null)
        else canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(image, 0f, 0f, null)
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun eachPageHasItsPreview() {
        for ((section, name) in listOf("home" to "home", "drawer" to "drawer", "icons" to "icons", "colors" to "colors", "fonts" to "fonts", "wallpaper" to "wallpaper")) {
            open(section).use {
                compose.onNodeWithTag("preview").assertExists()
                shot("preview-$name")
            }
        }
    }

    // --- Changing each setting and looking at the preview ---------------------------------------

    /** One setting and what changing it did to the preview. */
    private data class Check(val page: String, val setting: String, val shows: String, val pixels: Int, val ok: Boolean)

    private val checks = mutableListOf<Check>()

    private fun theme(t: (ThemeConfig) -> ThemeConfig) = app.store.updateTheme(t)
    private fun island(t: (IslandConfig) -> IslandConfig) = app.store.update { it.copy(island = t(it.island)) }

    private fun image(tag: String): Bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    /** How many pixels differ clearly between two pictures of the preview; a new size counts as all of them. */
    private fun changed(a: Bitmap, b: Bitmap): Int {
        if (a.width != b.width || a.height != b.height) return a.width * a.height
        val n = a.width * a.height
        val pa = IntArray(n).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(n).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var count = 0
        for (i in 0 until n) if (distance(pa[i], pb[i]) > 24) count++
        return count
    }

    private fun distance(x: Int, y: Int): Int = maxOf(
        abs((x ushr 24) - (y ushr 24)),
        abs(((x shr 16) and 255) - ((y shr 16) and 255)),
        abs(((x shr 8) and 255) - ((y shr 8) and 255)),
        abs((x and 255) - (y and 255)),
    )

    /** Pixels that turned toward [color]: the glow, and not a label or anything else that changed. */
    private fun lit(before: Bitmap, during: Bitmap, color: Int): Int {
        if (before.width != during.width || before.height != during.height) return 0
        val n = before.width * before.height
        val pa = IntArray(n).also { before.getPixels(it, 0, before.width, 0, 0, before.width, before.height) }
        val pb = IntArray(n).also { during.getPixels(it, 0, during.width, 0, 0, during.width, during.height) }
        val solid = color or (0xFF shl 24)
        var count = 0
        for (i in 0 until n) if (distance(pa[i], pb[i]) > 24 && distance(pb[i], solid) + 40 < distance(pa[i], solid)) count++
        return count
    }

    /** Steps the clock a frame at a time, letting the screen catch up after each one. */
    private fun frames(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(16L, left)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            left -= step
        }
    }

    /** Lets any label or glow from the last change finish. */
    private fun quiet() {
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(3_000)
        settle()
    }

    private fun noteShowing(text: String) =
        compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("previewNote"))).fetchSemanticsNodes().isNotEmpty()

    private fun noNote() = compose.onAllNodes(hasTestTag("previewNote")).fetchSemanticsNodes().isEmpty()

    /** The setting changes the picture in the preview. */
    private fun picture(page: String, setting: String, tag: String = "preview", change: () -> Unit) {
        quiet()
        val before = image(tag)
        change()
        settle()
        val px = changed(before, image(tag))
        checks += Check(page, setting, "changes the picture", px, px >= MIN_PIXELS)
    }

    /**
     * The setting can't be drawn, so a label saying [text] shows over the preview for 2 seconds.
     * With [peek] the preview also shows the look the setting is for while the label is up.
     */
    private fun label(page: String, setting: String, text: String, tag: String = "preview", peek: Boolean = false, change: () -> Unit) {
        quiet()
        val before = image(tag)
        compose.mainClock.autoAdvance = false
        change()
        frames(600)
        val shown = noteShowing(text)
        val px = changed(before, image(tag))
        frames(1_300)
        val stillAt19 = noteShowing(text)
        frames(1_000)
        val gone = noNote()
        compose.mainClock.autoAdvance = true
        val how = "label \"$text\" for 2 s" + if (peek) ", showing that look meanwhile" else ""
        checks += Check(page, setting, how, px, shown && stillAt19 && gone && (!peek || px >= MIN_PIXELS))
    }

    /** The setting plays the edge glow once around the preview, in [color]. */
    private fun glow(page: String, setting: String, color: Int, change: () -> Unit) {
        quiet()
        val before = image("preview")
        compose.mainClock.autoAdvance = false
        change()
        frames(450)
        val during = image("preview")
        frames(2_700)
        compose.mainClock.autoAdvance = true
        settle()
        val after = image("preview")
        val px = lit(before, during, color)
        val left = lit(before, after, color)
        checks += Check(page, setting, "glow plays around the preview once", px, px >= MIN_PIXELS && left < MIN_PIXELS)
    }

    private fun note(page: String, setting: String, why: String) {
        checks += Check(page, setting, why, -1, true)
    }

    /** Writes what was checked to build/preview-checks, and fails on any setting that didn't show. */
    private fun report(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/preview-checks").apply { mkdirs() }
        val lines = checks.joinToString("\n") { c ->
            "| ${c.page} | ${c.setting} | ${c.shows} | ${if (c.pixels < 0) "-" else c.pixels.toString()} | ${if (c.ok) "yes" else "NO"} |"
        }
        File(dir, "$name.md").writeText("| Page | Setting | How it shows | Pixels changed | Shows |\n|---|---|---|---|---|\n$lines\n")
        val failed = checks.filter { !it.ok }
        assertTrue("Not shown in the preview: " + failed.joinToString { "${it.page} > ${it.setting} (${it.pixels})" }, failed.isEmpty())
    }

    private fun id(label: String) = app.apps.apps.value.first { it.label == label }.key.id

    @Test
    fun homeScreenPageShowsEverySetting() {
        open("home").use {
            val p = "Home screen"
            picture(p, "Columns") { theme { it.copy(gridColumns = 5) } }
            picture(p, "Rows") { theme { it.copy(gridRows = 5) } }
            picture(p, "Page dots") { theme { it.copy(pageIndicator = false) } }
            app.store.update { it.copy(pageCount = 1) }
            label(p, "Page dots, with only one page", "once you have a second page") { theme { it.copy(pageIndicator = true) } }
            app.store.update { it.copy(pageCount = 2) }
            picture(p, "Show the dock") { theme { it.copy(dockEnabled = false) } }
            theme { it.copy(dockEnabled = true) }
            picture(p, "Apps in the dock (5 to 7)") { theme { it.copy(dockColumns = 7) } }
            picture(p, "Dock color") { theme { it.copy(dockBg = 0x99FF4D9A.toInt()) } }
            picture(p, "Corner roundness") { theme { it.copy(dockCornerDp = 2) } }
            picture(p, "Darken wallpaper") { theme { it.copy(wallpaperDim = 0.45f) } }
            picture(p, "Hide the status bar") { theme { it.copy(hideStatusBar = true) } }
            label(p, "Status bar icons, while it's hidden", "Hidden for now", peek = true) { theme { it.copy(statusBarIcons = BarIcons.Dark) } }
            theme { it.copy(hideStatusBar = false, statusBarIcons = BarIcons.Light) }
            picture(p, "Status bar icons") { theme { it.copy(statusBarIcons = BarIcons.Dark) } }
            picture(p, "Show notification dots") { theme { it.copy(notificationDots = false) } }
            glow(p, "Glow when you unlock", ThemeConfig().accent) { theme { it.copy(edgeGlow = true) } }
            glow(p, "Glow color", 0xFF00FF66.toInt()) { theme { it.copy(edgeGlowColor = 0xFF00FF66.toInt()) } }
            glow(p, "Glow when the sound mode changes", 0xFF22C55E.toInt()) { theme { it.copy(soundModeGlow = true) } }
            glow(p, "Try it", 0xFF00FF66.toInt()) { GlowBus.fire(0xFF00FF66.toInt()) }
            label(p, "Swipe up", "Opens quick settings") { theme { it.copy(gestureSwipeUp = GestureAction.QuickSettings) } }
            label(p, "Swipe down", "ready to search") { theme { it.copy(gestureSwipeDown = GestureAction.Search) } }
            label(p, "Double-tap", "Locks the screen") { theme { it.copy(gestureDoubleTap = GestureAction.LockScreen) } }
            val maps = id("Maps")
            label(p, "App to open", "Opens Maps") {
                theme { it.copy(gestureDoubleTap = GestureAction.OpenApp, gestureApps = it.gestureApps + ("doubleTap" to maps)) }
            }
            label(p, "Weather: units", "°F") { app.store.update { it.copy(weather = it.weather.copy(fahrenheit = true)) } }
            label(p, "Weather: city", "in Lisbon") {
                app.store.update { it.copy(weather = it.weather.copy(useDeviceLocation = false, place = "Lisbon", latitude = 38.72, longitude = -9.14)) }
            }
            picture(p, "Wallpaper (a wallpaper set from Veneer is drawn small)") {
                runBlocking { Wallpapers.applyGenerated(app, WallpaperSpec(WallpaperStyle.Vertical, 0xFFE53935.toInt(), 0xFF1E88E5.toInt()), WallpaperTarget.Both) }
            }
            shot("preview-home-changed")
        }
        report("home")
    }

    @Test
    fun appListPageShowsEverySetting() {
        open("drawer").use {
            val p = "App list"
            picture(p, "Columns") { theme { it.copy(drawerColumns = 5) } }
            picture(p, "Show app names") { theme { it.copy(showDrawerLabels = false) } }
            theme { it.copy(showDrawerLabels = true) }
            picture(p, "Background") { theme { it.copy(drawerBg = 0xF0F4F4F4.toInt()) } }
            picture(p, "Text color") { theme { it.copy(drawerTextColor = 0xFF111111.toInt()) } }
            shot("preview-drawer-changed")
        }
        report("drawer")
    }

    @Test
    fun iconsPageShowsEverySetting() {
        open("icons").use {
            val p = "Icons"
            picture(p, "Shape") { theme { it.copy(iconShape = IconShape.Teardrop) } }
            theme { it.copy(iconShape = IconShape.RoundedSquare) }
            picture(p, "Roundness") { theme { it.copy(iconCorner = 8) } }
            for (style in IconStyle.entries.drop(1)) picture(p, "Style: ${style.label}") { theme { it.copy(iconStyle = style) } }
            theme { it.copy(iconStyle = IconStyle.Themed) }
            picture(p, "Symbol color") { theme { it.copy(themedFg = 0xFFFFD43B.toInt()) } }
            picture(p, "Background color") { theme { it.copy(themedBg = 0xFF1B1B2F.toInt()) } }
            picture(p, "Gradient background") { theme { it.copy(themedBg2 = 0xFF6C4BFF.toInt()) } }
            picture(p, "Second color") { theme { it.copy(themedBg2 = 0xFF00C2A8.toInt()) } }
            picture(p, "Background (how solid)") { theme { it.copy(iconBgOpacity = 35) } }
            picture(p, "Finish: Glass") { theme { it.copy(iconFinish = IconFinish.Glass) } }
            picture(p, "Glass color") { theme { it.copy(glassTint = 0xFFE8D7FF.toInt()) } }
            picture(p, "See-through") { theme { it.copy(glassClarity = 70) } }
            picture(p, "Shine") { theme { it.copy(glassShine = 5) } }
            picture(p, "Depth") { theme { it.copy(glassDepth = 100) } }
            theme { it.copy(iconFinish = IconFinish.Flat, iconStyle = IconStyle.Original) }
            picture(p, "Shadow") { theme { it.copy(iconShadow = 90) } }
            picture(p, "Outline thickness") { theme { it.copy(iconOutline = 50) } }
            picture(p, "Outline color") { theme { it.copy(iconOutlineColor = 0xFFFF3B30.toInt()) } }
            picture(p, "Icon size (54 to 72 dp)") { theme { it.copy(iconSizeDp = 72) } }
            picture(p, "Icon size (72 to 80 dp, the largest)") { theme { it.copy(iconSizeDp = 80) } }
            picture(p, "Symbol size") { theme { it.copy(iconScale = 125) } }
            picture(p, "Color tint strength") { theme { it.copy(iconTintStrength = 70) } }
            picture(p, "Color tint color") { theme { it.copy(iconTint = 0xFFFF6B00.toInt()) } }
            picture(p, "Show notification dots") { theme { it.copy(notificationDots = false) } }
            label(p, "Dot color, while dots are off", "how they look when on", peek = true) { theme { it.copy(dotColor = 0xFF00FF66.toInt()) } }
            theme { it.copy(notificationDots = true) }
            picture(p, "Dot color") { theme { it.copy(dotColor = 0xFFFF3B30.toInt()) } }
            picture(p, "Show the number") { theme { it.copy(dotCount = true) } }
            picture(p, "Give old-style icons a background") { theme { it.copy(wrapLegacyIcons = false) } }
            theme { it.copy(wrapLegacyIcons = true) }
            picture(p, "Old-style icons: background color") { theme { it.copy(legacyBg = 0xFF00C2A8.toInt()) } }
            picture(p, "Reset icon settings") {
                val d = ThemeConfig()
                theme {
                    it.copy(
                        iconShape = d.iconShape, iconCorner = d.iconCorner, iconStyle = d.iconStyle, themedFg = d.themedFg, themedBg = d.themedBg,
                        themedBg2 = d.themedBg2, iconBgOpacity = d.iconBgOpacity, iconFinish = d.iconFinish, iconShadow = d.iconShadow,
                        iconOutline = d.iconOutline, iconOutlineColor = d.iconOutlineColor, iconScale = d.iconScale, iconSizeDp = d.iconSizeDp,
                        iconTint = d.iconTint, iconTintStrength = d.iconTintStrength, dotColor = d.dotColor, dotCount = d.dotCount,
                    )
                }
            }
            note(p, "Icon pack", "not run here: the test phone has no icon packs. Picking one redraws every icon in the preview, as on the home screen")
            shot("preview-icons-changed")
        }
        report("icons")
    }

    @Test
    fun colorsPageShowsEverySetting() {
        open("colors").use {
            val p = "Colors"
            picture(p, "Accent color") { theme { it.copy(accent = 0xFFFF6B00.toInt()) } }
            picture(p, "App names and widget text") { theme { it.copy(labelColor = 0xFFFFD43B.toInt()) } }
            picture(p, "Shadow behind text") { theme { it.copy(labelShadow = false) } }
            label(p, "Symbol color, while icons aren't Themed", "for the Themed style", peek = true) { theme { it.copy(themedFg = 0xFFFF3B30.toInt()) } }
            label(p, "Icon background, while icons aren't Themed", "for the Themed style", peek = true) { theme { it.copy(themedBg = 0xFF102030.toInt()) } }
            picture(p, "Behind old-style icons") { theme { it.copy(legacyBg = 0xFFFFD43B.toInt()) } }
            picture(p, "Use the Themed style") { theme { it.copy(iconStyle = IconStyle.Themed) } }
            picture(p, "Symbol color") { theme { it.copy(themedFg = 0xFF00E5FF.toInt()) } }
            picture(p, "Icon background") { theme { it.copy(themedBg = 0xFF3A0050.toInt()) } }
            picture(p, "Dock") { theme { it.copy(dockBg = 0x99FF4D9A.toInt()) } }
            picture(p, "App list background") { theme { it.copy(drawerBg = 0xEEF2F2F2.toInt()) } }
            picture(p, "App list text") { theme { it.copy(drawerTextColor = 0xFF101010.toInt()) } }
            theme { it.copy(wrapLegacyIcons = false) }
            label(p, "Behind old-style icons, while their backgrounds are off", "Give old-style icons a background", peek = true) {
                theme { it.copy(legacyBg = 0xFF00FF66.toInt()) }
            }
            theme { it.copy(pageIndicator = false, dotColor = 0xFFFFFFFF.toInt()) }
            label(p, "Accent color, with no page dots and an own dot color", "menus, highlights") { theme { it.copy(accent = 0xFF00C853.toInt()) } }
            val matched = matchWallpaper(app, app.store.current.theme)
            if (matched != null) picture(p, "Take colors from my wallpaper") { theme { matched } }
            else note(p, "Take colors from my wallpaper", "not run here: the test phone can't report wallpaper colors. It sets the accent, dock and icon colors, each checked above")
            shot("preview-colors-changed")
        }
        report("colors")
    }

    @Test
    fun fontsPageShowsEverySetting() {
        open("fonts").use {
            val p = "Fonts"
            for (font in app.fonts.builtIn.drop(1)) picture(p, "Font: ${font.name}") { theme { it.copy(fontId = font.id) } }
            picture(p, "Show names on the home screen") { theme { it.copy(showHomeLabels = false) } }
            picture(p, "Show names in the app list") { theme { it.copy(showDrawerLabels = false) } }
            theme { it.copy(showHomeLabels = true, showDrawerLabels = true) }
            picture(p, "Text size") { theme { it.copy(labelSizeSp = 17) } }
            picture(p, "Text color") { theme { it.copy(labelColor = 0xFFFFD43B.toInt()) } }
            picture(p, "Bold") { theme { it.copy(labelBold = true) } }
            picture(p, "Shadow") { theme { it.copy(labelShadow = false) } }
            shot("preview-fonts-changed")
        }
        report("fonts")
    }

    @Test
    fun wallpaperPageShowsEverySetting() {
        open("wallpaper").use {
            val p = "Wallpaper"
            val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst() // the page itself, not a row of chips
            list.performScrollToNode(hasText("Make a gradient"))
            list.performScrollToNode(hasText("Set as wallpaper"))
            picture(p, "Gradient: style") { compose.onNodeWithText("Glow").performClick() }
            compose.onNodeWithTag("tryingOn").assertExists()
            picture(p, "Gradient: another style") { compose.onNodeWithText("Diagonal").performClick() }
            picture(p, "Gradient: add a third color") { compose.onNodeWithText("Add a third color").performClick() }
            shot("preview-wallpaper-trying-on", null)
            // Setting it records a small copy, and the preview goes back to the real wallpaper behind the window.
            list.performScrollToNode(hasText("Set as wallpaper"))
            compose.onNodeWithText("Set as wallpaper").performClick()
            settle()
            compose.onNodeWithText("Home and lock screen").performClick()
            val deadline = System.currentTimeMillis() + 10_000
            while (compose.onAllNodes(hasTestTag("tryingOn")).fetchSemanticsNodes().isNotEmpty() && System.currentTimeMillis() < deadline) settle()
            val set = compose.onAllNodes(hasTestTag("tryingOn")).fetchSemanticsNodes().isEmpty() && CurrentWallpaper.load(app, 200) != null
            checks += Check(p, "Set as wallpaper", "the preview shows the new wallpaper (a copy is kept for the small previews)", -1, set)
            val shuffle = listOf("one.jpg", "two.jpg")
            label(p, "Add pictures (shuffle)", "2 new pictures") { app.store.update { it.copy(shuffle = it.shuffle.copy(files = shuffle)) } }
            label(p, "Shuffle", "A new picture each time you lock") { app.store.update { it.copy(shuffle = it.shuffle.copy(enabled = true)) } }
            label(p, "How often", "every hour") { app.store.update { it.copy(shuffle = it.shuffle.copy(interval = ShuffleInterval.Hourly)) } }
            label(p, "Where", "home screen only") { app.store.update { it.copy(shuffle = it.shuffle.copy(target = ShuffleTarget.Home)) } }
        }
        report("wallpaper")
    }

    @Test
    fun islandPageShowsEverySetting() {
        app.store.update { it.copy(island = it.island.copy(enabled = true, mediaApps = listOf("com.example.music"))) }
        open("island").use {
            val p = "Island"
            val tag = "islandPreview"
            picture(p, "Show the island", tag) { island { it.copy(enabled = false) } }
            quiet() // the "off" chip shows once the label about it has gone
            compose.onNodeWithTag("islandOff").assertExists()
            island { it.copy(enabled = true) }
            picture(p, "Charging", tag) { island { it.copy(charging = false) } }
            picture(p, "Music", tag) { island { it.copy(music = false) } }
            island { it.copy(music = true) }
            picture(p, "Lyrics", tag) { island { it.copy(lyrics = false) } }
            picture(p, "Notifications", tag) { island { it.copy(notifications = false) } }
            picture(p, "Alarms", tag) { island { it.copy(alarms = false) } }
            picture(p, "Resting pill", tag) { island { it.copy(idlePill = false) } }
            island { it.copy(idlePill = true) }
            label(p, "Pop-ups stay for", "Stay open for 7 seconds", tag) { island { it.copy(popSeconds = 7) } }
            label(p, "Music or video?", "Video: leaves the island as it is", tag) {
                island { it.copy(mediaKinds = mapOf("com.example.music" to MediaKind.Video)) }
            }
            picture(p, "Quick action tiles: add", tag) { island { it.copy(tiles = it.tiles + QuickTile.Airplane) } }
            picture(p, "Quick action tiles: remove", tag) { island { it.copy(tiles = it.tiles - QuickTile.Wifi) } }
            picture(p, "Quick action tiles: reorder", tag) { island { it.copy(tiles = it.tiles.reversed()) } }
            picture(p, "Quick actions drop-down", tag) { island { it.copy(quickActions = false) } }
            island { it.copy(quickActions = true) }
            picture(p, "Background", tag) { island { it.copy(background = 0xFF2A0A4A.toInt()) } }
            picture(p, "Text and icons", tag) { island { it.copy(content = 0xFFFFD43B.toInt()) } }
            picture(p, "Highlight", tag) { island { it.copy(accent = 0xFF00E5FF.toInt()) } }
            picture(p, "Glowing outline", tag) { island { it.copy(outline = true) } }
            picture(p, "Width", tag) { island { it.copy(widthDp = 170) } }
            picture(p, "Height", tag) { island { it.copy(heightDp = 44) } }
            picture(p, "From the top", tag) { island { it.copy(offsetYDp = 30) } }
            picture(p, "Left or right", tag) { island { it.copy(offsetXDp = -60) } }
        }
        report("island")
    }

    /** Your apps → an app: the app at the top shows each of its own settings as it looks at home. */
    @Test
    fun appEditorShowsEverySetting() {
        open("apps").use {
            compose.onNodeWithText("Maps").performClick()
            settle()
            val p = "Your apps → one app"
            val tag = "appPreview"
            val maps = id("Maps")
            fun own(t: (AppOverride) -> AppOverride) = app.store.updateOverride(maps, t)
            picture(p, "Name", tag) { own { it.copy(label = "Atlas") } }
            picture(p, "Use a picture", tag) {
                val picture = createBitmap(96, 96).apply { eraseColor(0xFFFF8A00.toInt()) }
                File(app.icons.customIconDir, "test-icon.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                own { it.copy(icon = IconRef.Image("test-icon.png")) }
            }
            own { it.copy(icon = null) }
            picture(p, "Shape", tag) { own { it.copy(shape = IconShape.Circle) } }
            picture(p, "Style", tag) { own { it.copy(style = IconStyle.Grayscale) } }
            picture(p, "Finish", tag) { own { it.copy(finish = IconFinish.Glass) } }
            picture(p, "Icon background", tag) { own { it.copy(style = IconStyle.Themed, iconBg = 0xFF2F9E44.toInt()) } }
            picture(p, "Symbol color", tag) { own { it.copy(iconFg = 0xFFFFD43B.toInt()) } }
            picture(p, "Name color on the home screen", tag) { own { it.copy(labelColor = 0xFFFF6B9A.toInt()) } }
            picture(p, "Match the other apps again", tag) {
                own { it.copy(shape = null, style = null, iconFg = null, iconBg = null, finish = null, labelColor = null) }
            }
            own { it.copy(hidden = true) }
            settle()
            val hidden = compose.onAllNodesWithText("Hidden from the app list").fetchSemanticsNodes().isNotEmpty()
            checks += Check(p, "Hide from the app list", "a \"Hidden from the app list\" tag under it", -1, hidden)
            note(p, "An icon from a pack", "not run here: the test phone has no icon packs. A pack icon replaces the icon at the top like a picture does")
            shot("preview-app-editor", null)
        }
        report("app")
    }

    /** Tapping a preview shows it full screen until it's tapped again. */
    @Test
    fun tappingThePreviewOpensItFullScreen() {
        for (section in listOf("home", "drawer", "icons", "colors", "fonts", "wallpaper")) {
            open(section).use {
                compose.onNodeWithTag("preview").performClick()
                settle()
                compose.onNodeWithTag("fullPreview").assertExists()
                if (section == "home") shot("preview-full-home")
                if (section == "drawer") shot("preview-full-drawer")
                compose.onNodeWithTag("fullPreview").performClick()
                settle()
                assertTrue(compose.onAllNodes(hasTestTag("fullPreview")).fetchSemanticsNodes().isEmpty())
                compose.onNodeWithTag("preview").assertExists()
            }
        }
        // The island page has its own preview, which opens full screen the same way.
        open("island").use {
            compose.onNodeWithTag("islandPreview").performClick()
            settle()
            compose.onNodeWithTag("islandFull").assertExists()
            shot("preview-full-island", null)
            compose.onNodeWithTag("islandFull").performClick()
            settle()
            assertTrue(compose.onAllNodes(hasTestTag("islandFull")).fetchSemanticsNodes().isEmpty())
        }
    }

    private companion object {
        /** A change counts as visible when at least this many pixels change (about 10 by 4 at this density). */
        const val MIN_PIXELS = 40
    }
}
