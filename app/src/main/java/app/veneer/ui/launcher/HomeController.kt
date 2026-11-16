package app.veneer.ui.launcher

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import app.veneer.VeneerApp
import app.veneer.apps.AppEntry
import app.veneer.apps.AppKey
import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.HomeItem
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.LayoutOps
import app.veneer.data.WidgetAlign
import app.veneer.data.WidgetStyle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A home item being moved with a finger. */
data class DragState(
    val item: HomeItem,
    /** Where inside the item the finger landed. */
    val grabOffset: Offset,
    val itemSize: IntSize,
    val start: Offset,
    val pointer: Offset,
    val started: Boolean,
)

/** UI state for the home screen that isn't saved: open overlays, menus and the item being dragged. */
class HomeController(val app: VeneerApp, private val touchSlop: Float) {

    val store = app.store

    var drawerOpen by mutableStateOf(false)
        private set
    var drawerFocusSearch by mutableStateOf(false)
        private set
    var widgetPickerOpen by mutableStateOf(false)
    var homeMenuOpen by mutableStateOf(false)
    var menuItemId by mutableStateOf<Long?>(null)
    var editingApp by mutableStateOf<AppKey?>(null)
    var editingStyleItemId by mutableStateOf<Long?>(null)
    var resizingItemId by mutableStateOf<Long?>(null)

    var drag by mutableStateOf<DragState?>(null)
        private set

    var currentPage by mutableIntStateOf(0)
    var gridBounds: Rect = Rect.Zero
    var dockBounds: Rect = Rect.Zero

    private val _scrollToPage = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val scrollToPage: SharedFlow<Int> = _scrollToPage.asSharedFlow()

    val anyOverlayOpen: Boolean
        get() = drawerOpen || widgetPickerOpen || homeMenuOpen || menuItemId != null || editingApp != null ||
            editingStyleItemId != null || resizingItemId != null

    fun openDrawer(focusSearch: Boolean = false) {
        drawerFocusSearch = focusSearch
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        drawerFocusSearch = false
    }

    fun closeAll() {
        closeDrawer()
        widgetPickerOpen = false
        homeMenuOpen = false
        menuItemId = null
        editingApp = null
        editingStyleItemId = null
        resizingItemId = null
        drag = null
    }

    /** Home pressed while already home: close whatever is open, or go back to the first page. */
    fun onHomePressed(alreadyOnHome: Boolean) {
        val hadOverlay = anyOverlayOpen
        closeAll()
        if (alreadyOnHome && !hadOverlay) _scrollToPage.tryEmit(0)
    }

    // --- Dragging -----------------------------------------------------------------------------

    fun onItemLongPress(item: HomeItem, grabOffset: Offset, pointerRoot: Offset, size: IntSize) {
        menuItemId = item.id
        drag = DragState(item, grabOffset, size, pointerRoot, pointerRoot, started = false)
    }

    fun onPointerMove(position: Offset) {
        val d = drag ?: return
        if (!d.started) {
            if ((position - d.start).getDistance() > touchSlop) {
                menuItemId = null
                drag = d.copy(pointer = position, started = true)
            }
        } else {
            drag = d.copy(pointer = position)
        }
    }

    fun onPointerUp() {
        val d = drag ?: return
        drag = null
        if (!d.started) return
        val spot = dropSpot(d, store.current) ?: return
        store.update { LayoutOps.move(it, d.item.id, spot) ?: it }
    }

    fun cancelDrag() {
        drag = null
    }

    /** Which grid cell or dock slot the dragged item would land in. */
    fun dropSpot(d: DragState, state: LauncherState): LayoutOps.Spot? {
        val t = state.theme
        val p = d.pointer
        val item = d.item
        if (t.dockEnabled && !dockBounds.isEmpty && dockBounds.contains(p)) {
            if (item.spanX != 1 || item.spanY != 1) return null
            val cw = dockBounds.width / t.dockColumns
            val col = ((p.x - dockBounds.left) / cw).toInt().coerceIn(0, t.dockColumns - 1)
            return LayoutOps.Spot(DOCK_PAGE, col, 0)
        }
        if (gridBounds.isEmpty || p.y > gridBounds.bottom + 24f) return null
        val cw = gridBounds.width / t.gridColumns
        val ch = gridBounds.height / t.gridRows
        val (col, row) = if (item.spanX == 1 && item.spanY == 1) {
            ((p.x - gridBounds.left) / cw).toInt() to ((p.y - gridBounds.top) / ch).toInt()
        } else {
            val topLeft = p - d.grabOffset
            ((topLeft.x - gridBounds.left) / cw + 0.5f).toInt() to ((topLeft.y - gridBounds.top) / ch + 0.5f).toInt()
        }
        return LayoutOps.Spot(
            currentPage,
            col.coerceIn(0, t.gridColumns - item.spanX.coerceAtMost(t.gridColumns)),
            row.coerceIn(0, t.gridRows - item.spanY.coerceAtMost(t.gridRows)),
        )
    }

