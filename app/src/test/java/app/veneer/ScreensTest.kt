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
import app.veneer.data.WidgetAlign
import app.veneer.data.LauncherState
import app.veneer.data.AppOverride
import app.veneer.data.DOCK_PAGE
import app.veneer.data.IconFinish
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
import app.veneer.system.Release
import app.veneer.system.Updater
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
        app.updater.autoCheck = false // no network in tests
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
            compose.onNodeWithTag("styles").assertExists()
            compose.onNodeWithText("Tinted").assertExists()
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

    /**
     * Six home screens built only from Veneer's own settings, to show what the app can do.
     * These are examples for the README, not presets: the app ships none.
     */
    @Test
    fun exampleSetups() {
        val file = File(app.cacheDir, "example-photo.jpg")
        Wallpapers.render(WallpaperSpec(photo = "mirror_sky"), 900, 1200, app).let { bmp ->
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val photo = kotlinx.coroutines.runBlocking { WidgetPhotos.save(app, android.net.Uri.fromFile(file)) }
        app.weather.setForPreview(WeatherNow(18, 21, 12, 2, true, false, "Lisbon", System.currentTimeMillis()))
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(
            android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, 76)
                .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100),
        )
        ShadowLooper.idleMainLooper()

        fun w(kind: BuiltinKind, tweak: (WidgetStyle) -> WidgetStyle = { it }) = ItemContent.Builtin(kind, tweak(HomeController.defaultStyle(kind)))
        fun LauncherState.widget(content: ItemContent.Builtin, x: Int, y: Int, sx: Int, sy: Int) = LayoutOps.addAt(this, content, Spot(0, x, y), sx, sy)
        fun LauncherState.appRow(y: Int, vararg labels: String): LauncherState {
            var n = this
            labels.forEachIndexed { i, l -> n = LayoutOps.addAt(n, appContent(l), Spot(0, i, y), 1, 1) }
            return n
        }
        fun start(theme: ThemeConfig, dock: List<String> = listOf("Phone", "Messages", "Browser", "Camera")): LauncherState {
            var n = app.store.current.copy(
                items = emptyList(), pageCount = 1, overrides = emptyMap(), theme = theme,
                weather = WeatherSettings(useDeviceLocation = false, fahrenheit = false),
            )
            dock.forEachIndexed { i, l -> n = LayoutOps.addAt(n, appContent(l), Spot(DOCK_PAGE, i, 0), 1, 1) }
            return n
        }
        val id = app.apps.apps.value.associate { it.label to it.key.id }
        val lightGlass: (WidgetStyle) -> WidgetStyle = { it.copy(background = 0x59FFFFFF, border = true, cornerDp = 28) }
        val darkGlass: (WidgetStyle) -> WidgetStyle = { it.copy(background = 0x40000000, border = true, cornerDp = 28) }

        val examples = listOf(
            "minimal" to WallpaperSpec(WallpaperStyle.Solid, 0xFF121316.toInt(), 0xFF121316.toInt()) to {
                start(ThemeConfig(
                    iconShape = IconShape.Circle, iconStyle = IconStyle.Themed, themedFg = 0xFFE6E6E6.toInt(), themedBg = 0xFF26272B.toInt(),
                    showHomeLabels = false, fontId = "inter", labelColor = 0xFFEDEDED.toInt(), labelShadow = false,
                    dockBg = 0x00000000, pageIndicator = false, accent = 0xFFBDBDBD.toInt(),
                ))
                    .widget(w(BuiltinKind.Clock) { it.copy(sizeSp = 104) }, 0, 0, 4, 2)
                    .widget(w(BuiltinKind.Date) { it.copy(sizeSp = 16, uppercase = true) }, 0, 2, 4, 1)
                    .appRow(4, "Calendar", "Maps", "Mail", "Notes")
                    .widget(w(BuiltinKind.Search) { it.copy(background = 0x1AFFFFFF) }, 0, 5, 4, 1)
            },
            "pastel" to WallpaperSpec(WallpaperStyle.Vertical, 0xFFFFD6E5.toInt(), 0xFFD9CCFF.toInt(), 0xFFC5EBFF.toInt()) to {
                start(ThemeConfig(
                    iconShape = IconShape.Teardrop, iconStyle = IconStyle.Themed, themedFg = 0xFFD6336C.toInt(), themedBg = 0xFFFFF0F5.toInt(),
                    fontId = "quicksand", labelBold = true, labelColor = 0xFF5A2440.toInt(), labelShadow = false,
                    dockBg = 0x66FFFFFF, accent = 0xFFE64980.toInt(),
                ))
                    .widget(w(BuiltinKind.Photo) { it.copy(image = photo, cornerDp = 36) }, 0, 0, 4, 2)
                    .widget(w(BuiltinKind.Weather, lightGlass), 0, 2, 2, 2)
                    .widget(w(BuiltinKind.MonthCalendar, lightGlass), 2, 2, 2, 2)
                    .appRow(4, "Photos", "Music", "Notes", "Weather")
                    .widget(w(BuiltinKind.Search) { it.copy(background = 0x80FFFFFF.toInt()) }, 0, 5, 4, 1)
            },
            "neon" to WallpaperSpec(WallpaperStyle.Aurora, 0xFF0A0614.toInt(), 0xFFFF00C8.toInt(), 0xFF00E5FF.toInt()) to {
                start(ThemeConfig(
                    iconShape = IconShape.Hexagon, iconStyle = IconStyle.Vibrant, fontId = "orbitron",
                    labelColor = 0xFF9CFBFF.toInt(), dockBg = 0x3300E5FF, accent = 0xFF00E5FF.toInt(),
                ))
                    .widget(w(BuiltinKind.FlipClock, darkGlass), 0, 0, 4, 2)
                    .widget(w(BuiltinKind.Battery) { it.copy(variant = 1) }, 0, 2, 2, 2)
                    .widget(w(BuiltinKind.Weather, darkGlass), 2, 2, 2, 2)
                    .appRow(4, "Music", "Videos", "Photos", "Store")
                    .appRow(5, "Maps", "Mail", "Files", "Settings")
            },
            "cozy" to WallpaperSpec(WallpaperStyle.Vertical, 0xFFF6EFE3.toInt(), 0xFFE6D6BD.toInt()) to {
                start(ThemeConfig(
                    iconShape = IconShape.RoundedSquare, iconStyle = IconStyle.Themed, themedFg = 0xFF6B4F3A.toInt(), themedBg = 0xFFFBF6EE.toInt(),
                    fontId = "lora", labelColor = 0xFF4A3426.toInt(), labelShadow = false,
                    dockBg = 0x66FFFFFF, accent = 0xFFA0522D.toInt(),
                ))
                    .widget(w(BuiltinKind.Greeting), 0, 0, 4, 1)
                    .widget(w(BuiltinKind.AnalogClock), 0, 1, 2, 2)
                    .widget(w(BuiltinKind.MonthCalendar), 2, 1, 2, 2)
                    .widget(w(BuiltinKind.Glance), 0, 3, 4, 1)
                    .appRow(4, "Notes", "Calendar", "Mail", "Files")
                    .appRow(5, "Photos", "Music", "Maps", "Weather")
            },
            "each-app" to WallpaperSpec(WallpaperStyle.Diagonal, 0xFF1B1B2F.toInt(), 0xFF35355A.toInt()) to {
                val colors = mapOf(
                    "Calendar" to 0xFFFF6B6B, "Maps" to 0xFFFFA94D, "Weather" to 0xFFFCC419, "Notes" to 0xFF69DB7C,
                    "Photos" to 0xFF38D9A9, "Music" to 0xFF4DABF7, "Mail" to 0xFF748FFC, "Files" to 0xFFDA77F2,
                    "Phone" to 0xFF51CF66, "Messages" to 0xFF339AF0, "Browser" to 0xFFFF922B, "Camera" to 0xFFF06595,
                )
                start(ThemeConfig(iconShape = IconShape.Squircle, iconStyle = IconStyle.Themed, fontId = "outfit"))
                    .widget(w(BuiltinKind.ClockDate), 0, 0, 4, 2)
                    .appRow(3, "Calendar", "Maps", "Weather", "Notes")
                    .appRow(4, "Photos", "Music", "Mail", "Files")
                    .widget(w(BuiltinKind.Search), 0, 5, 4, 1)
                    .copy(overrides = colors.mapKeys { id.getValue(it.key) }.mapValues { (_, c) ->
                        AppOverride(iconBg = c.toInt(), iconFg = 0xFFFFFFFF.toInt())
                    } + (id.getValue("Music") to AppOverride(iconBg = 0xFF4DABF7.toInt(), iconFg = 0xFFFFFFFF.toInt(), shape = IconShape.Circle)))
            },
            "retro" to WallpaperSpec(WallpaperStyle.Solid, 0xFF0B140B.toInt(), 0xFF0B140B.toInt()) to {
                start(ThemeConfig(
                    iconShape = IconShape.Square, iconStyle = IconStyle.Themed, themedFg = 0xFF33FF66.toInt(), themedBg = 0xFF10261A.toInt(),
                    fontId = "pixel", labelSizeSp = 8, labelColor = 0xFF33FF66.toInt(), labelShadow = false,
                    dockBg = 0xFF10261A.toInt(), dockCornerDp = 0, accent = 0xFF33FF66.toInt(),
                ))
                    .widget(w(BuiltinKind.Text) { it.copy(text = "> HELLO_", sizeSp = 16, align = WidgetAlign.Start) }, 0, 0, 4, 1)
                    .widget(w(BuiltinKind.Clock) { it.copy(sizeSp = 56) }, 0, 1, 4, 2)
                    .widget(w(BuiltinKind.Battery) { it.copy(sizeSp = 20, background = 0xFF10261A.toInt(), cornerDp = 0) }, 0, 3, 4, 1)
                    .appRow(4, "Notes", "Files", "Settings", "Mail")
                    .appRow(5, "Music", "Maps", "Store", "Weather")
            },
        )
        launchHome().use {
            for ((pair, build) in examples) {
                val (name, wallpaper) = pair
                app.store.update { build() }
                settle()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Tap to pick a photo").fetchSemanticsNodes().isEmpty() }
                settle()
                shot("example-$name", wallpaper)
            }
        }
    }

    /** Glass icons and a glass folder on a dark wallpaper, with purple symbols. */
    @Test
    fun glassHomeScreen() {
        app.store.update { s ->
            var next = s.copy(
                theme = s.theme.copy(
                    iconStyle = IconStyle.Themed, themedFg = 0xFFD54CFF.toInt(), iconFinish = IconFinish.Glass,
                    glassTint = 0xFF232327.toInt(), glassClarity = 0, dockBg = 0x00000000, accent = 0xFFB23CFF.toInt(),
                ),
            )
            val folder = ItemContent.Folder("Social", listOf(appContent("Messages"), appContent("Photos"), appContent("Music"), appContent("Mail")), null)
            next = LayoutOps.addAt(next, folder, Spot(0, 0, 2), 1, 1)
            next
        }
        launchHome().use {
            compose.onNodeWithText("Social").assertExists()
            settle()
            shot("home-glass", WallpaperSpec(WallpaperStyle.Radial, 0xFF6E6E78.toInt(), 0xFF1C1C21.toInt()))
            assertEquals(IconFinish.Glass, app.store.current.theme.iconFinish)
        }
    }

    /** The island's settings: its preview in each state, and turning it on. */
    @Test
    fun islandSettingsPreviewEachState() {
        app.store.update { it.copy(island = it.island.copy(background = 0xFF8A00E6.toInt(), accent = 0xFF3DDC84.toInt())) }
        ActivityScenario.launch(CustomizeActivity::class.java).use {
            compose.waitForIdle()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Island"))
            compose.onNodeWithText("Island").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("islandPreview").assertExists()
            for ((chip, name) in listOf("Resting" to "idle", "Charging" to "charging", "Music" to "music", "Music, open" to "music-open", "Notification" to "notification")) {
                compose.onAllNodesWithText(chip).onFirst().performClick()
                compose.waitForIdle()
                shot("island-$name", null)
            }
            compose.onNodeWithText("Show the island").performClick()
            compose.waitForIdle()
            assertTrue(app.store.current.island.enabled)
            compose.onNodeWithText("Let Veneer draw over the status bar").assertExists()
        }
    }

    /** A ready update shows as a banner at the top of Customize, and Backup has the update settings. */
    @Test
    fun updatesShowInCustomize() {
        val release = Release(
            "9.9", "Update Veneer from inside the app", "https://example.invalid/Veneer-9.9.apk", 3_800_000L, "https://example.invalid",
        )
        app.updater.setStateForPreview(Updater.State.Available(release))
        ActivityScenario.launch(CustomizeActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Veneer 9.9 is ready").assertExists()
            compose.onNodeWithText("Update now").assertExists()
            shot("update-banner", backdrop)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Backup"))
            compose.onNodeWithText("Backup").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Check automatically").assertExists()
            compose.onAllNodesWithText("Veneer 9.9 is ready").assertCountEquals(1)
        }
        app.updater.setStateForPreview(Updater.State.Idle)
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

    /** A sheet of glass icons: several looks (rows) on the same apps (columns). */
    @Test
    fun glassIconsRender() {
        val size = 168
        val looks = listOf(
            "Dark glass, purple symbols" to ThemeConfig(iconStyle = IconStyle.Themed, themedFg = 0xFFD54CFF.toInt(), iconFinish = IconFinish.Glass, glassTint = 0xFF232327.toInt(), glassClarity = 0),
            "Clear glass, white symbols" to ThemeConfig(iconStyle = IconStyle.Themed, themedFg = 0xFFFFFFFF.toInt(), iconFinish = IconFinish.Glass, glassTint = 0xFFFFFFFF.toInt(), glassClarity = 75, glassDepth = 45),
            "Full-color glass" to ThemeConfig(iconStyle = IconStyle.Original, iconFinish = IconFinish.Glass, glassClarity = 10),
            "Circle, pink glass" to ThemeConfig(iconShape = IconShape.Circle, iconStyle = IconStyle.Themed, themedFg = 0xFFFFFFFF.toInt(), iconFinish = IconFinish.Glass, glassTint = 0xFFE64980.toInt(), glassClarity = 20),
        )
        val apps = listOf("Phone", "Messages", "Camera", "Photos", "Music", "Maps")
        for ((_, theme) in looks) {
            val bmp = IconRenderer.render(TestApps.icon("Maps"), IconSource.App, size, theme.iconSpec())
            assertEquals(size, bmp.width)
            assertTrue("glass body should be drawn", Color.alpha(bmp.getPixel(size / 2, size / 2)) > 200)
        }
        val dir = System.getProperty("screenshots.dir") ?: return
        val cell = 200
        val labelH = 56
        val sheet = createBitmap(cell * apps.size + 40, looks.size * (cell + labelH) + 20)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(Wallpapers.render(WallpaperSpec(WallpaperStyle.Diagonal, 0xFF3A3A40.toInt(), 0xFF8A8A92.toInt()), sheet.width, sheet.height), 0f, 0f, null)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        looks.forEachIndexed { r, (label, theme) ->
            val y = 20 + r * (cell + labelH)
            canvas.drawText(label, 24f, y + 36f, text)
            apps.forEachIndexed { c, app ->
                val icon = IconRenderer.render(TestApps.icon(app), IconSource.App, size, theme.iconSpec())
                canvas.drawBitmap(icon, 20f + c * cell + (cell - size) / 2f, y + labelH.toFloat(), null)
            }
        }
        File(dir).mkdirs()
        File(dir, "icon-glass.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Rows of the newer icon options: each row changes one thing, on the same apps. */
    @Test
    fun iconOptionsRender() {
        val size = 150
        val dark = ThemeConfig(iconStyle = IconStyle.Themed, themedFg = 0xFFFFFFFF.toInt(), themedBg = 0xFF2B2B33.toInt())
        val rows = listOf(
            "New shapes" to listOf(IconShape.Pebble, IconShape.Octagon, IconShape.Flower, IconShape.Arch, IconShape.Diamond, IconShape.RoundedSquare)
                .mapIndexed { i, shape -> ThemeConfig(iconShape = shape, iconCorner = if (i == 5) 70 else 44) },
            "Tinted" to List(6) { ThemeConfig(iconStyle = IconStyle.Tinted) },
            "Color symbols on a gradient" to List(6) { ThemeConfig(iconStyle = IconStyle.ColorSymbols, themedBg = 0xFF1B1B2F.toInt(), themedBg2 = 0xFF3A1D5C.toInt()) },
            "Pastel" to List(6) { ThemeConfig(iconStyle = IconStyle.Pastel) },
            "Outline and shadow" to List(6) { ThemeConfig(iconShape = IconShape.Circle, iconOutline = 40, iconShadow = 70) },
            "See-through background" to List(6) { ThemeConfig(iconStyle = IconStyle.Themed, themedFg = 0xFFFFFFFF.toInt(), themedBg = 0xFF000000.toInt(), iconBgOpacity = 35, iconOutline = 25) },
            "Tinted toward purple" to List(6) { ThemeConfig(iconTint = 0xFFB23CFF.toInt(), iconTintStrength = 70) },
            "Bigger symbols" to List(6) { dark.copy(iconScale = 125) },
        )
        val apps = listOf("Phone", "Messages", "Photos", "Music", "Maps", "Weather")
        for ((_, themes) in rows) for ((i, theme) in themes.withIndex()) {
            val bmp = IconRenderer.render(TestApps.icon(apps[i]), IconSource.App, size, theme.iconSpec())
            assertTrue("icon should be drawn", Color.alpha(bmp.getPixel(size / 2, size / 2)) > 0)
        }
        val dir = System.getProperty("screenshots.dir") ?: return
        val cell = 180
        val labelH = 50
        val sheet = createBitmap(cell * apps.size + 40, rows.size * (cell + labelH) + 20)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(Wallpapers.render(WallpaperSpec(WallpaperStyle.Diagonal, 0xFF3B3B6D.toInt(), 0xFF9A6FB0.toInt()), sheet.width, sheet.height), 0f, 0f, null)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f }
        rows.forEachIndexed { r, (label, themes) ->
            val y = 20 + r * (cell + labelH)
            canvas.drawText(label, 24f, y + 32f, text)
            themes.forEachIndexed { c, theme ->
                val icon = IconRenderer.render(TestApps.icon(apps[c]), IconSource.App, size, theme.iconSpec())
                canvas.drawBitmap(icon, 20f + c * cell + (cell - size) / 2f, y + labelH.toFloat(), null)
            }
        }
        File(dir).mkdirs()
        File(dir, "icon-options.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
