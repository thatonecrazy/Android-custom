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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import app.veneer.data.Presets
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.icons.IconRenderer
import app.veneer.icons.IconSource
import app.veneer.icons.iconSpec
import app.veneer.system.Wallpapers
import app.veneer.ui.customize.withLookOf
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
        if (wallpaper != null) canvas.drawBitmap(Wallpapers.render(wallpaper, image.width, image.height), 0f, 0f, null)
        else canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(image, 0f, 0f, null)
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
            shot("home-default", Presets.all.first().wallpaper)
        }
    }

    @Test
    fun presetsRestyleTheHomeScreen() {
        launchHome().use {
            for (name in Presets.all.drop(1).map { it.name }) {
                val preset = Presets.all.first { it.name == name }
                app.store.updateTheme { it.withLookOf(preset) }
                compose.waitForIdle()
                assertEquals(preset.fontId, app.store.current.theme.fontId)
                assertEquals(4, app.store.current.theme.gridColumns)
                shot("home-${name.lowercase().replace(' ', '-')}", preset.wallpaper)
            }
        }
    }

    @Test
    fun swipeUpOpensTheDrawerAndSearchFilters() {
        launchHome().use {
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = bottom * 0.6f, endY = bottom * 0.2f) }
            compose.waitForIdle()
            compose.onNodeWithText("Search ${TestApps.samples.size} apps").assertExists()
            shot("drawer", Presets.all.first().wallpaper)
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
    fun customizeScreensOpenAndApplyAPreset() {
        val wallpaper = Presets.all.first().wallpaper
        ActivityScenario.launch(CustomizeActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("Customize").assertExists()
            shot("customize", wallpaper)

            compose.onNodeWithText("Icons").performClick()
            compose.onNodeWithText("Icon pack").assertExists()
            shot("customize-icons", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Fonts and labels").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add a font file"))
            compose.onNodeWithText("Add a font file").assertExists()
            compose.onNode(hasScrollAction()).performScrollToIndex(0)
            shot("customize-fonts", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Wallpaper and system colors").performClick()
            compose.onNodeWithText("Make a gradient").assertExists()
            shot("customize-wallpaper", wallpaper)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("Themes").performClick()
            compose.onNodeWithText("Ready-made looks").assertExists()
            shot("customize-themes", wallpaper)
            compose.onNode(hasScrollAction() and hasAnyChild(hasText("Minimal Mono")))
                .performScrollToIndex(Presets.all.indexOfFirst { it.name == "Neon" })
            compose.onNodeWithText("Neon").performClick()
            compose.onNodeWithText("Use \"Neon\"?").assertExists()
            compose.onNodeWithText("Also set its wallpaper (home and lock screen)").performClick() // untick
            compose.onNodeWithText("Apply").performClick()
            compose.waitForIdle()
            assertEquals("orbitron", app.store.current.theme.fontId)
            assertEquals(IconShape.Hexagon, app.store.current.theme.iconShape)
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
