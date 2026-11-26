package app.veneer.icons

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import app.veneer.data.IconShape
import kotlin.math.roundToInt

/**
 * The Glass finish: an icon sitting on a glossy, slightly puffed-up piece of glass, lit from above.
 * Everything that is the same for every icon (the shadow, the shading, the reflection and the rims)
 * is drawn once per shape and size and reused, so restyling dozens of icons stays quick.
 */
object GlassIcons {

    /** How much of the icon's square the glass takes up; the rest leaves room for its shadow. */
    const val BODY = 0.86f

    private class Layers(
        val body: Int,
        val left: Float,
        val top: Float,
        val path: Path,
        val shadow: Bitmap?,
        /** Shading drawn under the symbol: light at the top, dark at the bottom. */
        val under: Bitmap,
        /** The reflection, rims and outline, drawn over everything. */
        val over: Bitmap,
    )

    private val cache = object : LruCache<String, Layers>(24) {}

    private fun layers(spec: IconSpec, sizePx: Int): Layers {
        val key = "$sizePx|${spec.shape}|${spec.corner}|${spec.glassShine}|${spec.glassDepth}|${spec.outline}|${spec.outlineColor}"
        cache.get(key)?.let { return it }
        val built = build(spec, sizePx)
        cache.put(key, built)
        return built
    }

    private fun build(spec: IconSpec, sizePx: Int): Layers {
        val s = sizePx.toFloat()
        val body = (s * BODY).roundToInt().coerceAtLeast(1)
        val b = body.toFloat()
        val left = (s - b) / 2f
        val top = s * 0.035f
        val path = bodyPath(spec.shape, b, spec.corner)
        val shine = spec.glassShine.coerceIn(0, 100) / 100f
        val depth = spec.glassDepth.coerceIn(0, 100) / 100f

        val shadow = if (depth > 0f) {
            createBitmap(sizePx, sizePx).also { bmp ->
                val c = Canvas(bmp)
                c.translate(left, top + s * 0.035f * (0.5f + depth))
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((150 * depth).roundToInt(), 0, 0, 0)
                    maskFilter = BlurMaskFilter(s * 0.04f, BlurMaskFilter.Blur.NORMAL)
                })
            }
        } else null

        val under = createBitmap(body, body)
        Canvas(under).drawRect(0f, 0f, b, b, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, b,
                intArrayOf(Color.argb((70 * shine).roundToInt(), 255, 255, 255), Color.TRANSPARENT, Color.argb((120 * depth).roundToInt(), 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        })

        val over = createBitmap(body, body)
        val c = Canvas(over)
        if (shine > 0f) {
            c.drawOval(RectF(-b * 0.12f, -b * 0.45f, b * 1.12f, b * 0.44f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, b * 0.44f, Color.argb((95 * shine).roundToInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            })
        }
        if (depth > 0f) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = b * 0.09f
                shader = LinearGradient(0f, 0f, 0f, b, Color.TRANSPARENT, Color.argb((110 * depth).roundToInt(), 0, 0, 0), Shader.TileMode.CLAMP)
            })
        }
        if (shine > 0f) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = b * 0.04f
                shader = LinearGradient(
                    0f, 0f, b * 0.7f, b,
                    intArrayOf(Color.argb((230 * shine).roundToInt(), 255, 255, 255), Color.argb((25 * shine).roundToInt(), 255, 255, 255), Color.argb((90 * shine).roundToInt(), 255, 255, 255)),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
            })
        }
        if (spec.outline > 0) {
            // Drawn on the inside of the edge so it isn't cut off.
            val w = b * spec.outline / 1000f
            c.drawPath(insetPath(path, b, w / 2f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = spec.outlineColor
            })
        }
        IconRenderer.applyMask(c, path)
        return Layers(body, left, top, path, shadow, under, over)
    }

    /**
     * [content] is the icon drawn flat at full size. With [symbolOnly] it is just the symbol on a
     * clear background and sits on glass in the [base] color; otherwise the icon's own colors
     * become the glass.
     */
    fun compose(content: Bitmap, sizePx: Int, spec: IconSpec, symbolOnly: Boolean, base: Int): Bitmap {
        val l = layers(spec, sizePx)
        val depth = spec.glassDepth.coerceIn(0, 100) / 100f
        val clarity = spec.glassClarity.coerceIn(0, 90) / 100f
        val bodyRect = Rect(0, 0, l.body, l.body)
        val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Everything is drawn straight into the result: the glass first, cut to its shape, then the
        // shadow slipped in behind it.
        val out = createBitmap(sizePx, sizePx)
        val c = Canvas(out)
        c.save()
        c.translate(l.left, l.top)
        if (symbolOnly) {
            c.drawColor(ColorUtils.setAlphaComponent(base, ((1f - clarity) * 255).roundToInt().coerceIn(0, 255)))
            c.drawBitmap(l.under, 0f, 0f, null)
            if (depth > 0f) drawSymbolShadow(c, content, sizePx, l.body, depth)
            c.drawBitmap(content, null, bodyRect, filter)
        } else {
            filter.alpha = (255 - clarity * 0.6f * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(content, null, bodyRect, filter)
            c.drawBitmap(l.under, 0f, 0f, null)
        }
        c.drawBitmap(l.over, 0f, 0f, null)
        IconRenderer.applyMask(c, l.path)
        c.restore()
        l.shadow?.let { c.drawBitmap(it, 0f, 0f, behind) }
        content.recycle()
        return out
    }

    private val behind = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }

    /** A soft shadow under the symbol, so it looks pressed into the glass. */
    private fun drawSymbolShadow(c: Canvas, symbol: Bitmap, sizePx: Int, body: Int, depth: Float) {
        val offset = IntArray(2)
        val mask = symbol.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(sizePx * 0.018f + 0.5f, BlurMaskFilter.Blur.NORMAL) }, offset)
        val k = body / sizePx.toFloat()
        val dy = body * 0.016f
        c.drawBitmap(
            mask,
            null,
            RectF(offset[0] * k, offset[1] * k + dy, (offset[0] + mask.width) * k, (offset[1] + mask.height) * k + dy),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = Color.argb((130 * depth).roundToInt(), 0, 0, 0) },
        )
        mask.recycle()
    }

    /** The glass needs a real outline; the phone's own shape falls back to a squircle. */
    fun bodyPath(shape: IconShape, size: Float, corner: Int = 44): Path =
        IconShapes.path(shape, size, corner) ?: IconShapes.path(IconShape.Squircle, size)!!

    /** [path] shrunk around its center so a stroke of twice [by] stays inside the original edge. */
    fun insetPath(path: Path, size: Float, by: Float): Path {
        val k = ((size - by * 2f) / size).coerceAtLeast(0.1f)
        return Path(path).apply { transform(Matrix().apply { setScale(k, k, size / 2f, size / 2f) }) }
    }
}

