package app.veneer.ui.customize

import android.media.session.MediaSessionManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import app.veneer.data.MediaKind
import app.veneer.data.QuickTile
import app.veneer.system.LyricLine
import app.veneer.system.Lyrics
import app.veneer.system.MediaKinds
import app.veneer.system.TileKind
import app.veneer.system.kind
import app.veneer.ui.island.LyricsState
import app.veneer.ui.island.LyricsView
import app.veneer.ui.island.QuickPanel
import app.veneer.ui.island.QuickPanelActions
import app.veneer.ui.island.QuickPanelState
import app.veneer.ui.island.SampleCover
import app.veneer.ui.island.animationsOff
import app.veneer.ui.island.icon
import app.veneer.ui.launcher.AppIcon
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.veneer.apps.AppEntry
import app.veneer.data.IslandConfig
import app.veneer.data.LauncherState
import app.veneer.system.IslandEvent
import app.veneer.system.IslandEvents
import app.veneer.system.LockService
import app.veneer.system.NotificationDotsService
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.island.IslandContent
import app.veneer.ui.island.IslandView
import app.veneer.veneer

private enum class Sample(val label: String) {
    Idle("Resting"), Charging("Charging"), Music("Music"), MusicOpen("Music, open"), Lyrics("Lyrics"), Message("Notification"), Quick("Quick actions"),
}

