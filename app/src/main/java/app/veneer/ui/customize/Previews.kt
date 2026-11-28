package app.veneer.ui.customize

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.veneer.apps.AppEntry
import app.veneer.data.BarIcons
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.GestureAction
import app.veneer.data.HomeItem
import app.veneer.data.IconStyle
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.ShuffleInterval
import app.veneer.data.ShuffleTarget
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.icons.IconHints
import app.veneer.icons.IconProvider
import app.veneer.icons.IconRenderer
import app.veneer.icons.IconSource
import app.veneer.icons.iconSpec
import app.veneer.system.CurrentWallpaper
import app.veneer.system.Wallpapers
import app.veneer.ui.common.GlowPlayer
import app.veneer.ui.common.drawEdgeGlow
import app.veneer.ui.common.soundModeColor
import app.veneer.ui.launcher.AppTile
import app.veneer.ui.launcher.BuiltinWidgetView
import app.veneer.ui.launcher.CellGrid
import app.veneer.ui.launcher.FolderTile
import app.veneer.ui.launcher.GridPadding
import app.veneer.ui.launcher.HomeController
import app.veneer.ui.launcher.IconLabel
import app.veneer.ui.launcher.LocalNotificationCounts
import app.veneer.ui.launcher.PageIndicator
import app.veneer.ui.launcher.cell
import app.veneer.ui.launcher.dockIconSize
import app.veneer.ui.launcher.homeIconSize
import app.veneer.ui.launcher.iconShapeOf
import app.veneer.ui.theme.LauncherTheme
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.ui.theme.LocalThemeFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Date
import kotlin.math.roundToInt

/** What the preview at the top of a Customize page shows. */
enum class PreviewKind {
    /** A close-up of the home screen at its real size: the clock, a row of apps, page dots and the dock. */
    Home,

    /** The close-up next to a piece of the app list, for pages that change both. */
    HomeAndAppList,

    /** The whole home screen made small: wallpaper, status bar, the first page, page dots and the dock. */
    MiniHome,

    /** The whole app list made small. */
    MiniAppList,
}

/**
 * The preview of one Customize page. [folder] and [oldStyle] add a folder and an icon from an older
 * app to the close-up, for pages with settings only those two show.
 */
data class PagePreview(val kind: PreviewKind, val folder: Boolean = false, val oldStyle: Boolean = false) {
    val showsAppList: Boolean get() = kind == PreviewKind.MiniAppList
}

// --- The frame at the top of a page ------------------------------------------------------------

/** How tall the preview is: the small whole-screen copies get a little more room than the close-up. */
@Composable
internal fun previewHeight(kind: PreviewKind): Dp {
    val screen = screenSize()
    return when (kind) {
        PreviewKind.MiniHome, PreviewKind.MiniAppList -> (screen.height * 0.36f).coerceIn(220.dp, 300.dp)
        else -> 200.dp
    }
}

/**
 * The preview of a page: a live copy of the home screen or app list in the current settings.
 * Changes a still picture can't show (the edge glow, gestures, schedules) play or show a short
 * label here. Tapping it calls [onOpen] to show it full screen.
 */
@Composable
internal fun PreviewFrame(
    preview: PagePreview,
    state: LauncherState,
    apps: List<AppEntry>,
    tryingOn: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes = remember { PreviewNotes() }
    val glow = remember { GlowPlayer() }
    WatchForChanges(state, apps, notes, glow)
    val peek = notes.note?.peek
    val theme = peek?.invoke(state.theme) ?: state.theme
    val shown = if (theme == state.theme) state else state.copy(theme = theme)
    val font = LocalThemeFont.current
    val mini = preview.kind == PreviewKind.MiniHome || preview.kind == PreviewKind.MiniAppList
    val height = previewHeight(preview.kind)
    val frame = if (mini) {
        Modifier.height(height)
    } else {
        // The close-up is as tall as its icons need, so big icons are never cut off.
        Modifier
            .heightIn(min = height)
            .animateContentSize()
            .background(Color.Black.copy(alpha = theme.wallpaperDim.coerceIn(0f, 0.9f)))
            .drawWithContent {
                drawContent()
                drawEdgeGlow(glow, 24.dp.toPx(), widthScale = 0.7f)
            }
    }

    Box(modifier.then(frame).testTag("preview")) {
        LauncherTheme(theme, font) {
            Box(Modifier.fillMaxWidth().then(if (mini) Modifier.fillMaxHeight() else Modifier).clearAndSetSemantics { }) {
                when (preview.kind) {
                    PreviewKind.Home -> CloseUp(shown, apps, preview, columns = 4, modifier = Modifier.fillMaxWidth())
                    PreviewKind.HomeAndAppList -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        CloseUp(shown, apps, preview, columns = 3, modifier = Modifier.weight(0.62f))
                        AppListSample(shown, apps, Modifier.weight(0.38f).padding(top = 8.dp, bottom = 8.dp, end = 10.dp))
                    }
                    PreviewKind.MiniHome, PreviewKind.MiniAppList -> MiniPhone(glow, Modifier.fillMaxSize()) { picture, bars ->
                        if (preview.kind == PreviewKind.MiniHome) HomeReplica(shown, apps, picture, bars)
                        else AppListReplica(shown, apps, picture, bars)
                    }
                }
            }
        }
        // Over everything, so nothing inside the preview reacts to a tap.
        Box(
            Modifier
                .matchParentSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                .semantics {
                    contentDescription = "Preview. Tap to see it full screen"
                    role = Role.Button
                },
        )
        if (tryingOn) {
            Text(
                "Your gradient, not set yet",
                color = Color.White,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("tryingOn"),
            )
        }
        NoteView(notes, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 16.dp, end = 16.dp))
    }
}

