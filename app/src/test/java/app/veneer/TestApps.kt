package app.veneer

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import org.robolectric.Shadows.shadowOf

/** Pretend apps with distinct adaptive icons, so screens look like a real phone in tests. */
object TestApps {

    data class Sample(val pkg: String, val label: String, val color: Int, val glyph: String)

    val samples = listOf(
        Sample("com.example.phone", "Phone", 0xFF2E7D32.toInt(), "☎"),
        Sample("com.example.messages", "Messages", 0xFF1565C0.toInt(), "✉"),
        Sample("com.example.browser", "Browser", 0xFFEF6C00.toInt(), "◎"),
        Sample("com.example.camera", "Camera", 0xFF37474F.toInt(), "◉"),
        Sample("com.example.photos", "Photos", 0xFFD81B60.toInt(), "✿"),
        Sample("com.example.calendar", "Calendar", 0xFF3949AB.toInt(), "31"),
        Sample("com.example.maps", "Maps", 0xFF00897B.toInt(), "➤"),
        Sample("com.example.mail", "Mail", 0xFFC62828.toInt(), "@"),
        Sample("com.example.music", "Music", 0xFF8E24AA.toInt(), "♪"),
        Sample("com.example.settings", "Settings", 0xFF546E7A.toInt(), "⚙"),
        Sample("com.example.clock", "Clock", 0xFF283593.toInt(), "◷"),
        Sample("com.example.weather", "Weather", 0xFF0288D1.toInt(), "☀"),
        Sample("com.example.notes", "Notes", 0xFFF9A825.toInt(), "✎"),
        Sample("com.example.videos", "Videos", 0xFFE53935.toInt(), "▶"),
        Sample("com.example.files", "Files", 0xFF6D4C41.toInt(), "▤"),
        Sample("com.example.store", "Store", 0xFF43A047.toInt(), "★"),
    )

    fun install(context: Context) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val shadow = shadowOf(launcherApps)
        val user = Process.myUserHandle()
        samples.forEach { s ->
            shadow.addActivity(user, launcherActivityInfo(context, s.pkg, s.label, user))
            shadow.addEnabledPackage(user, s.pkg)
        }
    }

    private fun launcherActivityInfo(context: Context, pkg: String, label: String, user: UserHandle): LauncherActivityInfo {
        val info = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.MainActivity"
            nonLocalizedLabel = label
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
            }
        }
        // LauncherActivityInfo has no public constructor; build it the way the framework does.
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internalCtor = internalClass.declaredConstructors.first { it.parameterTypes.firstOrNull() == ActivityInfo::class.java }
        val args = internalCtor.parameterTypes.map { type ->
            when (type) {
                ActivityInfo::class.java -> info
                UserHandle::class.java -> user
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        }.toTypedArray()
        internalCtor.isAccessible = true
        val internal = internalCtor.newInstance(*args)
        val ctor = LauncherActivityInfo::class.java.getDeclaredConstructor(Context::class.java, internalClass)
        ctor.isAccessible = true
        return ctor.newInstance(context, internal)
    }

    fun icon(label: String): Drawable {
        val s = samples.firstOrNull { it.label == label } ?: samples.first()
        val glyph = GlyphDrawable(s.glyph, Color.WHITE)
        return AdaptiveIconDrawable(ColorDrawable(s.color), glyph, GlyphDrawable(s.glyph, Color.WHITE))
    }

    /** Draws a symbol in the middle of an adaptive icon layer. */
    class GlyphDrawable(private val glyph: String, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.textSize = b.height() * (if (glyph.length > 1) 0.22f else 0.3f)
            val y = b.exactCenterY() - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(glyph, b.exactCenterX(), y, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        override fun getConstantState(): ConstantState = object : ConstantState() {
            override fun newDrawable() = GlyphDrawable(glyph, paint.color)
            override fun getChangingConfigurations() = 0
        }
    }
}
