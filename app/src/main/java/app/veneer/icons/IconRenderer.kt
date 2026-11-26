package app.veneer.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.ColorUtils
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
    val corner: Int = 44,
    val scale: Int = 100,
    val outline: Int = 0,
    val outlineColor: Int = Color.WHITE,
    val shadow: Int = 0,
    val bgOpacity: Int = 100,
    val themedBg2: Int? = null,
    val tint: Int = 0xFF8C7BFF.toInt(),
    val tintStrength: Int = 0,
) {
    /** The outline icons are cut to, or null for the phone's own shape. */
    fun path(size: Float): Path? = IconShapes.path(shape, size, corner)
}

/** What's known about one app's icon, worked out once and reused for every restyle. */
data class IconHints(val dominant: Int? = null, val glyphLike: Boolean? = null)

enum class IconSource { App, Pack, Image }

/**
 * Turns any icon drawable into a finished bitmap in the user's chosen shape and style. It happens in
 * two steps: the art (the icon's picture, or for symbol styles just the symbol and its background
 * color), then the finish (flat with an optional shadow and outline, or glass).
 */
object IconRenderer {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /** Styles that draw the app's symbol in one color on a background. */
    private val symbolStyles = setOf(IconStyle.Themed, IconStyle.Tinted, IconStyle.ColorSymbols)

    /**
     * The icon's picture before its finish. Symbol styles fill [symbol] (just the symbol, on clear)
     * and [base] (the color behind it); everything else fills [full]. [path] is the shape to cut to,
     * or null to leave the picture as it is (icon packs draw their own outline).
     */
    private class Art(
        val full: Bitmap? = null,
        /** Symbol styles: the symbol, already sized and colored, drawn straight onto its background. */
        val symbol: Drawable? = null,
        val base: Int = 0,
        val base2: Int? = null,
        val path: Path?,
    )

    fun render(drawable: Drawable, source: IconSource, sizePx: Int, spec: IconSpec, hints: IconHints = IconHints()): Bitmap =
        finish(art(drawable, source, sizePx, spec, hints), sizePx, spec)

    // --- Art --------------------------------------------------------------------------------

    private fun art(drawable: Drawable, source: IconSource, sizePx: Int, spec: IconSpec, hints: IconHints): Art {
        val size = sizePx.toFloat()
        val scale = spec.scale.coerceIn(50, 150) / 100f
        val shape = spec.path(size)
        return when {
            source == IconSource.Image -> {
                val bmp = createBitmap(sizePx, sizePx)
                drawCenterCrop(Canvas(bmp), drawable, sizePx, scale)
                Art(full = bmp, path = shape ?: IconShapes.path(IconShape.RoundedSquare, size)!!)
            }
            drawable is AdaptiveIconDrawable -> adaptiveArt(drawable, sizePx, spec, source, hints, shape, scale)
            source == IconSource.Pack -> {
                val bmp = createBitmap(sizePx, sizePx)
                drawFit(Canvas(bmp), drawable, sizePx, scale)
                Art(full = bmp, path = null)
            }
            else -> legacyArt(drawable, sizePx, spec, hints, shape, scale)
        }
    }