/** Shows the home screen (or the app list on the App list page) at full size until it's tapped again. */
@Composable
internal fun FullScreenPreview(preview: PagePreview, state: LauncherState, apps: List<AppEntry>, onClose: () -> Unit) {
    val font = LocalThemeFont.current
    val theme = state.theme
    BarsLikeHome(theme, appList = preview.showsAppList)
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2500)
        hint = false
    }
    Box(Modifier.fillMaxSize().testTag("fullPreview")) {
        LauncherTheme(theme, font) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                if (preview.showsAppList) AppListReplica(state, apps, null, Bars.Real)
                else HomeReplica(state, apps, null, Bars.Real)
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .semantics {
                    contentDescription = "Full screen preview. Tap to go back"
                    role = Role.Button
                },
        )
        AnimatedVisibility(
            hint,
            enter = fadeIn(),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                "Tap anywhere to go back",
                color = Color.White,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** While the full-screen preview is up, the phone's own status bar looks the way it does at home. */
@Composable
private fun BarsLikeHome(theme: ThemeConfig, appList: Boolean) {
    val view = LocalView.current
    val dark = if (appList) Color(theme.drawerBg).luminance() > 0.5f else theme.statusBarIcons == BarIcons.Dark
    DisposableEffect(theme.hideStatusBar, dark) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val bars = WindowCompat.getInsetsController(window, view)
        val wasLight = bars.isAppearanceLightStatusBars
        val wasLightNav = bars.isAppearanceLightNavigationBars
        val oldBehavior = bars.systemBarsBehavior
        bars.isAppearanceLightStatusBars = dark
        bars.isAppearanceLightNavigationBars = dark
        if (theme.hideStatusBar) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            bars.isAppearanceLightStatusBars = wasLight
            bars.isAppearanceLightNavigationBars = wasLightNav
            bars.show(WindowInsetsCompat.Type.statusBars())
            bars.systemBarsBehavior = oldBehavior
        }
    }
}

/** Hides the phone's status bar while this is on screen, for full-screen previews of what sits over it. */
@Composable
internal fun StatusBarHidden() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val bars = WindowCompat.getInsetsController(window, view)
        val oldBehavior = bars.systemBarsBehavior
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            bars.show(WindowInsetsCompat.Type.statusBars())
            bars.systemBarsBehavior = oldBehavior
        }
    }
}

/** A wallpaper being made on the Wallpaper page, drawn behind the whole page as if it were set. */
@Composable
internal fun TryOnBackdrop(spec: WallpaperSpec) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = (constraints.maxWidth / 2).coerceAtLeast(1)
        val h = (constraints.maxHeight / 2).coerceAtLeast(1)
        val picture by produceState<ImageBitmap?>(null, spec, w, h) {
            value = withContext(Dispatchers.Default) { Wallpapers.render(spec, w, h, context).asImageBitmap() }
        }
        picture?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// --- Labels for what a still picture can't show ------------------------------------------------

/** A short label over the preview. [peek] changes how the preview looks while the label is up. */
internal data class PreviewNote(
    val title: String,
    val text: String,
    val icon: ImageVector,
    val peek: ((ThemeConfig) -> ThemeConfig)? = null,
)

