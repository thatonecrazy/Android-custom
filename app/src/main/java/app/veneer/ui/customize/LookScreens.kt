package app.veneer.ui.customize

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import app.veneer.apps.AppEntry
import app.veneer.data.IconShape
import app.veneer.data.IconStyle
import app.veneer.data.LauncherState
import app.veneer.data.Presets
import app.veneer.data.ThemeConfig
import app.veneer.data.VeneerJson
import app.veneer.data.WallpaperSpec
import app.veneer.data.WallpaperStyle
import app.veneer.fonts.FontKind
import app.veneer.icons.IconShapes
import app.veneer.system.SystemActions
import app.veneer.system.WallpaperTarget
import app.veneer.system.Wallpapers
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.FontCard
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.common.WallpaperPreview
import app.veneer.ui.launcher.AppIcon
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.veneer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Colors for themed icons and the accent, derived from the wallpaper's main color. */
fun matchWallpaper(context: Context, theme: ThemeConfig): ThemeConfig? {
    val main = Wallpapers.wallpaperColors(context).firstOrNull() ?: return null
    val luminance = ColorUtils.calculateLuminance(main)
    val accent = if (luminance < 0.25) ColorUtils.blendARGB(main, 0xFFFFFFFF.toInt(), 0.45f) else main
    return theme.copy(
        accent = accent or 0xFF000000.toInt(),
        themedBg = ColorUtils.blendARGB(main, 0xFFFFFFFF.toInt(), 0.78f) or 0xFF000000.toInt(),
        themedFg = ColorUtils.blendARGB(main, 0xFF000000.toInt(), 0.62f) or 0xFF000000.toInt(),
        dockBg = (main and 0x00FFFFFF) or 0x40000000,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val scope = rememberCoroutineScope()
    val theme = state.theme
    var applying by remember { mutableStateOf<ThemeConfig?>(null) }
    var saving by remember { mutableStateOf(false) }

    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(VeneerJson.encodeToString(ThemeConfig.serializer(), theme).toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Theme saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
                    val obj = VeneerJson.parseToJsonElement(text).jsonObject
                    val themeJson = (obj["theme"] as? JsonObject) ?: obj
                    VeneerJson.decodeFromJsonElement(ThemeConfig.serializer(), themeJson)
                }.getOrNull()
            }
            if (parsed == null) Toast.makeText(context, "That file isn't a Veneer theme", Toast.LENGTH_SHORT).show()
            else applying = parsed
        }
    }

    SettingsPage("Themes", onBack, state, apps, section = Section.Themes) {
        item { SectionHeader("Ready-made looks") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("presets"),
            ) {
                items(Presets.all, key = { it.name }) { preset ->
                    PresetCard(preset, apps, preset.name == theme.name) { applying = preset }
                }
            }
        }
        item { NoteText("Applying a look keeps your apps and widgets where they are. Grid and dock size stay as you set them.") }

        item { SectionHeader("Colors") }
        item {
            SettingCard {
                ColorRow("Accent color", theme.accent, "Menus, highlights and these settings", allowAlpha = false) { c ->
                    app.store.updateTheme { it.copy(accent = c) }
                }
                ClickRow("Match my wallpaper", "Sets the accent and themed icon colors from your wallpaper", icon = Icons.Filled.AutoAwesome) {
                    val next = matchWallpaper(context, theme)
                    if (next == null) Toast.makeText(context, "Couldn't read your wallpaper's colors", Toast.LENGTH_SHORT).show()
                    else app.store.updateTheme { next }
                }
            }
        }

        item { SectionHeader("Your themes") }
        item {
            SettingCard {
                ClickRow("Save the current look", "Keep it so you can switch back any time", icon = Icons.Filled.BookmarkAdd) { saving = true }
                state.savedThemes.forEachIndexed { index, saved ->
                    ClickRow(
                        saved.name,
                        "${saved.iconShape.label} icons · ${app.fonts.name(saved.fontId)}",
                        trailing = {
                            IconButton(onClick = {
                                app.store.update { s -> s.copy(savedThemes = s.savedThemes.filterIndexed { i, _ -> i != index }) }
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        },
                    ) { applying = saved }
                }
            }
        }

        item { SectionHeader("Share") }
        item {
            SettingCard {
                ClickRow("Export the current look", "Save it as a file you can send to another phone", icon = Icons.Filled.IosShare) {
                    exportTheme.launch("${theme.name.ifBlank { "theme" }}.veneer-theme.json")
                }
                ClickRow("Import a theme file", icon = Icons.Filled.FileOpen) { importTheme.launch(arrayOf("*/*")) }
            }
        }
    }

    applying?.let { ApplyThemeDialog(it) { applying = null } }
    if (saving) SaveThemeDialog(state) { saving = false }
}

