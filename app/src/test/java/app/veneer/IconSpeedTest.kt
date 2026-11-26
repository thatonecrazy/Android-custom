package app.veneer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veneer.data.IconFinish
import app.veneer.data.IconShape
import app.veneer.data.IconStyle
import app.veneer.data.ThemeConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import kotlin.system.measureTimeMillis

/**
 * Times a burst of restyles (what dragging a slider does) over every sample app at home screen size.
 * Prints the numbers; it's a measurement, not a pass/fail check.
 */
@RunWith(AndroidJUnit4::class)
class IconSpeedTest {

    @Test
    fun restyleBurst() {
        val app = ApplicationProvider.getApplicationContext<VeneerApp>()
        TestApps.install(app)
        app.icons.iconSource = { entry, _ -> TestApps.icon(entry.label) }
        app.apps.refresh()
        val deadline = System.currentTimeMillis() + 10_000
        while (app.apps.apps.value.size < TestApps.samples.size && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        val entries = app.apps.apps.value
        val size = 142 // 54 dp at a typical phone density
        val themes = buildList {
            add(ThemeConfig(iconStyle = IconStyle.Themed, iconFinish = IconFinish.Glass))
            for (shine in 40..70 step 2) add(ThemeConfig(iconStyle = IconStyle.Themed, iconFinish = IconFinish.Glass, glassShine = shine))
            for (shape in IconShape.entries) add(ThemeConfig(iconShape = shape, iconStyle = IconStyle.Original))
            for (px in listOf(132, 137, 142, 147, 152)) add(ThemeConfig(iconSizeDp = px))
        }
        runBlocking {
            // Each kind of change on its own, after a warm-up, in ms per icon.
            suspend fun phase(name: String, list: List<ThemeConfig>, px: (ThemeConfig) -> Int = { size }) {
                entries.map { e -> async { app.icons.appIcon(e, null, list.first().copy(iconShadow = 1), px(list.first())) } }.awaitAll()
                val ms = measureTimeMillis {
                    for (t in list) entries.map { e -> async { app.icons.appIcon(e, null, t, px(t)) } }.awaitAll()
                }
                println("ICON-PHASE $name: ${"%.2f".format(ms.toDouble() / (list.size * entries.size))} ms per icon (${list.size} changes)")
            }
            phase("flat shapes", IconShape.entries.map { ThemeConfig(iconShape = it) })
            phase("themed colors", (0 until 12).map { ThemeConfig(iconStyle = IconStyle.Themed, themedBg = 0xFF101010.toInt() + it * 0x0A0A0A) })
            phase("glass shine", (40..70 step 2).map { ThemeConfig(iconStyle = IconStyle.Themed, iconFinish = IconFinish.Glass, glassShine = it) })
            phase("size steps", (54..70).map { ThemeConfig(iconSizeDp = it) }) { (it.iconSizeDp * 2.625f).toInt() }
            // Warm up the JIT and the class loading.
            entries.map { e -> async { app.icons.appIcon(e, null, themes.first(), size) } }.awaitAll()
            val ms = measureTimeMillis {
                for (t in themes) {
                    val px = if (t.iconSizeDp != 54) t.iconSizeDp else size
                    entries.map { e -> async { app.icons.appIcon(e, null, t, px) } }.awaitAll()
                }
            }
            println("ICON-SPEED: ${themes.size} restyles x ${entries.size} icons = ${themes.size * entries.size} icons in $ms ms (${"%.2f".format(ms.toDouble() / (themes.size * entries.size))} ms per icon)")
        }
    }
}