@Stable
internal class PreviewNotes {
    var note by mutableStateOf<PreviewNote?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set

    fun show(n: PreviewNote) {
        note = n
        serial++
    }

    fun clear() {
        note = null
    }
}

@Composable
internal fun NoteView(notes: PreviewNotes, modifier: Modifier) {
    // Each label stays up for 2 seconds; a new one starts the 2 seconds again.
    LaunchedEffect(notes.serial) {
        if (notes.note == null) return@LaunchedEffect
        delay(2000)
        notes.clear()
    }
    var last by remember { mutableStateOf<PreviewNote?>(null) }
    notes.note?.let { last = it }
    AnimatedVisibility(
        visible = notes.note != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.9f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.95f),
        modifier = modifier,
    ) {
        val n = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6101216))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("previewNote"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(n.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(n.title, color = Color.White, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Text(n.text, color = Color.White.copy(alpha = 0.85f), style = TextStyle(fontSize = 13.sp))
            }
        }
    }
}

/** Watches the settings and explains or plays the changes a still picture can't show. */
@Composable
private fun WatchForChanges(state: LauncherState, apps: List<AppEntry>, notes: PreviewNotes, glow: GlowPlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val last = remember { arrayOf(state) }
    LaunchedEffect(state) {
        val old = last[0]
        last[0] = state
        if (old === state) return@LaunchedEffect
        explain(old, state, apps)?.let { notes.show(it) }
        glowFor(old.theme, state.theme) { ringerMode(context) }?.let { c ->
            if (glow.playing) glow.color = Color(c) else scope.launch { glow.play(Color(c)) }
        }
    }
}

private fun ringerMode(context: Context): Int =
    runCatching { context.getSystemService(AudioManager::class.java).ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)

/** The color the preview glows in after this change, or null when the edge glow wasn't changed. */
internal fun glowFor(old: ThemeConfig, new: ThemeConfig, ringer: () -> Int): Int? {
    val own = new.edgeGlowColor ?: new.accent
    return when {
        new.soundModeGlow && !old.soundModeGlow -> soundModeColor(ringer(), own)
        new.edgeGlow && !old.edgeGlow -> own
        new.edgeGlowColor != old.edgeGlowColor -> own
        else -> null
    }
}

private data class Gesture(val id: String, val title: String, val icon: ImageVector, val action: (ThemeConfig) -> GestureAction)

private val gestures = listOf(
    Gesture("swipeUp", "Swipe up", Icons.Filled.KeyboardDoubleArrowUp) { it.gestureSwipeUp },
    Gesture("swipeDown", "Swipe down", Icons.Filled.KeyboardDoubleArrowDown) { it.gestureSwipeDown },
    Gesture("doubleTap", "Double-tap", Icons.Filled.TouchApp) { it.gestureDoubleTap },
)

/** What a home screen gesture does, in a few words. */
internal fun describe(action: GestureAction, appName: String?): String = when (action) {
    GestureAction.None -> "Does nothing"
    GestureAction.AppList -> "Opens the app list"
    GestureAction.Search -> "Opens the app list, ready to search"
    GestureAction.Notifications -> "Pulls down your notifications"
    GestureAction.QuickSettings -> "Opens quick settings"
    GestureAction.LockScreen -> "Locks the screen"
    GestureAction.Customize -> "Opens Customize"
    GestureAction.OpenApp -> appName?.let { "Opens $it" } ?: "Opens the app you pick"
}

private fun every(interval: ShuffleInterval) = when (interval) {
    ShuffleInterval.EveryLock -> "each time you lock the phone"
    ShuffleInterval.Hourly -> "every hour"
    ShuffleInterval.Daily -> "every day"
}

private fun where(target: ShuffleTarget) = when (target) {
    ShuffleTarget.Both -> "Changes the home and lock screen"
    ShuffleTarget.Home -> "Changes the home screen only"
    ShuffleTarget.Lock -> "Changes the lock screen only; the home screen stays"
}