/** Settings for the island, with a live preview at the top of a pretend phone screen. */
@Composable
fun IslandScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val config = state.island
    fun update(transform: (IslandConfig) -> IslandConfig) = app.store.update { it.copy(island = transform(it.island)) }
    var serviceOn by remember { mutableStateOf(LockService.isEnabled(context)) }
    var notificationAccess by remember { mutableStateOf(NotificationDotsService.isAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = LockService.isEnabled(context)
        notificationAccess = NotificationDotsService.isAllowed(context)
    }
    var sample by remember { mutableStateOf(Sample.Charging) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    // A change jumps the preview to the sample that shows it, with a short label for what a picture can't show.
    val notes = remember { PreviewNotes() }
    val lastConfig = remember { arrayOf(config) }
    LaunchedEffect(config) {
        val old = lastConfig[0]
        lastConfig[0] = config
        if (old === config) return@LaunchedEffect
        val (jump, note) = islandChange(old, config, sample) { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
        jump?.let { sample = it }
        note?.let { notes.show(it) }
    }
    val accent = Color(config.accent ?: state.theme.accent)
    val font = remember(state.theme.fontId) { app.fonts.family(state.theme.fontId) }
    val reduceMotion = animationsOff()
    // Apps playing something right now join the "Music or video?" list.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        runCatching {
            context.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(NotificationDotsService.component(context))
                ?.forEach { MediaKinds.noteApp(context, it.packageName) }
        }
    }

    BackHandler(enabled = fullScreen) { fullScreen = false }
    Box(Modifier.fillMaxSize()) {
    SettingsPage("Island", onBack, state, apps, preview = null, section = Section.Island) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF16161C))))
                    .animateContentSize()
                    .heightIn(min = 220.dp)
                    .padding(bottom = 16.dp)
                    .testTag("islandPreview"),
            ) {
                IslandStage(config, accent, sample, font, reduceMotion)
                // Tapping the preview shows it full screen; nothing inside it reacts on its own.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { fullScreen = true }
                        .semantics {
                            contentDescription = "Island preview. Tap to see it full screen"
                            role = Role.Button
                        },
                )
                if (!config.enabled && notes.note == null) {
                    Text(
                        "The island is off",
                        color = Color.White,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("islandOff"),
                    )
                }
                NoteView(notes, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp))
            }
        }
        item {
            ChoiceRow(null, Sample.entries, sample, { it.label }) { sample = it }
        }

        item { SectionHeader("Turn on") }
        item {
            SettingCard {
                SwitchRow("Show the island", config.enabled, "Over every app, at the top of the screen") { b -> update { it.copy(enabled = b) } }
                if (config.enabled && !serviceOn) {
                    ClickRow(
                        "Let Veneer draw over the status bar",
                        "Turn on \"Veneer\" in Accessibility. It's the same switch as the screen lock gesture.",
                        icon = Icons.Filled.Accessibility,
                    ) { LockService.openSettings(context) }
                    NoteText("Android only lets accessibility services draw above the status bar. Veneer's reads nothing on your screen.")
                }
                if (config.enabled && (config.music || config.notifications) && !notificationAccess) {
                    ClickRow(
                        "Allow notification access",
                        "Needed to show music and notifications in the island",
                        icon = Icons.Filled.NotificationsActive,
                    ) { NotificationDotsService.requestAccess(context) }
                }
            }
        }

        item { SectionHeader("What it shows") }
        item {
            SettingCard {
                SwitchRow("Charging", config.charging, "Pops open with the battery level when you plug in") { b -> update { it.copy(charging = b) } }
                SwitchRow("Music", config.music, "The song's cover and moving bars while music plays. Tap for controls.") { b -> update { it.copy(music = b) } }
                SwitchRow("Lyrics", config.lyrics, "Looks the song up on lrclib.net when you open lyrics") { b -> update { it.copy(lyrics = b) } }
                SwitchRow("Notifications", config.notifications, "New messages pop up for a moment. On the lock screen it only says something arrived.") { b ->
                    update { it.copy(notifications = b) }
                }
                SwitchRow("Alarms", config.alarms, "A small bubble when an alarm is set for the next 12 hours") { b -> update { it.copy(alarms = b) } }
                SwitchRow("Resting pill", config.idlePill, "A small pill around the camera when nothing is happening") { b -> update { it.copy(idlePill = b) } }
                IntSliderRow("Pop-ups stay for", config.popSeconds, 2..10, { "$it seconds" }) { v -> update { it.copy(popSeconds = v) } }
            }
        }

        item { SectionHeader("Music or video?") }
        item {
            SettingCard {
                NoteText("Only music changes the island; videos leave it as it is. Apps that have played something are listed here. Pick Music or Video when Automatic gets an app wrong.")
                if (config.mediaApps.isEmpty()) {
                    NoteText(
                        if (notificationAccess) "Apps show up here after they play something."
                        else "Apps show up here after they play something, once Veneer has notification access.",
                    )
                }
                config.mediaApps.forEach { pkg -> MediaAppRow(pkg, apps, config.mediaKinds[pkg]) { choice -> update { it.copy(mediaKinds = if (choice == null) it.mediaKinds - pkg else it.mediaKinds + (pkg to choice)) } } }
            }
        }

        item { SectionHeader("Quick actions") }
        item {
            SettingCard {
                SwitchRow(
                    "Quick actions drop-down",
                    config.quickActions,
                    "Tap the resting island, or swipe down on it at any time, for tiles like Wi-Fi and the flashlight",
                ) { b -> update { it.copy(quickActions = b) } }
                AnimatedVisibility(config.quickActions, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        NoteText("Drag a tile by its handle to move it. Tiles with an arrow open Android's own pop-up, because Android doesn't let apps switch those settings.")
                        ReorderableTiles(
                            config.tiles,
                            onMove = { from, to -> update { it.copy(tiles = it.tiles.toMutableList().apply { add(to, removeAt(from)) }) } },
                            onRemove = { tile -> update { it.copy(tiles = it.tiles - tile) } },
                        )
                        val more = QuickTile.entries - config.tiles.toSet()
                        if (more.isNotEmpty()) {
                            Text(
                                "More tiles",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                            more.forEach { tile ->
                                ClickRow(tile.label, leading = { TileBadge(tile) }, trailing = { Icon(Icons.Filled.AddCircleOutline, "Add") }) {
                                    update { it.copy(tiles = it.tiles + tile) }
                                }
                            }
                        }
                        if (config.tiles != QuickTile.defaults) {
                            ClickRow("Back to the default tiles", icon = Icons.Filled.Restore) { update { it.copy(tiles = QuickTile.defaults) } }
                        }
                    }
                }
            }
        }

        item { SectionHeader("Look") }
        item {
            SettingCard {
                ColorRow("Background", config.background, "Black blends into the camera hole") { c -> update { it.copy(background = c) } }
                ColorRow("Text and icons", config.content, allowAlpha = false) { c -> update { it.copy(content = c) } }
                ColorRow(
                    "Highlight",
                    config.accent ?: state.theme.accent,
                    if (config.accent == null) "Same as your accent color. Used for the bolt, music bars and outline." else "The bolt, music bars and outline",
                    allowAlpha = false,
                ) { c -> update { it.copy(accent = c) } }
                SwitchRow("Glowing outline", config.outline, "A thin edge in the highlight color") { b -> update { it.copy(outline = b) } }
            }
        }

        item { SectionHeader("Size and place") }
        item {
            SettingCard {
                IntSliderRow("Width", config.widthDp, 40..240, { "$it dp" }) { v -> update { it.copy(widthDp = v) } }
                IntSliderRow("Height", config.heightDp, 20..56, { "$it dp" }) { v -> update { it.copy(heightDp = v) } }
                IntSliderRow("From the top", config.offsetYDp, 0..60, { "$it dp" }) { v -> update { it.copy(offsetYDp = v) } }
                IntSliderRow("Left or right", config.offsetXDp, -160..160, { if (it == 0) "Centered" else "$it dp" }) { v -> update { it.copy(offsetXDp = v) } }
                NoteText("The island centers itself on the front camera. Use these to fit it exactly around the camera hole.")
            }
        }

        item { SectionHeader("Try it") }
        item {
            SettingCard {
                ClickRow("Show a test notification", if (config.enabled && serviceOn) "Pops open at the top of your screen" else "Turn the island on first", icon = Icons.Filled.PlayCircle) {
                    IslandEvents.post(IslandEvent.Notification("Veneer", "Hello from the island", "This is what new messages look like.", null, null))
                }
                ClickRow("Show charging", icon = Icons.Filled.PlayCircle) { IslandEvents.post(IslandEvent.Charging(76)) }
            }
        }
    }
    if (fullScreen) {
        // The island at its real size and place at the top of a pretend screen, until it's tapped again.
        StatusBarHidden()
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF16161C))))
                .clickable(remember { MutableInteractionSource() }, indication = null) { fullScreen = false }
                .testTag("islandFull"),
        ) {
            IslandStage(config, accent, sample, font, reduceMotion)
            Text(
                "Tap anywhere to go back",
                color = Color.White.copy(alpha = 0.8f),
                style = TextStyle(fontSize = 14.sp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            )
        }
    }
    }
}

