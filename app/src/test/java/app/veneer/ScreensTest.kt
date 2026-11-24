package app.veneer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.IconShape
import app.veneer.data.IconStyle
import app.veneer.data.ItemContent
import app.veneer.data.LayoutOps
import app.veneer.data.LayoutOps.Spot
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.data.WallpaperStyle
import app.veneer.data.WidgetStyle
import app.veneer.data.forApp
import app.veneer.data.WeatherSettings
import app.veneer.icons.IconRenderer
import app.veneer.icons.IconSource
import app.veneer.icons.iconSpec
import app.veneer.system.PhotoWallpapers
import app.veneer.system.WeatherNow
import app.veneer.system.WidgetPhotos
import app.veneer.system.Wallpapers
import app.veneer.ui.common.GlowBus
import app.veneer.ui.launcher.HomeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xhdpi")
class ScreensTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var app: VeneerApp

    /** A wallpaper drawn behind screenshots, standing in for whatever picture the phone has. */
    private val backdrop = WallpaperSpec(WallpaperStyle.Aurora, 0xFF6C4BFF.toInt(), 0xFFFF4D9A.toInt(), 0xFF1FC8FF.toInt())

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestApps.install(app)
        app.icons.iconSource = { entry, _ -> TestApps.icon(entry.label) }
        app.apps.refresh()
        val deadline = System.currentTimeMillis() + 10_000
        while (app.apps.apps.value.size < TestApps.samples.size && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals(TestApps.samples.size, app.apps.apps.value.size)
        buildLayout()
    }

    private fun appContent(label: String): ItemContent.App {
        val entry = app.apps.apps.value.first { it.label == label }
        return ItemContent.App(entry.key.component.flattenToString(), entry.key.userSerial)
    }

    private fun buildLayout() {
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

    /** Saves what's on screen, drawn over [wallpaper] the way the real window shows the wallpaper behind it. */
    private fun shot(name: String, wallpaper: WallpaperSpec?) {
        compose.waitForIdle()
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

    /** Icons are drawn in the background; give them time to arrive before a screenshot. */
    private fun settle() {
        repeat(6) {
            compose.waitForIdle()
            Thread.sleep(80)
        }
        compose.waitForIdle()
    }

    private fun launchHome(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also {
        compose.waitForIdle()
    }

    @Test
    fun homeScreenShowsAppsWidgetsAndDock() {
        launchHome().use {
            compose.onNodeWithText("Search apps").assertExists()
            compose.onNodeWithText("Calendar").assertExists()
            compose.onNodeWithText("Files").assertExists()
            shot("home-default", backdrop)
        }
    }

    /** Builds a look one setting at a time, the way someone would in Customize, with a screenshot after each step. */
    @Test
    fun buildingALookStepByStep() {
        launchHome().use {
            app.store.updateTheme { it.copy(iconShape = IconShape.Teardrop) }
            settle()
            shot("step-1-shape", backdrop)

            app.store.updateTheme { it.copy(iconStyle = IconStyle.Themed, themedBg = 0xFFFFE3EE.toInt(), themedFg = 0xFFD6336C.toInt()) }
            settle()
            shot("step-2-icon-colors", backdrop)

            app.store.updateTheme { it.copy(fontId = "quicksand", labelBold = true) }
            settle()
            shot("step-3-font", backdrop)

            app.store.updateTheme { it.copy(dockBg = 0x66FFFFFF, labelColor = 0xFFFFF3F8.toInt(), accent = 0xFFFF4D9A.toInt()) }
            settle()
            shot("step-4-colors", backdrop)

            // Single apps with their own look.
            val maps = app.apps.apps.value.first { it.label == "Maps" }.key.id
            val music = app.apps.apps.value.first { it.label == "Music" }.key.id
            app.store.updateOverride(maps) { it.copy(iconBg = 0xFF2F9E44.toInt(), iconFg = 0xFFFFFFFF.toInt(), shape = IconShape.Circle) }
            app.store.updateOverride(music) { it.copy(style = IconStyle.Original, labelColor = 0xFFFFD43B.toInt()) }
            settle()
            shot("step-5-single-apps", backdrop)

            val theme = app.store.current.theme
            assertEquals("quicksand", theme.fontId)
            assertEquals(IconShape.Circle, theme.forApp(app.store.current.overrides[maps]).iconShape)
            assertEquals(IconShape.Teardrop, theme.forApp(app.store.current.overrides[music]).iconShape)
            assertEquals(IconStyle.Original, theme.forApp(app.store.current.overrides[music]).iconStyle)
        }
    }

    @Test
    fun swipeUpOpensTheDrawerAndSearchFilters() {
        launchHome().use {
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = bottom * 0.6f, endY = bottom * 0.2f) }
            compose.waitForIdle()
            compose.onNodeWithText("Search ${TestApps.samples.size} apps").assertExists()
            shot("drawer", backdrop)
            compose.onNode(hasSetTextAction()).performTextInput("ma")
            compose.waitForIdle()
            // The home screen stays composed behind the drawer, so its copies count too.
            compose.onAllNodesWithText("Maps").assertCountEquals(2)
            compose.onAllNodesWithText("Phone").assertCountEquals(0) // dock icons have no labels
        }
    }

    @Test
    fun holdingAnAppOpensItsMenuAndRemoveWorks() {
        launchHome().use {
            compose.onNodeWithText("Maps").performTouchInput { longClick() }
            compose.waitForIdle()
            compose.onNodeWithText("Edit icon and name").assertExists()
            compose.onNodeWithText("Remove from home").performClick()
            compose.waitForIdle()
            val mapsId = appContent("Maps")
            assertNull(app.store.current.items.firstOrNull { it.content == mapsId })
        }
    }

    @Test
    fun widgetPickerAndAppEditorOpen() {
        launchHome().use { scenario ->
            // Hold an empty spot (row 2, between the clock and the apps).
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(Offset(centerX, height * 0.38f)) }
            compose.waitForIdle()
            compose.onNodeWithText("Widgets").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Veneer widgets").assertExists()
            compose.onAllNodesWithText("Ready-made layouts").assertCountEquals(0)
            shot("widget-picker", null)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()

            compose.onNodeWithText("Maps").performTouchInput { longClick() }
            compose.onNodeWithText("Edit icon and name").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Edit app").assertExists()
            shot("edit-app", null)
        }
    }

    @Test
    fun droppingAnAppOnAnotherMakesAFolder() {
        launchHome().use {
            compose.onNodeWithText("Notes").performTouchInput {
                down(center)
                advanceEventTime(800)
                moveBy(Offset(-width * 0.5f, 0f), delayMillis = 32)
                moveBy(Offset(-width * 0.5f, 0f), delayMillis = 32)
                up()
            }
            compose.waitForIdle()
            val folder = app.store.current.items.firstNotNullOf { it.content as? ItemContent.Folder }
            assertEquals(listOf(appContent("Weather"), appContent("Notes")), folder.apps)
            compose.onNodeWithText("Folder").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Hold an app for more. Drop apps on this folder to add them.").assertExists()
            shot("folder", backdrop)
        }
    }

    @Test
    fun stacksAndLiveWidgetsRender() {
        app.store.update { s ->
            val clockId = s.items.first { (it.content as? ItemContent.Builtin)?.kind == BuiltinKind.ClockDate }.id
            var next = LayoutOps.addToStack(s, clockId, ItemContent.Builtin(BuiltinKind.Battery, HomeController.defaultStyle(BuiltinKind.Battery)))!!
            next = LayoutOps.addAt(next, ItemContent.Builtin(BuiltinKind.Media, HomeController.defaultStyle(BuiltinKind.Media)), Spot(0, 0, 2), 4, 1)
            next = LayoutOps.addAt(next, ItemContent.Builtin(BuiltinKind.Agenda, HomeController.defaultStyle(BuiltinKind.Agenda)), Spot(1, 0, 0), 4, 2)
            next
        }
        launchHome().use {
            compose.onNodeWithText("Tap to show what's playing").assertExists()
            shot("widgets", backdrop)
        }
    }

    @Test
    fun searchDoesSums() {
        launchHome().use {
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = bottom * 0.6f, endY = bottom * 0.2f) }
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).performTextInput("12*3+4")
            compose.waitForIdle()
            compose.onNodeWithText("= 40").assertExists()
            shot("drawer-calculator", backdrop)
        }
    }

    @Test
    fun edgeGlowOnUnlock() {
        val wallpaper = WallpaperSpec(WallpaperStyle.Tendrils, 0xFFB80E1B.toInt(), 0xFF3A0509.toInt(), 0xFFFF2233.toInt(), photo = "higanbana")
        app.store.update { s ->
            val clock = s.items.first { (it.content as? ItemContent.Builtin)?.kind == BuiltinKind.ClockDate }
            val c = clock.content as ItemContent.Builtin
            var next = LayoutOps.updateContent(s, clock.id, c.copy(style = c.style.copy(text = "HELLO!")))
            next = LayoutOps.addAt(next, ItemContent.Builtin(BuiltinKind.Battery, HomeController.defaultStyle(BuiltinKind.Battery)), Spot(1, 0, 0), 2, 1)
            next.copy(
                theme = next.theme.copy(
                    iconShape = IconShape.RoundedSquare,
                    iconStyle = IconStyle.Themed,
                    themedFg = 0xFFFF2A3A.toInt(),
                    themedBg = 0xFF150708.toInt(),
                    fontId = "bebas",
                    dockBg = 0xB30C0304.toInt(),
                    accent = 0xFFE3192B.toInt(),
                    edgeGlow = true,
                    edgeGlowColor = 0xFFFF1F33.toInt(),
                ),
            )
        }
        launchHome().use {
            compose.onNodeWithText("HELLO!").assertExists()
            shot("home-red", wallpaper)
            compose.mainClock.autoAdvance = false
            GlowBus.fire(app.store.current.theme.edgeGlowColor!!)
            compose.mainClock.advanceTimeBy(1100)
            shot("home-red-glow", wallpaper)
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun holdAndDragMovesAnApp() {
        launchHome().use {
            val maps = appContent("Maps")
            val before = app.store.current.items.first { it.content == maps }
            assertEquals(1 to 3, before.x to before.y)
            compose.onNodeWithText("Maps").performTouchInput {
                down(center)
                advanceEventTime(800)
                moveBy(Offset(0f, -height * 0.5f), delayMillis = 32)
                moveBy(Offset(0f, -height * 0.6f), delayMillis = 32)
                up()
            }
            compose.waitForIdle()
            val after = app.store.current.items.first { it.content == maps }
            assertEquals(1 to 2, after.x to after.y)
        }
    }

    @Test
    fun customizeScreensOpenAndChangeOneApp() {
        val wallpaper = backdrop
        ActivityScenario.launch(CustomizeActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("Make it yours").assertExists()
            compose.onAllNodesWithText("Quick looks").assertCountEquals(0)
            shot("customize", wallpaper)

            compose.onNodeWithText("Icons").performClick()
            compose.onNodeWithText("Icon pack").assertExists()
            shot("customize-icons", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Fonts").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add a font file"))
            compose.onNodeWithText("Add a font file").assertExists()
            compose.onNode(hasScrollAction()).performScrollToIndex(0)
            shot("customize-fonts", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Wallpaper").performClick()
            compose.onNodeWithText("Choose a picture").assertExists()
            shot("customize-wallpaper", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Colors").performClick()
            compose.onNodeWithText("Accent color").assertExists()
            compose.onNodeWithText("App names and widget text").assertExists()
            shot("customize-colors", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Your apps").performClick()
            compose.onNodeWithText("Maps").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Shape and style").performScrollTo()
            compose.onNodeWithText("Circle").performClick()
            compose.waitForIdle()
            val maps = app.apps.apps.value.first { it.label == "Maps" }.key.id
            assertEquals(IconShape.Circle, app.store.current.overrides[maps]?.shape)
            shot("edit-app-look", null)
        }
    }

    /** The photo, weather, calendar and clock widgets, styled by hand. */
    @Test
    fun newWidgetsRender() {
        // A real photo for the photo widget: one of the bundled wallpapers.
        val file = File(app.cacheDir, "widget-photo.jpg")
        Wallpapers.render(WallpaperSpec(photo = "mirror_sky"), 900, 1200, app).let { bmp ->
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val photo = kotlinx.coroutines.runBlocking { WidgetPhotos.save(app, android.net.Uri.fromFile(file)) }
        assertNotNull(photo)
        app.weather.setForPreview(WeatherNow(18, 21, 12, 2, true, false, "Lisbon", System.currentTimeMillis()))

        fun w(kind: BuiltinKind, tweak: (WidgetStyle) -> WidgetStyle = { it }) = ItemContent.Builtin(kind, tweak(HomeController.defaultStyle(kind)))
        val glass: (WidgetStyle) -> WidgetStyle = { it.copy(background = 0x26FFFFFF, border = true, cornerDp = 28) }
        app.store.update { s ->
            var next = s.copy(items = s.items.filter { it.page == DOCK_PAGE }, pageCount = 2, weather = WeatherSettings(useDeviceLocation = false, fahrenheit = false))
            next = LayoutOps.addAt(next, w(BuiltinKind.Photo) { it.copy(image = photo, cornerDp = 32) }, Spot(0, 0, 0), 4, 3)
            next = LayoutOps.addAt(next, w(BuiltinKind.Weather, glass), Spot(0, 0, 3), 2, 2)
            next = LayoutOps.addAt(next, w(BuiltinKind.MonthCalendar, glass), Spot(0, 2, 3), 2, 2)
            next = LayoutOps.addAt(next, w(BuiltinKind.Search), Spot(0, 0, 5), 4, 1)
            next = LayoutOps.addAt(next, w(BuiltinKind.FlipClock, glass), Spot(1, 0, 0), 4, 2)
            next = LayoutOps.addAt(next, w(BuiltinKind.Glance), Spot(1, 0, 2), 4, 1)
            next = LayoutOps.addAt(next, w(BuiltinKind.AnalogClock), Spot(1, 0, 3), 2, 2)
            next = LayoutOps.addAt(next, w(BuiltinKind.Battery) { it.copy(variant = 1) }, Spot(1, 2, 3), 2, 2)
            next
        }
        launchHome().use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Tap to pick a photo").fetchSemanticsNodes().isEmpty() }
            compose.onAllNodesWithText("Lisbon").onFirst().assertExists()
            shot("widgets-photo-weather", backdrop)
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeLeft() }
            compose.waitForIdle()
            shot("widgets-clocks", backdrop)
        }
    }

    @Test
    fun shuffleImportsPicturesAndMovesToTheNextOne() {
        val pictures = (0 until 3).map { i ->
            val file = File(app.cacheDir, "pick$i.png")
            val bmp = createBitmap(400, 800).apply { eraseColor(listOf(Color.RED, Color.BLACK, Color.WHITE)[i]) }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.net.Uri.fromFile(file)
        }
        val names = kotlinx.coroutines.runBlocking { app.shuffler.import(pictures) }
        assertEquals(3, names.size)
        app.store.update { it.copy(shuffle = it.shuffle.copy(enabled = true, files = names)) }
        assertTrue(kotlinx.coroutines.runBlocking { app.shuffler.next() })
        assertTrue(kotlinx.coroutines.runBlocking { app.shuffler.next() })
        assertEquals(2, app.store.current.shuffle.next)
        assertTrue(app.store.current.shuffle.lastChange > 0)
    }

    @Test
    fun photoWallpapersLoad() {
        for (photo in PhotoWallpapers.all) {
            val bmp = Wallpapers.render(WallpaperSpec(photo = photo.id), 270, 540, app)
            assertEquals(270, bmp.width)
            assertTrue("${photo.id} should be a real picture", Color.alpha(bmp.getPixel(135, 270)) == 255)
        }
    }

    @Test
    fun everyShapeAndStyleRenders() {
        val size = 144
        for (shape in IconShape.entries) for (style in IconStyle.entries) {
            val spec = ThemeConfig(iconShape = shape, iconStyle = style).iconSpec()
            val bmp = IconRenderer.render(TestApps.icon("Maps"), IconSource.App, size, spec)
            assertEquals(size, bmp.width)
            assertTrue("$shape/$style center should be drawn", Color.alpha(bmp.getPixel(size / 2, size / 2)) > 200)
            if (shape == IconShape.Circle) {
                assertEquals("$shape/$style corner should be clear", 0, Color.alpha(bmp.getPixel(2, 2)))
            }
            if (shape == IconShape.Square) {
                assertTrue("square corner should be drawn", Color.alpha(bmp.getPixel(1, 1)) > 200)
            }
        }
        assertNotNull(IconRenderer.render(TestApps.GlyphDrawable("A", Color.BLACK), IconSource.App, size, ThemeConfig().iconSpec()))

        // A sheet of every shape (columns) in every style (rows), saved next to the screenshots.
        val dir = System.getProperty("screenshots.dir") ?: return
        val cell = 180
        val labelW = 200
        val shapes = IconShape.entries
        val styles = IconStyle.entries
        val sheet = createBitmap(labelW + cell * shapes.size, 70 + cell * styles.size)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(Wallpapers.render(WallpaperSpec(), sheet.width, sheet.height), 0f, 0f, null)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        shapes.forEachIndexed { c, shape -> canvas.drawText(shape.label, labelW + c * cell + cell / 2f, 46f, text) }
        text.textAlign = android.graphics.Paint.Align.LEFT
        styles.forEachIndexed { r, style ->
            canvas.drawText(style.label, 24f, 70 + r * cell + cell / 2f + 10, text)
            shapes.forEachIndexed { c, shape ->
                val sample = TestApps.samples[(c + r * 3) % TestApps.samples.size]
                val spec = ThemeConfig(iconShape = shape, iconStyle = style).iconSpec()
                val icon = IconRenderer.render(TestApps.icon(sample.label), IconSource.App, size, spec)
                canvas.drawBitmap(icon, labelW + c * cell + (cell - size) / 2f, 70 + r * cell + (cell - size) / 2f, null)
            }
        }
        File(dir).mkdirs()
        File(dir, "icon-shapes.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
