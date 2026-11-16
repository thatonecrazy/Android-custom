package app.veneer.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar

data class IconPackInfo(val packageName: String, val label: String)

/**
 * A third-party icon pack in the widely used ADW / Nova format: an app that ships an
 * appfilter.xml mapping launcher components to drawables.
 */
class IconPack(
    val packageName: String,
    private val resources: Resources,
    private val context: Context,
    private val mapping: Map<ComponentName, String>,
    private val calendars: Map<ComponentName, String>,
    val iconBacks: List<String>,
    val iconMask: String?,
    val iconUpon: String?,
    val scale: Float,
) {
    @Volatile
    private var allNames: List<String>? = null

    fun drawableNameFor(component: ComponentName): String? {
        calendars[component]?.let { prefix -> return prefix + Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
        return mapping[component]
    }

    fun hasIconFor(component: ComponentName) = drawableNameFor(component) != null

    @SuppressLint("DiscouragedApi")
    fun load(name: String, density: Int): Drawable? {
        var id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    /** Every icon the pack offers, for manual picking. */
    fun allDrawableNames(): List<String> {
        allNames?.let { return it }
        val names = LinkedHashSet<String>()
        IconPackParser.openXml(context, resources, packageName, "drawable")?.use { parser ->
            parser.forEachStartTag { tag ->
                if (tag == "item") parser.getAttributeValue(null, "drawable")?.let(names::add)
            }
        }
        if (names.isEmpty()) names.addAll(mapping.values)
        return names.toList().also { allNames = it }
    }
}

class IconPackManager(private val context: Context) {

    private val lock = Mutex()
    private var loaded: IconPack? = null

    /** Installed icon packs, found through the intents launchers have used for years. */
    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in PACK_ACTIONS) {
            val matches = runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
                .getOrDefault(emptyList())
            for (info in matches) {
                val pkg = info.activityInfo.packageName
                if (pkg !in found) {
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    found[pkg] = IconPackInfo(pkg, label)
                }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Loads and caches one pack. Parsing a big appfilter takes a moment, so this runs on IO. */
    suspend fun get(packageName: String?): IconPack? {
        if (packageName == null) return null
        loaded?.let { if (it.packageName == packageName) return it }
        return lock.withLock {
            loaded?.let { if (it.packageName == packageName) return@withLock it }
            withContext(Dispatchers.IO) { IconPackParser.parse(context, packageName) }
                .also { loaded = it }
        }
    }

    fun peek(packageName: String?): IconPack? = loaded?.takeIf { it.packageName == packageName }

    fun forget(packageName: String) {
        if (loaded?.packageName == packageName) loaded = null
    }

    private companion object {
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "com.dlto.atom.launcher.THEME",
        )
    }
}

internal object IconPackParser {

    fun parse(context: Context, packageName: String): IconPack? {
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()
            ?: return null
        val mapping = HashMap<ComponentName, String>()
        val calendars = HashMap<ComponentName, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        val parser = openXml(context, res, packageName, "appfilter") ?: return null
        parser.use { p ->
            p.forEachStartTag { tag ->
                when (tag) {
                    "item" -> {
                        val component = parseComponent(p.getAttributeValue(null, "component"))
                        val drawable = p.getAttributeValue(null, "drawable")
                        if (component != null && !drawable.isNullOrBlank() && component !in mapping) {
                            mapping[component] = drawable
                        }
                    }
                    "calendar" -> {
                        val component = parseComponent(p.getAttributeValue(null, "component"))
                        val prefix = p.getAttributeValue(null, "prefix")
                        if (component != null && prefix != null) calendars[component] = prefix
                    }
                    "iconback" -> for (i in 0 until p.attributeCount) {
                        if (p.getAttributeName(i).startsWith("img")) backs += p.getAttributeValue(i)
                    }
                    "iconmask" -> mask = p.getAttributeValue(null, "img1") ?: mask
                    "iconupon" -> upon = p.getAttributeValue(null, "img1") ?: upon
                    "scale" -> scale = p.getAttributeValue(null, "factor")?.toFloatOrNull() ?: scale
                }
            }
        }
        if (mapping.isEmpty() && backs.isEmpty()) return null
        return IconPack(packageName, res, context, mapping, calendars, backs, mask, upon, scale)
    }

    private fun parseComponent(raw: String?): ComponentName? {
        if (raw.isNullOrBlank()) return null
        val inner = raw.removePrefix("ComponentInfo{").removeSuffix("}").trim()
        if (!inner.contains('/')) return null
        return ComponentName.unflattenFromString(inner)
    }

    /** Opens res/xml/[name].xml from the pack, falling back to assets/[name].xml. */
    @SuppressLint("DiscouragedApi")
    fun openXml(context: Context, res: Resources, packageName: String, name: String): ClosableParser? {
        val id = res.getIdentifier(name, "xml", packageName)
        if (id != 0) {
            val xml = runCatching { res.getXml(id) }.getOrNull()
            if (xml != null) return ClosableParser(xml) { xml.close() }
        }
        return try {
            val stream = context.createPackageContext(packageName, 0).assets.open("$name.xml")
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            ClosableParser(parser) { stream.close() }
        } catch (e: Exception) {
            if (name == "appfilter") Log.w("Veneer", "No appfilter in $packageName", e)
            null
        }
    }
}

internal class ClosableParser(private val parser: XmlPullParser, private val onClose: () -> Unit) :
    XmlPullParser by parser, AutoCloseable {
    override fun close() {
        runCatching { onClose() }
    }

    fun forEachStartTag(block: (String) -> Unit) {
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) block(parser.name)
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w("Veneer", "Icon pack XML ended early", e)
        }
    }
}