/** A label for a change the preview can't draw, or that only shows in a mode that's off. */
internal fun explain(old: LauncherState, new: LauncherState, apps: List<AppEntry>): PreviewNote? {
    val o = old.theme
    val t = new.theme
    for (g in gestures) {
        val action = g.action(t)
        val app = t.gestureApps[g.id]
        if (action != g.action(o) || (action == GestureAction.OpenApp && app != o.gestureApps[g.id])) {
            val name = app?.let { id -> apps.firstOrNull { it.key.id == id } }?.let { new.overrides[it.key.id]?.label ?: it.label }
            return PreviewNote(g.title, describe(action, name), g.icon)
        }
    }
    if (t.soundModeGlow && !o.soundModeGlow) return PreviewNote("Sound mode glow", "Green for sound, blue for vibrate, red for silent", Icons.Filled.Lightbulb)
    if (t.edgeGlow && !o.edgeGlow) return PreviewNote("Edge glow", "Plays like this each time you unlock", Icons.Filled.Lightbulb)
    if (t.pageIndicator && !o.pageIndicator && new.pageCount < 2) {
        return PreviewNote("Page dots", "They show once you have a second page", Icons.Filled.Circle)
    }
    // The labels below only show a look that's off when that one color or choice changed on its own,
    // not when a reset or a whole new look changed it along with everything else.
    if (t.statusBarIcons != o.statusBarIcons && t.hideStatusBar && o.hideStatusBar) {
        return PreviewNote(
            "Status bar",
            "Hidden for now. When you swipe it down it has ${t.statusBarIcons.label.lowercase()}",
            Icons.Filled.Smartphone,
        ) { it.copy(hideStatusBar = false) }
    }
    if (!t.notificationDots && !o.notificationDots && (t.dotColor != o.dotColor || t.dotCount != o.dotCount)) {
        return PreviewNote("Notification dots", "Dots are off. This is how they look when on", Icons.Filled.Circle) { it.copy(notificationDots = true) }
    }
    val themed = t.iconStyle == IconStyle.Themed
    val bgUsed = themed || t.iconStyle == IconStyle.ColorSymbols
    if (t.iconStyle == o.iconStyle && ((t.themedFg != o.themedFg && !themed) || (t.themedBg != o.themedBg && !bgUsed))) {
        return PreviewNote("Themed icons", "These colors are for the Themed style, shown for a moment", Icons.Filled.Palette) {
            it.copy(iconStyle = IconStyle.Themed)
        }
    }
    if (t.legacyBg != o.legacyBg && !t.wrapLegacyIcons && !o.wrapLegacyIcons) {
        return PreviewNote("Old-style icons", "Used when \"Give old-style icons a background\" is on in Icons", Icons.Filled.Apps) {
            it.copy(wrapLegacyIcons = true)
        }
    }
    val accentShows = t.pageIndicator || (t.notificationDots && t.dotColor == null)
    if (t.accent != o.accent && !accentShows) {
        return PreviewNote("Accent color", "Used for menus, highlights and weather icons", Icons.Filled.Palette)
    }
    val w = new.weather
    val ow = old.weather
    if (w.fahrenheit != ow.fahrenheit) {
        val units = when (w.fahrenheit) { null -> "the usual units where you are"; true -> "°F"; false -> "°C" }
        return PreviewNote("Weather", "Weather widgets show $units", Icons.Filled.Cloud)
    }
    if (w.useDeviceLocation != ow.useDeviceLocation || w.place != ow.place) {
        val place = if (w.useDeviceLocation || w.place == null) "near you" else "in ${w.place}"
        return PreviewNote("Weather", "Weather widgets show the weather $place", Icons.Filled.Cloud)
    }
    val s = new.shuffle
    val os = old.shuffle
    if (s.enabled != os.enabled && s.files.isNotEmpty()) {
        return PreviewNote("Shuffle", if (s.enabled) "A new picture ${every(s.interval)}" else "Off: the wallpaper stays as it is", Icons.Filled.Shuffle)
    }
    if (s.interval != os.interval) return PreviewNote("Shuffle", "A new picture ${every(s.interval)}", Icons.Filled.Shuffle)
    if (s.target != os.target) return PreviewNote("Shuffle", where(s.target), Icons.Filled.Shuffle)
    if (s.files.size > os.files.size) {
        val added = s.files.size - os.files.size
        return PreviewNote("Shuffle", "$added new picture${if (added == 1) "" else "s"}, shown ${every(s.interval)}", Icons.Filled.AutoAwesome)
    }
    return null
}

// --- The close-up ------------------------------------------------------------------------------

/** Notification counts for a couple of sample apps, so the dots show in every preview. */
private fun sampleCounts(entries: List<AppEntry?>): Map<String, Int> =
    entries.filterNotNull().take(2).mapIndexed { i, e -> "${e.packageName}#${e.key.userSerial}" to (if (i == 0) 3 else 12) }.toMap()

