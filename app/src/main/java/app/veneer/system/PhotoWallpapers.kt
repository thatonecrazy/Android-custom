package app.veneer.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo bundled with the app. Every one is public domain or openly licensed; see assets/photo_credits.txt. */
data class PhotoWallpaper(val id: String, val name: String, val credit: String)

object PhotoWallpapers {
    val all = listOf(
        PhotoWallpaper("higanbana", "Spider lilies", "Chihiro H, CC BY 3.0, via Wikimedia Commons (recolored)"),
        PhotoWallpaper("lone_lily", "Lone lily", "Offnfopt, public domain, via Wikimedia Commons (recolored)"),
        PhotoWallpaper("mirror_sky", "Mirror sky", "Abel Maestro Garcia, public domain, via Wikimedia Commons (tinted)"),
    )

    private fun asset(id: String) = "wallpapers/$id.webp"

    /** Loads a bundled photo cropped to fill [width] x [height], decoding only as much as needed. */
    fun render(context: Context, id: String, width: Int, height: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(asset(id)).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val src = context.assets.open(asset(id)).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = max(width.toFloat() / src.width, height.toFloat() / src.height)
        val cropW = (width / scale).roundToInt().coerceAtMost(src.width)
        val cropH = (height / scale).roundToInt().coerceAtMost(src.height)
        val left = (src.width - cropW) / 2
        val top = (src.height - cropH) / 2
        val out = createBitmap(width, height)
        Canvas(out).drawBitmap(src, Rect(left, top, left + cropW, top + cropH), Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        out
    }.getOrNull()
}