/** A soft shadow and an outline for flat icons. The shadow is drawn once per shape and size. */
object FlatExtras {

    private val shadows = object : LruCache<String, Bitmap>(24) {}

    fun compose(icon: Bitmap, sizePx: Int, spec: IconSpec, path: Path?): Bitmap {
        val s = sizePx.toFloat()
        val shadow = spec.shadow.coerceIn(0, 100) / 100f
        val b = if (shadow > 0f) s * 0.9f else s
        val left = (s - b) / 2f
        val top = if (shadow > 0f) s * 0.02f else 0f
        val out = createBitmap(sizePx, sizePx)
        val o = Canvas(out)
        if (shadow > 0f) {
            val shape = path?.let { scaledTo(it, s, b) }
            val bmp = if (shape != null) {
                val key = "$sizePx|${spec.shape}|${spec.corner}|${spec.shadow}"
                shadows.get(key) ?: shadowOf(shape, sizePx, left, top, s, shadow).also { shadows.put(key, it) }
            } else {
                // Icon pack art has its own outline, so its shadow follows the picture itself.
                alphaShadow(icon, sizePx, left, top, b, s, shadow)
            }
            o.drawBitmap(bmp, 0f, 0f, null)
        }
        o.drawBitmap(icon, null, RectF(left, top, left + b, top + b), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        icon.recycle()
        if (spec.outline > 0 && path != null) {
            val w = b * spec.outline / 1000f
            val ring = GlassIcons.insetPath(scaledTo(path, s, b), b, w / 2f).apply { offset(left, top) }
            o.drawPath(ring, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = spec.outlineColor
            })
        }
        return out
    }

    private fun scaledTo(path: Path, from: Float, to: Float): Path =
        if (from == to) Path(path) else Path(path).apply { transform(Matrix().apply { setScale(to / from, to / from) }) }

    private fun shadowOf(shape: Path, sizePx: Int, left: Float, top: Float, s: Float, strength: Float): Bitmap =
        createBitmap(sizePx, sizePx).also { bmp ->
            val c = Canvas(bmp)
            c.translate(left, top + s * 0.035f)
            c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((170 * strength).roundToInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(s * 0.035f, BlurMaskFilter.Blur.NORMAL)
            })
        }

    private fun alphaShadow(icon: Bitmap, sizePx: Int, left: Float, top: Float, b: Float, s: Float, strength: Float): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val offset = IntArray(2)
        val mask = icon.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(s * 0.035f, BlurMaskFilter.Blur.NORMAL) }, offset)
        val k = b / s
        Canvas(out).drawBitmap(
            mask,
            null,
            RectF(left + offset[0] * k, top + s * 0.035f + offset[1] * k, left + (offset[0] + mask.width) * k, top + s * 0.035f + (offset[1] + mask.height) * k),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = Color.argb((170 * strength).roundToInt(), 0, 0, 0) },
        )
        mask.recycle()
        return out
    }
}
