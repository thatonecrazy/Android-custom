package app.veneer.ui.island

import android.app.PendingIntent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.data.IslandConfig

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
    ) : IslandContent()
}

class MusicControls(val previous: () -> Unit, val playPause: () -> Unit, val next: () -> Unit, val openApp: () -> Unit)

/**
 * The island itself: a pill that grows into a wider pill or a card for what's happening. [alarm]
 * adds a small bubble beside it. The pill stays centered on the camera either way.
 */
@Composable
fun IslandView(
    config: IslandConfig,
    accent: Color,
    content: IslandContent?,
    alarm: String?,
    modifier: Modifier = Modifier,
    font: androidx.compose.ui.text.font.FontFamily? = null,
    onTap: () -> Unit = {},
) {
    if (content == null || (content == IslandContent.Idle && !config.idlePill)) return
    val bg = Color(config.background)
    val fg = Color(config.content)
    val h = config.heightDp.coerceIn(20, 60).dp
    val w = config.widthDp.coerceIn(40, 300).dp
    val showBubble = alarm != null && (content == IslandContent.Idle || content is IslandContent.Music && !content.open)
    val card = content is IslandContent.Notification || content is IslandContent.Music && content.open
    val shape: Shape = if (card) RoundedCornerShape(28.dp) else RoundedCornerShape(50)
    val text = TextStyle(color = fg, fontFamily = font, fontSize = 14.sp)

    Row(modifier.testTag("island"), verticalAlignment = Alignment.Top) {
        // Same width as the bubble on the other side, so the pill stays over the camera.
        if (showBubble) Spacer(Modifier.width(h + 6.dp))
        Box(
            Modifier
                .animateContentSize(spring(dampingRatio = 0.78f, stiffness = 420f))
                .clip(shape)
                .background(bg)
                .then(if (config.outline) Modifier.border(1.5.dp, accent.copy(alpha = 0.85f), shape) else Modifier)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        ) {
            when (content) {
                IslandContent.Idle -> Spacer(Modifier.size(w, h))
                is IslandContent.Charging -> Box(Modifier.width(maxOf(w + 170.dp, 260.dp)).height(h + 14.dp)) {
                    // The pill fills up with the battery level.
                    Box(Modifier.fillMaxHeight().fillMaxWidth(content.percent.coerceIn(0, 100) / 100f).background(accent.copy(alpha = 0.22f)))
                    Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = "Charging", tint = accent, modifier = Modifier.size(h * 0.8f))
                        Spacer(Modifier.weight(1f))
                        Text("${content.percent}%", style = text.copy(color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    }
                }
                is IslandContent.Notification -> Row(
                    Modifier.width(340.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundPicture(content.icon, Icons.Filled.Notifications, 42.dp, fg, CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(content.app, style = text.copy(fontSize = 11.sp, color = fg.copy(alpha = 0.6f)), maxLines = 1)
                        Text(content.title.ifBlank { content.app }, style = text.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (content.text.isNotBlank()) {
                            Text(content.text, style = text.copy(fontSize = 13.sp, color = fg.copy(alpha = 0.8f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                is IslandContent.Music -> if (content.open) {
                    Column(Modifier.width(340.dp).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clickable { content.controls?.openApp?.invoke() }) {
                                RoundPicture(content.art, Icons.Filled.MusicNote, 56.dp, fg, RoundedCornerShape(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(content.title, style = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(content.artist, style = text.copy(color = fg.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            MusicBars(accent, content.playing, h * 0.7f)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { content.controls?.previous?.invoke() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = fg, modifier = Modifier.size(30.dp)) }
                            IconButton(onClick = { content.controls?.playPause?.invoke() }) {
                                Icon(if (content.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = fg, modifier = Modifier.size(38.dp))
                            }
                            IconButton(onClick = { content.controls?.next?.invoke() }) { Icon(Icons.Filled.SkipNext, "Next", tint = fg, modifier = Modifier.size(30.dp)) }
                        }
                    }
                } else {
                    Row(
                        Modifier.width(w + 120.dp).height(h).padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundPicture(content.art, Icons.Filled.MusicNote, h - 8.dp, fg, CircleShape)
                        Spacer(Modifier.weight(1f))
                        MusicBars(accent, content.playing, h * 0.62f)
                        Spacer(Modifier.width(8.dp))
                    }
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
}

@Composable
private fun RoundPicture(picture: ImageBitmap?, fallback: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, fg: Color, shape: Shape) {
    Box(Modifier.size(size).clip(shape).background(fg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Icon(fallback, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

/** Four bars bouncing at different speeds while music plays; still when paused. */
@Composable
private fun MusicBars(color: Color, playing: Boolean, height: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(520, 380, 610, 450).forEachIndexed { i, period ->
            val level by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "bar$i",
            )
            val f = if (playing) level else listOf(0.45f, 0.75f, 0.6f, 0.4f)[i]
            Box(Modifier.width(4.dp).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