    private fun adaptiveArt(
        icon: AdaptiveIconDrawable,
        sizePx: Int,
        spec: IconSpec,
        source: IconSource,
        hints: IconHints,
        shape: Path?,
        scale: Float,
    ): Art {
        val size = sizePx.toFloat()
        icon.setBounds(0, 0, sizePx, sizePx)
        val path = shape ?: Path(icon.iconMask)
        // Adaptive layers are 108dp with only the middle 72dp visible, so they extend 25% past each edge.
        val inset = (size * 0.25f).roundToInt()
        val layerBounds = Rect(-inset, -inset, sizePx + inset, sizePx + inset)
        val symbolBounds = scaled(layerBounds, scale)

        if (spec.style in symbolStyles && source == IconSource.App) {
            val mono = if (Build.VERSION.SDK_INT >= 33) icon.monochrome else null
            val glyphLike = hints.glyphLike ?: icon.foreground?.let { isGlyphLike(it, layerBounds) } ?: false
            val glyph = mono ?: icon.foreground?.takeIf { glyphLike }
            if (glyph != null) {
                val colors = symbolColors(spec, hints.dominant ?: dominantColor(icon))
                val g = glyph.constantState?.newDrawable()?.mutate() ?: glyph.mutate()
                g.bounds = symbolBounds
                g.colorFilter = PorterDuffColorFilter(colors.fg, PorterDuff.Mode.SRC_IN)
                return Art(symbol = g, base = colors.bg, base2 = colors.bg2, path = path)
            }
        }
        val full = createBitmap(sizePx, sizePx)
        val canvas = Canvas(full)
        icon.background?.let { bg ->
            bg.bounds = layerBounds
            if (spec.bgOpacity < 100) {
                canvas.saveLayerAlpha(null, spec.bgOpacity.coerceIn(0, 100) * 255 / 100)
                bg.draw(canvas)
                canvas.restore()
            } else {
                bg.draw(canvas)
            }
        }
        icon.foreground?.let { it.bounds = symbolBounds; it.draw(canvas) }
        return Art(full = full, path = path)
    }

    private fun legacyArt(drawable: Drawable, sizePx: Int, spec: IconSpec, hints: IconHints, shape: Path?, scale: Float): Art {
        val bmp = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        if (!spec.wrapLegacy || shape == null) {
            drawFit(canvas, drawable, sizePx, scale)
            return Art(full = bmp, path = null)
        }
        val bg = when (spec.style) {
            IconStyle.Themed, IconStyle.ColorSymbols -> spec.themedBg
            IconStyle.Tinted -> soft(hints.dominant ?: spec.legacyBg)
            else -> spec.legacyBg
        }
        canvas.drawColor(withOpacity(bg, spec.bgOpacity))
        drawFit(canvas, drawable, sizePx, 0.7f * scale)
        return Art(full = bmp, path = shape)
    }

    private class SymbolColors(val fg: Int, val bg: Int, val bg2: Int?)

    private fun symbolColors(spec: IconSpec, dominant: Int): SymbolColors = when (spec.style) {
        IconStyle.Tinted -> SymbolColors(deep(dominant), soft(dominant), null)
        IconStyle.ColorSymbols -> SymbolColors(readableOn(vivid(dominant), spec.themedBg, spec.themedBg2), spec.themedBg, spec.themedBg2)
        else -> SymbolColors(spec.themedFg, spec.themedBg, spec.themedBg2)
    }

    // --- Finish -----------------------------------------------------------------------------

    private fun finish(art: Art, sizePx: Int, spec: IconSpec): Bitmap {
        val symbolArt = art.symbol != null
        val matrix = colorMatrix(spec, symbolArt)
        if (spec.finish == IconFinish.Glass) {
            return if (art.symbol != null) {
                val base = if (spec.style == IconStyle.Tinted) art.base else spec.glassTint
                val symbol = createBitmap(sizePx, sizePx).also { art.symbol.draw(Canvas(it)) }
                GlassIcons.compose(recolor(symbol, matrix), sizePx, spec, symbolOnly = true, base = recolorColor(base, matrix))
            } else {
                GlassIcons.compose(recolor(art.full!!, matrix), sizePx, spec, symbolOnly = false, base = spec.glassTint)
            }
        }
        val body = if (art.symbol != null) {
            val out = createBitmap(sizePx, sizePx)
            val canvas = Canvas(out)
            fillBase(canvas, sizePx.toFloat(), art.base, art.base2, spec.bgOpacity)
            art.symbol.draw(canvas)
            out
        } else art.full!!
        art.path?.let { applyMask(Canvas(body), it) }
        val styled = recolor(body, matrix)
        if (spec.shadow <= 0 && spec.outline <= 0) return styled
        return FlatExtras.compose(styled, sizePx, spec, art.path)
    }