/** A pretend status bar with the island in its place, showing [sample]. */
@Composable
private fun IslandStage(config: IslandConfig, accent: Color, sample: Sample, font: FontFamily, reduceMotion: Boolean) {
    Box(Modifier.fillMaxWidth()) {
        val bar = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text("9:41", style = bar)
            Spacer(Modifier.weight(1f))
            Text("5G  100", style = bar)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = config.offsetXDp.dp, y = config.offsetYDp.dp)
                // While the island is off it's only a faint outline of what it would look like.
                .graphicsLayer { alpha = if (config.enabled) 1f else 0.4f },
            contentAlignment = Alignment.TopCenter,
        ) {
            IslandView(
                config, accent, sampleContent(sample, config),
                alarm = if (config.alarms) "7:00 AM" else null,
                font = font,
                reduceMotion = reduceMotion,
                panel = if (sample == Sample.Quick && config.quickActions) {
                    { QuickPanel(config, accent, sampleQuickState(config), QuickPanelActions(), reduceMotion = reduceMotion, font = font) }
                } else null,
            )
        }
    }
}

/** Lines made up for the preview; real lyrics come from lrclib.net on the phone. */
private val sampleLyrics = Lyrics(
    listOf(
        LyricLine(0, "These lines are made up for the preview"),
        LyricLine(4_000, "Your song's own lyrics show here"),
        LyricLine(8_000, "The line being sung lights up"),
        LyricLine(12_000, "and stays in the middle as it plays"),
        LyricLine(16_000, "Tap a line to jump the song there"),
    ),
    synced = true,
)

