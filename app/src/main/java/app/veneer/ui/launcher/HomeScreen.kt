package app.veneer.ui.launcher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.veneer.apps.AppEntry
import app.veneer.apps.AppKey
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.HomeItem
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.LayoutOps
import app.veneer.system.SystemActions
import app.veneer.ui.theme.Vivid
import app.veneer.widgets.WidgetHost
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** What the home screen needs from its activity. */
interface LauncherActions {
    val widgetHost: WidgetHost
    fun launch(key: AppKey)
    fun addSystemWidget(info: AppWidgetProviderInfo)
    fun reconfigureWidget(appWidgetId: Int)
    fun deleteWidget(appWidgetId: Int)
    fun openCustomize(section: String? = null)
}

private data class Cell(val x: Int, val y: Int, val spanX: Int, val spanY: Int)

private fun HomeItem.cell() = Cell(x, y, spanX, spanY)

private val GridPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

/** Lays children out on a fixed grid. Each child carries its cell as its layoutId. */
@Composable
private fun CellGrid(columns: Int, rows: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cw = w.toFloat() / columns
        val ch = h.toFloat() / rows
        val placeables = measurables.map { m ->
            val c = m.layoutId as? Cell ?: Cell(0, 0, 1, 1)
            val pw = (c.spanX * cw).roundToInt().coerceAtLeast(0)
            val ph = (c.spanY * ch).roundToInt().coerceAtLeast(0)
            m.measure(Constraints.fixed(pw, ph)) to c
        }
        layout(w, h) {
            placeables.forEach { (p, c) -> p.place((c.x * cw).roundToInt(), (c.y * ch).roundToInt()) }
        }
    }
}

fun appId(content: ItemContent.App) = "${content.component}#${content.user}"

