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

fun ThemeConfig.iconSpec() = IconSpec(
    iconShape, iconStyle, themedFg, themedBg, wrapLegacyIcons, legacyBg,
    iconFinish, glassTint, glassClarity, glassShine, glassDepth,
)

/**
 * Produces finished icons for apps, applying icon packs, per-app overrides, shapes and styles,
 * with a memory cache so scrolling stays smooth.
 */
class IconProvider(private val context: Context, val packs: IconPackManager) {

    private val density = context.resources.displayMetrics.densityDpi
    private val work = Dispatchers.Default.limitedParallelism(4)

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

    private fun key(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int) =
        "${entry.key.id}|$override|${theme.iconPack}|${theme.iconSpec()}|$sizePx|$generation"

    fun cached(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap? =
        cache.get(key(entry, override, theme, sizePx))

    suspend fun appIcon(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap {
        val key = key(entry, override, theme, sizePx)
        cache.get(key)?.let { return it }
        return withContext(work) {
            val bitmap = renderAppIcon(entry, override, theme, sizePx)
            cache.put(key, bitmap)
            bitmap
        }
    }

    private suspend fun renderAppIcon(entry: AppEntry, override: IconRef?, theme: ThemeConfig, sizePx: Int): ImageBitmap {
        val spec = theme.iconSpec()
        val result = when (override) {
            is IconRef.Pack -> packs.get(override.pack)?.load(override.drawable, density)
                ?.let { IconRenderer.render(it, IconSource.Pack, sizePx, spec) }
            is IconRef.Image -> loadImage(override.file)
                ?.let { IconRenderer.render(it, IconSource.Image, sizePx, spec) }
            null -> null
        } ?: run {
            val pack = packs.get(theme.iconPack)
            val packDrawable = pack?.drawableNameFor(entry.key.component)?.let { pack.load(it, density) }
            when {
                packDrawable != null -> IconRenderer.render(packDrawable, IconSource.Pack, sizePx, spec)
                else -> {
                    val raw = appDrawable(entry)
                    val themedByPack = if (pack != null && theme.iconStyle != IconStyle.Themed) {
                        IconRenderer.renderWithPackBack(raw, pack, sizePx, density, entry.key.id.hashCode(), spec)
                    } else null
                    themedByPack ?: IconRenderer.render(raw, IconSource.App, sizePx, spec)
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
        iconSource(entry, density) ?: context.packageManager.defaultActivityIcon

    /** Renders one icon from a pack, for the icon picker grid. */
    suspend fun packIcon(packName: String, drawable: String, sizePx: Int, theme: ThemeConfig): ImageBitmap? =
        withContext(work) {
            val key = "pack|$packName|$drawable|$sizePx|${theme.iconSpec()}"
            cache.get(key) ?: packs.get(packName)?.load(drawable, density)?.let {
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
        _version.value = generation
    }

    fun onPackageChanged(packageName: String) {
        packs.forget(packageName)
        invalidateAll()
    }
}