/** The island in each sample, as it would really look with these settings: a part that's off doesn't open it. */
private fun sampleContent(sample: Sample, config: IslandConfig): IslandContent {
    val lyricsButton = if (config.lyrics) LyricsView(false, LyricsState.Loading, 0L) else null
    fun song(open: Boolean, lyrics: LyricsView?) =
        IslandContent.Music("Late Night Drive", "Your music shows here", SampleCover.image, playing = true, open = open, lyrics = lyrics)
    return when (sample) {
        Sample.Idle, Sample.Quick -> IslandContent.Idle
        Sample.Charging -> if (config.charging) IslandContent.Charging(76) else IslandContent.Idle
        Sample.Music -> if (config.music) song(open = false, lyrics = null) else IslandContent.Idle
        Sample.MusicOpen -> if (config.music) song(open = true, lyrics = lyricsButton) else IslandContent.Idle
        Sample.Lyrics -> when {
            !config.music -> IslandContent.Idle
            config.lyrics -> song(open = true, lyrics = LyricsView(open = true, state = LyricsState.Found(sampleLyrics), positionMs = 8_500))
            else -> song(open = true, lyrics = null)
        }
        Sample.Message -> if (config.notifications) IslandContent.Notification("Messages", "Sam", "On my way, see you in 10!", null) else IslandContent.Idle
    }
}

/**
 * Which sample shows a change on the Island page, and a short label for what a still picture
 * can't show (something turned off, how long pop-ups stay, music or video).
 */
private fun islandChange(old: IslandConfig, new: IslandConfig, current: Sample, appName: (String) -> String): Pair<Sample?, PreviewNote?> {
    fun off(title: String, text: String) = PreviewNote(title, text, Icons.Filled.Smartphone)
    return when {
        new.enabled != old.enabled -> null to if (new.enabled) null else off("Island", "Off: it won't show over your apps")
        new.charging != old.charging -> Sample.Charging to if (new.charging) null else off("Charging", "Off: plugging in leaves the island as it is")
        new.music != old.music -> Sample.Music to if (new.music) null else off("Music", "Off: songs leave the island as it is")
        new.lyrics != old.lyrics -> (if (new.lyrics) Sample.Lyrics else Sample.MusicOpen) to null
        new.notifications != old.notifications -> Sample.Message to if (new.notifications) null else off("Notifications", "Off: new messages leave the island as it is")
        new.alarms != old.alarms -> Sample.Idle to if (new.alarms) null else off("Alarms", "Off: no bubble for the next alarm")
        new.idlePill != old.idlePill -> Sample.Idle to if (new.idlePill) null else off("Resting pill", "Off: the island hides until something happens")
        new.popSeconds != old.popSeconds -> Sample.Message to PreviewNote("Pop-ups", "Stay open for ${new.popSeconds} seconds", Icons.Filled.Timer)
        new.mediaKinds != old.mediaKinds -> {
            val pkg = (new.mediaKinds.keys + old.mediaKinds.keys).first { new.mediaKinds[it] != old.mediaKinds[it] }
            val kind = new.mediaKinds[pkg]
            val text = when (kind) {
                MediaKind.Music -> "Music: changes the island"
                MediaKind.Video -> "Video: leaves the island as it is"
                null -> "Automatic: ${if (MediaKinds.decide(pkg, null, null, 0, null, false).kind == MediaKind.Music) "music" else "decided by what plays"}"
            }
            (if (kind == MediaKind.Video) Sample.Idle else Sample.Music) to PreviewNote(appName(pkg), text, Icons.Filled.MusicNote)
        }
        new.quickActions != old.quickActions || new.tiles != old.tiles ->
            Sample.Quick to if (new.quickActions) null else off("Quick actions", "Off: tapping the resting island does nothing")
        // The highlight shows on the charging bolt, text on the battery level, and the size on the resting pill.
        new.accent != old.accent && !new.outline && current in setOf(Sample.Idle, Sample.Message, Sample.Quick) -> Sample.Charging to null
        new.content != old.content && current == Sample.Idle -> Sample.Charging to null
        (new.widthDp != old.widthDp || new.heightDp != old.heightDp) && current in setOf(Sample.MusicOpen, Sample.Lyrics, Sample.Message) ->
            (if (new.idlePill) Sample.Idle else Sample.Charging) to null
        else -> null to null
    }
}

