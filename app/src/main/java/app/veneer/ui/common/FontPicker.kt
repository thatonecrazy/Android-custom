package app.veneer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veneer.fonts.FontOption
import app.veneer.veneer

/**
 * A horizontal strip of font cards, each written in its own font. With [themeOption] the first
 * card means "same as the theme" and selects null.
 */
@Composable
fun FontStrip(selected: String?, themeOption: Boolean, onSelect: (String?) -> Unit) {
    val fonts = LocalContext.current.veneer.fonts
    val imported by fonts.imported.collectAsState()
    val options: List<FontOption?> = (if (themeOption) listOf(null) else emptyList<FontOption?>()) + fonts.builtIn + imported
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it?.id ?: "__theme" }) { option ->
            FontCard(
                name = option?.name ?: "Theme font",
                family = option?.family ?: FontFamily.Default,
                selected = option?.id == selected,
            ) { onSelect(option?.id) }
        }
    }
}

@Composable
fun FontCard(name: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 0.dp, if (selected) scheme.primary else scheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("Aa", style = TextStyle(fontFamily = family, fontSize = 26.sp, color = scheme.onSurface), maxLines = 1)
        Text(
            name,
            style = TextStyle(fontFamily = family, fontSize = 12.sp, color = scheme.onSurfaceVariant),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