/** A tall card that looks like a tiny phone in that preset: wallpaper, clock and icons. */
@Composable
private fun PresetCard(preset: ThemeConfig, apps: List<AppEntry>, selected: Boolean, onClick: () -> Unit) {
    val app = LocalContext.current.veneer
    val font = remember(preset.fontId) { app.fonts.family(preset.fontId) }
    val sample = remember(apps) { apps.take(6) }
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(212.dp)
                .border(3.dp, ring, RoundedCornerShape(26.dp))
                .padding(if (selected) 5.dp else 0.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            WallpaperPreview(preset.wallpaper ?: WallpaperSpec(), Modifier.matchParentSize())
            CompositionLocalProvider(LocalLauncherTheme provides preset) {
                Column(Modifier.matchParentSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    Text("9:41", style = TextStyle(fontFamily = font, fontSize = 30.sp, color = Color(preset.labelColor)))
                    Spacer(Modifier.weight(1f))
                    sample.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { AppIcon(it, null, 28.dp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(preset.dockCornerDp.coerceAtMost(15).dp))
                            .background(Color(preset.dockBg)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(preset.name, style = TextStyle(fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(
            "${preset.iconShape.label} · ${preset.iconStyle.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ApplyThemeDialog(theme: ThemeConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    var withWallpaper by remember { mutableStateOf(theme.wallpaper != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use \"${theme.name}\"?") },
        text = {
            Column {
                Text("Changes icons, fonts and colors. Your apps and widgets stay where they are.")
                if (theme.wallpaper != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { withWallpaper = !withWallpaper },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = withWallpaper, onCheckedChange = { withWallpaper = it })
                        Text("Also set its wallpaper (home and lock screen)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.store.updateTheme { it.withLookOf(theme) }
                val spec = theme.wallpaper
                if (withWallpaper && spec != null) {
                    app.scope.launch {
                        val ok = Wallpapers.applyGenerated(context.applicationContext, spec, WallpaperTarget.Both)
                        if (!ok) Toast.makeText(context.applicationContext, "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
                    }
                }
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SaveThemeDialog(state: LauncherState, onDismiss: () -> Unit) {
    val app = LocalContext.current.veneer
    var name by remember { mutableStateOf("My theme ${state.savedThemes.size + 1}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this look") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                app.store.update { s -> s.copy(savedThemes = s.savedThemes + s.theme.copy(name = name.trim())) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun IconsScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit, openApps: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val theme = state.theme
    val packs = remember { app.icons.packs.installedPacks() }
    fun update(transform: (ThemeConfig) -> ThemeConfig) = app.store.updateTheme(transform)

    SettingsPage("Icons", onBack, state, apps, section = Section.IconsSection) {
        item { SectionHeader("Icon pack") }
        item {
            SettingCard {
                RadioRow("None", "Each app's own icon, shaped and styled below", theme.iconPack == null) {
                    update { it.copy(iconPack = null) }
                }
                packs.forEach { pack ->
                    RadioRow(pack.label, null, theme.iconPack == pack.packageName) {
                        update { it.copy(iconPack = pack.packageName) }
                    }
                }
                ClickRow("Get icon packs", "Thousands of free and paid packs on the Play Store", icon = Icons.Filled.Storefront) {
                    val market = Intent(Intent.ACTION_VIEW, "market://search?q=icon%20pack&c=apps".toUri())
                    if (!SystemActions.open(context, market, quiet = true)) {
                        SystemActions.open(context, Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=icon%20pack&c=apps".toUri()))
                    }
                }
            }
        }
        item { SectionHeader("Shape") }
        item { ShapePicker(theme.iconShape) { s -> update { it.copy(iconShape = s) } } }
        item {
            NoteText("Shapes apply to modern app icons, your own pictures, and old-style icons when they get a background. Icon pack icons keep their own design.")
        }
        item { SectionHeader("Style") }
        item {
            SettingCard {
                ChoiceRow(null, IconStyle.entries, theme.iconStyle, { it.label }) { s -> update { it.copy(iconStyle = s) } }
                when (theme.iconStyle) {
                    IconStyle.Themed -> {
                        NoteText("Uses the one-color icon apps provide for Android 13 and newer, or a tinted logo. Apps whose icon can't be tinted keep their colors.")
                        ColorRow("Symbol color", theme.themedFg, allowAlpha = false) { c -> update { it.copy(themedFg = c) } }
                        ColorRow("Background color", theme.themedBg, allowAlpha = false) { c -> update { it.copy(themedBg = c) } }
                        ClickRow("Match my wallpaper") {
                            matchWallpaper(context, theme)?.let { next -> update { next } }
                                ?: Toast.makeText(context, "Couldn't read your wallpaper's colors", Toast.LENGTH_SHORT).show()
                        }
                    }
                    IconStyle.Grayscale -> NoteText("Every icon in black and white. Pairs well with a colorful wallpaper.")
                    IconStyle.Vibrant -> NoteText("Turns up the colors of every icon so they pop off the wallpaper.")
                    IconStyle.Original -> {}
                }
            }
        }
        item { SectionHeader("Size") }
        item {
            SettingCard {
                IntSliderRow("Icon size", theme.iconSizeDp, 36..80, { "$it dp" }) { v -> update { it.copy(iconSizeDp = v) } }
            }
        }
        item { SectionHeader("Old-style icons") }
        item {
            SettingCard {
                SwitchRow(
                    "Give old-style icons a background",
                    theme.wrapLegacyIcons,
                    "Square or oddly shaped icons from older apps get the same shape as the rest",
                ) { b -> update { it.copy(wrapLegacyIcons = b) } }
                if (theme.wrapLegacyIcons) {
                    ColorRow("Background color", theme.legacyBg, allowAlpha = false) { c -> update { it.copy(legacyBg = c) } }
                }
            }
        }
        item {
            SettingCard {
                ClickRow("Change a single app's icon", "Pick from any icon pack or use your own picture", icon = Icons.Filled.Brush, onClick = openApps)
            }
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ClickRow(title, subtitle, leading = { RadioButton(selected = selected, onClick = onClick) }, onClick = onClick)
}

@Composable
private fun ShapePicker(selected: IconShape, onSelect: (IconShape) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(IconShape.entries) { shape ->
            val isSelected = shape == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) color else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onSelect(shape) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(44.dp)) {
                    val path = IconShapes.path(shape, size.minDimension)
                    if (path != null) {
                        drawPath(path.asComposePath(), color)
                    } else {
                        val dashed = IconShapes.path(IconShape.Squircle, size.minDimension)!!.asComposePath()
                        drawPath(dashed, outline, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(shape.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontsScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val scope = rememberCoroutineScope()
    val theme = state.theme
    val imported by app.fonts.imported.collectAsState()
    fun update(transform: (ThemeConfig) -> ThemeConfig) = app.store.updateTheme(transform)

    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val font = app.fonts.import(uri)
            if (font == null) {
                Toast.makeText(context, "That file isn't a font Android can read (.ttf or .otf)", Toast.LENGTH_LONG).show()
            } else {
                update { it.copy(fontId = font.id) }
                Toast.makeText(context, "Added ${font.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsPage("Fonts", onBack, state, apps, section = Section.Fonts) {
        item { SectionHeader("Font") }
        item { NoteText("Used for app names, the app list, menus and every widget set to the theme font.") }
        item {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (app.fonts.builtIn + imported).forEach { option ->
                    FontCard(option.name, option.family, option.id == theme.fontId) { update { it.copy(fontId = option.id) } }
                }
            }
        }
        item {
            SettingCard {
                ClickRow("Add a font file", "Use any .ttf or .otf font saved on your phone", icon = Icons.Filled.Add) { importFont.launch(arrayOf("*/*")) }
                imported.filter { it.kind == FontKind.Imported }.forEach { font ->
                    ClickRow(
                        font.name,
                        "Imported",
                        trailing = {
                            IconButton(onClick = {
                                if (theme.fontId == font.id) update { it.copy(fontId = "system") }
                                app.fonts.delete(font.id)
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Remove font") }
                        },
                    ) { update { it.copy(fontId = font.id) } }
                }
            }
        }
        item { SectionHeader("App names") }
        item {
            SettingCard {
                SwitchRow("Show names on the home screen", theme.showHomeLabels) { b -> update { it.copy(showHomeLabels = b) } }
                SwitchRow("Show names in the app list", theme.showDrawerLabels) { b -> update { it.copy(showDrawerLabels = b) } }
                IntSliderRow("Text size", theme.labelSizeSp, 8..20, { "$it sp" }) { v -> update { it.copy(labelSizeSp = v) } }
                ColorRow("Text color", theme.labelColor) { c -> update { it.copy(labelColor = c) } }
                SwitchRow("Bold", theme.labelBold) { b -> update { it.copy(labelBold = b) } }
                SwitchRow("Shadow", theme.labelShadow, "Keeps names readable on bright wallpapers") { b -> update { it.copy(labelShadow = b) } }
            }
        }
        item { SectionHeader("The phone's own font") }
        item {
            SettingCard {
                ClickRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Change the system font",
                    subtitle = "Only your phone's settings can change the font inside other apps. Samsung, Xiaomi, OPPO, vivo, " +
                        "OnePlus and others have a font picker there; Pixels offer size and bold text.",
                ) { SystemActions.openDisplaySettings(context) }
            }
        }
    }
}
