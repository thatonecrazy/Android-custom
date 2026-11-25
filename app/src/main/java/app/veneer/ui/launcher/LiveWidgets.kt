package app.veneer.ui.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.veneer.system.NotificationDotsService
import app.veneer.system.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- Notification access (shared by notification dots and the music widget) ------------------

/** Whether Veneer has notification access, checked again each time the screen comes back. */
@Composable
fun rememberNotificationAccess(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NotificationDotsService.isAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = NotificationDotsService.isAllowed(context) }
    return state
}

// --- Music ------------------------------------------------------------------------------------

data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/** Follows whichever app is playing media and reports what's on. */
internal class NowPlayingWatcher(context: Context, private val onChange: (NowPlaying?) -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = NotificationDotsService.component(context)
    private val handler = Handler(Looper.getMainLooper())
    private var current: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun start() {
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            refresh()
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(callback)
        current = null
    }

    private fun refresh() = pick(runCatching { manager.getActiveSessions(component) }.getOrNull())

    private fun pick(controllers: List<MediaController>?) {
        val chosen = controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers?.firstOrNull()
        if (chosen?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = chosen
            chosen?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = current ?: return onChange(null)
        val meta = c.metadata ?: return onChange(null)
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) return onChange(null)
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART))
            ?.let { shrink(it).asImageBitmap() }
        onChange(NowPlaying(title, artist, art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c))
    }

    private fun shrink(b: Bitmap): Bitmap {
        val max = 192
        if (b.width <= max && b.height <= max) return b
        val ratio = max.toFloat() / maxOf(b.width, b.height)
        return b.scale((b.width * ratio).toInt().coerceAtLeast(1), (b.height * ratio).toInt().coerceAtLeast(1))
    }
}

@Composable
internal fun rememberNowPlaying(allowed: Boolean): State<NowPlaying?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<NowPlaying?>(null) }
    DisposableEffect(allowed) {
        if (!allowed) {
            state.value = null
            return@DisposableEffect onDispose { }
        }
        val watcher = NowPlayingWatcher(context) { state.value = it }
        watcher.start()
        onDispose { watcher.stop() }
    }
    return state
}

/** Album art, title, artist and play controls for whatever is playing. */
@Composable
fun MusicWidget(base: TextStyle, sizeSp: Int, textColor: Color) {
    val context = LocalContext.current
    val preview = LocalWidgetPreview.current
    val allowed = rememberNotificationAccess().value || preview
    val now by rememberNowPlaying(allowed && !preview)
    val playing = now
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(textColor.copy(alpha = 0.15f))
                .clickable(enabled = playing != null) { runCatching { playing?.controller?.sessionActivity?.send() } },
            contentAlignment = Alignment.Center,
        ) {
            val art = playing?.art
            if (art != null) Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
            else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = textColor)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = !allowed) { NotificationDotsService.requestAccess(context) },
        ) {
            val title = when {
                !allowed -> "Tap to show what's playing"
                preview && playing == null -> "Late Night Drive"
                playing == null -> "Nothing playing"
                else -> playing.title
            }
            Text(
                title,
                style = base.copy(fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                !allowed -> "Needs notification access"
                preview && playing == null -> "Your music shows here"
                playing == null -> "Start music in any app"
                else -> playing.artist
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = base.copy(fontSize = (sizeSp * 0.8f).sp, color = textColor.copy(alpha = 0.75f), textAlign = TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (playing != null) {
            val controls = playing.controller.transportControls
            IconButton(onClick = { controls.skipToPrevious() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = textColor) }
            IconButton(
                onClick = { if (playing.playing) controls.pause() else controls.play() },
                modifier = Modifier.clip(CircleShape).background(textColor.copy(alpha = 0.18f)),
            ) { Icon(if (playing.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = textColor) }
            IconButton(onClick = { controls.skipToNext() }) { Icon(Icons.Filled.SkipNext, "Next", tint = textColor) }
        } else if (preview) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(textColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = textColor)
            }
        }
    }
}

// --- Calendar agenda --------------------------------------------------------------------------

data class AgendaEvent(val id: Long, val title: String, val begin: Long, val allDay: Boolean, val color: Int)

internal fun nextEvents(context: Context, limit: Int = 8): List<AgendaEvent> {
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now - 12 * 60 * 60 * 1000L)
        ContentUris.appendId(it, now + 7 * 24 * 60 * 60 * 1000L)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
    )
    val events = ArrayList<AgendaEvent>()
    runCatching {
        context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && events.size < limit) {
                if (c.getLong(3) < now) continue
                events += AgendaEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = c.getLong(2),
                    allDay = c.getInt(4) == 1,
                    color = c.getInt(5) or 0xFF000000.toInt(),
                )
            }
        }
    }
    return events
}

/** Made-up events for widget previews, so a layout shows what it will look like with a full calendar. */
private fun sampleEvents(now: Long): List<AgendaEvent> {
    val hour = 60 * 60 * 1000L
    val start = now - now % hour
    return listOf(
        AgendaEvent(-1, "Coffee with Sam", start + 2 * hour, false, 0xFFFFB74D.toInt()),
        AgendaEvent(-2, "Gym", start + 5 * hour, false, 0xFF4DD0E1.toInt()),
        AgendaEvent(-3, "Movie night", start + 26 * hour, false, 0xFFBA68C8.toInt()),
    )
}

private fun timeLabel(context: Context, e: AgendaEvent): String {
    val today = Calendar.getInstance()
    val day = Calendar.getInstance().apply { timeInMillis = e.begin }
    val sameDay = today.get(Calendar.YEAR) == day.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    val weekday = java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(Date(e.begin))
    val time = DateFormat.getTimeFormat(context).format(Date(e.begin))
    return when {
        e.allDay && sameDay -> "Today"
        e.allDay -> weekday
        sameDay -> time
        else -> "$weekday $time"
    }
}

/** Today's date and the next events on your calendars, each in its calendar's color. */
@Composable
fun AgendaWidget(base: TextStyle, sizeSp: Int, textColor: Color) {
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val preview = LocalWidgetPreview.current
    var granted by remember { mutableStateOf(preview || check()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = preview || check() }
    val now by rememberNow(false)
    val events by produceState(emptyList<AgendaEvent>(), granted, now / (5 * 60_000L)) {
        value = when {
            preview -> sampleEvents(now)
            granted -> withContext(Dispatchers.IO) { nextEvents(context) }
            else -> emptyList()
        }
    }
    val small = base.copy(fontSize = (sizeSp * 0.85f).sp, textAlign = TextAlign.Start)
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = textColor, modifier = Modifier.size((sizeSp + 4).dp))
            Spacer(Modifier.width(8.dp))
            Text(
                java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale).format(Date(now)),
                style = base.copy(fontSize = (sizeSp + 2).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                maxLines = 1,
            )
        }
        when {
            !granted -> Text(
                "Tap to show your next events",
                style = small,
                modifier = Modifier.clickable { ask.launch(Manifest.permission.READ_CALENDAR) },
            )
            events.isEmpty() -> Text("Nothing coming up this week", style = small.copy(color = textColor.copy(alpha = 0.75f)))
            else -> events.take(4).forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { openEvent(context, e) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = (sizeSp + 4).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(e.color)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(timeLabel(context, e), style = small.copy(color = textColor.copy(alpha = 0.8f)), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(e.title, style = small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

private fun openEvent(context: Context, e: AgendaEvent) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
    if (!SystemActions.open(context, intent, quiet = true)) SystemActions.openCalendar(context)
}
