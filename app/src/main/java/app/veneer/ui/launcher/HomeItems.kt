package app.veneer.ui.launcher

import android.content.pm.ShortcutInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.veneer.apps.AppEntry
import app.veneer.apps.AppKey
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.HomeItem
import app.veneer.data.IconFinish
import app.veneer.ui.common.glassSurface
import app.veneer.data.IconShape
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.LayoutOps
import app.veneer.icons.IconShapes
import app.veneer.system.SystemActions
import app.veneer.ui.theme.LocalThemeFont
import app.veneer.ui.theme.Vivid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** The theme's icon shape as a Compose shape, so folders match the icons around them. */
fun iconShapeOf(shape: IconShape, corner: Int = 44): Shape = GenericShape { size, _ ->
    val path = IconShapes.path(shape, min(size.width, size.height), corner)
    if (path != null) addPath(path.asComposePath()) else addRoundRect(
        androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, size.width * 0.3f, size.height * 0.3f),
    )
}

@Composable
fun HomeItemView(
    item: HomeItem,
    controller: HomeController,
    actions: LauncherActions,
    state: LauncherState,
    appMap: Map<String, AppEntry>,
    iconSize: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    when (val content = item.content) {
        is ItemContent.App -> {
            val entry = appMap[content.id]
            val override = state.overrides[content.id]
            val label = override?.label ?: entry?.label ?: ""
            val bounds = remember { LaunchBounds() }
            ItemFrame(item, controller, modifier.recordBounds(bounds), onClick = { entry?.let { actions.launch(it.key, bounds.rect) } }) { pressed ->
                AppTile(entry, label, override?.icon, iconSize, showLabel, Color(override?.labelColor ?: state.theme.labelColor), pressed = pressed)
                ItemMenu(item, controller, actions, entry)
            }
        }
        is ItemContent.Folder -> ItemFrame(item, controller, modifier, onClick = { controller.openFolderId = item.id }) { pressed ->
            FolderTile(content, appMap, state, iconSize, showLabel, pressed)
            ItemMenu(item, controller, actions, null)
        }
        is ItemContent.Widget -> ItemFrame(item, controller, modifier, onClick = null) {
            HostedWidget(content.appWidgetId, actions.widgetHost)
            ItemMenu(item, controller, actions, null)
        }
        is ItemContent.Builtin -> ItemFrame(item, controller, modifier, onClick = builtinClick(content, controller, StyleTarget(item.id), actions)) {
            BuiltinWidgetView(content.kind, content.style)
            ItemMenu(item, controller, actions, null)
        }
        is ItemContent.Stack -> ItemFrame(item, controller, modifier, onClick = null) {
            WidgetStack(item, content, controller, actions)
            ItemMenu(item, controller, actions, null)
        }
    }
}

@Composable
private fun builtinClick(content: ItemContent.Builtin, controller: HomeController, target: StyleTarget, actions: LauncherActions): (() -> Unit)? {
    val context = LocalContext.current
    return when (content.kind) {
        BuiltinKind.Clock, BuiltinKind.ClockDate, BuiltinKind.AnalogClock, BuiltinKind.FlipClock -> { { SystemActions.openAlarms(context) } }
        BuiltinKind.Date, BuiltinKind.Agenda, BuiltinKind.MonthCalendar -> { { SystemActions.openCalendar(context) } }
        BuiltinKind.Battery -> { { SystemActions.openBatterySettings(context) } }
        BuiltinKind.Search -> { { controller.openDrawer(focusSearch = true) } }
        BuiltinKind.Weather, BuiltinKind.Glance -> { { actions.openCustomize("home") } }
        BuiltinKind.Photo -> if (content.style.image == null) { { controller.photoTarget = target } } else null
        BuiltinKind.Greeting, BuiltinKind.Text, BuiltinKind.Media -> null
    }
}

// --- Folders ----------------------------------------------------------------------------------

