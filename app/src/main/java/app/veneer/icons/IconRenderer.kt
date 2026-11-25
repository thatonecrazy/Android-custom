package app.veneer.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import app.veneer.data.IconFinish
import app.veneer.data.IconShape
import app.veneer.data.IconStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The parts of the theme that change how an icon is drawn. */
data class IconSpec(
    val shape: IconShape,
    val style: IconStyle,
    val themedFg: Int,
    val themedBg: Int,
    val wrapLegacy: Boolean,
    val legacyBg: Int,
    val finish: IconFinish = IconFinish.Flat,
    val glassTint: Int = 0xFF232327.toInt(),
    val glassClarity: Int = 10,
    val glassShine: Int = 70,
    val glassDepth: Int = 60,
)

enum class IconSource { App, Pack, Image }

/** Turns any icon drawable into a finished bitmap in the user's chosen shape and style. */
object IconRenderer {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    fun render(drawable: Drawable, source: IconSource, sizePx: Int, spec: IconSpec): Bitmap {
        if (spec.finish == IconFinish.Glass) {
            // With the Themed style the glass takes the place of the background color, so only the symbol is drawn flat.
            val symbolOnly = spec.style == IconStyle.Themed && source == IconSource.App
            val flat = renderFlat(drawable, source, sizePx, spec.copy(finish = IconFinish.Flat, themedBg = if (symbolOnly) Color.TRANSPARENT else spec.themedBg))
            return GlassIcons.compose(flat, sizePx, spec, symbolOnly)
        }
        return renderFlat(drawable, source, sizePx, spec)
    }

    private fun renderFlat(drawable: Drawable, source: IconSource, sizePx: Int, spec: IconSpec): Bitmap {
        val size = sizePx.toFloat()
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        when {
            source == IconSource.Image -> {
                drawCenterCrop(canvas, drawable, sizePx)
                applyMask(canvas, IconShapes.path(spec.shape, size) ?: IconShapes.path(IconShape.RoundedSquare, size)!!)
            }
            drawable is AdaptiveIconDrawable -> drawAdaptive(canvas, drawable, sizePx, spec, source)
            source == IconSource.Pack -> drawFit(canvas, drawable, sizePx, 1f)
            else -> drawLegacy(canvas, drawable, sizePx, spec)
        }
        return finish(bitmap, spec.style)
    }

    private fun finish(bitmap: Bitmap, style: IconStyle): Bitmap = when (style) {
        IconStyle.Grayscale -> recolor(bitmap, ColorMatrix().apply { setSaturation(0f) })
        IconStyle.Vibrant -> recolor(bitmap, vividMatrix)
        else -> bitmap
    }

    /** More saturation and a little more contrast, so colors jump off the wallpaper. */
    private val vividMatrix = ColorMatrix().apply {
        setSaturation(1.5f)
        val c = 1.08f
        val t = (1f - c) * 128f
        postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, sizePx: Int, spec: IconSpec, source: IconSource) {
        val size = sizePx.toFloat()
        icon.setBounds(0, 0, sizePx, sizePx)
        val path = IconShapes.path(spec.shape, size) ?: Path(icon.iconMask)
        // Adaptive layers are 108dp with only the middle 72dp visible, so they extend 25% past each edge.
        val inset = (size * 0.25f).roundToInt()
        val layerBounds = Rect(-inset, -inset, sizePx + inset, sizePx + inset)

        if (spec.style == IconStyle.Themed && source == IconSource.App) {
            val mono = if (Build.VERSION.SDK_INT >= 33) icon.monochrome else null
            val glyph = mono ?: icon.foreground?.takeIf { isGlyphLike(it, layerBounds) }
            if (glyph != null) {
                canvas.drawColor(spec.themedBg)
                val g = glyph.constantState?.newDrawable()?.mutate() ?: glyph.mutate()
                g.bounds = layerBounds
                g.colorFilter = PorterDuffColorFilter(spec.themedFg, PorterDuff.Mode.SRC_IN)
                g.draw(canvas)
                applyMask(canvas, path)
                return
            }
        }
        icon.background?.let { it.bounds = layerBounds; it.draw(canvas) }
        icon.foreground?.let { it.bounds = layerBounds; it.draw(canvas) }
        applyMask(canvas, path)
    }

