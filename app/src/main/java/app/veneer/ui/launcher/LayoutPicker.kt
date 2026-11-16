package app.veneer.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.veneer.data.LayoutTemplate
import app.veneer.data.Layouts
import app.veneer.data.ThemeConfig
import app.veneer.data.WallpaperSpec
import app.veneer.ui.common.WallpaperPreview

/** The phone-sized frame previews are drawn at before being scaled down, so text sizes look right. */
private val FrameW = 380.dp
private val FrameH = 600.dp

/** A horizontal row of ready-made layouts. Picking one asks where to put it. */
@Composable
fun LayoutTemplatesRow(theme: ThemeConfig, onApply: (LayoutTemplate, Boolean) -> Unit) {
    var picked by remember { mutableStateOf<LayoutTemplate?>(null) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("layouts"),
    ) {
        items(Layouts.all, key = { it.name }) { template ->
            Column(
                Modifier
                    .width(168.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { picked = template }
                    .padding(10.dp),
            ) {
                LayoutPreview(template, theme, Modifier.fillMaxWidth().height(232.dp).clip(RoundedCornerShape(14.dp))) { picked = template }
                Spacer(Modifier.height(8.dp))
                Text(template.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    picked?.let { template ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Use \"${template.name}\"?") },
            text = { Text("It goes on a page of its own, so nothing on your home screen is moved or removed. Tap any widget afterwards to restyle it.") },
            confirmButton = {
                TextButton(onClick = { picked = null; onApply(template, false) }) { Text("Add as a new page") }
            },
            dismissButton = {
                TextButton(onClick = { picked = null; onApply(template, true) }) { Text("Make it my first page") }
            },
        )
    }
}

/** The layout drawn at phone size over the current wallpaper, then scaled down to fit. Taps go to the card. */
@Composable
fun LayoutPreview(template: LayoutTemplate, theme: ThemeConfig, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val scale = minOf(maxWidth / FrameW, maxHeight / FrameH)
        WallpaperPreview(theme.wallpaper ?: WallpaperSpec(), Modifier.fillMaxSize())
        Box(
            Modifier
                .requiredSize(FrameW, FrameH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            CompositionLocalProvider(LocalWidgetPreview provides true) {
                val cellW = FrameW / 4
                val cellH = (FrameH - 24.dp) / 6
                template.items.forEach { p ->
                    BuiltinWidgetView(
                        p.content.kind,
                        p.content.style,
                        Modifier
                            .offset(cellW * p.x, 24.dp + cellH * p.y)
                            .size(cellW * p.spanX, cellH * p.spanY),
                    )
                }
            }
        }
        // Covers the preview so its widgets' own buttons don't swallow the tap.
        if (onClick != null) Box(Modifier.fillMaxSize().clickable(onClick = onClick))
    }
}
