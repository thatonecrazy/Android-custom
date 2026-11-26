package app.veneer.ui.customize

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import app.veneer.data.WallpaperSpec
import app.veneer.system.SystemActions
import app.veneer.ui.common.EdgeGlowOverlay
import app.veneer.ui.common.EditAppScreen
import app.veneer.ui.common.WallpaperPreview
import app.veneer.ui.launcher.AppIcon
import app.veneer.ui.launcher.LocalAppLooks
import app.veneer.ui.launcher.AppTile
import app.veneer.ui.launcher.BuiltinWidgetView
import app.veneer.ui.launcher.HomeController
import app.veneer.ui.theme.LauncherTheme
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.SectionColors
import app.veneer.ui.theme.SettingsTheme
import app.veneer.ui.theme.Vivid
import app.veneer.veneer

enum class Section(val id: String, val title: String, val subtitle: String, val icon: ImageVector, val colors: List<Color>) {
    IconsSection("icons", "Icons", "Shape, style, size, packs", Icons.Filled.Category, listOf(Vivid.Orange, Vivid.Coral)),
    Apps("apps", "Your apps", "Name, icon, colors", Icons.Filled.Brush, listOf(Vivid.Amber, Vivid.Orange)),
    Colors("colors", "Colors", "Accent, text, dock, icons", Icons.Filled.Palette, listOf(Vivid.Pink, Vivid.Violet)),
    Fonts("fonts", "Fonts", "13 fonts, or your own", Icons.Filled.TextFields, listOf(Vivid.Teal, Vivid.Sky)),
    HomeLayout("home", "Home screen", "Grid, dock, bars, gestures", Icons.Filled.Home, listOf(Vivid.Blue, Vivid.Violet)),
    Drawer("drawer", "App list", "Columns and labels", Icons.Filled.Apps, listOf(Vivid.Green, Vivid.Teal)),
    Wallpaper("wallpaper", "Wallpaper", "Your photos, gradients", Icons.Filled.Wallpaper, listOf(Vivid.Purple, Vivid.Pink)),
    Island("island", "Island", "A live pill at the top", Icons.Filled.Smartphone, listOf(Vivid.Violet, Vivid.Blue)),
    Backup("backup", "Backup", "Saved looks, restore", Icons.Filled.Backup, listOf(Vivid.Slate, Vivid.Blue)),
    ;