    private fun drawLegacy(canvas: Canvas, drawable: Drawable, sizePx: Int, spec: IconSpec) {
        val path = IconShapes.path(spec.shape, sizePx.toFloat())
        if (!spec.wrapLegacy || path == null) {
            drawFit(canvas, drawable, sizePx, 1f)
            return
        }
        val bg = if (spec.style == IconStyle.Themed) spec.themedBg else spec.legacyBg
        canvas.drawColor(bg)
        drawFit(canvas, drawable, sizePx, 0.7f)
        applyMask(canvas, path)
    }

    /**
     * Draws an app icon through an icon pack's iconback / iconmask / iconupon images, the way packs
     * theme apps they have no icon for.
     */
    fun renderWithPackBack(app: Drawable, pack: IconPack, sizePx: Int, density: Int, seed: Int, spec: IconSpec): Bitmap? {
        if (pack.iconBacks.isEmpty()) return null
        val back = pack.load(pack.iconBacks[Math.floorMod(seed, pack.iconBacks.size)], density) ?: return null
        val result = createBitmap(sizePx, sizePx)
        val canvas = Canvas(result)
        back.setBounds(0, 0, sizePx, sizePx)
        back.draw(canvas)

        val layer = createBitmap(sizePx, sizePx)
        val layerCanvas = Canvas(layer)
        val plain = render(app, IconSource.App, sizePx, spec.copy(shape = IconShape.Original, style = IconStyle.Original, finish = IconFinish.Flat))
        val scaled = (sizePx * pack.scale).roundToInt().coerceIn(1, sizePx)
        val offset = (sizePx - scaled) / 2
        layerCanvas.drawBitmap(plain, null, Rect(offset, offset, offset + scaled, offset + scaled), Paint(Paint.FILTER_BITMAP_FLAG))
        pack.iconMask?.let { name ->
            pack.load(name, density)?.let { mask ->
                val maskBitmap = createBitmap(sizePx, sizePx)
                mask.setBounds(0, 0, sizePx, sizePx)
                mask.draw(Canvas(maskBitmap))
                layerCanvas.drawBitmap(maskBitmap, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            }
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        pack.iconUpon?.let { name ->
            pack.load(name, density)?.let { upon ->
                upon.setBounds(0, 0, sizePx, sizePx)
                upon.draw(canvas)
            }
        }
        val styled = finish(result, spec.style)
        return if (spec.finish == IconFinish.Glass) GlassIcons.compose(styled, sizePx, spec, symbolOnly = false) else styled
    }

    /** Erases everything outside [path], with anti-aliased edges. */
    private fun applyMask(canvas: Canvas, path: Path) {
        val inverse = Path(path).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(inverse, maskPaint)
    }

    private fun drawFit(canvas: Canvas, drawable: Drawable, sizePx: Int, fraction: Float) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val box = sizePx * fraction
        val scale = min(box / w, box / h)
        val dw = w * scale
        val dh = h * scale
        val left = (sizePx - dw) / 2f
        val top = (sizePx - dh) / 2f
        drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + dw).roundToInt(), (top + dh).roundToInt())
        drawable.draw(canvas)
    }

    private fun drawCenterCrop(canvas: Canvas, drawable: Drawable, sizePx: Int) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val scale = max(sizePx.toFloat() / w, sizePx.toFloat() / h)
        val dw = w * scale
        val dh = h * scale
        val left = (sizePx - dw) / 2f
        val top = (sizePx - dh) / 2f
        if (drawable is BitmapDrawable) {
            canvas.drawBitmap(drawable.bitmap, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + dw).roundToInt(), (top + dh).roundToInt())
            drawable.draw(canvas)
        }
    }

    /**
     * For apps without a monochrome layer: a foreground that is mostly transparent is a logo
     * glyph and tints well. A foreground that fills its area would become a solid blob.
     */
    private fun isGlyphLike(foreground: Drawable, layerBounds: Rect): Boolean {
        val probe = 48
        val bmp = createBitmap(probe, probe)
        val copy = foreground.constantState?.newDrawable()?.mutate() ?: return false
        val scale = probe / layerBounds.width().toFloat()
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-layerBounds.left.toFloat(), -layerBounds.top.toFloat())
        copy.bounds = layerBounds
        copy.draw(c)
        // Only look at the visible middle two thirds.
        val from = probe / 6
        val to = probe - from
        var opaque = 0
        var total = 0
        for (y in from until to) for (x in from until to) {
            total++
            if (Color.alpha(bmp[x, y]) > 128) opaque++
        }
        bmp.recycle()
        val coverage = opaque.toFloat() / total
        return coverage in 0.03f..0.55f
    }

    private fun recolor(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = createBitmap(src.width, src.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }
}
