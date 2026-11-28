package app.veneer.system

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileDescriptor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The home screen wallpaper as a picture the Customize previews can draw small.
 *
 * Android only hands the wallpaper picture to apps with a storage permission Veneer doesn't ask
 * for. So Veneer keeps a small copy of every wallpaper it sets itself, together with the number
 * Android gives that wallpaper. While the phone's wallpaper still has that number, previews draw
 * the copy. For any other wallpaper the previews are see-through and show the real one behind
 * the window, at its real size.
 */
object CurrentWallpaper {
    private const val DIR = "preview"
    private const val PICTURE = "wallpaper.jpg"
    private const val NUMBER = "wallpaper.id"

    /** Longest side of the saved copy, in pixels: plenty for a preview a third of the screen tall. */
    private const val COPY_SIDE = 720

    private val _changes = MutableStateFlow(0)

    /** Goes up every time Veneer sets a wallpaper, so previews load the new one. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** Call right after Veneer set [bitmap] as the wallpaper with these WallpaperManager [flags]. */
    fun record(context: Context, bitmap: Bitmap, flags: Int) {
        if (flags and WallpaperManager.FLAG_SYSTEM == 0) return // lock screen only: the home screen didn't change
        runCatching {
            val number = WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val longest = max(bitmap.width, bitmap.height)
            val small = if (longest > COPY_SIDE) {
                val f = COPY_SIDE.toFloat() / longest
                bitmap.scale((bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1))
            } else bitmap
            val temp = File(dir, "$PICTURE.part")
            temp.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (small !== bitmap) small.recycle()
            if (!temp.renameTo(File(dir, PICTURE))) error("couldn't save the wallpaper copy")
            File(dir, NUMBER).writeText(number.toString())
        }
        changed()
    }

    /** Tells previews to look at the wallpaper again, e.g. after another app changed it. */
    fun changed() {
        _changes.value++
    }

    /**
     * The home screen wallpaper, decoded to about [maxSide] pixels on its long side, or null when
     * Veneer can't see it: a live wallpaper, or a picture set by another app.
     */
    fun load(context: Context, maxSide: Int): Bitmap? {
        val wm = WallpaperManager.getInstance(context)
        if (runCatching { wm.wallpaperInfo }.getOrNull() != null) return null // live wallpapers only move behind the window
        val current = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        val dir = File(context.filesDir, DIR)
        val saved = runCatching { File(dir, NUMBER).readText().trim().toInt() }.getOrNull()
        if (current >= 0 && saved == current) decode(maxSide) { opts -> BitmapFactory.decodeFile(File(dir, PICTURE).absolutePath, opts) }?.let { return it }
        return if (Build.VERSION.SDK_INT < 27) readOnOreo(wm, maxSide) else null
    }

    /**
     * Android 8.0 shares the wallpaper picture with any app. From 8.1 on it needs a storage
     * permission Veneer doesn't ask for, so this is only called on 8.0.
     */
    @SuppressLint("MissingPermission")
    private fun readOnOreo(wm: WallpaperManager, maxSide: Int): Bitmap? = try {
        wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
            val fd: FileDescriptor = pfd.fileDescriptor
            decode(maxSide) { opts -> BitmapFactory.decodeFileDescriptor(fd, null, opts) }
        }
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.IOException) {
        null
    }

    /** Decodes in two passes, shrinking by powers of two so a big picture never fills the memory. */
    private fun decode(maxSide: Int, read: (BitmapFactory.Options) -> Bitmap?): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        read(BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