private fun visibleApps(state: LauncherState, apps: List<AppEntry>): List<AppEntry> {
    val collator = Collator.getInstance()
    return apps.filter { state.overrides[it.key.id]?.hidden != true }
        .sortedWith { a, b -> collator.compare(state.overrides[a.key.id]?.label ?: a.label, state.overrides[b.key.id]?.label ?: b.label) }
}

/**
 * The home screen up close at its real size, over the real wallpaper behind the window: a clock,
 * a row of apps, page dots and the dock. Icon size, shape, style, finish, outline, shadow, tint,
 * dots, fonts and colors all show as they will at home.
 */
@Composable
private fun CloseUp(state: LauncherState, apps: List<AppEntry>, preview: PagePreview, columns: Int, modifier: Modifier) {
    val theme = state.theme
    val byId = remember(apps) { apps.associateBy { it.key.id } }
    val dockApps = remember(state.items, byId) {
        state.items.filter { it.page == DOCK_PAGE }.sortedBy { it.x }
            .mapNotNull { (it.content as? ItemContent.App)?.let { c -> byId[c.id] } }
    }
    val visible = remember(apps, state.overrides) { visibleApps(state, apps) }
    val folder = remember(state.items, visible) {
        if (!preview.folder) null
        else state.items.firstNotNullOfOrNull { it.content as? ItemContent.Folder }
            ?: ItemContent.Folder("Folder", visible.takeLast(4).map { ItemContent.App(it.key.component.flattenToString(), it.key.userSerial) })
    }
    val slots = columns - (if (folder != null) 1 else 0) - (if (preview.oldStyle) 1 else 0)
    // Your own first page's apps come first, so the close-up looks like your home screen.
    val firstPage = remember(state.items, byId) {
        state.items.filter { it.page == 0 }.sortedWith(compareBy({ it.y }, { it.x }))
            .mapNotNull { (it.content as? ItemContent.App)?.let { c -> byId[c.id] } }
    }
    val row = remember(firstPage, visible, dockApps, slots) {
        (firstPage + visible).distinct().filter { it !in dockApps }.ifEmpty { visible }.take(slots.coerceAtLeast(1))
    }
    val dock = remember(dockApps, visible, row) { dockApps.ifEmpty { visible.filter { it !in row } } }
    val counts = remember(row) { sampleCounts(row) }

    CompositionLocalProvider(LocalNotificationCounts provides counts) {
        Box(modifier) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    BuiltinWidgetView(BuiltinKind.ClockDate, HomeController.defaultStyle(BuiltinKind.ClockDate).copy(sizeSp = 34))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                    row.forEach { e ->
                        val o = state.overrides[e.key.id]
                        AppTile(
                            e, o?.label ?: e.label, o?.icon, theme.iconSizeDp.dp,
                            showLabel = theme.showHomeLabels, labelColor = Color(o?.labelColor ?: theme.labelColor),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (folder != null) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            FolderTile(folder, byId, state, theme.iconSizeDp.dp, theme.showHomeLabels)
                        }
                    }
                    if (preview.oldStyle) OldStyleTile(theme.iconSizeDp.dp, theme.showHomeLabels, Color(theme.labelColor), Modifier.weight(1f))
                }
                if (theme.pageIndicator) PageIndicator(maxOf(2, state.pageCount), 0, Color(theme.accent))
                if (theme.dockEnabled) {
                    val size = dockIconSize(theme)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.dockCornerDp.dp))
                            .background(Color(theme.dockBg))
                            .padding(vertical = 8.dp),
                    ) {
                        for (col in 0 until theme.dockColumns) {
                            Box(Modifier.weight(1f).height(size + 4.dp), contentAlignment = Alignment.Center) {
                                val e = dock.getOrNull(col)
                                if (e != null) AppTile(e, "", state.overrides[e.key.id]?.icon, size, showLabel = false, labelColor = Color.White)
                                else EmptySlot(theme, size)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A piece of the app list in its own colors and font, next to the close-up. */
@Composable
private fun AppListSample(state: LauncherState, apps: List<AppEntry>, modifier: Modifier) {
    val theme = state.theme
    val fg = Color(theme.drawerTextColor)
    val font = LocalThemeFont.current
    val sample = remember(apps, state.overrides) { visibleApps(state, apps).take(4) }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(theme.drawerBg))
            .padding(10.dp),
    ) {
        Text("App list", color = fg.copy(alpha = 0.6f), style = TextStyle(fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(fg.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Search", color = fg.copy(alpha = 0.55f), style = TextStyle(fontFamily = font, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        sample.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                pair.forEach { e ->
                    val o = state.overrides[e.key.id]
                    AppTile(e, o?.label ?: e.label, o?.icon, 36.dp, showLabel = theme.showDrawerLabels, labelColor = fg, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** An empty spot in the dock, so the number of spots you chose shows even when they aren't all filled. */
@Composable
private fun EmptySlot(theme: ThemeConfig, size: Dp) {
    val shape = iconShapeOf(theme.iconShape, theme.iconCorner)
    Box(
        Modifier
            .size(size * 0.8f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(2.dp, Color.White.copy(alpha = 0.3f), shape),
    )
}

// --- An icon from an older app -------------------------------------------------------------------

/** A made-up icon in the old style (a logo with no background of its own), so its settings show. */
private object OldStyleSample {
    @Volatile
    private var picture: Bitmap? = null

    fun drawable(context: Context): Drawable {
        val bmp = picture ?: createBitmap(192, 192).also { b ->
            val c = android.graphics.Canvas(b)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFFF7A1A.toInt()
            c.drawCircle(96f, 96f, 70f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            val plane = android.graphics.Path().apply {
                moveTo(60f, 98f)
                lineTo(140f, 62f)
                lineTo(112f, 138f)
                lineTo(98f, 108f)
                close()
            }
            c.drawPath(plane, paint)
            picture = b
        }
        return bmp.toDrawable(context.resources)
    }
}

@Composable
private fun OldStyleTile(size: Dp, showLabel: Boolean, labelColor: Color, modifier: Modifier) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val spec = theme.iconSpec()
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bucket = IconProvider.bucket(px)
    val icon by produceState<ImageBitmap?>(null, spec, bucket) {
        value = withContext(Dispatchers.Default) {
            val d = OldStyleSample.drawable(context)
            val needsHints = spec.style == IconStyle.Themed || spec.style == IconStyle.Tinted || spec.style == IconStyle.ColorSymbols
            val hints = if (needsHints) IconRenderer.analyze(OldStyleSample.drawable(context)) else IconHints()
            runCatching { IconRenderer.render(d, IconSource.App, bucket, spec, hints).asImageBitmap() }.getOrNull()
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            IconLabel("Older app", labelColor, Modifier.padding(horizontal = 2.dp))
        }
    }
}

// --- Whole-screen copies -------------------------------------------------------------------------

/** How a copy of a whole screen fills the system bar areas. */
internal sealed interface Bars {
    /** In a small preview: the copy draws its own status bar strip and leaves room for the navigation bar. */
    data class Drawn(val statusBar: Dp, val navigationBar: Dp) : Bars

    /** Full screen: room for the phone's own bars, which show (or hide) the way they do at home. */
    data object Real : Bars
}

/** The size of the whole window, which a small copy is drawn at before it's shrunk. */
@Composable
internal fun screenSize(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) {
        if (size.width > 0 && size.height > 0) DpSize(size.width.toDp(), size.height.toDp()) else DpSize(411.dp, 891.dp)
    }
}

/**
 * A small phone in the middle of the preview with a whole screen inside it: drawn at full size,
 * then shrunk, so everything sits exactly where it does on the phone. The wallpaper inside is the
 * real one: a small copy when Veneer can see it, otherwise the phone's wallpaper through the window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiniPhone(glow: GlowPlayer, modifier: Modifier, content: @Composable (ImageBitmap?, Bars) -> Unit) {
    val screen = screenSize()
    val density = LocalDensity.current
    val statusBar = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }.takeIf { it > 0.dp } ?: 24.dp
    val navigationBar = with(density) { WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp() }
    val picture = rememberWallpaperPicture()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / screen.width, (maxHeight - 16.dp) / screen.height)
        val w = screen.width * scale
        val h = screen.height * scale
        val corner = 18.dp
        val phone = Modifier
            .size(w, h)
            .clip(RoundedCornerShape(corner))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(corner))
            .drawWithContent {
                drawContent()
                drawEdgeGlow(glow, corner.toPx(), widthScale = 0.55f)
            }
        // Darken the wallpaper around the little phone so it stands out.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val pw = w.toPx()
                    val ph = h.toPx()
                    val left = (size.width - pw) / 2
                    val top = (size.height - ph) / 2
                    val hole = Path().apply { addRoundRect(RoundRect(left, top, left + pw, top + ph, CornerRadius(corner.toPx()))) }
                    clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }
                },
        )
        Box(phone.testTag("miniPhone")) {
            Shrunk(screen) { content(picture, Bars.Drawn(statusBar, navigationBar)) }
        }
    }
}

/** Lays [content] out at [full] size and draws it shrunk to fit the space it's given. */
@Composable
private fun Shrunk(full: DpSize, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val fw = full.width.roundToPx().coerceAtLeast(1)
        val fh = full.height.roundToPx().coerceAtLeast(1)
        val scale = minOf(constraints.maxWidth.toFloat() / fw, constraints.maxHeight.toFloat() / fh)
        val placeables = measurables.map { it.measure(Constraints.fixed(fw, fh)) }
        layout((fw * scale).roundToInt(), (fh * scale).roundToInt()) {
            placeables.forEach { p ->
                p.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/**
 * The home screen wallpaper as a picture, when Veneer can see it; reloaded whenever the wallpaper
 * changes. Null means "look through the window", where the real one always shows.
 */
@Composable
private fun rememberWallpaperPicture(): ImageBitmap? {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val recorded by CurrentWallpaper.changes.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                tick++
            }
        }
        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val picture by produceState<ImageBitmap?>(null, tick, recorded) {
        value = withContext(Dispatchers.IO) { CurrentWallpaper.load(context, 720)?.asImageBitmap() }
    }
    return picture
}

@Composable
private fun TopBar(bars: Bars, hidden: Boolean, darkIcons: Boolean) {
    when (bars) {
        is Bars.Drawn -> if (!hidden) StatusBarStrip(bars.statusBar, darkIcons)
        Bars.Real -> Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    }
}

@Composable
private fun BottomBar(bars: Bars) {
    when (bars) {
        is Bars.Drawn -> Spacer(Modifier.height(bars.navigationBar))
        Bars.Real -> Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** The phone's status bar as it looks over the home screen: the time, then Wi-Fi, signal and battery. */
@Composable
private fun StatusBarStrip(height: Dp, dark: Boolean) {
    val context = LocalContext.current
    val color = if (dark) Color.Black.copy(alpha = 0.85f) else Color.White
    val time = remember { DateFormat.getTimeFormat(context).format(Date()) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 30.dp)
            .testTag("statusStrip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = color, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.weight(1f))
        listOf(Icons.Filled.Wifi, Icons.Filled.SignalCellularAlt, Icons.Filled.Battery5Bar).forEach {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.padding(start = 3.dp).size(16.dp))
        }
    }
}

/**
 * A copy of the home screen's first page: the wallpaper and its darkening, the status bar, every
 * item on the grid you chose, page dots and the dock with every spot. Two apps show sample
 * notification dots. Nothing in it can be tapped.
 */
@Composable
internal fun HomeReplica(state: LauncherState, apps: List<AppEntry>, wallpaper: ImageBitmap?, bars: Bars) {
    val theme = state.theme
    val appMap = remember(apps) { apps.associateBy { it.key.id } }
    val page = remember(state.items) { state.items.filter { it.page == 0 }.sortedWith(compareBy({ it.y }, { it.x })) }
    val dock = remember(state.items) { state.items.filter { it.page == DOCK_PAGE } }
    val counts = remember(page, dock, appMap) {
        sampleCounts((page + dock.sortedBy { it.x }).mapNotNull { (it.content as? ItemContent.App)?.let { c -> appMap[c.id] } })
    }
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) Image(wallpaper, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (theme.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.wallpaperDim.coerceIn(0f, 0.9f))))
        CompositionLocalProvider(LocalNotificationCounts provides counts) {
            Column(Modifier.fillMaxSize()) {
                TopBar(bars, theme.hideStatusBar, theme.statusBarIcons == BarIcons.Dark)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val iconSize = homeIconSize(theme, maxWidth, maxHeight)
                    CellGrid(theme.gridColumns, theme.gridRows, Modifier.fillMaxSize().padding(GridPadding)) {
                        page.forEach { item ->
                            key(item.id) { ReplicaItem(item, state, appMap, iconSize, theme.showHomeLabels, Modifier.layoutId(item.cell())) }
                        }
                    }
                }
                if (theme.pageIndicator && state.pageCount > 1) PageIndicator(state.pageCount, 0, Color(theme.accent))
                if (theme.dockEnabled) DockReplica(state, dock, appMap) else Spacer(Modifier.height(12.dp))
                BottomBar(bars)
            }
        }
    }
}

@Composable
private fun DockReplica(state: LauncherState, dock: List<HomeItem>, appMap: Map<String, AppEntry>) {
    val theme = state.theme
    val size = dockIconSize(theme)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(theme.dockCornerDp.dp))
            .background(Color(theme.dockBg))
            .padding(vertical = 10.dp)
            .testTag("dock"),
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until theme.dockColumns) {
                Box(Modifier.weight(1f).height(size + 6.dp), contentAlignment = Alignment.Center) {
                    val item = dock.firstOrNull { it.x == col }
                    if (item != null) ReplicaItem(item, state, appMap, size, showLabel = false, Modifier.fillMaxSize())
                    else EmptySlot(theme, size)
                }
            }
        }
    }
}

/** One home screen item, drawn the way the home screen draws it but without reacting to touch. */
@Composable
private fun ReplicaItem(item: HomeItem, state: LauncherState, appMap: Map<String, AppEntry>, iconSize: Dp, showLabel: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val c = item.content) {
            is ItemContent.App -> {
                val entry = appMap[c.id]
                val o = state.overrides[c.id]
                AppTile(entry, o?.label ?: entry?.label ?: "", o?.icon, iconSize, showLabel, Color(o?.labelColor ?: state.theme.labelColor))
            }
            is ItemContent.Folder -> FolderTile(c, appMap, state, iconSize, showLabel)
            is ItemContent.Builtin -> BuiltinWidgetView(c.kind, c.style)
            is ItemContent.Widget -> SystemWidgetPicture(c.appWidgetId)
            is ItemContent.Stack -> when (val first = c.widgets.firstOrNull()) {
                is ItemContent.Builtin -> BuiltinWidgetView(first.kind, first.style)
                is ItemContent.Widget -> SystemWidgetPicture(first.appWidgetId)
                else -> Unit
            }
        }
    }
}