/** A folder on the home screen: up to four app icons inside the theme's icon shape. */
@Composable
fun FolderTile(
    folder: ItemContent.Folder,
    appMap: Map<String, AppEntry>,
    state: LauncherState,
    iconSize: Dp,
    showLabel: Boolean,
    pressed: Boolean = false,
) {
    val theme = state.theme
    val counts = LocalNotificationCounts.current
    val count = folder.apps.sumOf { a -> appMap[a.id]?.let { counts["${it.packageName}#${it.key.userSerial}"] } ?: 0 }
    val hasDot = theme.notificationDots && count > 0
    val scale = if (pressed) 0.92f else 1f
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            val glass = theme.iconFinish == IconFinish.Glass
            Box(
                if (glass) {
                    // Same footprint as a glass icon: a little smaller than the icon square, leaving room for the shadow.
                    Modifier
                        .size(iconSize)
                        .padding(start = iconSize * 0.07f, end = iconSize * 0.07f, top = iconSize * 0.035f, bottom = iconSize * 0.105f)
                        .glassSurface(iconShapeOf(theme.iconShape, theme.iconCorner), Color(folder.color ?: theme.glassTint), theme.glassClarity, theme.glassShine, theme.glassDepth)
                        .padding(iconSize * 0.08f)
                } else {
                    Modifier
                        .size(iconSize)
                        .clip(iconShapeOf(theme.iconShape, theme.iconCorner))
                        .background(folder.color?.let { Color(it).copy(alpha = 0.85f) } ?: Color.White.copy(alpha = 0.3f))
                        .padding(iconSize * 0.12f)
                },
                contentAlignment = Alignment.Center,
            ) {
                val mini = if (glass) (iconSize * 0.70f - 4.dp) / 2 else (iconSize * 0.76f - 4.dp) / 2
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    folder.apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { app ->
                                AppIcon(appMap[app.id], state.overrides[app.id]?.icon, mini)
                            }
                        }
                    }
                }
            }
            if (hasDot) NotificationDot(iconSize, count, Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            IconLabel(folder.name, Color(theme.labelColor), Modifier.padding(horizontal = 2.dp))
        }
    }
}

private val FolderColors = listOf<Int?>(
    null, 0xFF7C5CFF.toInt(), 0xFFFF4D8D.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFB020.toInt(),
    0xFF22C55E.toInt(), 0xFF12C2A9.toInt(), 0xFF3D7BFF.toInt(),
)

/** The opened folder: its name (tap to rename), its apps, and a row of colors for the folder icon. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPopup(controller: HomeController, actions: LauncherActions, state: LauncherState, appMap: Map<String, AppEntry>) {
    val itemId = controller.openFolderId ?: return
    val item = state.items.firstOrNull { it.id == itemId }
    val folder = item?.content as? ItemContent.Folder
    if (folder == null) {
        LaunchedEffect(itemId) { controller.openFolderId = null }
        return
    }
    val theme = state.theme
    val font = LocalThemeFont.current
    val bg = Color(theme.drawerBg).copy(alpha = 0.97f)
    val fg = Color(theme.drawerTextColor)
    val accent = folder.color?.let { Color(it) } ?: Color(theme.accent)
    var name by remember(itemId) { mutableStateOf(folder.name) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    fun close() {
        controller.openFolderId = null
    }
    BackHandler { close() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::close),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { text ->
                    name = text
                    controller.store.update { LayoutOps.renameFolder(it, itemId, text.ifBlank { "Folder" }) }
                },
                singleLine = true,
                cursorBrush = SolidColor(accent),
                textStyle = TextStyle(fontFamily = font, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val columns = if (folder.apps.size <= 4) 2.coerceAtLeast(min(folder.apps.size, 4)) else 4
            folder.apps.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { app ->
                        val entry = appMap[app.id]
                        val override = state.overrides[app.id]
                        val bounds = remember { LaunchBounds() }
                        Box(
                            Modifier
                                .weight(1f)
                                .recordBounds(bounds)
                                .combinedClickable(
                                    onClick = {
                                        entry?.let { actions.launch(it.key, bounds.rect) }
                                        close()
                                    },
                                    onLongClick = { menuFor = app.id },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppTile(entry, override?.label ?: entry?.label ?: "", override?.icon, 54.dp, showLabel = true, labelColor = fg)
                            FriendlyMenu(expanded = menuFor == app.id, onDismissRequest = { menuFor = null }) {
                                if (entry != null) ShortcutMenuRows(entry.key, controller) { menuFor = null; close() }
                                MenuRow("Move to home screen", Icons.Filled.UnfoldMore, Vivid.Green) {
                                    menuFor = null
                                    controller.store.update { LayoutOps.removeFromFolder(it, itemId, app.id, toHome = true, page = controller.currentPage) }
                                }
                                MenuRow("Remove from folder", Icons.Filled.RemoveCircleOutline, Vivid.Orange) {
                                    menuFor = null
                                    controller.store.update { LayoutOps.removeFromFolder(it, itemId, app.id, toHome = false) }
                                }
                                if (entry != null) {
                                    MenuRow("Edit icon and name", Icons.Filled.Edit, Vivid.Violet) {
                                        menuFor = null
                                        close()
                                        controller.editingApp = entry.key
                                    }
                                    MenuRow("App info", Icons.Filled.Info, Vivid.Blue) {
                                        menuFor = null
                                        controller.app.apps.openAppInfo(entry.key)
                                    }
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderColors.forEach { c ->
                    val selected = c == folder.color
                    Box(
                        Modifier
                            .size(if (selected) 28.dp else 24.dp)
                            .clip(CircleShape)
                            .background(c?.let { Color(it) } ?: Color.White.copy(alpha = 0.3f))
                            .border(if (selected) 2.dp else 0.dp, fg, CircleShape)
                            .clickable { controller.store.update { LayoutOps.setFolderColor(it, itemId, c) } },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Hold an app for more. Drop apps on this folder to add them.", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

// --- Widget stacks ----------------------------------------------------------------------------

/** Several widgets in one spot. Swipe up and down to switch; the dots show which one is up. */
@Composable
fun WidgetStack(item: HomeItem, stack: ItemContent.Stack, controller: HomeController, actions: LauncherActions) {
    val count = stack.widgets.size
    val pager = rememberPagerState(initialPage = (controller.stackPages[item.id] ?: 0).coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    val requested = controller.stackPages[item.id]
    LaunchedEffect(requested, count) {
        if (requested != null && requested != pager.currentPage && count > 0) {
            pager.animateScrollToPage(requested.coerceIn(0, count - 1))
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { controller.stackPages[item.id] = it }
    }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { i -> stackChildKey(stack.widgets.getOrNull(i), i) }) { i ->
            when (val child = stack.widgets.getOrNull(i)) {
                is ItemContent.Widget -> HostedWidget(child.appWidgetId, actions.widgetHost)
                is ItemContent.Builtin -> {
                    val click = builtinClick(child, controller, StyleTarget(item.id, i), actions)
                    BuiltinWidgetView(child.kind, child.style, if (click != null) Modifier.clickable(onClick = click) else Modifier)
                }
                else -> Spacer(Modifier.fillMaxSize())
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f)),
                )
            }
        }
    }
}

