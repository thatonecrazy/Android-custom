package app.veneer.ui.launcher

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.veneer.data.BuiltinKind
import app.veneer.data.ItemContent
import app.veneer.data.LayoutOps
import app.veneer.data.WidgetAlign
import app.veneer.data.WallpaperSpec
import app.veneer.data.WidgetStyle
import app.veneer.ui.customize.brush
import app.veneer.ui.common.ChoiceRow
import app.veneer.ui.common.ClickRow
import app.veneer.ui.common.ColorRow
import app.veneer.ui.common.FontStrip
import app.veneer.ui.common.IntSliderRow
import app.veneer.ui.common.SectionHeader
import app.veneer.ui.common.SwitchRow

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit) =
    this.padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)

/** The menu shown after holding an empty spot on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(controller: HomeController, actions: LauncherActions) {
    val state by controller.store.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    val page = controller.currentPage
    val itemsOnPage = state.items.count { it.page == page }
    ModalBottomSheet(
        onDismissRequest = { controller.homeMenuOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetRow(Icons.Filled.Add, "Widgets", "Clocks, search and widgets from your apps") {
                controller.homeMenuOpen = false
                controller.widgetPickerOpen = true
            }
            SheetRow(Icons.Filled.Face, "Wallpaper", "Pictures, gradients and system colors") {
                controller.homeMenuOpen = false
                actions.openCustomize("wallpaper")
            }
            SheetRow(Icons.Filled.Edit, "Customize", "Icons, fonts, themes, grid and dock") {
                controller.homeMenuOpen = false
                actions.openCustomize()
            }
            SheetRow(Icons.Filled.Home, "Add a page") {
                controller.homeMenuOpen = false
                controller.addPage()
            }
            if (state.pageCount > 1) {
                SheetRow(
                    Icons.Filled.Delete,
                    "Remove this page",
                    if (itemsOnPage > 0) "Also removes the $itemsOnPage things on it" else "It's empty",
                ) {
                    if (itemsOnPage == 0) {
                        controller.homeMenuOpen = false
                        controller.removePage(page, actions::deleteWidget)
                    } else {
                        confirmRemove = true
                    }
                }
            }
            SheetRow(Icons.Filled.Settings, "Phone settings") {
                controller.homeMenuOpen = false
                app.veneer.system.SystemActions.open(
                    controller.app.applicationContext,
                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
                )
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this page?") },
            text = { Text("The $itemsOnPage apps and widgets on it will be removed from your home screen. Apps stay installed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    controller.homeMenuOpen = false
                    controller.removePage(page, actions::deleteWidget)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/** Every styling option for one of Veneer's own widgets, with a live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetStyleSheet(controller: HomeController, itemId: Long) {
    val state by controller.store.state.collectAsState()
    val item = state.items.firstOrNull { it.id == itemId }
    val content = item?.content as? ItemContent.Builtin
    if (content == null) {
        LaunchedEffect(Unit) { controller.editingStyleItemId = null }
        return
    }
    val style = content.style
    fun update(transform: (WidgetStyle) -> WidgetStyle) = controller.store.update { s ->
        val current = s.items.firstOrNull { it.id == itemId }?.content as? ItemContent.Builtin ?: return@update s
        LayoutOps.updateContent(s, itemId, current.copy(style = transform(current.style)))
    }
    ModalBottomSheet(
        onDismissRequest = { controller.editingStyleItemId = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(content.kind.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Box(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(if (item.spanY > 1) 150.dp else 90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background((state.theme.wallpaper ?: WallpaperSpec()).brush()),
            ) {
                BuiltinWidgetView(content.kind, style)
            }
            SectionHeader("Font")
            FontStrip(selected = style.fontId, themeOption = true) { id -> update { it.copy(fontId = id) } }
            IntSliderRow("Size", style.sizeSp, 10..160, { "$it sp" }) { v -> update { it.copy(sizeSp = v) } }
            val theme = state.theme
            ColorRow("Text color", style.color ?: theme.labelColor, if (style.color == null) "Following the theme" else null) { c ->
                update { it.copy(color = c) }
            }
            if (style.color != null) {
                ClickRow("Use the theme's text color") { update { it.copy(color = null) } }
            }
            ChoiceRow("Alignment", WidgetAlign.entries, style.align, { it.name }) { a -> update { it.copy(align = a) } }
            SwitchRow("Bold", style.bold) { b -> update { it.copy(bold = b) } }
            SwitchRow("Shadow", style.shadow ?: theme.labelShadow) { b -> update { it.copy(shadow = b) } }
            SwitchRow("All caps", style.uppercase) { b -> update { it.copy(uppercase = b) } }

            if (content.kind == BuiltinKind.Clock || content.kind == BuiltinKind.ClockDate) {
                SectionHeader("Time")
                val formats = listOf<Boolean?>(null, false, true)
                ChoiceRow(null, formats, style.use24h, { when (it) { null -> "Phone setting"; false -> "12-hour"; true -> "24-hour" } }) { f ->
                    update { it.copy(use24h = f) }
                }
                SwitchRow("Show seconds", style.showSeconds) { b -> update { it.copy(showSeconds = b) } }
            }
            if (content.kind == BuiltinKind.Text || content.kind == BuiltinKind.Greeting) {
                SectionHeader(if (content.kind == BuiltinKind.Text) "Text" else "Your name (optional)")
                var text by remember { mutableStateOf(style.text) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t; update { it.copy(text = t) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            SectionHeader("Background")
            val defaultBg = if (content.kind == BuiltinKind.Search) theme.dockBg else 0
            ColorRow(
                "Background color",
                style.background ?: defaultBg,
                subtitle = if (style.background == null) "Following the theme" else "Lower the opacity for a see-through card",
            ) { c -> update { it.copy(background = c) } }
            if (style.background != null) {
                ClickRow("Use the theme's background") { update { it.copy(background = null) } }
            }
            IntSliderRow("Corner roundness", style.cornerDp, 0..48, { "$it dp" }) { v -> update { it.copy(cornerDp = v) } }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ResizeDialog(controller: HomeController, itemId: Long) {
    val context = LocalContext.current
    val state = controller.store.current
    val item = state.items.firstOrNull { it.id == itemId }
    if (item == null) {
        LaunchedEffect(Unit) { controller.resizingItemId = null }
        return
    }
    var w by remember { mutableIntStateOf(item.spanX) }
    var h by remember { mutableIntStateOf(item.spanY) }
    AlertDialog(
        onDismissRequest = { controller.resizingItemId = null },
        title = { Text("Resize") },
        text = {
            Column {
                IntSliderRow("Width", w, 1..state.theme.gridColumns, { "$it cells" }) { w = it }
                IntSliderRow("Height", h, 1..state.theme.gridRows, { "$it cells" }) { h = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val next = LayoutOps.resize(controller.store.current, itemId, w, h)
                if (next == null) {
                    Toast.makeText(context, "Not enough free space on this page", Toast.LENGTH_SHORT).show()
                } else {
                    controller.store.update { LayoutOps.resize(it, itemId, w, h) ?: it }
                    controller.resizingItemId = null
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = { controller.resizingItemId = null }) { Text("Cancel") } },
    )
}