/**
 * A widget from another app. Its live view belongs to the home screen, so the preview shows the
 * picture the widget's app provides for it, or its icon and name.
 */
@Composable
private fun SystemWidgetPicture(appWidgetId: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val info = remember(appWidgetId) { runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) }.getOrNull() }
    val picture by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dpi = (density * 160).toInt()
                val d = info?.loadPreviewImage(context, dpi) ?: info?.loadIcon(context, dpi)
                d?.let { it.toBitmap(it.intrinsicWidth.takeIf { w -> w > 0 } ?: 192, it.intrinsicHeight.takeIf { h -> h > 0 } ?: 192).asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val p = picture
        if (p != null) {
            Image(p, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        } else {
            Text(
                info?.loadLabel(context.packageManager) ?: "Widget",
                color = Color.White,
                style = TextStyle(fontSize = 12.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A copy of the app list: its background over the wallpaper, the search bar, and the apps in the
 * chosen number of columns, with or without names, in its text color.
 */
@Composable
internal fun AppListReplica(state: LauncherState, apps: List<AppEntry>, wallpaper: ImageBitmap?, bars: Bars) {
    val theme = state.theme
    val bg = Color(theme.drawerBg)
    val fg = Color(theme.drawerTextColor)
    val font = LocalThemeFont.current
    val visible = remember(apps, state.overrides) { visibleApps(state, apps) }
    val iconSize = theme.iconSizeDp.coerceAtMost(64).dp
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) Image(wallpaper, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (theme.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.wallpaperDim.coerceIn(0f, 0.9f))))
        Column(Modifier.fillMaxSize().background(bg)) {
            TopBar(bars, theme.hideStatusBar, bg.luminance() > 0.5f)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(fg.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = fg.copy(alpha = 0.7f))
                    Spacer(Modifier.width(10.dp))
                    Text("Search ${apps.size} apps", color = fg.copy(alpha = 0.55f), style = TextStyle(fontFamily = font, fontSize = 16.sp), maxLines = 1)
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = fg)
                }
            }
            // Only the rows that fit, like the top of the real list.
            Column(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                visible.chunked(theme.drawerColumns.coerceAtLeast(1)).take(12).forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        line.forEach { e ->
                            val o = state.overrides[e.key.id]
                            Box(Modifier.weight(1f).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                AppTile(e, o?.label ?: e.label, o?.icon, iconSize, showLabel = theme.showDrawerLabels, labelColor = fg)
                            }
                        }
                        repeat(theme.drawerColumns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            BottomBar(bars)
        }
    }
}
