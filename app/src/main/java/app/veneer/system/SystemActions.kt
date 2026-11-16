package app.veneer.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import app.veneer.data.WallpaperSpec
import app.veneer.data.WallpaperStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max

/** Things a launcher can ask the system to do. */
object SystemActions {

    /** Pulls down the notification shade. Uses the long-standing StatusBarManager method launchers rely on. */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    /** Pulls down quick settings, through the same StatusBarManager method as the notification shade. */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandQuickSettings(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(service)
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Asks to become the home app: the system role dialog where available, else the settings page. */
    fun requestDefaultLauncher(activity: Activity, requestCode: Int = 77) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                runCatching { activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode) }
                    .onSuccess { return }
            }
        }
        open(activity, Intent(Settings.ACTION_HOME_SETTINGS))
    }

    fun openDisplaySettings(context: Context) = open(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun openSystemWallpaperPicker(context: Context) =
        open(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"))

    fun openAlarms(context: Context) = open(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), quiet = true)

    fun openCalendar(context: Context) =
        open(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), quiet = true)

    fun openBatterySettings(context: Context) =
        open(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), quiet = true)

    fun webSearch(context: Context, query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
        if (!open(context, intent, quiet = true)) {
            open(context, Intent(Intent.ACTION_VIEW, ("https://www.google.com/search?q=" + Uri.encode(query)).toUri()))
        }
    }

    fun open(context: Context, intent: Intent, quiet: Boolean = false): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(context, "Your phone doesn't have a screen for that", Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        false
    }
}

enum class WallpaperTarget(val label: String, val flags: Int) {
    Both("Home and lock screen", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
    Home("Home screen", WallpaperManager.FLAG_SYSTEM),
    Lock("Lock screen", WallpaperManager.FLAG_LOCK),
}

/** Generates gradient wallpapers and sets wallpapers from pictures. */
object Wallpapers {

    fun render(spec: WallpaperSpec, width: Int, height: Int): Bitmap {
        val bmp = createBitmap(width, height)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()
        val stops = listOfNotNull(spec.color1, spec.color2, spec.color3).toIntArray()
        paint.shader = when (spec.style) {
            WallpaperStyle.Solid, WallpaperStyle.Aurora, WallpaperStyle.Tendrils -> null
            WallpaperStyle.Vertical -> LinearGradient(0f, 0f, 0f, h, stops, null, Shader.TileMode.CLAMP)
            WallpaperStyle.Diagonal -> LinearGradient(0f, 0f, w, h, stops, null, Shader.TileMode.CLAMP)
            WallpaperStyle.Radial -> RadialGradient(
                w / 2f, h * 0.35f, max(hypot(w, h) * 0.6f, 1f), stops, null, Shader.TileMode.CLAMP,
            )
        }
        if (spec.style == WallpaperStyle.Solid) paint.color = spec.color1
        if (spec.style == WallpaperStyle.Aurora) {
            drawAurora(canvas, spec, w, h)
        } else if (spec.style == WallpaperStyle.Tendrils) {
            Tendrils.draw(canvas, spec, w, h)
        } else {
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        return bmp
    }

    /** Soft overlapping glows of each color on a deep base, like a mesh gradient. */
    private fun drawAurora(canvas: Canvas, spec: WallpaperSpec, w: Float, h: Float) {
        val third = spec.color3 ?: ColorUtils.blendARGB(spec.color1, spec.color2, 0.5f)
        val base = ColorUtils.blendARGB(spec.color1, 0xFF000000.toInt(), 0.55f)
        canvas.drawColor(base)
        val glows = listOf(
            Triple(spec.color1, floatArrayOf(0.15f, 0.12f), 0.85f),
            Triple(spec.color2, floatArrayOf(0.95f, 0.45f), 0.8f),
            Triple(third, floatArrayOf(0.25f, 0.88f), 0.85f),
            Triple(spec.color2, floatArrayOf(0.6f, 0.72f), 0.45f),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        for ((color, at, size) in glows) {
            val r = max(w, h) * size
            val solid = color or 0xFF000000.toInt()
            paint.shader = RadialGradient(
                w * at[0], h * at[1], r,
                intArrayOf(solid, ColorUtils.setAlphaComponent(solid, 0x80), ColorUtils.setAlphaComponent(solid, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = WallpaperManager.getInstance(context)
        val dm = context.resources.displayMetrics
        val w = wm.desiredMinimumWidth.takeIf { it > 0 } ?: dm.widthPixels
        val h = wm.desiredMinimumHeight.takeIf { it > 0 } ?: dm.heightPixels
        // Keep generated wallpapers a sensible size even if a device reports something huge.
        return w.coerceIn(dm.widthPixels, 2 * dm.widthPixels) to h.coerceIn(dm.heightPixels, 2 * dm.heightPixels)
    }

    suspend fun applyGenerated(context: Context, spec: WallpaperSpec, target: WallpaperTarget): Boolean =
        withContext(Dispatchers.Default) {
            val (w, h) = screenSize(context)
            val bmp = render(spec, w, h)
            runCatching { WallpaperManager.getInstance(context).setBitmap(bmp, null, true, target.flags) }
                .also { bmp.recycle() }.isSuccess
        }

    suspend fun applyImage(context: Context, uri: Uri, target: WallpaperTarget): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (w, h) = screenSize(context)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= w && opts.outHeight / (sample * 2) >= h) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Couldn't read the picture")
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, target.flags)
            bitmap.recycle()
        }.isSuccess
    }

    /** The main colors of the current wallpaper (Android 8.1+), used to match the theme to it. */
    fun wallpaperColors(context: Context): List<Int> {
        if (Build.VERSION.SDK_INT < 27) return emptyList()
        val colors = runCatching {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return emptyList()
        return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { it.toArgb() }
    }
}
