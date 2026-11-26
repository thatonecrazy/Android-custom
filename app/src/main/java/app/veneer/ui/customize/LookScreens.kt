package app.veneer.ui.customize

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.graphicsLayer
import app.veneer.icons.IconRenderer
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
import app.veneer.data.IconFinish
import app.veneer.data.IconShape
import app.veneer.data.IconStyle
import app.veneer.data.LauncherState
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

/** Every color Veneer uses, in one place. */
@Composable
fun ColorsScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val theme = state.theme
    fun update(transform: (ThemeConfig) -> ThemeConfig) = app.store.updateTheme(transform)

    SettingsPage("Colors", onBack, state, apps, section = Section.Colors) {
        item { SectionHeader("Accent") }
        item {
            SettingCard {
                ColorRow("Accent color", theme.accent, "Menus, highlights, weather icons and these settings", allowAlpha = false) { c ->
                    update { it.copy(accent = c) }
                }
                ClickRow("Take colors from my wallpaper", "Sets the accent, dock and icon colors from your wallpaper's main color", icon = Icons.Filled.AutoAwesome) {
                    val next = matchWallpaper(context, theme)
                    if (next == null) Toast.makeText(context, "Couldn't read your wallpaper's colors", Toast.LENGTH_SHORT).show()
                    else update { next }
                }
            }
        }

        item { SectionHeader("Text") }
        item {
            SettingCard {
                ColorRow("App names and widget text", theme.labelColor, "Widgets you've given their own color keep it") { c ->
                    update { it.copy(labelColor = c) }
                }
                SwitchRow("Shadow behind text", theme.labelShadow, "Keeps light text readable on busy wallpapers") { b ->
                    update { it.copy(labelShadow = b) }
                }
            }
        }

        item { SectionHeader("Icons") }
        item {
            SettingCard {
                ColorRow("Symbol color", theme.themedFg, "For the Themed icon style", allowAlpha = false) { c -> update { it.copy(themedFg = c) } }
                ColorRow("Icon background", theme.themedBg, "For the Themed icon style", allowAlpha = false) { c -> update { it.copy(themedBg = c) } }
                ColorRow("Behind old-style icons", theme.legacyBg, "Fills the shape around icons that aren't made for it", allowAlpha = false) { c ->
                    update { it.copy(legacyBg = c) }
                }
                if (theme.iconStyle != IconStyle.Themed) {
                    ClickRow("Use the Themed style", "Every icon becomes one symbol color on one background color", icon = Icons.Filled.Brush) {
                        update { it.copy(iconStyle = IconStyle.Themed) }
                    }
                }
                NoteText("Single apps can have their own colors too: Customize → Your apps.")
            }
        }

        item { SectionHeader("Dock and app list") }
        item {
            SettingCard {
                ColorRow("Dock", theme.dockBg, "Lower the opacity for glass, or make it fully clear") { c -> update { it.copy(dockBg = c) } }
                ColorRow("App list background", theme.drawerBg, "Lower the opacity to see your wallpaper through it") { c -> update { it.copy(drawerBg = c) } }
                ColorRow("App list text", theme.drawerTextColor, allowAlpha = false) { c -> update { it.copy(drawerTextColor = c) } }
            }
        }
    }
}

/** Looks you saved yourself, and look files to move between phones. */
@Composable
fun YourLooksCard(state: LauncherState) {
    val context = LocalContext.current
    val app = context.veneer
    val scope = rememberCoroutineScope()
    val theme = state.theme
    var applying by remember { mutableStateOf<ThemeConfig?>(null) }
    var saving by remember { mutableStateOf(false) }

    val exportLook = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(VeneerJson.encodeToString(ThemeConfig.serializer(), theme).toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Look saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    val importLook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
                    val obj = VeneerJson.parseToJsonElement(text).jsonObject
                    val themeJson = (obj["theme"] as? JsonObject) ?: obj
                    VeneerJson.decodeFromJsonElement(ThemeConfig.serializer(), themeJson)
                }.getOrNull()
            }
            if (parsed == null) Toast.makeText(context, "That file isn't a Veneer look", Toast.LENGTH_SHORT).show()
            else applying = parsed
        }
    }

    SettingCard {
        ClickRow("Save my current look", "Keep your icons, font and colors so you can come back to them", icon = Icons.Filled.BookmarkAdd) { saving = true }
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
        ClickRow("Export my look", "As a file you can move to another phone", icon = Icons.Filled.IosShare) {
            exportLook.launch("my-look.veneer-theme.json")
        }
        ClickRow("Open a look file", icon = Icons.Filled.FileOpen) { importLook.launch(arrayOf("*/*")) }
    }

    applying?.let { UseLookDialog(it) { applying = null } }
    if (saving) SaveLookDialog(state) { saving = false }
}

