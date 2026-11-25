package app.veneer.ui.island

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

/** The live island in its overlay window: picks what to show from charging, notifications, music and alarms. */
@Composable
fun IslandHost() {
    val context = LocalContext.current
    val app = context.veneer
    val state by app.store.state.collectAsState()
    val config = state.island
    val accent = Color(config.accent ?: state.theme.accent)
    val font = remember(state.theme.fontId) { app.fonts.family(state.theme.fontId) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    val playing = nowPlaying?.takeIf { it.playing }
    val content: IslandContent? = when {
        landscape -> null // games and videos get the whole screen
        pop != null -> pop
        playing != null -> IslandContent.Music(
            playing.title, playing.artist, playing.art, playing.playing, open,
            MusicControls(
                previous = { playing.controller.transportControls.skipToPrevious() },
                playPause = { if (playing.playing) playing.controller.transportControls.pause() else playing.controller.transportControls.play() },
                next = { playing.controller.transportControls.skipToNext() },
                openApp = { playing.controller.sessionActivity?.let { send(context, it) } },
            ),
        )
        else -> IslandContent.Idle
    }
    IslandView(config, accent, content, alarm, font, onTap = {
        when (val c = content) {
            is IslandContent.Notification -> { c.open?.let { send(context, it) }; pop = null }
            is IslandContent.Charging -> pop = null
            is IslandContent.Music -> open = !open
            else -> Unit
        }
    })
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
