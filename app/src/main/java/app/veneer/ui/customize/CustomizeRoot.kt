package app.veneer.ui.customize

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.veneer.apps.AppEntry
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.ThemeConfig
import app.veneer.system.SystemActions
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.EditAppScreen
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SettingCard
import app.veneer.ui.launcher.AppTile
import app.veneer.ui.launcher.BuiltinWidgetView
import app.veneer.ui.launcher.HomeController
import app.veneer.ui.theme.LauncherTheme
import app.veneer.ui.theme.SettingsTheme
import app.veneer.veneer

enum class Section(val id: String, val title: String, val subtitle: String, val icon: ImageVector) {
    Themes("themes", "Themes", "One-tap looks, saved themes, sharing", Icons.Filled.Palette),
    IconsSection("icons", "Icons", "Icon packs, shapes, themed icons, size", Icons.Filled.Category),
    Fonts("fonts", "Fonts and labels", "Bundled fonts, your own font files, label style", Icons.Filled.TextFields),
    HomeLayout("home", "Home screen and dock", "Grid, dock, status bar, gestures", Icons.Filled.Home),
    Drawer("drawer", "App list", "Columns and colors of the app drawer", Icons.AutoMirrored.Filled.ViewList),
    Wallpaper("wallpaper", "Wallpaper and system colors", "Pictures, gradients, Material You colors", Icons.Filled.Wallpaper),
    Apps("apps", "Individual apps", "Change one app's icon or name, hide apps", Icons.Filled.Apps),
    Backup("backup", "Backup and about", "Save or restore everything, licenses", Icons.Filled.Backup),
    ;

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Applies another theme's look while keeping this phone's layout choices (grid, dock size, gestures). */
fun ThemeConfig.withLookOf(other: ThemeConfig) = other.copy(
    gridColumns = gridColumns,
    gridRows = gridRows,
    dockEnabled = dockEnabled,
    dockColumns = dockColumns,
    drawerColumns = drawerColumns,
    pageIndicator = pageIndicator,
    swipeDownNotifications = swipeDownNotifications,
)

@Composable
fun CustomizeRoot(initialSection: String?, onFinish: () -> Unit) {
    val app = LocalContext.current.veneer
    val state by app.store.state.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val imported by app.fonts.imported.collectAsStateWithLifecycle()
    val font = remember(state.theme.fontId, imported) { app.fonts.family(state.theme.fontId) }
    var sectionId by rememberSaveable { mutableStateOf(initialSection) }
    var editingAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val section = Section.from(sectionId)

    SettingsTheme(state.theme, font) {
        val editing = editingAppId?.let { id -> apps.firstOrNull { it.key.id == id } }
        when {
            editing != null -> EditAppScreen(editing) { editingAppId = null }
            section == null -> {
                BackHandler(onBack = onFinish)
                MainMenu(state, apps, onOpen = { sectionId = it.id }, onClose = onFinish)
            }
            else -> {
                val back = { if (initialSection != null && section.id == initialSection) onFinish() else sectionId = null }
                BackHandler(onBack = back)
                when (section) {
                    Section.Themes -> ThemesScreen(state, apps, back)
                    Section.IconsSection -> IconsScreen(state, apps, back, openApps = { sectionId = Section.Apps.id })
                    Section.Fonts -> FontsScreen(state, apps, back)
                    Section.HomeLayout -> HomeLayoutScreen(state, apps, back)
                    Section.Drawer -> DrawerScreen(state, apps, back)
                    Section.Wallpaper -> WallpaperScreen(state, apps, back)
                    Section.Apps -> AppsScreen(state, apps, back) { editingAppId = it.key.id }
                    Section.Backup -> BackupScreen(state, back)
                }
            }
        }
    }
}

/**
 * The frame of every Customize page. With [preview], the top of the page is a see-through window
 * onto the real wallpaper with a live sample of the home screen.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    state: LauncherState,
    apps: List<AppEntry>,
    preview: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (preview) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            ThemePreview(state, apps, Modifier.fillMaxWidth().height(200.dp))
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = if (preview) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(0.dp),
        ) {
            Column(
                Modifier.windowInsetsPadding(
                    if (preview) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime),
                ),
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.fillMaxSize(), content = {
                    content()
                    item { Spacer(Modifier.height(32.dp)) }
                })
            }
        }
    }
}

/** A small live copy of the home screen: a clock, a row of apps and the dock, all in the current theme. */
@Composable
fun ThemePreview(state: LauncherState, apps: List<AppEntry>, modifier: Modifier = Modifier, theme: ThemeConfig = state.theme) {
    val app = LocalContext.current.veneer
    val font = remember(theme.fontId) { app.fonts.family(theme.fontId) }
    val byId = remember(apps) { apps.associateBy { it.key.id } }
    val dockApps = remember(state.items, byId) {
        state.items.filter { it.page == DOCK_PAGE }.sortedBy { it.x }
            .mapNotNull { (it.content as? ItemContent.App)?.let { c -> byId["${c.component}#${c.user}"] } }
    }
    val sample = remember(apps, dockApps) {
        val visible = apps.filter { state.overrides[it.key.id]?.hidden != true && it !in dockApps }
        visible.take(4) to (dockApps.ifEmpty { visible.drop(4) }).take(theme.dockColumns.coerceAtMost(5))
    }
    LauncherTheme(theme, font) {
        Box(modifier.background(Color.Black.copy(alpha = theme.wallpaperDim))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    BuiltinWidgetView(BuiltinKind.ClockDate, HomeController.defaultStyle(BuiltinKind.ClockDate).copy(sizeSp = 40))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    sample.first.forEach { e ->
                        val o = state.overrides[e.key.id]
                        AppTile(
                            e, o?.label ?: e.label, o?.icon, theme.iconSizeDp.coerceAtMost(52).dp,
                            showLabel = theme.showHomeLabels, labelColor = Color(theme.labelColor),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (theme.dockEnabled) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.dockCornerDp.dp))
                            .background(Color(theme.dockBg))
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        sample.second.forEach { e ->
                            AppTile(e, "", state.overrides[e.key.id]?.icon, 40.dp, showLabel = false, labelColor = Color.White, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainMenu(state: LauncherState, apps: List<AppEntry>, onOpen: (Section) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(SystemActions.isDefaultLauncher(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = SystemActions.isDefaultLauncher(context) }
    SettingsPage("Customize", onClose, state, apps) {
        if (!isDefault) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Make Veneer your home screen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your icons, fonts and widgets show up once Veneer is your home app. You can switch back any time in your phone's settings.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { (context as? Activity)?.let { SystemActions.requestDefaultLauncher(it) } }) {
                            Text("Set as home app")
                        }
                    }
                }
            }
        }
        item {
            SettingCard {
                Section.entries.forEach { s ->
                    ClickRow(
                        title = s.title,
                        subtitle = s.subtitle,
                        leading = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        },
                        onClick = { onOpen(s) },
                    )
                }
            }
        }
        item {
            NoteText(
                "Android doesn't let any app restyle the system itself or other apps' screens. Veneer restyles " +
                    "everything you see on the home screen: every app icon, the app list, fonts and widgets. Wallpapers " +
                    "you set here also recolor the system on Android 12 and newer.",
            )
        }
    }
}
