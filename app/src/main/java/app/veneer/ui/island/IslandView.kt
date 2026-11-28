package app.veneer.ui.island

import android.animation.ValueAnimator
import android.app.PendingIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.data.IslandConfig
import app.veneer.system.LrcParser
import app.veneer.system.Lyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the island is showing right now. */
sealed class IslandContent {
    data object Idle : IslandContent()
    data class Charging(val percent: Int) : IslandContent()
    data class Notification(
        val app: String,
        val title: String,
        val text: String,
        val icon: ImageBitmap?,
        val open: PendingIntent? = null,
    ) : IslandContent()
    data class Music(
        val title: String,
        val artist: String,
        val art: ImageBitmap?,
        val playing: Boolean,
        val open: Boolean,
        val controls: MusicControls? = null,
        /** The lyrics part of the open card; null when lyrics are off. */
        val lyrics: LyricsView? = null,
    ) : IslandContent()
}

class MusicControls(
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val openApp: () -> Unit,
    val toggleLyrics: () -> Unit = {},
    val seekTo: (Long) -> Unit = {},
)

/** Where the lyrics are at: still loading, found, or why there are none. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data object Offline : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

data class LyricsView(val open: Boolean, val state: LyricsState, val positionMs: Long)

/** True when Android's "Remove animations" is on (animator duration scale 0): the island then moves without animating. */
fun animationsOff(): Boolean = !ValueAnimator.areAnimatorsEnabled()

/** Which layout a content uses. Moving to another layout fades the old contents out, resizes, then fades the new ones in. */
private fun IslandContent.layoutKey(): String = when (this) {
    IslandContent.Idle -> "idle"
    is IslandContent.Charging -> "charging"
    is IslandContent.Notification -> "notification:$app:$title:$text"
    is IslandContent.Music -> when {
        !open -> "pill"
        lyrics?.open == true -> "lyrics"
        else -> "card"
    }
}

/** Holds the last drop-down so it can fold away after the island stops asking for it. */
private class PanelHolder {
    var panel: (@Composable () -> Unit)? = null
}

/**
 * The island: a pill that grows into a wider pill or a card for what's happening, with an alarm
 * bubble beside it and the quick actions drop-down under it. Tap and swipe down are reported to
 * [onTap] and [onSwipeDown]; buttons inside handle their own taps.
 */
