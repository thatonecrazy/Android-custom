package app.veneer.system

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import app.veneer.data.ShuffleTarget
import app.veneer.data.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Rotates the wallpaper through the user's own pictures. It changes while the screen is off, so a
 * new picture is waiting at the next unlock and the switch is never seen.
 */
class WallpaperShuffler(private val context: Context, private val store: StateStore, private val scope: CoroutineScope) {

    val dir: File get() = File(context.filesDir, "wallpapers").apply { mkdirs() }
    private val lock = Mutex()

    fun start() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) scope.launch { maybeAdvance() }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private suspend fun maybeAdvance() {
        val s = store.current.shuffle
        if (!s.enabled || s.files.isEmpty()) return
        if (s.interval.millis > 0 && System.currentTimeMillis() - s.lastChange < s.interval.millis) return
        next()
    }

    /** Shows the next picture now. Returns false if there's nothing to show or it couldn't be set. */
    suspend fun next(): Boolean = lock.withLock {
        val s = store.current.shuffle
        if (s.files.isEmpty()) return@withLock false
        val index = s.next.mod(s.files.size)
        val ok = withContext(Dispatchers.IO) { apply(File(dir, s.files[index]), s.target) }
        store.update { st -> st.copy(shuffle = st.shuffle.copy(next = index + 1, lastChange = System.currentTimeMillis())) }
        ok
    }

    private fun apply(file: File, target: ShuffleTarget): Boolean = runCatching {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val flags = when (target) {
            ShuffleTarget.Both -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            ShuffleTarget.Home -> WallpaperManager.FLAG_SYSTEM
            ShuffleTarget.Lock -> WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
        bitmap.recycle()
        true
    }.getOrDefault(false)

    /** Copies picked pictures into app storage, scaled to the screen. Returns the stored file names. */
    suspend fun import(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val (w, h) = Wallpapers.screenSize(context)
        uris.mapNotNull { uri ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val name = UUID.randomUUID().toString() + ".jpg"
                File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bitmap.recycle()
                name
            }.getOrNull()
        }
    }

    fun delete(name: String) {
        File(dir, name).delete()
    }

    /** A small version of a stored picture for the settings screen. */
    fun thumbnail(name: String, size: Int): Bitmap? = runCatching {
        val file = File(dir, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
