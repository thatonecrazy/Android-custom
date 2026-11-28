package app.veneer.ui.island

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import app.veneer.data.MediaKind
import app.veneer.data.QuickTile
import app.veneer.system.IslandPanel
import app.veneer.system.LyricsResult
import app.veneer.system.QuickActions
import app.veneer.system.TileKind
import app.veneer.system.TileOutcome
import app.veneer.system.kind
import kotlinx.coroutines.launch
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.veneer.system.IslandEvent
import app.veneer.system.IslandEvents
import app.veneer.system.NotificationDotsService
import app.veneer.ui.launcher.rememberNowPlaying
import app.veneer.veneer
import kotlinx.coroutines.delay
import java.util.Date

/**
 * The live island in its overlay window: picks what to show from charging, notifications and
 * music, runs the quick actions drop-down, and looks up lyrics when they're opened.
 */
@Composable
fun IslandHost() {
    val context = LocalContext.current
    val app = context.veneer
    val state by app.store.state.collectAsState()
    val config = state.island
    val accent = Color(config.accent ?: state.theme.accent)
    val font = remember(state.theme.fontId) { app.fonts.family(state.theme.fontId) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reduceMotion = animationsOff()
    val scope = rememberCoroutineScope()

    var pop by remember { mutableStateOf<IslandContent?>(null) }
    var open by remember { mutableStateOf(false) }
    val musicAllowed = remember(config.music) { config.music && NotificationDotsService.isAllowed(context) }
    val nowPlaying by rememberNowPlaying(musicAllowed)
    val alarm by rememberNextAlarm(config.alarms)

    LaunchedEffect(Unit) {
        IslandEvents.events.collect { e ->
            pop = when (e) {
                is IslandEvent.Charging -> IslandContent.Charging(e.percent)
                is IslandEvent.Notification -> IslandContent.Notification(e.app, e.title, e.text, e.icon?.asImageBitmap(), e.open)
            }
            open = false
        }
    }
    LaunchedEffect(pop) {
        if (pop != null) {
            delay(config.popSeconds.coerceIn(2, 15) * 1000L)
            pop = null
        }
    }
    // Plugging in pops the island open with the battery level.
    DisposableEffect(config.charging) {
        if (!config.charging) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val battery = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
                val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
                if (level >= 0) IslandEvents.post(IslandEvent.Charging(level * 100 / scale))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Only music changes the island; a video leaves it as it is. The open card stays while paused.
    val music = nowPlaying?.takeIf { showsMusic(it.kind, it.playing, open) }
    LaunchedEffect(music == null) { if (music == null) open = false }

    // Lyrics: looked up only when opened, and they follow the song's position while it plays.
    var lyricsOpen by remember { mutableStateOf(false) }
    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var positionMs by remember { mutableLongStateOf(0L) }
    val song = music?.let { listOf(it.packageName, it.title, it.artist, it.album, it.durationMs) }
    LaunchedEffect(open) { if (!open) lyricsOpen = false }
    LaunchedEffect(lyricsOpen, song) {
        val np = music
        if (!lyricsOpen || np == null) return@LaunchedEffect
        lyricsState = LyricsState.Loading
        lyricsState = when (val r = app.lyrics.lookup(np.title, np.artist, np.album, np.durationMs)) {
            is LyricsResult.Found -> LyricsState.Found(r.lyrics)
            LyricsResult.NotFound -> LyricsState.NotFound
            LyricsResult.Offline -> LyricsState.Offline
        }
    }
    val controller = music?.controller
    LaunchedEffect(lyricsOpen, controller) {
        if (controller == null) return@LaunchedEffect
        while (lyricsOpen) {
            positionMs = currentPosition(controller)
            delay(200)
        }
    }

    // The quick actions drop-down.
    val quick = remember(context) { QuickActions(context) }
    DisposableEffect(quick) {
        quick.start()
        onDispose { quick.stop() }
    }
    val panelOpen by IslandPanel.open.collectAsState()
    val panelShown = panelOpen && config.quickActions && !landscape
    var panelState by remember { mutableStateOf(QuickPanelState(config.tiles, emptyMap(), 0.5f, false, 0.5f)) }
    var message by remember { mutableStateOf<PanelMessage?>(null) }
    fun read() = QuickPanelState(config.tiles, config.tiles.associateWith { quick.isOn(it) }, quick.brightness(), quick.autoBrightness(), quick.volume())
    LaunchedEffect(config.quickActions, landscape) { if (!config.quickActions || landscape) IslandPanel.close() }
    LaunchedEffect(panelShown, config.tiles) {
        if (!panelShown) {
            message = null
            return@LaunchedEffect
        }
        // Every tile shows the setting's real state, kept up to date while the drop-down is open.
        while (true) {
            panelState = read()
            delay(700)
        }
    }
    fun handle(outcome: TileOutcome) {
        when (outcome) {
            TileOutcome.Done -> panelState = read()
            TileOutcome.Opened -> IslandPanel.close()
            is TileOutcome.NeedsPermission -> message = PanelMessage(outcome.text, "Allow") {
                runCatching { context.startActivity(Intent(outcome.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toast.makeText(context, "Android couldn't open that settings page.", Toast.LENGTH_LONG).show() }
                IslandPanel.close()
            }
            is TileOutcome.Failed -> message = PanelMessage(outcome.text)
        }
    }
    val actions = QuickPanelActions(
        onTile = { tile ->
            if (tile.kind == TileKind.Action) {
                // Fold the drop-down away first so it isn't in the screenshot or behind the power menu.
                IslandPanel.close()
                scope.launch {
                    delay(if (reduceMotion) 80 else if (tile == QuickTile.Screenshot) 450 else 150)
                    val outcome = quick.tap(tile)
                    if (outcome is TileOutcome.Failed) Toast.makeText(context, outcome.text, Toast.LENGTH_LONG).show()
                }
            } else {
                handle(quick.tap(tile))
            }
        },
        onBrightness = { v ->
            val outcome = quick.setBrightness(v)
            if (outcome == TileOutcome.Done) panelState = panelState.copy(brightness = v, autoBrightness = false) else handle(outcome)
        },
        onAutoBrightness = { handle(quick.setAutoBrightness(!panelState.autoBrightness)) },
        onVolume = { v ->
            val outcome = quick.setVolume(v)
            if (outcome == TileOutcome.Done) panelState = panelState.copy(volume = v) else handle(outcome)
        },
        onClose = { IslandPanel.close() },
    )

    val content: IslandContent? = when {
        landscape -> null // games and videos get the whole screen
        pop != null -> pop
        music != null -> IslandContent.Music(
            music.title, music.artist, music.art, music.playing, open,
            MusicControls(
                previous = { music.controller.transportControls.skipToPrevious() },
                playPause = { if (music.playing) music.controller.transportControls.pause() else music.controller.transportControls.play() },
                next = { music.controller.transportControls.skipToNext() },
                openApp = { music.controller.sessionActivity?.let { send(context, it) } },
                toggleLyrics = { lyricsOpen = !lyricsOpen },
                seekTo = { ms ->
                    music.controller.transportControls.seekTo(ms)
                    positionMs = ms
                },
            ),
            // Lyrics are for music only; videos never get here.
            lyrics = if (lyricsAllowed(config.lyrics, music.kind)) LyricsView(lyricsOpen, lyricsState, positionMs) else null,
        )
        else -> IslandContent.Idle
    }
    IslandView(
        config, accent, content, alarm,
        font = font,
        reduceMotion = reduceMotion,
        panel = if (panelShown) {
            { QuickPanel(config, accent, panelState.copy(message = message), actions, reduceMotion = reduceMotion, font = font) }
        } else null,
        onTap = {
            when (val c = content) {
                is IslandContent.Notification -> { c.open?.let { send(context, it) }; pop = null }
                is IslandContent.Charging -> pop = null
                is IslandContent.Music -> open = !open
                // Tapping the resting island opens (or closes) the quick actions.
                IslandContent.Idle -> if (config.quickActions) IslandPanel.open.value = !panelOpen
                null -> Unit
            }
        },
        // A swipe down opens the quick actions from any state, even during music or charging.
        onSwipeDown = { if (config.quickActions) IslandPanel.open.value = true },
    )
}

/** Only music changes the island; a video leaves it as it is. The open card stays while the song is paused. */
internal fun showsMusic(kind: MediaKind, playing: Boolean, cardOpen: Boolean): Boolean = kind == MediaKind.Music && (playing || cardOpen)

/** Lyrics are for music only, and only while the setting is on. */
internal fun lyricsAllowed(setting: Boolean, kind: MediaKind): Boolean = setting && kind == MediaKind.Music

/** Where the song is now: the last reported position, moved on by the time since, while it plays. */
private fun currentPosition(c: MediaController): Long {
    val s = c.playbackState ?: return 0L
    var pos = s.position
    if (s.state == PlaybackState.STATE_PLAYING) {
        pos += ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
    }
    return pos.coerceAtLeast(0L)
}

/** Opens what a notification or a music app points to, from the overlay. */
private fun send(context: Context, intent: PendingIntent) {
    runCatching {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        intent.send(context, 0, null, null, null, null, options.toBundle())
    }
}

/** The next alarm, as a time, when it's within the next 12 hours. */
@Composable
private fun rememberNextAlarm(enabled: Boolean): State<String?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<String?>(null) }
    fun read() {
        val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
        val soon = next != null && next - System.currentTimeMillis() in 0..12 * 60 * 60 * 1000L
        state.value = if (enabled && soon) DateFormat.getTimeFormat(context).format(Date(next!!)) else null
    }
    DisposableEffect(enabled) {
        read()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read()
        }
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED).apply { addAction(Intent.ACTION_TIME_TICK) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
