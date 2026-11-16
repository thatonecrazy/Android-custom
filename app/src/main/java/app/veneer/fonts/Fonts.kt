package app.veneer.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.veneer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class FontKind { System, Bundled, Imported }

data class FontOption(val id: String, val name: String, val family: FontFamily, val kind: FontKind)

/**
 * The fonts Veneer can use for labels, the drawer and widgets: the phone's built-in families,
 * fonts bundled with the app (all SIL Open Font License), and any .ttf/.otf the user imports.
 */
class FontManager(private val context: Context) {

    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }

    val builtIn: List<FontOption> = listOf(
        system("system", "System default", FontFamily.Default),
        system("condensed", "System condensed", typeface("sans-serif-condensed")),
        system("light", "System light", typeface("sans-serif-light")),
        system("thin", "System thin", typeface("sans-serif-thin")),
        system("medium", "System medium", typeface("sans-serif-medium")),
        system("smallcaps", "Small caps", typeface("sans-serif-smallcaps")),
        system("serif", "Serif", FontFamily.Serif),
        system("mono", "Monospace", FontFamily.Monospace),
        system("cursive", "Cursive", FontFamily.Cursive),
        bundled("inter", "Inter", R.font.inter_regular, R.font.inter_bold),
        bundled("outfit", "Outfit", R.font.outfit_regular, R.font.outfit_bold),
        bundled("space_grotesk", "Space Grotesk", R.font.space_grotesk_regular, R.font.space_grotesk_bold),
        bundled("quicksand", "Quicksand", R.font.quicksand_regular, R.font.quicksand_bold),
        bundled("comfortaa", "Comfortaa", R.font.comfortaa_regular, R.font.comfortaa_bold),
        bundled("playfair", "Playfair Display", R.font.playfair_display_regular, R.font.playfair_display_bold),
        bundled("lora", "Lora", R.font.lora_regular, R.font.lora_bold),
        bundled("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold),
        bundled("orbitron", "Orbitron", R.font.orbitron_regular, R.font.orbitron_bold),
        bundled("bebas", "Bebas Neue", R.font.bebas_neue_regular, null),
        bundled("caveat", "Caveat", R.font.caveat_regular, R.font.caveat_bold),
        bundled("pacifico", "Pacifico", R.font.pacifico_regular, null),
        bundled("pixel", "Press Start 2P", R.font.press_start_2p_regular, null),
    )

    private val _imported = MutableStateFlow(scanImported())
    val imported: StateFlow<List<FontOption>> = _imported.asStateFlow()

    fun all(): List<FontOption> = builtIn + _imported.value

    fun family(id: String?): FontFamily =
        all().firstOrNull { it.id == id }?.family ?: FontFamily.Default

    fun name(id: String?): String = all().firstOrNull { it.id == id }?.name ?: "System default"

    /** Copies a font file the user picked into app storage. Returns the new font, or null if it isn't a font. */
    suspend fun import(uri: Uri): FontOption? = withContext(Dispatchers.IO) {
        val displayName = queryName(uri) ?: "font.ttf"
        val ext = displayName.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
        val base = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Font" }
        var target = File(dir, "$base.$ext")
        var n = 2
        while (target.exists()) target = File(dir, "$base $n.$ext").also { n++ }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            Typeface.Builder(target).build() ?: throw IllegalArgumentException("not a font")
        } catch (e: Exception) {
            target.delete()
            return@withContext null
        }
        _imported.value = scanImported()
        _imported.value.firstOrNull { it.id == idFor(target) }
    }

    fun delete(id: String) {
        if (!id.startsWith(IMPORTED_PREFIX)) return
        File(dir, id.removePrefix(IMPORTED_PREFIX)).delete()
        _imported.value = scanImported()
    }

    private fun scanImported(): List<FontOption> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file ->
                runCatching {
                    FontOption(idFor(file), file.nameWithoutExtension, FontFamily(Font(file)), FontKind.Imported)
                }.getOrNull()
            }

    private fun idFor(file: File) = IMPORTED_PREFIX + file.name

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun system(id: String, name: String, family: FontFamily) = FontOption(id, name, family, FontKind.System)

    private fun typeface(name: String) = FontFamily(Typeface.create(name, Typeface.NORMAL))

    private fun bundled(id: String, name: String, regular: Int, bold: Int?) = FontOption(
        id,
        name,
        if (bold != null) {
            FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))
        } else {
            FontFamily(Font(regular, FontWeight.Normal))
        },
        FontKind.Bundled,
    )

    companion object {
        const val IMPORTED_PREFIX = "file:"
    }
}
