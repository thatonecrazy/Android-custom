package app.veneer.ui.island

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** A made-up album cover for previews: a striped sun setting into purple waves. Drawn in code. */
object SampleCover {
    val image: ImageBitmap by lazy { draw(256).asImageBitmap() }

    /** A second cover, so previews and tests can show a song change. */
    val other: ImageBitmap by lazy { draw(256, night = true).asImageBitmap() }

    fun draw(size: Int, night: Boolean = false): Bitmap {
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val sky = if (night) intArrayOf(0xFF061A40.toInt(), 0xFF0353A4.toInt(), 0xFF00B4D8.toInt(), 0xFF90E0EF.toInt())
        else intArrayOf(0xFF2B1055.toInt(), 0xFF7B2FF7.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFC371.toInt())
        c.drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, s, sky, floatArrayOf(0f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP)
        })
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                s * 0.5f, s * 0.5f, s * 0.24f,
                if (night) intArrayOf(0xFFFFFFFF.toInt(), 0xFFCAF0F8.toInt()) else intArrayOf(0xFFFFF3B0.toInt(), 0xFFFF9F1C.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(s * 0.5f, s * 0.52f, s * 0.24f, sun)
        // Bands cut across the lower half of the sun.
        val band = Paint().apply { color = sky[1] }
        for (i in 0 until 5) {
            val y = s * (0.56f + i * 0.045f)
            c.drawRect(0f, y, s, y + s * 0.008f * (i + 1), band)
        }
        // Two rolling hills in front.
        val hill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((i, color) in listOf(if (night) 0xFF023E7D.toInt() else 0xFF5A189A.toInt(), if (night) 0xFF001845.toInt() else 0xFF240046.toInt()).withIndex()) {
            hill.color = color
            val top = s * (0.72f + i * 0.08f)
            c.drawPath(Path().apply {
                moveTo(0f, s)
                lineTo(0f, top)
                cubicTo(s * 0.3f, top - s * 0.08f, s * 0.6f, top + s * 0.08f, s, top - s * 0.03f)
                lineTo(s, s)
                close()
            }, hill)
        }
        return bmp
    }
}

/**
 * A soft, blurred copy of [art] for the open music card: shrunk, box-blurred a few times, and left
 * small so it's stretched smoothly when drawn. Cheap enough to redo for every song.
 */
fun blurredCopy(art: ImageBitmap): ImageBitmap {
    val src = art.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    val w = 28
    val h = (w * src.height / src.width.toFloat()).roundToInt().coerceAtLeast(1)
    val small = src.scale(w, h)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) { boxBlur(pixels, w, h, 2) }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(px.size)
    fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) src[line * w + j] else src[j * w + line]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                val out = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            }
        }
    }
    pass(px, tmp, horizontal = true)
    pass(tmp, px, horizontal = false)
}

/** Play and pause that morph into each other: the triangle's two halves become the two bars. */
@Composable
fun PlayPauseIcon(playing: Boolean, color: Color, size: Dp, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (playing) 1f else 0f, if (reduceMotion) snap() else tween(220), label = "playPause")
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        fun mix(a: Float, b: Float) = a + (b - a) * t
        // Points on a 24 x 24 grid: the play triangle split down the middle, and the pause bars.
        val halves = listOf(
            listOf(8f to 5f, 13.5f to 8.5f, 13.5f to 15.5f, 8f to 19f) to listOf(6f to 5f, 10f to 5f, 10f to 19f, 6f to 19f),
            listOf(13.5f to 8.5f, 19f to 12f, 19f to 12f, 13.5f to 15.5f) to listOf(14f to 5f, 18f to 5f, 18f to 19f, 14f to 19f),
        )
        for ((play, pause) in halves) {
            val path = androidx.compose.ui.graphics.Path()
            play.zip(pause).forEachIndexed { i, (a, b) ->
                val x = mix(a.first, b.first) * k
                val y = mix(a.second, b.second) * k
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color)
        }
    }
}