    /** A background color, or a top-to-bottom gradient of two, at the chosen opacity. */
    private fun fillBase(canvas: Canvas, size: Float, color: Int, color2: Int?, opacity: Int) {
        if (color2 == null) {
            canvas.drawColor(withOpacity(color or 0xFF000000.toInt(), opacity))
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (color2 != null) {
            paint.shader = LinearGradient(0f, 0f, size * 0.35f, size, color or 0xFF000000.toInt(), color2 or 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        } else {
            paint.color = color or 0xFF000000.toInt()
        }
        paint.alpha = opacity.coerceIn(0, 100) * 255 / 100
        canvas.drawRect(0f, 0f, size, size, paint)
    }

    /** The style's color change (for full-color icons) and the tint toward one color, as one pass. */
    private fun colorMatrix(spec: IconSpec, symbolArt: Boolean): ColorMatrix? {
        var m: ColorMatrix? = if (symbolArt) null else when (spec.style) {
            IconStyle.Vibrant -> ColorMatrix(vividMatrix)
            IconStyle.Grayscale -> ColorMatrix().apply { setSaturation(0f) }
            IconStyle.Pastel -> ColorMatrix(pastelMatrix)
            else -> null
        }
        if (spec.tintStrength > 0) {
            val t = tintMatrix(spec.tint, spec.tintStrength / 100f)
            m = m?.apply { postConcat(t) } ?: t
        }
        return m
    }

    /** More saturation and a little more contrast, so colors jump off the wallpaper. */
    private val vividMatrix = ColorMatrix().apply {
        setSaturation(1.5f)
        val c = 1.08f
        val t = (1f - c) * 128f
        postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }

    /** Softer, lighter colors. */
    private val pastelMatrix = ColorMatrix().apply {
        setSaturation(0.8f)
        val k = 0.6f
        val lift = 255f * (1f - k)
        postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, lift, 0f, k, 0f, 0f, lift, 0f, 0f, k, 0f, lift, 0f, 0f, 0f, 1f, 0f)))
    }

    /**
     * Mixes each pixel toward [tint] by [strength], keeping how light or dark it is: a pixel's
     * brightness times the tint's color.
     */
    fun tintMatrix(tint: Int, strength: Float): ColorMatrix {
        val r = Color.red(tint) / 255f
        val g = Color.green(tint) / 255f
        val b = Color.blue(tint) / 255f
        val lum = max(0.299f * r + 0.587f * g + 0.114f * b, 0.15f)
        val s = strength.coerceIn(0f, 1f)
        fun row(k: Float, own: Int) = FloatArray(5) { i ->
            val weight = when (i) { 0 -> 0.299f; 1 -> 0.587f; 2 -> 0.114f; else -> 0f }
            (if (i == own) 1f - s else 0f) + s * weight * k / lum
        }
        val m = row(r, 0) + row(g, 1) + row(b, 2) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        return ColorMatrix(m)
    }

    private fun recolorColor(color: Int, matrix: ColorMatrix?): Int {
        if (matrix == null) return color
        val m = matrix.array
        val r = Color.red(color).toFloat()
        val g = Color.green(color).toFloat()
        val b = Color.blue(color).toFloat()
        fun ch(o: Int) = (m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 4]).roundToInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), ch(0), ch(5), ch(10))
    }

    // --- Icon packs ---------------------------------------------------------------------------

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
        val plain = render(app, IconSource.App, sizePx, IconSpec(IconShape.Original, IconStyle.Original, 0, 0, false, 0))
        val scaled = (sizePx * pack.scale * spec.scale.coerceIn(50, 150) / 100f).roundToInt().coerceIn(1, sizePx)
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
        return finish(Art(full = result, path = null), sizePx, spec)
    }

    // --- Looking at an icon -------------------------------------------------------------------

    /** Works out an app icon's main color and whether its foreground is a tintable logo. */
    fun analyze(drawable: Drawable): IconHints {
        if (drawable !is AdaptiveIconDrawable) return IconHints(dominant = dominantColor(drawable), glyphLike = false)
        val probe = 108
        val inset = probe / 4
        val bounds = Rect(-inset, -inset, probe + inset, probe + inset)
        return IconHints(dominantColor(drawable), drawable.foreground?.let { isGlyphLike(it, bounds) } ?: false)
    }

    /** The icon's main color: the most common strong hue, or its average color for gray icons. */
    fun dominantColor(drawable: Drawable): Int {
        val probe = 32
        val bmp = createBitmap(probe, probe)
        val canvas = Canvas(bmp)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        if (copy is AdaptiveIconDrawable) {
            val inset = probe / 4
            val bounds = Rect(-inset, -inset, probe + inset, probe + inset)
            copy.background?.let { it.bounds = bounds; it.draw(canvas) }
            copy.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        } else {
            copy.setBounds(0, 0, probe, probe)
            copy.draw(canvas)
        }
        val buckets = FloatArray(12)
        val sums = Array(12) { FloatArray(3) }
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        var count = 0
        val hsv = FloatArray(3)
        for (y in 0 until probe) for (x in 0 until probe) {
            val c = bmp[x, y]
            if (Color.alpha(c) < 128) continue
            count++
            avgR += Color.red(c); avgG += Color.green(c); avgB += Color.blue(c)
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            val i = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val w = hsv[1] * hsv[2]
            buckets[i] += w
            sums[i][0] += Color.red(c) * w; sums[i][1] += Color.green(c) * w; sums[i][2] += Color.blue(c) * w
        }
        bmp.recycle()
        if (count == 0) return Color.GRAY
        val best = buckets.indices.maxByOrNull { buckets[it] }!!
        return if (buckets[best] > count * 0.05f) {
            val w = buckets[best]
            Color.rgb((sums[best][0] / w).roundToInt(), (sums[best][1] / w).roundToInt(), (sums[best][2] / w).roundToInt())
        } else {
            Color.rgb((avgR / count).roundToInt(), (avgG / count).roundToInt(), (avgB / count).roundToInt())
        }
    }

    /** A pale version of a color, for backgrounds. */
    fun soft(color: Int): Int = ColorUtils.blendARGB(color or 0xFF000000.toInt(), Color.WHITE, 0.78f)

    /** A deep version of a color, for symbols on its pale version. */
    fun deep(color: Int): Int = ColorUtils.blendARGB(color or 0xFF000000.toInt(), Color.BLACK, 0.42f)

    private fun vivid(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = min(1f, hsv[1] * 1.2f + 0.1f)
        hsv[2] = max(hsv[2], 0.55f)
        return Color.HSVToColor(hsv)
    }

    /** Lightens or darkens [fg] until it stands out against [bg] (and [bg2], for gradients). */
    private fun readableOn(fg: Int, bg: Int, bg2: Int? = null): Int {
        val backs = listOfNotNull(bg, bg2).map { it or 0xFF000000.toInt() }
        val darkBack = backs.map { ColorUtils.calculateLuminance(it) }.average() < 0.5
        val towards = if (darkBack) Color.WHITE else Color.BLACK
        var c = fg or 0xFF000000.toInt()
        var step = 0
        while (backs.minOf { ColorUtils.calculateContrast(c, it) } < 3.6 && step < 12) {
            c = ColorUtils.blendARGB(c, towards, 0.14f)
            step++
        }
        return c
    }

    private fun withOpacity(color: Int, opacity: Int) = ColorUtils.setAlphaComponent(color, opacity.coerceIn(0, 100) * 255 / 100)

    // --- Drawing helpers ----------------------------------------------------------------------

    /** Erases everything outside [path], with anti-aliased edges. */
    fun applyMask(canvas: Canvas, path: Path) {
        val inverse = Path(path).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(inverse, maskPaint)
    }

    private fun scaled(r: Rect, k: Float): Rect {
        if (k == 1f) return Rect(r)
        val cx = r.exactCenterX()
        val cy = r.exactCenterY()
        val hw = r.width() * k / 2f
        val hh = r.height() * k / 2f
        return Rect((cx - hw).roundToInt(), (cy - hh).roundToInt(), (cx + hw).roundToInt(), (cy + hh).roundToInt())
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

    private fun drawCenterCrop(canvas: Canvas, drawable: Drawable, sizePx: Int, zoom: Float) {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val scale = max(sizePx.toFloat() / w, sizePx.toFloat() / h) * zoom
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

    private fun recolor(src: Bitmap, matrix: ColorMatrix?): Bitmap {
        if (matrix == null) return src
        val out = createBitmap(src.width, src.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }
}
