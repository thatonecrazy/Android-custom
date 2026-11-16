package app.veneer.ui.launcher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.veneer.apps.AppKey
import app.veneer.data.BarIcons
import app.veneer.data.ItemContent
import app.veneer.data.LayoutOps
import app.veneer.system.NotificationDotsService
import app.veneer.ui.common.EdgeGlowOverlay
import app.veneer.ui.common.EdgeGlowTriggers
import app.veneer.ui.common.EditAppScreen
import app.veneer.ui.theme.LauncherTheme

@Composable
fun LauncherRoot(controller: HomeController, actions: LauncherActions) {
    val app = controller.app
    val state by app.store.state.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val loaded by app.apps.loaded.collectAsStateWithLifecycle()
    val importedFonts by app.fonts.imported.collectAsStateWithLifecycle()
    val theme = state.theme
    val font = remember(theme.fontId, importedFonts) { app.fonts.family(theme.fontId) }
    val appMap = remember(apps) { apps.associateBy { it.key.id } }

    // First launch: build a starter home screen once the app list is known.
    LaunchedEffect(loaded, state.firstRunDone) {
        if (loaded && !state.firstRunDone) controller.setupDefaultLayout()
    }

    // Drop shortcuts to apps that were uninstalled, on the home screen and inside folders.
    LaunchedEffect(appMap, loaded) {
        if (!loaded || appMap.isEmpty()) return@LaunchedEffect
        val placed = app.store.current.items.flatMap { item ->
            when (val c = item.content) {
                is ItemContent.App -> listOf(c.id)
                is ItemContent.Folder -> c.apps.map { it.id }
                else -> emptyList()
            }
        }
        val gone = placed.filter { id ->
            if (appMap.containsKey(id)) return@filter false
            val key = AppKey.parse(id) ?: return@filter true
            app.apps.userSerialIsMain(key.userSerial) && !app.apps.isInstalled(key)
        }.toSet()
        if (gone.isNotEmpty()) app.store.update { LayoutOps.removeApps(it, gone) }
    }

    val notificationCounts by NotificationDotsService.counts.collectAsStateWithLifecycle()
    EdgeGlowTriggers(theme)

    // Status bar: hidden or shown, with light or dark icons. The drawer picks icons that suit its background.
    val view = LocalView.current
    val darkIcons = if (controller.drawerOpen) Color(theme.drawerBg).luminance() > 0.5f else theme.statusBarIcons == BarIcons.Dark
    LaunchedEffect(theme.hideStatusBar, darkIcons) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = darkIcons
        bars.isAppearanceLightNavigationBars = darkIcons
        if (theme.hideStatusBar) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            bars.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    // The home screen is the bottom of the stack: back only closes things.
    BackHandler { controller.closeAll() }

    LauncherTheme(theme, font) {
      CompositionLocalProvider(LocalNotificationCounts provides notificationCounts) {
        Box(
            Modifier
                .fillMaxSize()
                .trackDrags(controller),
        ) {
            if (theme.wallpaperDim > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.wallpaperDim.coerceIn(0f, 0.9f))))
            }
            val homeAlpha by animateFloatAsState(if (controller.drawerOpen) 0f else 1f, label = "home")
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                HomeScreen(controller, actions, state, appMap)
            }
            DragGhost(controller, state, appMap)
            AnimatedVisibility(
                visible = controller.drawerOpen,
                enter = slideInVertically { it / 5 } + fadeIn(),
                exit = slideOutVertically { it / 5 } + fadeOut(),
            ) {
                AppDrawer(controller, actions, state, apps)
            }
            FolderPopup(controller, actions, state, appMap)
            if (controller.widgetPickerOpen) WidgetPicker(controller, actions)
            if (controller.homeMenuOpen) HomeMenuSheet(controller, actions)
            controller.editingStyle?.let { WidgetStyleSheet(controller, it) }
            controller.resizingItemId?.let { ResizeDialog(controller, it) }
            EdgeGlowOverlay()
            controller.editingApp?.let { key ->
                val entry = appMap[key.id]
                if (entry != null) {
                    EditAppScreen(entry) { controller.editingApp = null }
                } else {
                    LaunchedEffect(key) { controller.editingApp = null }
                }
            }
        }
      }
    }
}
