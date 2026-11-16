package app.veneer.system

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import app.veneer.data.WallpaperSpec
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * An original, code-drawn wallpaper: glowing segmented tendrils curling up from the bottom corner,
 * and (when a third color is set) a watching eye with a black sclera and a glowing iris.
 */
internal object Tendrils {

    fun draw(canvas: Canvas, spec: WallpaperSpec, w: Float, h: Float) {
        val main = spec.color1 or BLACK
        val deep = spec.color2 or BLACK
        canvas.drawColor(ColorUtils.blendARGB(deep, BLACK, 0.92f))
        glow(canvas, w * 0.88f, h * 0.98f, max(w, h) * 0.75f, deep, 0xD0)
        glow(canvas, w * 0.05f, h * 0.02f, max(w, h) * 0.5f, deep, 0x60)

        val origin = PointF(w * 1.02f, h * 1.03f)
        val tendrils = listOf(
            Tendril(PointF(w * 0.06f, h * 0.33f), PointF(-0.10f, -0.05f), PointF(0.25f, 0.10f), 0.060f),
            Tendril(PointF(w * 0.90f, h * 0.06f), PointF(-0.32f, -0.06f), PointF(-0.10f, 0.06f), 0.046f),
            Tendril(PointF(w * 0.00f, h * 0.70f), PointF(-0.05f, 0.05f), PointF(0.10f, 0.12f), 0.052f),
            Tendril(PointF(w * 0.99f, h * 0.48f), PointF(-0.10f, -0.04f), PointF(-0.12f, 0.04f), 0.030f),
        )
        tendrils.forEachIndexed { i, t -> drawTendril(canvas, origin, t, w, h, main, Random(11 + i)) }

        spec.color3?.let { eye(canvas, w * 0.5f, h * 0.405f, w * 0.46f, it or BLACK) }
    }

    private data class Tendril(val end: PointF, val bend1: PointF, val bend2: PointF, val thickness: Float)

    private fun bezier(p0: PointF, c1: PointF, c2: PointF, p3: PointF, t: Float): PointF {
        val u = 1 - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return PointF(a * p0.x + b * c1.x + c * c2.x + d * p3.x, a * p0.y + b * c1.y + c * c2.y + d * p3.y)
    }

    private fun drawTendril(canvas: Canvas, origin: PointF, t: Tendril, w: Float, h: Float, main: Int, rnd: Random) {
        val end = t.end
        val c1 = PointF(origin.x + (end.x - origin.x) * 0.3f + t.bend1.x * w, origin.y + (end.y - origin.y) * 0.25f + t.bend1.y * h)
        val c2 = PointF(origin.x + (end.x - origin.x) * 0.75f + t.bend2.x * w, origin.y + (end.y - origin.y) * 0.8f + t.bend2.y * h)
        val maxR = w * t.thickness
        val steps = 320
        fun radius(s: Float) = maxR * (1 - s).pow(0.8f) + w * 0.0025f
        val dark = ColorUtils.blendARGB(main, BLACK, 0.7f)
        val bright = ColorUtils.blendARGB(main, WHITE, 0.25f)

        // Soft glow underneath.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(main, 0x48)
            maskFilter = BlurMaskFilter(max(maxR * 0.9f, 1f), BlurMaskFilter.Blur.NORMAL)
        }
        for (i in 0..steps step 6) {
            val s = i / steps.toFloat()
            val p = bezier(origin, c1, c2, end, s)
            canvas.drawCircle(p.x, p.y, radius(s) * 1.5f, glow)
        }

        // The body: overlapping discs shading from dark at the root to bright at the tip.
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ColorUtils.blendARGB(main, BLACK, 0.8f) }
        val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        val segment = 14 + rnd.nextInt(4)
        for (i in 0..steps) {
            val s = i / steps.toFloat()
            val p = bezier(origin, c1, c2, end, s)
            val r = radius(s)
            body.color = ColorUtils.blendARGB(dark, main, min(1f, s * 1.3f))
            canvas.drawCircle(p.x, p.y, r, body)
            if (i % segment == 0 && s < 0.92f) {
                ring.strokeWidth = max(r * 0.14f, 1f)
                canvas.drawCircle(p.x, p.y, r * 0.98f, ring)
            }
        }
        // A highlight along one side makes them look rounded.
        for (i in 0..steps) {
            val s = i / steps.toFloat()
            val p = bezier(origin, c1, c2, end, s)
            val r = radius(s)
            shine.color = ColorUtils.setAlphaComponent(bright, (40 * (1 - s)).toInt() + 12)
            canvas.drawCircle(p.x - r * 0.35f, p.y - r * 0.35f, r * 0.28f, shine)
        }
    }

    private fun eye(canvas: Canvas, cx: Float, cy: Float, width: Float, iris: Int) {
        val half = width / 2f
        val height = width * 0.36f
        val almond = Path().apply {
            moveTo(cx - half, cy)
            quadTo(cx, cy - height, cx + half, cy)
            quadTo(cx, cy + height, cx - half, cy)
            close()
        }
        // Veins spreading out from the corners and edges.
        val vein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = ColorUtils.setAlphaComponent(iris, 0xB0)
        }
        val rnd = Random(3)
        repeat(16) { i ->
            val angle = (i / 16.0) * Math.PI * 2 + rnd.nextDouble(-0.15, 0.15)
            val sx = cx + cos(angle).toFloat() * half * 0.92f
            val sy = cy + sin(angle).toFloat() * height * 0.5f
            var x = sx
            var y = sy
            var dir = angle
            vein.strokeWidth = width * 0.008f
            val path = Path().apply { moveTo(x, y) }
            repeat(5) {
                dir += rnd.nextDouble(-0.5, 0.5)
                val len = width * rnd.nextDouble(0.03, 0.07).toFloat()
                x += cos(dir).toFloat() * len
                y += sin(dir).toFloat() * len
                path.lineTo(x, y)
            }
            canvas.drawPath(path, vein)
        }
        // Red rim glow, then the black sclera.
        canvas.drawPath(almond, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(iris, 0xC0)
            maskFilter = BlurMaskFilter(width * 0.05f, BlurMaskFilter.Blur.OUTER)
        })
        canvas.drawPath(almond, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF050404.toInt() })
        canvas.withClip(almond) {
            val irisR = height * 0.62f
            drawCircle(cx, cy, irisR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, irisR,
                    intArrayOf(ColorUtils.blendARGB(iris, WHITE, 0.35f), iris, ColorUtils.blendARGB(iris, BLACK, 0.55f)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
                )
            })
            drawOval(cx - irisR * 0.22f, cy - irisR * 0.42f, cx + irisR * 0.22f, cy + irisR * 0.42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK })
            drawCircle(cx - irisR * 0.35f, cy - irisR * 0.35f, irisR * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        }
        canvas.drawPath(almond, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width * 0.012f
            color = ColorUtils.blendARGB(iris, BLACK, 0.4f)
        })
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = RadialGradient(
                x, y, max(r, 1f),
                intArrayOf(ColorUtils.setAlphaComponent(color, alpha), ColorUtils.setAlphaComponent(color, 0)),
                null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
