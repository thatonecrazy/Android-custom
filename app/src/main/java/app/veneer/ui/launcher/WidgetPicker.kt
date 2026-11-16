package app.veneer.ui.launcher

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.veneer.data.BuiltinKind
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.ui.customize.brush
import app.veneer.widgets.WidgetGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How many grid cells a widget wants, based on its declared size and the current cell size. */
fun widgetSpan(info: AppWidgetProviderInfo, cellWidthPx: Float, cellHeightPx: Float, columns: Int, rows: Int): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
    }
    if (cellWidthPx <= 0f || cellHeightPx <= 0f) return 2.coerceAtMost(columns) to 1
    val sx = ceil(info.minWidth / cellWidthPx).roundToInt().coerceIn(1, columns)
    val sy = ceil(info.minHeight / cellHeightPx).roundToInt().coerceIn(1, rows)
    return sx to sy
}

@Composable
fun WidgetPicker(controller: HomeController, actions: LauncherActions) {
    val host = actions.widgetHost
    val theme = controller.store.current.theme
    var query by remember { mutableStateOf("") }
    BackHandler { controller.widgetPickerOpen = false }
    val groups by produceState<List<WidgetGroup>?>(null) { value = host.groups() }
    val cellW = controller.gridBounds.width / theme.gridColumns
    val cellH = controller.gridBounds.height / theme.gridRows

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controller.widgetPickerOpen = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Widgets", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search widgets") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    SectionTitle("Veneer widgets", "Fully styleable: pick any font, size and color after adding.")
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(BuiltinKind.entries.filter { it.label.contains(query, ignoreCase = true) || query.isBlank() }) { kind ->
                            BuiltinCard(kind, theme) {
                                controller.addBuiltin(kind)
                                controller.widgetPickerOpen = false
                            }
                        }
                    }
                }
                val list = groups
                if (list == null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else {
                    val filtered = list.mapNotNull { g ->
                        if (query.isBlank() || g.appLabel.contains(query, ignoreCase = true)) g
                        else g.widgets.filter { host.label(it).contains(query, ignoreCase = true) }
                            .takeIf { it.isNotEmpty() }?.let { g.copy(widgets = it) }
                    }
                    if (filtered.isEmpty()) {
                        item { SectionTitle("No widgets found", null) }
                    }
                    filtered.forEach { group ->
                        item(key = group.packageName) {
                            SectionTitle(group.appLabel, null)
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(group.widgets, key = { it.provider.flattenToString() }) { info ->
                                    val (sx, sy) = widgetSpan(info, cellW, cellH, theme.gridColumns, theme.gridRows)
                                    SystemWidgetCard(info, host.label(info), "$sx × $sy", actions) {
                                        controller.widgetPickerOpen = false
                                        actions.addSystemWidget(info)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BuiltinCard(kind: BuiltinKind, theme: ThemeConfig, onClick: () -> Unit) {
    Column(
        Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(14.dp))
                .background((theme.wallpaper ?: WallpaperSpec()).brush()),
        ) {
            BuiltinWidgetView(kind, HomeController.defaultStyle(kind))
        }
        Spacer(Modifier.height(8.dp))
        Text(kind.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("${kind.defaultSpanX} × ${kind.defaultSpanY}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SystemWidgetCard(info: AppWidgetProviderInfo, label: String, size: String, actions: LauncherActions, onClick: () -> Unit) {
    val preview by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                actions.widgetHost.preview(info)?.let { d ->
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 300
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 200
                    val scale = minOf(1f, 480f / maxOf(w, h))
                    d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Column(
        Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp).size(160.dp, 120.dp))
            } ?: Spacer(Modifier.height(90.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