private fun stackChildKey(child: ItemContent?, index: Int): Any = when (child) {
    is ItemContent.Widget -> "w${child.appWidgetId}"
    is ItemContent.Builtin -> "b$index${child.kind}"
    else -> index
}

// --- Menus ------------------------------------------------------------------------------------

private data class ShortcutRow(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

/** The app's own shortcuts (like "New message"), shown at the top of its hold menu. */
@Composable
fun ShortcutMenuRows(key: AppKey, controller: HomeController, onPicked: () -> Unit) {
    val apps = controller.app.apps
    val density = LocalDensity.current
    val sizePx = with(density) { 24.dp.roundToPx() }
    val rows by produceState(emptyList<ShortcutRow>(), key) {
        value = withContext(Dispatchers.IO) {
            apps.shortcuts(key).map { info ->
                val label = (info.shortLabel ?: info.longLabel ?: "").toString()
                ShortcutRow(info, label, apps.shortcutIcon(info)?.toBitmap(sizePx, sizePx)?.asImageBitmap())
            }
        }
    }
    rows.forEach { row ->
        DropdownMenuItem(
            text = { Text(row.label) },
            leadingIcon = {
                if (row.icon != null) Image(row.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                else Spacer(Modifier.size(24.dp))
            },
            onClick = {
                onPicked()
                apps.startShortcut(row.info)
            },
        )
    }
}

@Composable
private fun ItemMenu(item: HomeItem, controller: HomeController, actions: LauncherActions, entry: AppEntry?) {
    val expanded = controller.menuItemId == item.id && controller.drag?.started != true
    fun close() {
        controller.menuItemId = null
    }
    val canResize = item.page != DOCK_PAGE
    fun stackAnother() {
        close()
        controller.stackTargetId = item.id
        controller.widgetPickerOpen = true
    }
    FriendlyMenu(expanded = expanded, onDismissRequest = ::close) {
        when (val content = item.content) {
            is ItemContent.App -> {
                if (entry != null) {
                    ShortcutMenuRows(entry.key, controller, ::close)
                    MenuRow("Edit icon and name", Icons.Filled.Edit, Vivid.Violet) { close(); controller.editingApp = entry.key }
                    MenuRow("App info", Icons.Filled.Info, Vivid.Blue) { close(); controller.app.apps.openAppInfo(entry.key) }
                }
                MenuRow("Remove from home", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
                if (entry != null) {
                    MenuRow("Uninstall", Icons.Filled.Delete, Vivid.Coral) { close(); controller.app.apps.uninstall(entry.key) }
                }
            }
            is ItemContent.Folder -> {
                MenuRow("Open and rename", Icons.Filled.Edit, Vivid.Violet) { close(); controller.openFolderId = item.id }
                MenuRow("Ungroup", Icons.Filled.UnfoldMore, Vivid.Green) { close(); controller.ungroupFolder(item.id) }
                MenuRow("Remove folder", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
            }
            is ItemContent.Builtin -> {
                MenuRow("Style", Icons.Filled.Palette, Vivid.Pink) { close(); controller.editingStyle = StyleTarget(item.id) }
                MenuRow("Stack another widget here", Icons.Filled.Layers, Vivid.Purple, ::stackAnother)
                if (canResize) MenuRow("Resize", Icons.Filled.OpenInFull, Vivid.Teal) { close(); controller.resizingItemId = item.id }
                MenuRow("Remove", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
            }
            is ItemContent.Widget -> {
                MenuRow("Stack another widget here", Icons.Filled.Layers, Vivid.Purple, ::stackAnother)
                if (canResize) MenuRow("Resize", Icons.Filled.OpenInFull, Vivid.Teal) { close(); controller.resizingItemId = item.id }
                val info = actions.widgetHost.info(content.appWidgetId)
                if (info != null && actions.widgetHost.isReconfigurable(info)) {
                    MenuRow("Settings", Icons.Filled.Settings, Vivid.Blue) { close(); actions.reconfigureWidget(content.appWidgetId) }
                }
                MenuRow("Remove", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
            }
            is ItemContent.Stack -> {
                val index = (controller.stackPages[item.id] ?: 0).coerceIn(0, (content.widgets.size - 1).coerceAtLeast(0))
                when (val current = content.widgets.getOrNull(index)) {
                    is ItemContent.Builtin -> MenuRow("Style this widget", Icons.Filled.Palette, Vivid.Pink) {
                        close()
                        controller.editingStyle = StyleTarget(item.id, index)
                    }
                    is ItemContent.Widget -> {
                        val info = actions.widgetHost.info(current.appWidgetId)
                        if (info != null && actions.widgetHost.isReconfigurable(info)) {
                            MenuRow("Widget settings", Icons.Filled.Settings, Vivid.Blue) { close(); actions.reconfigureWidget(current.appWidgetId) }
                        }
                    }
                    else -> Unit
                }
                MenuRow("Add a widget to this stack", Icons.Filled.Add, Vivid.Purple, ::stackAnother)
                MenuRow("Take this widget out", Icons.Filled.LayersClear, Vivid.Amber) {
                    close()
                    controller.removeFromStack(item.id, index, actions::deleteWidget)
                }
                if (canResize) MenuRow("Resize", Icons.Filled.OpenInFull, Vivid.Teal) { close(); controller.resizingItemId = item.id }
                MenuRow("Remove stack", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
            }
        }
    }
}

/** The copy of an item that follows the finger while it is dragged. */
@Composable
fun DragGhost(controller: HomeController, state: LauncherState, appMap: Map<String, AppEntry>) {
    val d = controller.drag?.takeIf { it.started } ?: return
    val density = LocalDensity.current
    val topLeft = d.pointer - d.grabOffset
    val width = with(density) { d.itemSize.width.toDp() }
    val height = with(density) { d.itemSize.height.toDp() }
    val theme = state.theme
    val iconSize = min(theme.iconSizeDp.toFloat(), with(density) { (min(d.itemSize.width, d.itemSize.height) * 0.8f).toDp().value }).dp
    val showLabel = d.item.page != DOCK_PAGE && theme.showHomeLabels
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(width, height)
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = 0.92f
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val content = d.item.content) {
            is ItemContent.App -> {
                val override = state.overrides[content.id]
                val entry = appMap[content.id]
                AppTile(entry, override?.label ?: entry?.label ?: "", override?.icon, iconSize, showLabel, Color(override?.labelColor ?: theme.labelColor))
            }
            is ItemContent.Folder -> FolderTile(content, appMap, state, iconSize, showLabel)
            is ItemContent.Builtin -> BuiltinWidgetView(content.kind, content.style)
            is ItemContent.Widget, is ItemContent.Stack -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            )
        }
    }
}