@Composable
fun IslandView(
    config: IslandConfig,
    accent: Color,
    content: IslandContent?,
    alarm: String?,
    modifier: Modifier = Modifier,
    font: FontFamily? = null,
    panel: (@Composable () -> Unit)? = null,
    reduceMotion: Boolean = false,
    onTap: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
) {
    if (content == null || (content == IslandContent.Idle && !config.idlePill)) return
    val bg = Color(config.background)
    val fg = Color(config.content)
    val h = config.heightDp.coerceIn(20, 60).dp
    val w = config.widthDp.coerceIn(40, 300).dp
    val text = TextStyle(color = fg, fontFamily = font, fontSize = 14.sp)

    // Changing layout: contents fade out, the island springs to its new size, then the new contents fade in.
    var shown by remember { mutableStateOf(content) }
    val contentAlpha = remember { Animatable(1f) }
    val drop = remember { Animatable(0f) }
    val key = content.layoutKey()
    LaunchedEffect(key) {
        if (shown.layoutKey() == key) return@LaunchedEffect
        if (reduceMotion) {
            shown = content
            contentAlpha.snapTo(1f)
            drop.snapTo(0f)
            return@LaunchedEffect
        }
        contentAlpha.animateTo(0f, tween(70))
        shown = content
        if (content is IslandContent.Notification) {
            // Notifications drop down from the camera with a small bounce.
            launch {
                drop.snapTo(-20f)
                drop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 650f))
            }
        }
        delay(170)
        contentAlpha.animateTo(1f, tween(110))
    }
    // Within the same layout (a new song, a new battery level) changes show right away.
    val display = if (content.layoutKey() == shown.layoutKey()) content else shown

    var pressed by remember { mutableStateOf(false) }
    val squeeze by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.4f, stiffness = 900f),
        label = "squeeze",
    )

    val showBubble = alarm != null && (display == IslandContent.Idle || display is IslandContent.Music && !display.open)
    val card = display is IslandContent.Notification || display is IslandContent.Music && display.open
    val shape: Shape = if (card) RoundedCornerShape(28.dp) else RoundedCornerShape(50)
    val holder = remember { PanelHolder() }
    if (panel != null) holder.panel = panel

    Column(modifier.testTag("island"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            // Same width as the bubble on the other side, so the pill stays over the camera.
            if (showBubble) Spacer(Modifier.width(h + 6.dp))
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = squeeze
                        scaleY = squeeze
                        translationY = drop.value.dp.toPx()
                    }
                    .clip(shape)
                    .background(bg)
                    .then(if (config.outline) Modifier.border(1.5.dp, accent.copy(alpha = 0.85f), shape) else Modifier)
                    .pointerInput(onTap, onSwipeDown) {
                        val swipe = 20.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            var swiped = false
                            var moved = false
                            var handledInside = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val delta: Offset = change.position - down.position
                                // A swipe down opens the drop-down, unless something inside (the lyrics) is scrolling.
                                if (!swiped && !change.isConsumed && delta.y > swipe && abs(delta.y) > abs(delta.x)) {
                                    swiped = true
                                    change.consume()
                                    onSwipeDown()
                                }
                                if (delta.getDistance() > viewConfiguration.touchSlop) moved = true
                                if (!change.pressed) {
                                    handledInside = change.isConsumed && !swiped
                                    break
                                }
                            }
                            pressed = false
                            if (!swiped && !moved && !handledInside) onTap()
                        }
                    }
                    .testTag("islandBody")
                    .animateContentSize(if (reduceMotion) snap() else spring<IntSize>(dampingRatio = 0.6f, stiffness = 900f)),
            ) {
                Box(Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                    when (display) {
                        IslandContent.Idle -> Spacer(Modifier.size(w, h))
                        is IslandContent.Charging -> ChargingContent(display.percent, w, h, accent, text, reduceMotion)
                        is IslandContent.Notification -> NotificationContent(display, fg, text, reduceMotion)
                        is IslandContent.Music ->
                            if (display.open) MusicCard(display, accent, fg, text, h, reduceMotion) else MusicPill(display, w, h, accent, reduceMotion)
                    }
                }
            }
            if (showBubble) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(h).clip(CircleShape).background(bg)
                        .then(if (config.outline) Modifier.border(1.5.dp, accent.copy(alpha = 0.85f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Alarm, contentDescription = "Alarm at $alarm", tint = accent, modifier = Modifier.size(h * 0.62f)) }
            }
        }
        // The quick actions drop-down unfolds downward from the island.
        AnimatedVisibility(
            visible = panel != null,
            enter = if (reduceMotion) EnterTransition.None else
                expandVertically(spring(dampingRatio = 0.8f, stiffness = 600f), expandFrom = Alignment.Top) +
                    scaleIn(tween(260), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                    fadeIn(tween(180)),
            exit = if (reduceMotion) ExitTransition.None else
                shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
        ) {
            Box(Modifier.padding(top = 8.dp)) { holder.panel?.invoke() }
        }
    }
}

// --- Charging ----------------------------------------------------------------------------------

