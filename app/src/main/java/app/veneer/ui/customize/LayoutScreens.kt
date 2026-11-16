package app.veneer.ui.customize

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.veneer.apps.AppEntry
import app.veneer.data.BarIcons
import app.veneer.data.LauncherState
import app.veneer.data.ThemeConfig
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.FloatSliderRow
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.veneer
import kotlin.math.roundToInt

@Composable
fun HomeLayoutScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val store = LocalContext.current.veneer.store
    val theme = state.theme
    fun update(transform: (ThemeConfig) -> ThemeConfig) = store.updateTheme(transform)

    SettingsPage("Home screen and dock", onBack, state, apps) {
        item { SectionHeader("Grid") }
        item {
            SettingCard {
                IntSliderRow("Columns", theme.gridColumns, 3..8) { v -> update { it.copy(gridColumns = v) } }
                IntSliderRow("Rows", theme.gridRows, 3..10) { v -> update { it.copy(gridRows = v) } }
                SwitchRow("Page dots", theme.pageIndicator, "Shows which page you're on when there's more than one") { b ->
                    update { it.copy(pageIndicator = b) }
                }
            }
        }
        item { NoteText("Making the grid smaller never deletes anything: items that no longer fit move to free space or a new page.") }

        item { SectionHeader("Dock") }
        item {
            SettingCard {
                SwitchRow("Show the dock", theme.dockEnabled) { b -> update { it.copy(dockEnabled = b) } }
                if (theme.dockEnabled) {
                    IntSliderRow("Apps in the dock", theme.dockColumns, 3..7) { v -> update { it.copy(dockColumns = v) } }
                    ColorRow("Dock color", theme.dockBg, "Lower the opacity for glass, or make it fully clear") { c ->
                        update { it.copy(dockBg = c) }
                    }
                    IntSliderRow("Corner roundness", theme.dockCornerDp, 0..48, { "$it dp" }) { v -> update { it.copy(dockCornerDp = v) } }
                }
            }
        }

        item { SectionHeader("Wallpaper") }
        item {
            SettingCard {
                FloatSliderRow("Darken wallpaper", theme.wallpaperDim, 0f..0.7f, { "${(it * 100).roundToInt()}%" }) { v ->
                    update { it.copy(wallpaperDim = v) }
                }
            }
        }

        item { SectionHeader("Status bar") }
        item {
            SettingCard {
                SwitchRow("Hide the status bar", theme.hideStatusBar, "Swipe down from the top edge to see it for a moment") { b ->
                    update { it.copy(hideStatusBar = b) }
                }
                ChoiceRow("Status bar icons", BarIcons.entries, theme.statusBarIcons, { it.label }) { v ->
                    update { it.copy(statusBarIcons = v) }
                }
                NoteText("Pick dark icons for light wallpapers and light icons for dark ones.")
            }
        }

        item { SectionHeader("Gestures") }
        item {
            SettingCard {
                SwitchRow("Swipe down for notifications", theme.swipeDownNotifications, "Swipe down anywhere on the home screen") { b ->
                    update { it.copy(swipeDownNotifications = b) }
                }
                NoteText("Swipe up for the app list. Hold an empty spot for widgets, wallpaper and Customize. Hold an app or widget to move, edit or resize it.")
            }
        }
    }
}

@Composable
fun DrawerScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val store = LocalContext.current.veneer.store
    val theme = state.theme
    fun update(transform: (ThemeConfig) -> ThemeConfig) = store.updateTheme(transform)

    SettingsPage("App list", onBack, state, apps) {
        item { SectionHeader("Layout") }
        item {
            SettingCard {
                IntSliderRow("Columns", theme.drawerColumns, 3..7) { v -> update { it.copy(drawerColumns = v) } }
                SwitchRow("Show app names", theme.showDrawerLabels) { b -> update { it.copy(showDrawerLabels = b) } }
            }
        }
        item { SectionHeader("Colors") }
        item {
            SettingCard {
                ColorRow("Background", theme.drawerBg, "Lower the opacity to see your wallpaper through it") { c ->
                    update { it.copy(drawerBg = c) }
                }
                ColorRow("Text color", theme.drawerTextColor, allowAlpha = false) { c -> update { it.copy(drawerTextColor = c) } }
            }
        }
        item { NoteText("To hide an app, hold it in the app list and pick \"Hide from app list\", or use Individual apps.") }
    }
}