private fun sampleQuickState(config: IslandConfig) = QuickPanelState(
    tiles = config.tiles,
    on = config.tiles.associateWith { tile ->
        when (tile.kind) {
            TileKind.Action -> null
            else -> tile in setOf(QuickTile.Wifi, QuickTile.Bluetooth, QuickTile.AutoRotate, QuickTile.Location, QuickTile.MobileData)
        }
    },
    brightness = 0.6f,
    autoBrightness = true,
    volume = 0.4f,
)

/** A tile's icon in a small circle, as it looks in the drop-down. */
@Composable
private fun TileBadge(tile: QuickTile) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(tile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
}

/** One app in the "Music or video?" list, with Automatic / Music / Video. */
@Composable
private fun MediaAppRow(pkg: String, apps: List<AppEntry>, choice: MediaKind?, onChoose: (MediaKind?) -> Unit) {
    val context = LocalContext.current
    val entry = remember(apps, pkg) { apps.firstOrNull { it.packageName == pkg } }
    val label = remember(entry, pkg) {
        entry?.label ?: MediaKinds.musicApps[pkg] ?: MediaKinds.videoApps[pkg]
            ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            ?: pkg
    }
    val automatic = remember(pkg) { MediaKinds.automatic(context, pkg) }
    Column(Modifier.padding(top = 6.dp).testTag("media-$pkg")) {
        ClickRow(
            label,
            if (choice == null) automatic else "Your choice: ${choice.label.lowercase()}",
            leading = { if (entry != null) AppIcon(entry, null, 36.dp) else Icon(Icons.Filled.PlayCircle, null) },
        ) {}
        ChoiceRow(null, listOf<MediaKind?>(null, MediaKind.Music, MediaKind.Video), choice, { it?.label ?: "Automatic" }, onChoose)
    }
}

/**
 * The chosen tiles in order. Drag a tile by its handle to move it: the others slide out of the way,
 * and the tile lands where it's let go.
 */
@Composable
private fun ReorderableTiles(tiles: List<QuickTile>, onMove: (Int, Int) -> Unit, onRemove: (QuickTile) -> Unit) {
    val rowHeight = 52.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<QuickTile?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val from = dragging?.let { tiles.indexOf(it) } ?: -1
    val to = if (from < 0) -1 else (from + (offset / rowPx).roundToInt()).coerceIn(0, tiles.lastIndex)
    Column(Modifier.fillMaxWidth().testTag("tileOrder")) {
        tiles.forEachIndexed { index, tile ->
            val isDragged = tile == dragging
            val shift = when {
                from < 0 || isDragged -> 0f
                index in (from + 1)..to -> -rowPx
                index in to until from -> rowPx
                else -> 0f
            }
            val slide by animateFloatAsState(shift, label = "tileShift")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) offset else slide
                        shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                    }
                    .background(if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .testTag("tileRow-${tile.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .pointerInput(tiles) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = tile
                                    offset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                },
                                onDragEnd = {
                                    val f = tiles.indexOf(tile)
                                    val t = (f + (offset / rowPx).roundToInt()).coerceIn(0, tiles.lastIndex)
                                    dragging = null
                                    offset = 0f
                                    if (f != t) onMove(f, t)
                                },
                                onDragCancel = {
                                    dragging = null
                                    offset = 0f
                                },
                            )
                        }
                        .testTag("drag-${tile.name}"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.DragHandle, contentDescription = "Drag ${tile.label} to move it") }
                TileBadge(tile)
                Spacer(Modifier.width(12.dp))
                Text(tile.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(tile) }) { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove ${tile.label}") }
            }
        }
    }
}