@Composable
fun HomeScreen(
    controller: HomeController,
    actions: LauncherActions,
    state: LauncherState,
    appMap: Map<String, AppEntry>,
) {
    val theme = state.theme
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val pagerState = rememberPagerState(initialPage = 0) { state.pageCount }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { controller.currentPage = it }
    }
    LaunchedEffect(controller) {
        controller.scrollToPage.collect { page ->
            delay(16)
            pagerState.animateScrollToPage(page.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)))
        }
    }

    // While dragging an item, holding it near the left or right edge flips pages.
    val edgePx = with(density) { 28.dp.toPx() }
    val edge by remember {
        derivedStateOf {
            val d = controller.drag
            val b = controller.gridBounds
            when {
                d == null || !d.started || b.isEmpty -> 0
                d.pointer.y > b.bottom -> 0
                d.pointer.x < b.left + edgePx -> -1
                d.pointer.x > b.right - edgePx -> 1
                else -> 0
            }
        }
    }
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(650)
            val target = pagerState.currentPage + edge
            val s = latestState
            when {
                target in 0 until s.pageCount -> pagerState.animateScrollToPage(target)
                target == s.pageCount && s.items.any { it.page == pagerState.currentPage } -> {
                    controller.store.update { LayoutOps.addPage(it) }
                    delay(32)
                    pagerState.animateScrollToPage(target)
                }
                else -> break
            }
        }
    }

    var gridHeightPx by remember { mutableIntStateOf(0) }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    val labelSpace = if (theme.showHomeLabels) (theme.labelSizeSp * 1.3f + 6f).dp else 0.dp
    val iconSize: Dp = with(density) {
        if (gridHeightPx == 0) theme.iconSizeDp.dp else {
            val cellH = ((gridHeightPx - GridPadding.calculateTopPadding().toPx() * 2) / theme.gridRows).toDp()
            val cellW = ((gridWidthPx - GridPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).toPx() * 2) / theme.gridColumns).toDp()
            minOf(theme.iconSizeDp.dp, cellH - labelSpace - 4.dp, cellW - 8.dp).coerceAtLeast(24.dp)
        }
    }
    val dockIconSize = min(theme.iconSizeDp, 60).dp

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(theme.swipeDownNotifications) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 56.dp.toPx()
                        when {
                            total < -threshold -> controller.openDrawer()
                            total > threshold && theme.swipeDownNotifications -> SystemActions.expandNotifications(context)
                        }
                    },
                ) { change, dy ->
                    change.consume()
                    total += dy
                }
            },
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInRoot()
                    gridHeightPx = coords.size.height
                    gridWidthPx = coords.size.width
                    with(density) {
                        val px = GridPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).toPx()
                        val py = GridPadding.calculateTopPadding().toPx()
                        controller.gridBounds = Rect(b.left + px, b.top + py, b.right - px, b.bottom - py)
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.homeMenuOpen = true
                                })
                            },
                    )
                    CellGrid(theme.gridColumns, theme.gridRows, Modifier.fillMaxSize().padding(GridPadding)) {
                        DropHighlight(controller, state, page)
                        state.items.filter { it.page == page }.forEach { item ->
                            key(item.id) {
                                HomeItemView(
                                    item, controller, actions, state, appMap,
                                    iconSize = iconSize,
                                    showLabel = theme.showHomeLabels,
                                    modifier = Modifier.layoutId(item.cell()),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (theme.pageIndicator && state.pageCount > 1) {
            PageIndicator(state.pageCount, pagerState.currentPage, Color(theme.accent))
        }
        if (theme.dockEnabled) {
            Dock(controller, actions, state, appMap, dockIconSize)
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun DropHighlight(controller: HomeController, state: LauncherState, page: Int) {
    val d = controller.drag?.takeIf { it.started } ?: return
    val spot = controller.dropSpot(d, state) ?: return
    if (spot.page != page) return
    val valid = LayoutOps.move(state, d.item.id, spot) != null
    Box(
        Modifier
            .layoutId(Cell(spot.x, spot.y, d.item.spanX, d.item.spanY))
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (valid) Color.White.copy(alpha = 0.18f) else Color(0x40FF5252))
            .border(1.5.dp, if (valid) Color.White.copy(alpha = 0.5f) else Color(0x99FF5252), RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun PageIndicator(count: Int, current: Int, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else Color.White.copy(alpha = 0.45f)),
            )
        }
    }
}

@Composable
private fun Dock(
    controller: HomeController,
    actions: LauncherActions,
    state: LauncherState,
    appMap: Map<String, AppEntry>,
    iconSize: Dp,
) {
    val theme = state.theme
    val haptics = LocalHapticFeedback.current
    val items = state.items.filter { it.page == DOCK_PAGE }
    val target = controller.drag?.takeIf { it.started }?.let { controller.dropSpot(it, state) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(theme.dockCornerDp.dp))
            .background(Color(theme.dockBg))
            .onGloballyPositioned { controller.dockBounds = it.boundsInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.homeMenuOpen = true
                })
            }
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until theme.dockColumns) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(iconSize + 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (target?.page == DOCK_PAGE && target.x == col) {
                        Box(
                            Modifier
                                .size(iconSize + 6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                        )
                    }
                    items.firstOrNull { it.x == col }?.let { item ->
                        key(item.id) {
                            HomeItemView(item, controller, actions, state, appMap, iconSize, showLabel = false, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
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
    val context = LocalContext.current
    when (val content = item.content) {
        is ItemContent.App -> {
            val id = appId(content)
            val entry = appMap[id]
            val override = state.overrides[id]
            val label = override?.label ?: entry?.label ?: ""
            ItemFrame(item, controller, modifier, onClick = { entry?.let { actions.launch(it.key) } }) { pressed ->
                AppTile(entry, label, override?.icon, iconSize, showLabel, Color(state.theme.labelColor), pressed = pressed)
                ItemMenu(item, controller, actions, entry)
            }
        }
        is ItemContent.Widget -> ItemFrame(item, controller, modifier, onClick = null) {
            HostedWidget(content.appWidgetId, actions.widgetHost)
            ItemMenu(item, controller, actions, null)
        }
        is ItemContent.Builtin -> {
            val onClick: (() -> Unit)? = when (content.kind) {
                BuiltinKind.Clock, BuiltinKind.ClockDate -> { { SystemActions.openAlarms(context) } }
                BuiltinKind.Date -> { { SystemActions.openCalendar(context) } }
                BuiltinKind.Battery -> { { SystemActions.openBatterySettings(context) } }
                BuiltinKind.Search -> { { controller.openDrawer(focusSearch = true) } }
                BuiltinKind.Greeting, BuiltinKind.Text -> null
            }
            ItemFrame(item, controller, modifier, onClick = onClick) {
                BuiltinWidgetView(content.kind, content.style)
                ItemMenu(item, controller, actions, null)
            }
        }
    }
}

@Composable
private fun ItemMenu(item: HomeItem, controller: HomeController, actions: LauncherActions, entry: AppEntry?) {
    val expanded = controller.menuItemId == item.id && controller.drag?.started != true
    fun close() {
        controller.menuItemId = null
    }
    FriendlyMenu(expanded = expanded, onDismissRequest = ::close) {
        when (val content = item.content) {
            is ItemContent.App -> {
                if (entry != null) {
                    MenuRow("Edit icon and name", Icons.Filled.Edit, Vivid.Violet) { close(); controller.editingApp = entry.key }
                    MenuRow("App info", Icons.Filled.Info, Vivid.Blue) { close(); controller.app.apps.openAppInfo(entry.key) }
                }
                MenuRow("Remove from home", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
                if (entry != null) {
                    MenuRow("Uninstall", Icons.Filled.Delete, Vivid.Coral) { close(); controller.app.apps.uninstall(entry.key) }
                }
            }
            is ItemContent.Builtin -> {
                MenuRow("Style", Icons.Filled.Palette, Vivid.Pink) { close(); controller.editingStyleItemId = item.id }
                if (item.page != DOCK_PAGE) {
                    MenuRow("Resize", Icons.Filled.OpenInFull, Vivid.Teal) { close(); controller.resizingItemId = item.id }
                }
                MenuRow("Remove", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
            }
            is ItemContent.Widget -> {
                if (item.page != DOCK_PAGE) {
                    MenuRow("Resize", Icons.Filled.OpenInFull, Vivid.Teal) { close(); controller.resizingItemId = item.id }
                }
                val info = actions.widgetHost.info(content.appWidgetId)
                if (info != null && actions.widgetHost.isReconfigurable(info)) {
                    MenuRow("Settings", Icons.Filled.Settings, Vivid.Blue) { close(); actions.reconfigureWidget(content.appWidgetId) }
                }
                MenuRow("Remove", Icons.Filled.RemoveCircleOutline, Vivid.Orange) { close(); controller.removeItem(item.id, actions::deleteWidget) }
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
                val id = appId(content)
                val override = state.overrides[id]
                val entry = appMap[id]
                val iconSize = min(theme.iconSizeDp.toFloat(), with(density) { (min(d.itemSize.width, d.itemSize.height) * 0.8f).toDp().value }).dp
                AppTile(
                    entry, override?.label ?: entry?.label ?: "", override?.icon, iconSize,
                    showLabel = d.item.page != DOCK_PAGE && theme.showHomeLabels,
                    labelColor = Color(theme.labelColor),
                )
            }
            is ItemContent.Builtin -> BuiltinWidgetView(content.kind, content.style)
            is ItemContent.Widget -> Box(
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

/** Follows the finger once a long press has started a drag. Must sit on the root of the screen. */
fun Modifier.trackDrags(controller: HomeController): Modifier = pointerInput(controller) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (controller.drag == null) continue
            val change = event.changes.firstOrNull() ?: continue
            event.changes.forEach { it.consume() }
            if (change.pressed) controller.onPointerMove(change.position) else controller.onPointerUp()
        }
    }
}
