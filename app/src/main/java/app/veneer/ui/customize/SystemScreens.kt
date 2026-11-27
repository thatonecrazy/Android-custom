package app.veneer.ui.customize

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import app.veneer.data.ShuffleInterval
import app.veneer.data.ShuffleTarget
import app.veneer.system.PhotoWallpapers
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.apps.AppEntry
import app.veneer.apps.normalizeForSearch
import app.veneer.data.LauncherState
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.data.WallpaperStyle
import app.veneer.system.SystemActions
import app.veneer.system.WallpaperTarget
import app.veneer.system.Wallpapers
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.NoteText
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SettingCard
import app.veneer.ui.common.SwitchRow
import app.veneer.ui.common.WallpaperPreview
import app.veneer.ui.launcher.AppIcon
import app.veneer.veneer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WallpaperScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    var busy by remember { mutableStateOf(false) }
    var spec by remember { mutableStateOf((state.theme.wallpaper ?: WallpaperSpec()).copy(photo = null)) }
    val shuffle = state.shuffle
    val scope = rememberCoroutineScope()
    val addPictures = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val names = app.shuffler.import(uris)
            app.store.update { st -> st.copy(shuffle = st.shuffle.copy(files = st.shuffle.files + names, enabled = true)) }
            busy = false
            Toast.makeText(context, "Added ${names.size} picture${if (names.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
        }
    }
    var chooseTarget by remember { mutableStateOf<((WallpaperTarget) -> Unit)?>(null) }

    fun finish(ok: Boolean) {
        busy = false
        Toast.makeText(context, if (ok) "Wallpaper set" else "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) chooseTarget = { target ->
            busy = true
            app.scope.launch { finish(Wallpapers.applyImage(context.applicationContext, uri, target)) }
        }
    }

    SettingsPage("Wallpaper", onBack, state, apps, section = Section.Wallpaper) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item { SectionHeader("Picture") }
        item {
            SettingCard {
                ClickRow("Choose a picture", "From your photos", icon = Icons.Filled.Image) {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ClickRow("Your phone's wallpaper screen", "Live wallpapers, your phone maker's collections and color options", icon = Icons.Filled.Wallpaper) {
                    SystemActions.open(context, Intent(Intent.ACTION_SET_WALLPAPER))
                }
            }
        }
        item { SectionHeader("Your pictures on shuffle") }
        item {
            SettingCard {
                if (shuffle.files.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(shuffle.files, key = { it }) { name -> ShuffleThumb(name) {
                            app.shuffler.delete(name)
                            app.store.update { st -> st.copy(shuffle = st.shuffle.copy(files = st.shuffle.files - name)) }
                        } }
                    }
                }
                ClickRow(
                    "Add pictures",
                    if (shuffle.files.isEmpty()) "Pick any number of pictures from your phone" else "${shuffle.files.size} in the shuffle",
                    icon = Icons.Filled.AddPhotoAlternate,
                ) { addPictures.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (shuffle.files.isNotEmpty()) {
                    SwitchRow("Shuffle", shuffle.enabled, "Changes while your screen is off, so a new picture greets you at unlock") { on ->
                        app.store.update { st -> st.copy(shuffle = st.shuffle.copy(enabled = on)) }
                    }
                    ChoiceRow("How often", ShuffleInterval.entries, shuffle.interval, { it.label }) { v ->
                        app.store.update { st -> st.copy(shuffle = st.shuffle.copy(interval = v)) }
                    }
                    ChoiceRow("Where", ShuffleTarget.entries, shuffle.target, { it.label }) { v ->
                        app.store.update { st -> st.copy(shuffle = st.shuffle.copy(target = v)) }
                    }
                    ClickRow("Show the next one now", icon = Icons.Filled.SkipNext) {
                        busy = true
                        app.scope.launch { finish(app.shuffler.next()) }
                    }
                }
            }
        }

        item { SectionHeader("Make a gradient") }
        item {
            SettingCard {
                WallpaperPreview(
                    spec,
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                ChoiceRow("Style", WallpaperStyle.entries, spec.style, { it.label }) { spec = spec.copy(style = it) }
                ColorRow(if (spec.style == WallpaperStyle.Solid) "Color" else "First color", spec.color1, allowAlpha = false) {
                    spec = spec.copy(color1 = it)
                }
                if (spec.style != WallpaperStyle.Solid) {
                    ColorRow("Second color", spec.color2, allowAlpha = false) { spec = spec.copy(color2 = it) }
                    val third = spec.color3
                    SwitchRow("Add a third color", third != null) { on ->
                        spec = spec.copy(color3 = if (on) 0xFFFFC93C.toInt() else null)
                    }
                    if (third != null) {
                        ColorRow("Third color", third, allowAlpha = false) { spec = spec.copy(color3 = it) }
                    }
                }
                Button(
                    onClick = {
                        val chosen = spec
                        chooseTarget = { target ->
                            busy = true
                            app.store.updateTheme { it.copy(wallpaper = chosen) }
                            app.scope.launch { finish(Wallpapers.applyGenerated(context.applicationContext, chosen, target)) }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) { Text("Set as wallpaper") }
            }
        }
        item { SectionHeader("Free photos") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(PhotoWallpapers.all, key = { it.id }) { photo ->
                    Column(
                        Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                val chosen = WallpaperSpec(photo = photo.id)
                                chooseTarget = { target ->
                                    busy = true
                                    app.scope.launch { finish(Wallpapers.applyGenerated(context.applicationContext, chosen, target)) }
                                }
                            },
                    ) {
                        WallpaperPreview(
                            WallpaperSpec(photo = photo.id),
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                        Text(photo.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    }
                }
            }
        }
        item { NoteText("Free-to-use photos from Wikimedia Commons, recolored for Veneer. Credits are under Backup → Photo and weather credits.") }

        item { SectionHeader("System colors") }
        item {
            SettingCard {
                NoteText(
                    "On Android 12 and newer, your phone takes its system colors (quick settings, the Settings app, " +
                        "the keyboard and apps that follow Material You) from the wallpaper. A gradient from here is the " +
                        "easiest way to choose those colors.",
                )
                ClickRow("Dark theme, display and font size", "Your phone's display settings", icon = Icons.Filled.DarkMode) {
                    SystemActions.openDisplaySettings(context)
                }
            }
        }
    }

    chooseTarget?.let { apply ->
        AlertDialog(
            onDismissRequest = { chooseTarget = null },
            title = { Text("Set wallpaper on") },
            text = {
                Column {
                    WallpaperTarget.entries.forEach { target ->
                        ClickRow(target.label) {
                            chooseTarget = null
                            apply(target)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AppsScreen(state: LauncherState, apps: List<AppEntry>, onBack: () -> Unit, onEdit: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, state.overrides, query) {
        val q = normalizeForSearch(query.trim())
        apps.filter { e ->
            q.isEmpty() || normalizeForSearch(state.overrides[e.key.id]?.label ?: e.label).contains(q)
        }
    }
    SettingsPage("Your apps", onBack, state, apps, preview = false, section = Section.Apps) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(shown, key = { it.key.id }) { entry ->
            val override = state.overrides[entry.key.id]
            val notes = listOfNotNull(
                if (override?.icon != null) "Custom icon" else null,
                if (override?.hasOwnLook == true || override?.labelColor != null) "Own look" else null,
                if (override?.label != null) "Renamed from ${entry.label}" else null,
                if (override?.hidden == true) "Hidden" else null,
                if (entry.isWorkProfile) "Work" else null,
            )
            ClickRow(
                title = override?.label ?: entry.label,
                subtitle = notes.joinToString(" · ").ifEmpty { null },
                leading = { AppIcon(entry, override?.icon, 40.dp) },
            ) { onEdit(entry) }
        }
    }
}

@Composable
fun BackupScreen(state: LauncherState, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var licenses by remember { mutableStateOf<String?>(null) }
    var licensesTitle by remember { mutableStateOf("Font licenses") }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(app.store.exportJson().toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirm = "Replace your current setup with this backup?" to {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                }
                val ok = text != null && runCatching { app.store.importJson(text) }.isSuccess
                Toast.makeText(context, if (ok) "Backup restored" else "That file isn't a Veneer backup", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    SettingsPage("Backup", onBack, state, emptyList(), preview = false, section = Section.Backup) {
        item { SectionHeader("Updates") }
        item { UpdatesCard() }

        item { SectionHeader("Backup") }
        item {
            SettingCard {
                ClickRow("Save a backup", "Your look, home screen layout, everything you changed on single apps, and saved looks", icon = Icons.Filled.Save) {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    export.launch("veneer-backup-$day.json")
                }
                ClickRow("Restore a backup", icon = Icons.Filled.Restore) { import.launch(arrayOf("*/*")) }
            }
        }
        item {
            NoteText("Widgets from other apps can't move between phones; they show as \"unavailable\" after restoring on a new phone. Custom pictures and imported fonts stay on this phone.")
        }
        item { SectionHeader("Your saved looks") }
        item { YourLooksCard(state) }

        item { SectionHeader("Start over") }
        item {
            SettingCard {
                ClickRow("Reset the look", "Back to the default icons, font and colors. Keeps your layout.", icon = Icons.Filled.Refresh) {
                    confirm = "Reset icons, font and colors to the defaults?" to {
                        app.store.updateTheme { it.withLookOf(ThemeConfig()) }
                    }
                }
                ClickRow("Reset the home screen", "Removes everything from your home screen and dock and sets up the starter layout again", icon = Icons.Filled.Home) {
                    confirm = "Clear your home screen and dock?" to {
                        app.store.update { it.copy(items = emptyList(), pageCount = 1, firstRunDone = false) }
                    }
                }
            }
        }
        item { SectionHeader("About") }
        item {
            SettingCard {
                ClickRow("Veneer $version", "A home screen you can restyle from top to bottom", icon = Icons.Filled.Favorite) {}
                ClickRow("Font licenses", "The bundled fonts are free under the SIL Open Font License", icon = Icons.Filled.Description) {
                    scope.launch {
                        licensesTitle = "Font licenses"
                        licenses = withContext(Dispatchers.IO) {
                            runCatching { context.assets.open("font_licenses.txt").use { it.readBytes().decodeToString() } }.getOrNull()
                        }
                    }
                }
                ClickRow("Photo and weather credits", "Who took the bundled wallpaper photos, and where the weather comes from", icon = Icons.Filled.Image) {
                    scope.launch {
                        licensesTitle = "Photo and weather credits"
                        licenses = withContext(Dispatchers.IO) {
                            runCatching { context.assets.open("photo_credits.txt").use { it.readBytes().decodeToString() } }.getOrNull()
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    action()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    licenses?.let { text ->
        AlertDialog(
            onDismissRequest = { licenses = null },
            title = { Text(licensesTitle) },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licenses = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ShuffleThumb(name: String, onRemove: () -> Unit) {
    val app = LocalContext.current.veneer
    val density = LocalDensity.current
    val image by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            app.shuffler.thumbnail(name, with(density) { 160.dp.roundToPx() })?.asImageBitmap()
        }
    }
    Box(
        Modifier
            .size(width = 72.dp, height = 128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}