    // --- Editing ------------------------------------------------------------------------------

    fun addToHome(entry: AppEntry) {
        store.update { LayoutOps.add(it, ItemContent.App(entry.key.component.flattenToString(), entry.key.userSerial), preferredPage = currentPage) }
    }

    fun addBuiltin(kind: BuiltinKind) {
        val style = defaultStyle(kind)
        store.update { LayoutOps.add(it, ItemContent.Builtin(kind, style), kind.defaultSpanX, kind.defaultSpanY, currentPage) }
    }

    fun addSystemWidget(appWidgetId: Int, provider: String, spanX: Int, spanY: Int) {
        store.update { LayoutOps.add(it, ItemContent.Widget(appWidgetId, provider), spanX, spanY, currentPage) }
    }

    fun removeItem(id: Long, onWidgetRemoved: (Int) -> Unit) {
        val item = store.current.items.firstOrNull { it.id == id } ?: return
        (item.content as? ItemContent.Widget)?.let { onWidgetRemoved(it.appWidgetId) }
        store.update { LayoutOps.remove(it, id) }
    }

    fun addPage() {
        store.update { LayoutOps.addPage(it) }
        _scrollToPage.tryEmit(store.current.pageCount - 1)
    }

    fun removePage(page: Int, onWidgetRemoved: (Int) -> Unit) {
        store.current.items.filter { it.page == page }.forEach { item ->
            (item.content as? ItemContent.Widget)?.let { onWidgetRemoved(it.appWidgetId) }
        }
        store.update { LayoutOps.removePage(it, page) }
    }

    /** Fills an empty home screen with a clock, a search bar and the phone's main apps. */
    fun setupDefaultLayout() {
        val apps = app.apps
        val dockIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        )
        val homeIntents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        val used = HashSet<String>()
        fun resolve(intents: List<Intent>) = intents.mapNotNull { apps.appForIntent(it) }.filter { used.add(it.key.id) }
        val dockApps = resolve(dockIntents)
        val ownEntry = apps.apps.value.firstOrNull { it.packageName == app.packageName }?.also { used.add(it.key.id) }
        val homeApps = (resolve(homeIntents) + listOfNotNull(ownEntry)).take(4)

        store.update { s0 ->
            if (s0.firstRunDone) return@update s0
            var s = s0
            val t = s.theme
            s = LayoutOps.addAt(
                s, ItemContent.Builtin(BuiltinKind.ClockDate, defaultStyle(BuiltinKind.ClockDate)),
                LayoutOps.Spot(0, 0, 0), t.gridColumns, 2,
            )
            homeApps.forEachIndexed { i, e ->
                s = LayoutOps.addAt(
                    s, ItemContent.App(e.key.component.flattenToString(), e.key.userSerial),
                    LayoutOps.Spot(0, i % t.gridColumns, t.gridRows - 2 - i / t.gridColumns), 1, 1,
                )
            }
            s = LayoutOps.addAt(
                s, ItemContent.Builtin(BuiltinKind.Search, defaultStyle(BuiltinKind.Search)),
                LayoutOps.Spot(0, 0, t.gridRows - 1), t.gridColumns, 1,
            )
            dockApps.take(t.dockColumns).forEachIndexed { i, e ->
                s = LayoutOps.addAt(s, ItemContent.App(e.key.component.flattenToString(), e.key.userSerial), LayoutOps.Spot(DOCK_PAGE, i, 0), 1, 1)
            }
            LayoutOps.reflow(s.copy(firstRunDone = true))
        }
    }

    companion object {
        fun defaultStyle(kind: BuiltinKind): WidgetStyle = when (kind) {
            BuiltinKind.Clock -> WidgetStyle(sizeSp = 84)
            BuiltinKind.ClockDate -> WidgetStyle(sizeSp = 72)
            BuiltinKind.Date -> WidgetStyle(sizeSp = 24)
            BuiltinKind.Greeting -> WidgetStyle(sizeSp = 28, align = WidgetAlign.Start)
            BuiltinKind.Text -> WidgetStyle(sizeSp = 22, text = "Make it yours.")
            BuiltinKind.Search -> WidgetStyle(sizeSp = 16, cornerDp = 28, shadow = false)
            BuiltinKind.Battery -> WidgetStyle(sizeSp = 28)
        }
    }
}
