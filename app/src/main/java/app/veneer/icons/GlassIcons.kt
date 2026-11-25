package app.veneer.icons

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import app.veneer.data.IconShape
import kotlin.math.roundToInt

/**
 * The Glass finish: an icon sitting on a glossy, slightly puffed-up piece of glass, lit from above.
 * Built from layers: a soft shadow, the glass body, shading, a reflection across the top and a bright rim.
 */
object GlassIcons {

    /** How much of the icon's square the glass takes up; the rest leaves room for its shadow. */
    const val BODY = 0.86f

    /**
     * [content] is the icon drawn flat. With [symbolOnly] it is just the symbol on a clear background and
     * sits on glass in the tint color; otherwise the icon's own colors become the glass.
     */
    fun compose(content: Bitmap, sizePx: Int, spec: IconSpec, symbolOnly: Boolean): Bitmap {
        val s = sizePx.toFloat()
        val body = s * BODY
        val left = (s - body) / 2f
        val top = s * 0.035f
        val rect = RectF(left, top, left + body, top + body)
        val path = bodyPath(spec.shape, body).apply { offset(left, top) }
        val shine = spec.glassShine.coerceIn(0, 100) / 100f
        val depth = spec.glassDepth.coerceIn(0, 100) / 100f
        val clarity = spec.glassClarity.coerceIn(0, 90) / 100f

        val out = createBitmap(sizePx, sizePx)
        val c = Canvas(out)

        // A soft shadow below, so the glass seems to float above the wallpaper.
        if (depth > 0f) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((150 * depth).roundToInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(s * 0.04f, BlurMaskFilter.Blur.NORMAL)
            }
            c.save()
            c.translate(0f, s * 0.035f * (0.5f + depth))
            c.drawPath(path, shadow)
            c.restore()
        }

        c.save()
        c.clipPath(path)
        val solid = ((1f - clarity) * 255).roundToInt().coerceIn(0, 255)
        val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (symbolOnly) {
            c.drawColor(ColorUtils.setAlphaComponent(spec.glassTint, solid))
        } else {
            filter.alpha = (255 - clarity * 0.6f * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(content, null, rect, filter)
            filter.alpha = 255
        }

        // Lighter at the top, darker at the bottom: reads as a curved surface.
        c.drawRect(rect, Paint().apply {
            shader = LinearGradient(
                0f, top, 0f, top + body,
                intArrayOf(Color.argb((70 * shine).roundToInt(), 255, 255, 255), Color.TRANSPARENT, Color.argb((120 * depth).roundToInt(), 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        })

        // The symbol, pressed slightly into the glass with its own small shadow.
        if (symbolOnly) {
            if (depth > 0f) {
                val offset = IntArray(2)
                val mask = content.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(s * 0.018f, BlurMaskFilter.Blur.NORMAL) }, offset)
                c.save()
                c.translate(left, top + s * 0.014f)
                c.scale(body / s, body / s)
                c.drawBitmap(mask, offset[0].toFloat(), offset[1].toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((130 * depth).roundToInt(), 0, 0, 0)
                })
                c.restore()
                mask.recycle()
            }
            c.drawBitmap(content, null, rect, filter)
        }

        // A curved reflection across the top half.
        if (shine > 0f) {
            c.drawOval(
                RectF(left - body * 0.12f, top - body * 0.45f, left + body * 1.12f, top + body * 0.44f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, top, 0f, top + body * 0.44f,
                        Color.argb((95 * shine).roundToInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

        // Edges: a dark inner rim at the bottom for thickness, and a bright rim where light catches the top.
        if (depth > 0f) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = body * 0.09f
                shader = LinearGradient(0f, top, 0f, top + body, Color.TRANSPARENT, Color.argb((110 * depth).roundToInt(), 0, 0, 0), Shader.TileMode.CLAMP)
            })
        }
        if (shine > 0f) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = body * 0.04f
                shader = LinearGradient(
                    left, top, left + body * 0.7f, top + body,
                    intArrayOf(Color.argb((230 * shine).roundToInt(), 255, 255, 255), Color.argb((25 * shine).roundToInt(), 255, 255, 255), Color.argb((90 * shine).roundToInt(), 255, 255, 255)),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }
        c.restore()
        return out
    }

    /** The glass needs a real outline; the phone's own shape falls back to a squircle. */
    fun bodyPath(shape: IconShape, size: Float): Path =
        IconShapes.path(shape, size) ?: IconShapes.path(IconShape.Squircle, size)!!
}
