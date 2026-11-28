package app.veneer

import android.media.AudioManager
import app.veneer.data.BarIcons
import app.veneer.data.GestureAction
import app.veneer.data.IconStyle
import app.veneer.data.LauncherState
import app.veneer.data.ShuffleInterval
import app.veneer.data.ThemeConfig
import app.veneer.ui.customize.describe
import app.veneer.ui.customize.explain
import app.veneer.ui.customize.glowFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** The rules for the labels and the glow the Customize previews show. */
@RunWith(AndroidJUnit4::class)
class PreviewLogicTest {

    private val start = LauncherState(pageCount = 2)

    private fun after(change: (ThemeConfig) -> ThemeConfig) = start.copy(theme = change(start.theme))

    @Test
    fun gesturesSayWhatTheyDo() {
        val note = explain(start, after { it.copy(gestureSwipeUp = GestureAction.QuickSettings) }, emptyList())!!
        assertEquals("Swipe up", note.title)
        assertEquals("Opens quick settings", note.text)
        assertEquals("Double-tap", explain(start, after { it.copy(gestureDoubleTap = GestureAction.LockScreen) }, emptyList())!!.title)
        assertEquals("Opens the app you pick", describe(GestureAction.OpenApp, null))
        assertEquals("Opens Maps", describe(GestureAction.OpenApp, "Maps"))
        assertEquals("Does nothing", describe(GestureAction.None, null))
    }

    @Test
    fun pageDotsWithOnePageAreExplained() {
        val one = start.copy(pageCount = 1, theme = start.theme.copy(pageIndicator = false))
        val note = explain(one, one.copy(theme = one.theme.copy(pageIndicator = true)), emptyList())
        assertEquals("They show once you have a second page", note!!.text)
        // With two pages the dots simply show, so there's nothing to explain.
        assertNull(explain(start.copy(theme = start.theme.copy(pageIndicator = false)), start, emptyList()))
    }

    @Test
    fun aLookThatsOffIsShownForAMomentOnlyWhenItsOwnColorChanged() {
        val themed = explain(start, after { it.copy(themedFg = 0xFFFF0000.toInt()) }, emptyList())!!
        assertEquals(IconStyle.Themed, themed.peek!!(start.theme).iconStyle)
        // Reset changes those colors along with the style: no label then.
        val styled = start.copy(theme = start.theme.copy(iconStyle = IconStyle.Themed, themedFg = 0xFFFF0000.toInt()))
        assertNull(explain(styled, styled.copy(theme = styled.theme.copy(iconStyle = IconStyle.Original, themedFg = ThemeConfig().themedFg)), emptyList()))
        // Dark status bar icons while the bar is hidden: shown with the bar for a moment.
        val hidden = start.copy(theme = start.theme.copy(hideStatusBar = true))
        val bar = explain(hidden, hidden.copy(theme = hidden.theme.copy(statusBarIcons = BarIcons.Dark)), emptyList())!!
        assertEquals(false, bar.peek!!(hidden.theme).hideStatusBar)
    }

    @Test
    fun shuffleChangesAreSpelledOut() {
        val withFiles = start.copy(shuffle = start.shuffle.copy(files = listOf("a.jpg")))
        val note = explain(withFiles, withFiles.copy(shuffle = withFiles.shuffle.copy(interval = ShuffleInterval.Daily)), emptyList())
        assertEquals("A new picture every day", note!!.text)
    }

    @Test
    fun theGlowPlaysWhenTurnedOnOrRecolored() {
        val t = ThemeConfig()
        assertEquals(t.accent, glowFor(t, t.copy(edgeGlow = true)) { AudioManager.RINGER_MODE_NORMAL })
        assertEquals(0xFF00FF00.toInt(), glowFor(t, t.copy(edgeGlowColor = 0xFF00FF00.toInt())) { AudioManager.RINGER_MODE_NORMAL })
        // Sound mode glow plays in the color of the current mode: red for silent.
        assertEquals(0xFFFF3B3B.toInt(), glowFor(t, t.copy(soundModeGlow = true)) { AudioManager.RINGER_MODE_SILENT })
        assertNull(glowFor(t.copy(edgeGlow = true), t.copy(edgeGlow = false)) { AudioManager.RINGER_MODE_NORMAL })
        assertNull(glowFor(t, t.copy(gridColumns = 5)) { AudioManager.RINGER_MODE_NORMAL })
        assertNotNull(glowFor(t, t.copy(edgeGlow = true, edgeGlowColor = 0xFF0000FF.toInt())) { AudioManager.RINGER_MODE_NORMAL })
    }
}
