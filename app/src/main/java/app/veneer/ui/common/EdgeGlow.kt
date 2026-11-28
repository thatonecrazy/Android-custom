package app.veneer.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.veneer.data.ThemeConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Asks every open Veneer screen to play the edge glow once, in [color]. */
object GlowBus {
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events: SharedFlow<Int> = _events.asSharedFlow()
    fun fire(color: Int) {
        _events.tryEmit(color)
    }
}

/** Glow colors for each sound mode: green for sound, blue for vibrate, red for silent. */
fun soundModeColor(mode: Int, fallback: Int): Int = when (mode) {
    AudioManager.RINGER_MODE_NORMAL -> 0xFF22C55E.toInt()
    AudioManager.RINGER_MODE_VIBRATE -> 0xFF3D7BFF.toInt()
    AudioManager.RINGER_MODE_SILENT -> 0xFFFF3B3B.toInt()
    else -> fallback
}

/** Fires the glow when the phone is unlocked or the sound mode changes, as the theme asks. */
@Composable
fun EdgeGlowTriggers(theme: ThemeConfig) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(theme)
    DisposableEffect(theme.edgeGlow, theme.soundModeGlow) {
        if (!theme.edgeGlow && !theme.soundModeGlow) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val t = latest
                val color = t.edgeGlowColor ?: t.accent
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> if (t.edgeGlow) GlowBus.fire(color)
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> if (t.soundModeGlow && !isInitialStickyBroadcast) {
                        GlowBus.fire(soundModeColor(intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), color))
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/** Plays one glow: a sweep around the edge, then a fade. Draw it with [drawEdgeGlow]. */
class GlowPlayer {
    val sweep = Animatable(0f)
    val fade = Animatable(0f)
    var color by mutableStateOf(Color.Red)

    val playing: Boolean get() = fade.value > 0f

    suspend fun play(c: Color) {
        color = c
        fade.snapTo(1f)
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(1100, easing = LinearEasing))
    }
}

/**
 * A light that sweeps around the screen's rounded edge, then fades: Veneer's take on edge
 * lighting. It only draws while a glow is playing.
 */
@Composable
fun EdgeGlowOverlay() {
    val view = LocalView.current
    val density = LocalDensity.current
    val player = remember { GlowPlayer() }
    val corner = remember(view) {
        val fromDevice = if (Build.VERSION.SDK_INT >= 31) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
        } else null
        fromDevice?.takeIf { it > 0f } ?: with(density) { 40.dp.toPx() }
    }
    LaunchedEffect(Unit) {
        GlowBus.events.collect { c -> launch { player.play(Color(c)) } }
    }
    if (!player.playing) return
    Canvas(Modifier.fillMaxSize()) { drawEdgeGlow(player, corner) }
}

/**
 * Draws [player]'s glow around this area's edge, rounded by [cornerPx]. [widthScale] thins the
 * light for small previews.
 */
fun DrawScope.drawEdgeGlow(player: GlowPlayer, cornerPx: Float, widthScale: Float = 1f) {
    val a = player.fade.value
    if (a <= 0f) return
    val inset = 1.5.dp.toPx() * widthScale
    val r = (cornerPx - inset).coerceAtLeast(0f)
    // The full outline, building up as the sweep goes round.
    val outline = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r)),
        )
    }
    val measure = PathMeasure().apply { setPath(outline, true) }
    val length = measure.length
    // Two arcs grow from the bottom middle, meeting at the top: like a ring of light closing.
    val start = length * 0.5f
    val grown = length * 0.5f * player.sweep.value
    fun arc(from: Float, to: Float): Path {
        val p = Path()
        val a0 = ((from % length) + length) % length
        val a1 = ((to % length) + length) % length
        if (a0 <= a1) measure.getSegment(a0, a1, p, true) else {
            measure.getSegment(a0, length, p, true)
            measure.getSegment(0f, a1, p, true)
        }
        return p
    }
    val lit = Path().apply {
        addPath(arc(start - grown, start))
        addPath(arc(start, start + grown))
    }
    listOf(26.dp to 0.10f, 14.dp to 0.22f, 7.dp to 0.45f, 3.dp to 1f).forEach { (w, alpha) ->
        drawPath(lit, player.color.copy(alpha = alpha * a), style = Stroke(width = w.toPx() * widthScale, cap = StrokeCap.Round))
    }
}
