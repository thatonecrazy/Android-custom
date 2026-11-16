package app.veneer.ui.customize

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.veneer.apps.AppEntry
import app.veneer.data.BarIcons
import app.veneer.data.GestureAction
import app.veneer.data.LauncherState
import app.veneer.data.ThemeConfig
import app.veneer.system.LockService
import app.veneer.system.NotificationDotsService
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.FloatSliderRow
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.launcher.AppIcon
import app.veneer.ui.launcher.rememberNotificationAccess
import app.veneer.veneer
import kotlin.math.roundToInt

@Composable
fun HomeLayoutScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = context.veneer.store
    val theme = state.theme
    fun update(transform: (ThemeConfig) -> ThemeConfig) = store.updateTheme(transform)
    val notificationAccess by rememberNotificationAccess()
    var lockReady by remember { mutableStateOf(LockService.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lockReady = LockService.isEnabled(context) }

    SettingsPage("Home screen", onBack, state, apps, section = Section.HomeLayout) {
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

        item { SectionHeader("Notification dots") }
        item {
            SettingCard {
                SwitchRow(
                    "Show notification dots",
                    theme.notificationDots,
                    "A dot on apps and folders with unread notifications",
                    icon = Icons.Filled.Circle,
                ) { b -> update { it.copy(notificationDots = b) } }
                if (theme.notificationDots && !notificationAccess) {
                    ClickRow(
                        "Allow notification access",
                        "Android only shares notification counts this way. It also powers the Music widget.",
                        icon = Icons.Filled.NotificationsActive,
                    ) { NotificationDotsService.requestAccess(context) }
                }
            }
        }

        item { SectionHeader("Gestures") }
        item {
            SettingCard {
                GestureSetting("Swipe up", "swipeUp", theme.gestureSwipeUp, theme, apps, lockReady) { a -> update { it.copy(gestureSwipeUp = a) } }
                GestureSetting("Swipe down", "swipeDown", theme.gestureSwipeDown, theme, apps, lockReady) { a -> update { it.copy(gestureSwipeDown = a) } }
                GestureSetting("Double-tap", "doubleTap", theme.gestureDoubleTap, theme, apps, lockReady) { a -> update { it.copy(gestureDoubleTap = a) } }
                NoteText("Swipes work anywhere on the home screen; double-tap works on an empty spot. Hold an empty spot for widgets, wallpaper and Customize. Hold an app or widget to move, edit or resize it. Drop an app on another app to make a folder.")
            }
        }
    }
}

/** One gesture: a row of actions to pick from, plus the app to open or the lock permission when needed. */
@Composable
private fun GestureSetting(
    title: String,
    gesture: String,
    current: GestureAction,
    theme: ThemeConfig,
    apps: List<AppEntry>,
    lockReady: Boolean,
    onPick: (GestureAction) -> Unit,
) {
    val context = LocalContext.current
    val store = context.veneer.store
    var pickingApp by remember { mutableStateOf(false) }
    val options = GestureAction.entries.filter { it != GestureAction.LockScreen || LockService.supported }
    ChoiceRow(title, options, current, { it.label }) { action ->
        onPick(action)
        if (action == GestureAction.OpenApp && theme.gestureApps[gesture] == null) pickingApp = true
    }
    if (current == GestureAction.OpenApp) {
        val chosen = theme.gestureApps[gesture]?.let { id -> apps.firstOrNull { it.key.id == id } }
        ClickRow("App to open", chosen?.label ?: "Pick an app", icon = Icons.Filled.Apps) { pickingApp = true }
    }
    if (current == GestureAction.LockScreen && !lockReady) {
        ClickRow(
            "Turn on Veneer screen lock",
            "Android only lets apps lock the screen through an accessibility service. It reads nothing on your screen.",
            icon = Icons.Filled.Lock,
        ) { LockService.openSettings(context) }
    }
    if (pickingApp) {
        AlertDialog(
            onDismissRequest = { pickingApp = false },
            title = { Text("$title opens") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.key.id }) { entry ->
                        ClickRow(entry.label, leading = { AppIcon(entry, null, 36.dp) }) {
                            store.updateTheme { it.copy(gestureApps = it.gestureApps + (gesture to entry.key.id)) }
                            pickingApp = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingApp = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun DrawerScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val store = LocalContext.current.veneer.store
    val theme = state.theme
    fun update(transform: (ThemeConfig) -> ThemeConfig) = store.updateTheme(transform)

    SettingsPage("App list", onBack, state, apps, section = Section.Drawer) {
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