/** A bolt that pulses once, and a fill that slides up to the battery level. */
@Composable
private fun ChargingContent(percent: Int, w: Dp, h: Dp, accent: Color, text: TextStyle, reduceMotion: Boolean) {
    val target = percent.coerceIn(0, 100) / 100f
    val fill = remember { Animatable(if (reduceMotion) target else 0f) }
    LaunchedEffect(target, reduceMotion) {
        if (reduceMotion) fill.snapTo(target) else fill.animateTo(target, tween(360, easing = FastOutSlowInEasing))
    }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(180)
        pulse.animateTo(1.35f, tween(140))
        pulse.animateTo(1f, tween(180))
    }
    Box(Modifier.width(maxOf(w + 170.dp, 260.dp)).height(h + 14.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).background(accent.copy(alpha = 0.22f)).testTag("chargeFill"))
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = "Charging",
                tint = accent,
                modifier = Modifier.size(h * 0.8f).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            )
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = text.copy(color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
    }
}

// --- Notifications -----------------------------------------------------------------------------

/** The text fades in with the card; the app icon pops in last. */
@Composable
private fun NotificationContent(n: IslandContent.Notification, fg: Color, text: TextStyle, reduceMotion: Boolean) {
    val iconScale = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        delay(300)
        iconScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 650f))
    }
    Row(Modifier.width(340.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value }.testTag("notificationIcon")) {
            RoundPicture(n.icon, Icons.Filled.Notifications, 42.dp, fg, CircleShape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.app, style = text.copy(fontSize = 11.sp, color = fg.copy(alpha = 0.6f)), maxLines = 1)
            Text(n.title.ifBlank { n.app }, style = text.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) {
                Text(n.text, style = text.copy(fontSize = 13.sp, color = fg.copy(alpha = 0.8f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Music -------------------------------------------------------------------------------------

/** The old cover slides out to the left as the new one slides in from the right. */
private fun AnimatedContentTransitionScope<IslandContent.Music>.artSlide(reduceMotion: Boolean): ContentTransform =
    if (reduceMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        (slideInHorizontally(tween(300)) { it } + fadeIn(tween(200))) togetherWith
            (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200))) using SizeTransform(clip = true)
    }

private fun IslandContent.Music.songKey() = "$title\u0000$artist"

/** The resting pill while music plays: the cover fills it edge to edge, darkened so the bars stay visible. */
@Composable
private fun MusicPill(m: IslandContent.Music, w: Dp, h: Dp, accent: Color, reduceMotion: Boolean) {
    Box(Modifier.width(w + 120.dp).height(h).testTag("musicPill")) {
        AnimatedContent(m, contentKey = { it.songKey() }, transitionSpec = { artSlide(reduceMotion) }, label = "pillArt", modifier = Modifier.fillMaxSize()) { song ->
            val art = song.art
            if (art != null) {
                Image(art, contentDescription = "Cover of ${song.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.35f)))
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            MusicBars(accent, m.playing, h * 0.62f, reduceMotion)
        }
    }
}

/** The open card: the cover blurred behind everything, the song, the controls and the lyrics button. */
@Composable
private fun MusicCard(m: IslandContent.Music, accent: Color, fg: Color, text: TextStyle, h: Dp, reduceMotion: Boolean) {
    val controls = m.controls
    Box(Modifier.width(340.dp).testTag("musicCard")) {
        Box(Modifier.matchParentSize()) {
            AnimatedContent(m, contentKey = { it.songKey() }, transitionSpec = { artSlide(reduceMotion) }, label = "cardBackground", modifier = Modifier.fillMaxSize()) { song ->
                val art = song.art
                if (art != null) {
                    val blurred = remember(art) { blurredCopy(art) }
                    Image(blurred, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("blurredCover"))
                } else {
                    Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.3f)))
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).clickable { controls?.openApp?.invoke() }) {
                    AnimatedContent(m, contentKey = { it.songKey() }, transitionSpec = { artSlide(reduceMotion) }, label = "cardArt") { song ->
                        RoundPicture(song.art, Icons.Filled.MusicNote, 56.dp, fg, RoundedCornerShape(14.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.artist, style = text.copy(color = fg.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MusicBars(accent, m.playing, h * 0.7f, reduceMotion)
            }
            val lyrics = m.lyrics
            if (lyrics?.open == true) {
                Spacer(Modifier.height(10.dp))
                LyricsPane(lyrics, fg, text, reduceMotion) { controls?.seekTo?.invoke(it) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controls?.previous?.invoke() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = fg, modifier = Modifier.size(30.dp)) }
                IconButton(onClick = { controls?.playPause?.invoke() }, modifier = Modifier.testTag("playPause")) {
                    PlayPauseIcon(m.playing, fg, 34.dp, reduceMotion)
                }
                IconButton(onClick = { controls?.next?.invoke() }) { Icon(Icons.Filled.SkipNext, "Next", tint = fg, modifier = Modifier.size(30.dp)) }
                if (lyrics != null) {
                    Text(
                        "Lyrics",
                        style = text.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (lyrics.open) readableOn(accent) else fg),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (lyrics.open) accent else fg.copy(alpha = 0.16f))
                            .clickable { controls?.toggleLyrics?.invoke() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("lyricsButton"),
                    )
                }
            }
        }
    }
}

/** Lyrics, or a message about them. Timed lyrics follow the song; tap a line to jump there. */
@Composable
private fun LyricsPane(view: LyricsView, fg: Color, text: TextStyle, reduceMotion: Boolean, onSeek: (Long) -> Unit) {
    Box(Modifier.fillMaxWidth().height(210.dp).testTag("lyrics"), contentAlignment = Alignment.Center) {
        val note = text.copy(color = fg.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        when (val s = view.state) {
            LyricsState.Loading -> Text("Looking for lyrics...", style = note)
            LyricsState.NotFound -> Text("No lyrics found for this song", style = note)
            LyricsState.Offline -> Text("Can't reach the lyrics service", style = note)
            is LyricsState.Found -> LyricsList(s.lyrics, view.positionMs, fg, text, reduceMotion, onSeek)
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, positionMs: Long, fg: Color, text: TextStyle, reduceMotion: Boolean, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    val current = if (lyrics.synced) LrcParser.currentIndex(lyrics.lines, positionMs + 150) else -1
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = if (lyrics.synced) maxHeight / 2 - 14.dp else 8.dp
        LazyColumn(state = list, contentPadding = PaddingValues(vertical = pad), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lyrics.lines) { i, line ->
                val isCurrent = i == current
                val time = line.timeMs
                Text(
                    line.text.ifBlank { "♪" },
                    style = text.copy(
                        fontSize = if (isCurrent) 17.sp else 15.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent || !lyrics.synced) fg else fg.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (lyrics.synced && time != null) Modifier.clickable { onSeek(time) } else Modifier)
                        .padding(vertical = 5.dp)
                        .testTag(if (isCurrent) "lyricCurrent" else "lyric$i"),
                )
            }
        }
        // Keep the line being sung in the middle.
        LaunchedEffect(current) {
            if (current < 0) return@LaunchedEffect
            if (list.layoutInfo.visibleItemsInfo.none { it.index == current }) list.scrollToItem(current)
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
            val middle = (list.layoutInfo.viewportStartOffset + list.layoutInfo.viewportEndOffset) / 2
            val delta = (item.offset + item.size / 2 - middle).toFloat()
            if (reduceMotion) list.scrollBy(delta) else list.animateScrollBy(delta, tween(300))
        }
    }
}

@Composable
private fun RoundPicture(picture: ImageBitmap?, fallback: ImageVector, size: Dp, fg: Color, shape: Shape) {
    Box(Modifier.size(size).clip(shape).background(fg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Icon(fallback, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

/** Four bars bouncing at different speeds while music plays; still when paused. */
@Composable
private fun MusicBars(color: Color, playing: Boolean, height: Dp, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(380, 300, 340, 320).forEachIndexed { i, period ->
            val level by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "bar$i",
            )
            val f = if (playing && !reduceMotion) level else listOf(0.45f, 0.75f, 0.6f, 0.4f)[i]
            Box(Modifier.width(4.dp).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
