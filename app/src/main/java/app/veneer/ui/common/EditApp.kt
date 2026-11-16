package app.veneer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.veneer.apps.AppEntry
import app.veneer.apps.normalizeForSearch
import app.veneer.data.IconRef
import app.veneer.icons.IconPack
import app.veneer.icons.IconPackInfo
import app.veneer.ui.launcher.AppIcon
import app.veneer.ui.theme.LocalLauncherTheme
import app.veneer.veneer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

/** Copies a picked picture into app storage as a square icon. Returns the stored file name. */
suspend fun saveCustomIcon(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val target = 384
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val side = min(src.width, src.height)
        val crop = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        val out = createBitmap(target, target)
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, target, target), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        val name = UUID.randomUUID().toString() + ".png"
        File(context.veneer.icons.customIconDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
        name
    }.getOrNull()
}

/** Rename an app, change its icon (from an icon pack or a picture) or hide it. */
@Composable
fun EditAppScreen(entry: AppEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    val state by app.store.state.collectAsState()
    val override = state.overrides[entry.key.id]
    val scope = rememberCoroutineScope()
    var name by remember(entry.key) { mutableStateOf(override?.label ?: "") }
    var browsingPack by remember { mutableStateOf<IconPackInfo?>(null) }
    val packs = remember { app.icons.packs.installedPacks() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val file = saveCustomIcon(context, uri)
            if (file != null) app.store.updateOverride(entry.key.id) { it.copy(icon = IconRef.Image(file)) }
        }
    }

    val pack = browsingPack
    if (pack != null) {
        IconPackBrowser(pack, entry, onBack = { browsingPack = null }) { drawable ->
            app.store.updateOverride(entry.key.id) { it.copy(icon = IconRef.Pack(pack.packageName, drawable)) }
            browsingPack = null
        }
        return
    }

    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Edit app", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done") }
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(entry, override?.icon, 84.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(override?.label ?: entry.label, style = MaterialTheme.typography.titleMedium)
                if (entry.isWorkProfile) {
                    Text("Work profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader("Name")
            OutlinedTextField(
                value = name,
                onValueChange = { text ->
                    name = text
                    app.store.updateOverride(entry.key.id) { it.copy(label = text.trim().ifEmpty { null }) }
                },
                placeholder = { Text(entry.label) },
                singleLine = true,
                supportingText = { Text("Leave empty to use the app's own name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            SectionHeader("Icon")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use a picture")
                }
                packs.forEach { info ->
                    FilledTonalButton(onClick = { browsingPack = info }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Category, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Pick from ${info.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (packs.isEmpty()) {
                    Text(
                        "Install an icon pack from the Play Store to pick icons from it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { app.store.updateOverride(entry.key.id) { it.copy(icon = null) } },
                    enabled = override?.icon != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Back to the theme's icon")
                }
            }

            SectionHeader("App list")
            SwitchRow(
                "Hide from the app list",
                checked = override?.hidden == true,
                subtitle = "Still opens from search and stays on your home screen",
            ) { hide -> app.store.updateOverride(entry.key.id) { it.copy(hidden = hide) } }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IconPackBrowser(pack: IconPackInfo, entry: AppEntry, onBack: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.veneer
    var query by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    val loaded by produceState<Pair<IconPack?, List<String>>?>(null, pack.packageName) {
        value = withContext(Dispatchers.IO) {
            val p = app.icons.packs.get(pack.packageName)
            p to (p?.allDrawableNames() ?: emptyList())
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(pack.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search icons") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val data = loaded
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val (iconPack, names) = data
            val labelWords = remember(entry) { normalizeForSearch(entry.label).split(' ').filter { it.length > 2 } }
            val suggested = remember(names) {
                val exact = iconPack?.drawableNameFor(entry.key.component)
                val fuzzy = names.filter { n -> labelWords.any { w -> n.lowercase().replace('_', ' ').contains(w) } }.take(24)
                (listOfNotNull(exact) + fuzzy).distinct()
            }
            val shown = remember(names, query) {
                val q = normalizeForSearch(query.trim()).replace(' ', '_')
                if (q.isEmpty()) names else names.filter { it.lowercase().contains(q) }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), modifier = Modifier.fillMaxSize()) {
                if (query.isBlank() && suggested.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Suggested") }
                    items(suggested, key = { "s_$it" }) { name -> PackIconCell(pack.packageName, name) { onPick(name) } }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("All icons (${names.size})") }
                }
                items(shown, key = { it }) { name -> PackIconCell(pack.packageName, name) { onPick(name) } }
            }
        }
    }
}

@Composable
private fun PackIconCell(packName: String, drawable: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val sizePx = with(LocalDensity.current) { 52.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packName, drawable) {
        value = context.veneer.icons.packIcon(packName, drawable, sizePx, theme)
    }
    Box(
        Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { Image(it, contentDescription = drawable, modifier = Modifier.size(52.dp)) }
    }
}
