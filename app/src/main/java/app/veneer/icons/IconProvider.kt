package app.veneer.icons

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import app.veneer.apps.AppEntry
import app.veneer.data.IconRef
import app.veneer.data.IconStyle
import app.veneer.data.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

fun ThemeConfig.iconSpec() = IconSpec(
    iconShape, iconStyle, themedFg, themedBg, wrapLegacyIcons, legacyBg,
    iconFinish, glassTint, glassClarity, glassShine, glassDepth,
    iconCorner, iconScale, iconOutline, iconOutlineColor, iconShadow, iconBgOpacity, themedBg2, iconTint, iconTintStrength,
)

/**
 * Produces finished icons for apps, applying icon packs, per-app overrides, shapes and styles,
 * with a memory cache so scrolling stays smooth.
 */
class IconProvider(private val context: Context, val packs: IconPackManager) {

    private val density = context.resources.displayMetrics.densityDpi
    private val work = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

    /**
     * Each app's original icon, kept so restyling only redraws instead of asking Android for the
     * icon again. Holds the drawable's shared state; every render gets its own copy.
     */
    private val sources = object : LruCache<String, Drawable.ConstantState>(400) {}

    /** Each app's main color and whether its logo can be tinted, worked out once per app. */
    private val hints = ConcurrentHashMap<String, IconHints>()

    @Volatile
    private var generation = 0

    /** Bumped whenever cached icons become stale, so on-screen icons reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount / 1024
    }

    val customIconDir: File get() = File(context.filesDir, "icons").apply { mkdirs() }

    private fun cacheSizeKb(): Int {
        val max = Runtime.getRuntime().maxMemory() / 1024
        return (max / 8).toInt().coerceIn(8 * 1024, 64 * 1024)
    }

    private fun key(entry: AppEntry, override: IconRef?, theme: ThemeConfig, bucket: Int) =
        "${entry.key.id}|$override|${theme.iconPack}|${theme.iconSpec()}|$bucket|$generation"

    /** A finished icon if one is ready for this size, without drawing anything. */
    fun cached(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap? =
        cache.get(key(entry, override, theme, bucket(sizePx)))

    /**
     * The finished icon, drawn at the nearest of a few fixed sizes at or above [sizePx] and scaled on
     * screen. Nearby sizes share one drawing, so moving the icon size slider rarely redraws anything.
     */
    suspend fun appIcon(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap {
        val bucket = bucket(sizePx)
        val key = key(entry, override, theme, bucket)
        cache.get(key)?.let { return it }
        return withContext(work) {
            cache.get(key) ?: renderAppIcon(entry, override, theme, bucket).also { cache.put(key, it) }
        }
    }

    private suspend fun renderAppIcon(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap {
        val spec = theme.iconSpec()
        val result = when (override) {
            is IconRef.Pack -> packDrawable(override.pack, override.drawable)
                ?.let { IconRenderer.render(it, IconSource.Pack, sizePx, spec) }
            is IconRef.Image -> loadImage(override.file)
                ?.let { IconRenderer.render(it, IconSource.Image, sizePx, spec) }
            null -> null
        } ?: run {
            val pack = packs.get(theme.iconPack)
            val packDrawable = pack?.drawableNameFor(entry.key.component)?.let { packDrawable(pack.packageName, it) }
            when {
                packDrawable != null -> IconRenderer.render(packDrawable, IconSource.Pack, sizePx, spec)
                else -> {
                    val raw = appDrawable(entry)
                    val symbolStyle = theme.iconStyle == IconStyle.Themed || theme.iconStyle == IconStyle.Tinted || theme.iconStyle == IconStyle.ColorSymbols
                    val themedByPack = if (pack != null && !symbolStyle) {
                        IconRenderer.renderWithPackBack(raw, pack, sizePx, density, entry.key.id.hashCode(), spec)
                    } else null
                    themedByPack ?: IconRenderer.render(raw, IconSource.App, sizePx, spec, hintsFor(entry, raw, spec))
                }
            }
        }
        val finished = if (entry.isWorkProfile) {
            val badged = context.packageManager.getUserBadgedIcon(result.toDrawable(context.resources), entry.user)
            badged.toBitmap(sizePx, sizePx)
        } else result
        return finished.asImageBitmap()
    }

    /** Where raw app icons come from. Tests swap this out; the app always uses the system's icons. */
    @Volatile
    var iconSource: (AppEntry, Int) -> Drawable? = { entry, dpi -> runCatching { entry.info.getIcon(dpi) }.getOrNull() }

    fun appDrawable(entry: AppEntry): Drawable =
        remembered("app|${entry.key.id}|$generation") { iconSource(entry, density) } ?: context.packageManager.defaultActivityIcon

    private suspend fun packDrawable(packName: String, drawable: String): Drawable? =
        remembered("pack|$packName|$drawable|$generation") { packs.get(packName)?.load(drawable, density) }

    private inline fun remembered(key: String, load: () -> Drawable?): Drawable? {
        sources.get(key)?.let { state -> runCatching { state.newDrawable(context.resources) }.getOrNull()?.let { return it } }
        val fresh = load() ?: return null
        fresh.constantState?.let { sources.put(key, it) }
        return fresh
    }

    /** Only the styles that need an app's own color or logo pay for working them out, and only once. */
    private fun hintsFor(entry: AppEntry, raw: Drawable, spec: IconSpec): IconHints {
        if (spec.style != IconStyle.Themed && spec.style != IconStyle.Tinted && spec.style != IconStyle.ColorSymbols) return IconHints()
        val key = "${entry.key.id}|$generation"
        return hints[key] ?: (raw.constantState?.newDrawable(context.resources) ?: raw).let { IconRenderer.analyze(it) }.also { hints[key] = it }
    }

    /** Renders one icon from a pack, for the icon picker grid. */
    suspend fun packIcon(packName: String, drawable: String, sizePx: Int, theme: ThemeConfig): ImageBitmap? =
        withContext(work) {
            val key = "pack|$packName|$drawable|$sizePx|${theme.iconSpec()}"
            cache.get(key) ?: packDrawable(packName, drawable)?.let {
                IconRenderer.render(it, IconSource.Pack, sizePx, theme.iconSpec()).asImageBitmap()
            }?.also { cache.put(key, it) }
        }

    private fun loadImage(fileName: String): Drawable? {
        val file = File(customIconDir, fileName)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return bitmap.toDrawable(context.resources)
    }

    /** Drops every cached icon, e.g. after an app update or icon pack change. */
    fun invalidateAll() {
        generation++
        cache.evictAll()
        sources.evictAll()
        hints.clear()
        _version.value = generation
    }

    companion object {
        private val buckets = intArrayOf(24, 32, 40, 48, 56, 64, 72, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 288, 320, 384, 448, 512)

        /** The drawing size used for an icon shown at [sizePx]: the smallest fixed size that's at least as big. */
        fun bucket(sizePx: Int): Int = buckets.firstOrNull { it >= sizePx } ?: sizePx
    }

    fun onPackageChanged(packageName: String) {
        packs.forget(packageName)
        invalidateAll()
    }
}