@Composable
private fun UseLookDialog(theme: ThemeConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    var withWallpaper by remember { mutableStateOf(theme.wallpaper != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch to \"${theme.name}\"?") },
        text = {
            Column {
                Text("Changes icons, fonts and colors back to how you saved them. Your apps and widgets stay where they are.")
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
            }) { Text("Switch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SaveLookDialog(state: LauncherState, onDismiss: () -> Unit) {
    val app = LocalContext.current.veneer
    var name by remember { mutableStateOf("My look ${state.savedThemes.size + 1}") }
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
    val samples = remember(apps) { apps.filter { state.overrides[it.key.id]?.hidden != true }.take(3) }
    fun update(transform: (ThemeConfig) -> ThemeConfig) = app.store.updateTheme(transform)
    val symbolStyle = theme.iconStyle == IconStyle.Themed || theme.iconStyle == IconStyle.ColorSymbols

    SettingsPage("Icons", onBack, state, apps, section = Section.IconsSection) {
        item { SectionHeader("Shape") }
        item { ShapePicker(theme.iconShape, theme.iconCorner) { s -> update { it.copy(iconShape = s) } } }
        item {
            AnimatedVisibility(theme.iconShape == IconShape.RoundedSquare, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                SettingCard {
                    IntSliderRow("Roundness", theme.iconCorner, 0..100, { "$it%" }) { v -> update { it.copy(iconCorner = v) } }
                }
            }
        }

        item { SectionHeader("Style") }
        item { StylePicker(theme, samples) { s -> update { it.copy(iconStyle = s) } } }
        item {
            SettingCard {
                AnimatedContent(theme.iconStyle, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "styleNote") { style ->
                    NoteText(
                        when (style) {
                            IconStyle.Original -> "Every app's own colors."
                            IconStyle.Vibrant -> "Turns up the colors of every icon so they pop off the wallpaper."
                            IconStyle.Themed -> "Each app's symbol in one color on one background color. Uses the one-color icons apps make for Android 13 and newer, or a tinted logo."
                            IconStyle.Tinted -> "Each app gets its own soft color: a pale background and a deep symbol, both from the app's own color."
                            IconStyle.ColorSymbols -> "Each app's symbol in its own bright color, on your background color. Looks great on dark or glass."
                            IconStyle.Pastel -> "Softer, lighter colors for every icon."
                            IconStyle.Grayscale -> "Every icon in black and white. Pairs well with a colorful wallpaper."
                        },
                    )
                }
                if (theme.iconStyle == IconStyle.Themed) {
                    ColorRow("Symbol color", theme.themedFg, allowAlpha = false) { c -> update { it.copy(themedFg = c) } }
                }
                if (symbolStyle) {
                    ColorRow("Background color", theme.themedBg, allowAlpha = false) { c -> update { it.copy(themedBg = c) } }
                    SwitchRow("Gradient background", theme.themedBg2 != null, "Blends the background into a second color") { on ->
                        update { it.copy(themedBg2 = if (on) IconRenderer.deep(it.themedBg) else null) }
                    }
                    theme.themedBg2?.let { second ->
                        ColorRow("Second color", second, allowAlpha = false) { c -> update { it.copy(themedBg2 = c) } }
                    }
                    ClickRow("Match my wallpaper", "Symbol and background colors from your wallpaper", icon = Icons.Filled.AutoAwesome) {
                        matchWallpaper(context, theme)?.let { next -> update { next } }
                            ?: Toast.makeText(context, "Couldn't read your wallpaper's colors", Toast.LENGTH_SHORT).show()
                    }
                }
                IntSliderRow("Background", theme.iconBgOpacity, 0..100, { if (it == 100) "Solid" else if (it == 0) "Clear" else "$it%" }) { v ->
                    update { it.copy(iconBgOpacity = v) }
                }
            }
        }

        item { SectionHeader("Finish") }
        item {
            SettingCard {
                ChoiceRow(null, IconFinish.entries, theme.iconFinish, { it.label }) { f -> update { it.copy(iconFinish = f) } }
                AnimatedContent(theme.iconFinish, transitionSpec = { (fadeIn(tween(220)) + expandVertically()) togetherWith (fadeOut(tween(120)) + shrinkVertically()) }, label = "finish") { finish ->
                    Column {
                        if (finish == IconFinish.Glass) {
                            NoteText(
                                if (symbolStyle) "Each symbol sits on a piece of glass in the glass color. Folders use the same glass."
                                else "Each icon becomes glossy glass in its own colors. Pick Themed or Color symbols to put symbols on glass of one color.",
                            )
                            ColorRow("Glass color", theme.glassTint, "Also the color of folders", allowAlpha = false) { c -> update { it.copy(glassTint = c) } }
                            IntSliderRow("See-through", theme.glassClarity, 0..90, { if (it == 0) "Solid" else "$it%" }) { v -> update { it.copy(glassClarity = v) } }
                            IntSliderRow("Shine", theme.glassShine, 0..100, { "$it%" }) { v -> update { it.copy(glassShine = v) } }
                            IntSliderRow("Depth", theme.glassDepth, 0..100, { "$it%" }) { v -> update { it.copy(glassDepth = v) } }
                        } else {
                            IntSliderRow("Shadow", theme.iconShadow, 0..100, { if (it == 0) "None" else "$it%" }) { v -> update { it.copy(iconShadow = v) } }
                            NoteText("Glass puts every icon on a glossy 3D base with a light rim, a shine and a soft shadow.")
                        }
                    }
                }
            }
        }

        item { SectionHeader("Outline") }
        item {
            SettingCard {
                IntSliderRow("Thickness", theme.iconOutline, 0..60, { if (it == 0) "None" else "%.1f%%".format(it / 10f) }) { v -> update { it.copy(iconOutline = v) } }
                AnimatedVisibility(theme.iconOutline > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ColorRow("Outline color", theme.iconOutlineColor, "A ring around every icon") { c -> update { it.copy(iconOutlineColor = c) } }
                }
            }
        }

        item { SectionHeader("Size") }
        item {
            SettingCard {
                IntSliderRow("Icon size", theme.iconSizeDp, 36..80, { "$it dp" }) { v -> update { it.copy(iconSizeDp = v) } }
                IntSliderRow("Symbol size", theme.iconScale, 60..130, { "$it%" }) { v -> update { it.copy(iconScale = v) } }
                NoteText("Symbol size zooms the picture inside each icon's shape.")
            }
        }

        item { SectionHeader("Color tint") }
        item {
            SettingCard {
                IntSliderRow("Strength", theme.iconTintStrength, 0..100, { if (it == 0) "Off" else "$it%" }) { v -> update { it.copy(iconTintStrength = v) } }
                ColorRow("Tint color", theme.iconTint, "Pulls every icon's colors toward this one", allowAlpha = false) { c ->
                    update { it.copy(iconTint = c, iconTintStrength = if (it.iconTintStrength == 0) 60 else it.iconTintStrength) }
                }
            }
        }

        item { SectionHeader("Notification dots") }
        item {
            SettingCard {
                SwitchRow("Show notification dots", theme.notificationDots) { b -> update { it.copy(notificationDots = b) } }
                ColorRow("Dot color", theme.dotColor ?: theme.accent, if (theme.dotColor == null) "Same as your accent color" else null, allowAlpha = false) { c ->
                    update { it.copy(dotColor = c) }
                }
                SwitchRow("Show the number", theme.dotCount, "How many notifications are waiting") { b -> update { it.copy(dotCount = b) } }
            }
        }

        item { SectionHeader("Icon pack") }
        item {
            SettingCard {
                RadioRow("None", "Each app's own icon, shaped and styled above", theme.iconPack == null) {
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
                ClickRow("Change a single app's icon", "Its own picture, shape, style, finish and colors", icon = Icons.Filled.Brush, onClick = openApps)
                ClickRow("Reset icon settings", "Back to plain icons. Your apps and other settings stay.", icon = Icons.Filled.Refresh) {
                    val d = ThemeConfig()
                    update {
                        it.copy(
                            iconShape = d.iconShape, iconCorner = d.iconCorner, iconStyle = d.iconStyle, themedFg = d.themedFg, themedBg = d.themedBg,
                            themedBg2 = d.themedBg2, iconBgOpacity = d.iconBgOpacity, iconFinish = d.iconFinish, iconShadow = d.iconShadow,
                            iconOutline = d.iconOutline, iconOutlineColor = d.iconOutlineColor, iconScale = d.iconScale, iconSizeDp = d.iconSizeDp,
                            iconTint = d.iconTint, iconTintStrength = d.iconTintStrength, dotColor = d.dotColor, dotCount = d.dotCount,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ClickRow(title, subtitle, leading = { RadioButton(selected = selected, onClick = onClick) }, onClick = onClick)
}

/** A choice card that grows and lights up when picked. */
@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "choiceScale")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "choiceBg")
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "choiceRing")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(2.dp, ring, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ShapePicker(selected: IconShape, corner: Int, onSelect: (IconShape) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (IconShape.entries.indexOf(selected) - 1).coerceAtLeast(0))
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(IconShape.entries) { shape ->
            ChoiceCard(shape == selected, { onSelect(shape) }) {
                Canvas(Modifier.size(44.dp)) {
                    val path = IconShapes.path(shape, size.minDimension, corner)
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

/** Every style shown on three of your own apps, so you can see each one before picking it. */
@Composable
private fun StylePicker(theme: ThemeConfig, samples: List<AppEntry>, onSelect: (IconStyle) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (IconStyle.entries.indexOf(theme.iconStyle) - 1).coerceAtLeast(0))
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("styles")) {
        items(IconStyle.entries) { style ->
            ChoiceCard(style == theme.iconStyle, { onSelect(style) }) {
                CompositionLocalProvider(LocalLauncherTheme provides theme.copy(iconStyle = style)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        samples.forEach { AppIcon(it, null, 30.dp) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(style.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