    val color: Color get() = colors.first()
    val brush: Brush get() = Brush.linearGradient(colors)

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Applies a saved look (or the defaults) while keeping this phone's layout choices (grid, dock size, gestures). */
fun ThemeConfig.withLookOf(other: ThemeConfig) = other.copy(
    gridColumns = gridColumns,
    gridRows = gridRows,
    dockEnabled = dockEnabled,
    dockColumns = dockColumns,
    drawerColumns = drawerColumns,
    pageIndicator = pageIndicator,
    gestureSwipeUp = gestureSwipeUp,
    gestureSwipeDown = gestureSwipeDown,
    gestureDoubleTap = gestureDoubleTap,
    gestureApps = gestureApps,
    notificationDots = notificationDots,
)

/** How deep a Customize page is: the menu, a section, or one app inside Your apps. */
private fun depth(page: Pair<String?, String?>): Int = when {
    page.second != null -> 2
    page.first != null -> 1
    else -> 0
}

@Composable
fun CustomizeRoot(initialSection: String?, onFinish: () -> Unit) {
    val app = LocalContext.current.veneer
    val state by app.store.state.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val imported by app.fonts.imported.collectAsStateWithLifecycle()
    val font = remember(state.theme.fontId, imported) { app.fonts.family(state.theme.fontId) }
    var sectionId by rememberSaveable { mutableStateOf(initialSection) }
    var editingAppId by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsTheme(state.theme, font) {
      CompositionLocalProvider(LocalAppLooks provides state.overrides) {
      Box(Modifier.fillMaxSize()) {
        // Pages slide in from the right going deeper and back out to the right going back.
        AnimatedContent(
            targetState = sectionId to editingAppId,
            transitionSpec = {
                val deeper = depth(targetState) >= depth(initialState)
                val enter = slideInHorizontally(tween(320)) { w -> if (deeper) w / 3 else -w / 6 } + fadeIn(tween(240))
                val exit = slideOutHorizontally(tween(320)) { w -> if (deeper) -w / 6 else w / 3 } + fadeOut(tween(180))
                (enter togetherWith exit).apply { targetContentZIndex = if (deeper) 1f else -1f }
            },
            label = "customizePage",
        ) { (shownSectionId, shownAppId) ->
        val section = Section.from(shownSectionId)
        val editing = shownAppId?.let { id -> apps.firstOrNull { it.key.id == id } }
        when {
            editing != null -> SectionColors(Section.Apps.color) { EditAppScreen(editing) { editingAppId = null } }
            section == null -> {
                BackHandler(onBack = onFinish)
                MainMenu(state, apps, onOpen = { sectionId = it.id }, onClose = onFinish)
            }
            else -> {
                val back = { if (initialSection != null && section.id == initialSection) onFinish() else sectionId = null }
                BackHandler(onBack = back)
                SectionColors(section.color) {
                    when (section) {
                        Section.Colors -> ColorsScreen(state, apps, back)
                        Section.IconsSection -> IconsScreen(state, apps, back, openApps = { sectionId = Section.Apps.id })
                        Section.Fonts -> FontsScreen(state, apps, back)
                        Section.HomeLayout -> HomeLayoutScreen(state, apps, back)
                        Section.Drawer -> DrawerScreen(state, apps, back)
                        Section.Wallpaper -> WallpaperScreen(state, apps, back)
                        Section.Island -> IslandScreen(state, apps, back)
                        Section.Apps -> AppsScreen(state, apps, back) { editingAppId = it.key.id }
                        Section.Backup -> BackupScreen(state, back)
                    }
                }
            }
        }
        }
        EdgeGlowOverlay()
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
    section: Section? = null,
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
            shape = if (preview) RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) else RoundedCornerShape(0.dp),
        ) {
            Column(
                Modifier.windowInsetsPadding(
                    if (preview) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime),
                ),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.width(12.dp))
                    if (section != null) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(section.brush),
                            contentAlignment = Alignment.Center,
                        ) { Icon(section.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
    SettingsPage("Make it yours", onClose, state, apps) {
        item {
            Text(
                "Everything here starts plain and is yours to change: each part of the home screen, and each app.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
            )
        }
        if (!isDefault) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Vivid.Violet, Vivid.Pink)))
                        .padding(20.dp),
                ) {
                    Text("Make Veneer your home screen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your icons, fonts and widgets show up once Veneer is your home app. You can switch back any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { (context as? Activity)?.let { SystemActions.requestDefaultLauncher(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Vivid.Violet),
                    ) { Text("Set as home app", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Text(
                "Customize",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp),
            )
        }
        Section.entries.chunked(2).forEach { row ->
            item(key = row.first().id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { s -> SectionTile(s, Section.entries.indexOf(s), Modifier.weight(1f)) { onOpen(s) } }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Android doesn't let apps restyle the system or other apps' screens, so Veneer restyles everything " +
                        "on your home screen: icons, the app list, fonts and widgets. Wallpapers you set here also " +
                        "recolor the system on Android 12 and newer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTile(section: Section, index: Int, modifier: Modifier, onClick: () -> Unit) {
    // Tiles rise into place one after another, and sink a little under your finger.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(420, delayMillis = 45 * index, easing = FastOutSlowInEasing)) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "tilePress")
    Box(
        modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 36.dp.toPx()
                val s = (0.92f + 0.08f * appear.value) * press
                scaleX = s
                scaleY = s
            }
            .height(128.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp), ambientColor = section.color, spotColor = section.color)
            .clip(RoundedCornerShape(28.dp))
            .background(section.brush)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) { Icon(section.icon, contentDescription = null, tint = Color.White) }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(
                section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
