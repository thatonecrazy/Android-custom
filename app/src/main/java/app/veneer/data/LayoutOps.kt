package app.veneer.data

/**
 * Pure functions that place, move and resize items on the home grid and dock.
 * They never mutate their input and return null when a requested change does not fit.
 */
object LayoutOps {

    data class Spot(val page: Int, val x: Int, val y: Int)

    class Occupancy(val cols: Int, val rows: Int) {
        private val cells = Array(rows) { BooleanArray(cols) }

        fun fits(x: Int, y: Int, sx: Int, sy: Int): Boolean {
            if (x < 0 || y < 0 || sx < 1 || sy < 1 || x + sx > cols || y + sy > rows) return false
            for (r in y until y + sy) for (c in x until x + sx) if (cells[r][c]) return false
            return true
        }

        fun mark(x: Int, y: Int, sx: Int, sy: Int) {
            for (r in y until (y + sy).coerceAtMost(rows)) {
                for (c in x until (x + sx).coerceAtMost(cols)) {
                    if (r >= 0 && c >= 0) cells[r][c] = true
                }
            }
        }

        fun findFree(sx: Int, sy: Int): Pair<Int, Int>? {
            for (y in 0..rows - sy) for (x in 0..cols - sx) if (fits(x, y, sx, sy)) return x to y
            return null
        }
    }

    private fun occupancy(state: LauncherState, page: Int, excludeId: Long? = null): Occupancy {
        val t = state.theme
        val occ = if (page == DOCK_PAGE) Occupancy(t.dockColumns, 1) else Occupancy(t.gridColumns, t.gridRows)
        state.items.forEach { if (it.page == page && it.id != excludeId) occ.mark(it.x, it.y, it.spanX, it.spanY) }
        return occ
    }

    fun clampSpan(state: LauncherState, spanX: Int, spanY: Int): Pair<Int, Int> =
        spanX.coerceIn(1, state.theme.gridColumns) to spanY.coerceIn(1, state.theme.gridRows)

    /** First free spot for an item of the given size, starting at [startPage] and wrapping around. */
    fun findFreeSpot(state: LauncherState, spanX: Int, spanY: Int, startPage: Int = 0): Spot? {
        val pages = (startPage until state.pageCount) + (0 until startPage.coerceAtMost(state.pageCount))
        for (p in pages) {
            occupancy(state, p).findFree(spanX, spanY)?.let { (x, y) -> return Spot(p, x, y) }
        }
        return null
    }

    /** Adds an item at the first free spot, creating a page if every page is full. */
    fun add(
        state: LauncherState,
        content: ItemContent,
        spanX: Int = 1,
        spanY: Int = 1,
        preferredPage: Int = 0,
    ): LauncherState {
        val (sx, sy) = clampSpan(state, spanX, spanY)
        val spot = findFreeSpot(state, sx, sy, preferredPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
        val withPage = if (spot == null) state.copy(pageCount = state.pageCount + 1) else state
        val target = spot ?: Spot(state.pageCount, 0, 0)
        return addAt(withPage, content, target, sx, sy)
    }

    fun addAt(state: LauncherState, content: ItemContent, spot: Spot, spanX: Int, spanY: Int): LauncherState {
        val item = HomeItem(state.nextId, spot.page, spot.x, spot.y, spanX, spanY, content)
        return state.copy(items = state.items + item, nextId = state.nextId + 1)
    }

    fun canPlace(state: LauncherState, itemId: Long?, spot: Spot, spanX: Int, spanY: Int): Boolean {
        if (spot.page == DOCK_PAGE && (spanX != 1 || spanY != 1 || !state.theme.dockEnabled)) return false
        if (spot.page != DOCK_PAGE && spot.page !in 0 until state.pageCount) return false
        return occupancy(state, spot.page, itemId).fits(spot.x, spot.y, spanX, spanY)
    }

    /** Moves an item. If a single-cell item is dropped on another single-cell item, the two swap. */
    fun move(state: LauncherState, itemId: Long, spot: Spot): LauncherState? {
        val item = state.items.firstOrNull { it.id == itemId } ?: return null
        if (canPlace(state, itemId, spot, item.spanX, item.spanY)) {
            return state.copy(items = state.items.map {
                if (it.id == itemId) it.copy(page = spot.page, x = spot.x, y = spot.y) else it
            })
        }
        if (item.spanX == 1 && item.spanY == 1) {
            val other = state.items.firstOrNull {
                it.id != itemId && it.page == spot.page && it.x == spot.x && it.y == spot.y &&
                    it.spanX == 1 && it.spanY == 1
            } ?: return null
            return state.copy(items = state.items.map {
                when (it.id) {
                    itemId -> it.copy(page = spot.page, x = spot.x, y = spot.y)
                    other.id -> it.copy(page = item.page, x = item.x, y = item.y)
                    else -> it
                }
            })
        }
        return null
    }

    /**
     * Drops a dragged item on a spot. Free spots just move it. An app dropped on another app makes a
     * folder; an app dropped on a folder joins it. Other single-cell items swap places.
     */
    fun drop(state: LauncherState, itemId: Long, spot: Spot): LauncherState? {
        val item = state.items.firstOrNull { it.id == itemId } ?: return null
        if (canPlace(state, itemId, spot, item.spanX, item.spanY)) return move(state, itemId, spot)
        if (item.spanX != 1 || item.spanY != 1) return null
        val other = state.items.firstOrNull {
            it.id != itemId && it.page == spot.page && it.x == spot.x && it.y == spot.y && it.spanX == 1 && it.spanY == 1
        } ?: return null
        val dragged = item.content
        val target = other.content
        return when {
            dragged is ItemContent.App && target is ItemContent.App -> state.copy(
                items = state.items.filterNot { it.id == itemId }.map {
                    if (it.id == other.id) it.copy(content = ItemContent.Folder("Folder", listOf(target, dragged))) else it
                },
            )
            dragged is ItemContent.App && target is ItemContent.Folder -> state.copy(
                items = state.items.filterNot { it.id == itemId }.map {
                    if (it.id == other.id && target.apps.none { a -> a.id == dragged.id }) {
                        it.copy(content = target.copy(apps = target.apps + dragged))
                    } else it
                },
            )
            else -> move(state, itemId, spot)
        }
    }

    // --- Folders ------------------------------------------------------------------------------

    private fun updateFolder(state: LauncherState, itemId: Long, transform: (ItemContent.Folder) -> ItemContent.Folder) =
        state.copy(items = state.items.map { item ->
            val folder = item.content as? ItemContent.Folder
            if (item.id == itemId && folder != null) item.copy(content = transform(folder)) else item
        })

    fun renameFolder(state: LauncherState, itemId: Long, name: String) = updateFolder(state, itemId) { it.copy(name = name) }

    fun setFolderColor(state: LauncherState, itemId: Long, color: Int?) = updateFolder(state, itemId) { it.copy(color = color) }

    /**
     * Takes an app out of a folder. With [toHome], the app gets its own spot (starting at [page]).
     * A folder left with one app turns back into that app; an empty folder disappears.
     */
    fun removeFromFolder(state: LauncherState, itemId: Long, appId: String, toHome: Boolean, page: Int = 0): LauncherState {
        val item = state.items.firstOrNull { it.id == itemId } ?: return state
        val folder = item.content as? ItemContent.Folder ?: return state
        val app = folder.apps.firstOrNull { it.id == appId } ?: return state
        var next = normalizeFolder(updateFolder(state, itemId) { f -> f.copy(apps = f.apps.filterNot { it.id == appId }) }, itemId)
        if (toHome) next = add(next, app, preferredPage = page)
        return next
    }

    private fun normalizeFolder(state: LauncherState, itemId: Long): LauncherState {
        val item = state.items.firstOrNull { it.id == itemId } ?: return state
        val folder = item.content as? ItemContent.Folder ?: return state
        return when (folder.apps.size) {
            0 -> remove(state, itemId)
            1 -> updateContent(state, itemId, folder.apps.single())
            else -> state
        }
    }

    /** Removes apps everywhere they appear: as home items and inside folders. */
    fun removeApps(state: LauncherState, appIds: Set<String>): LauncherState {
        if (appIds.isEmpty()) return state
        var next = state.copy(items = state.items.filterNot { (it.content as? ItemContent.App)?.id in appIds })
        next.items.filter { it.content is ItemContent.Folder }.forEach { item ->
            next = normalizeFolder(updateFolder(next, item.id) { f -> f.copy(apps = f.apps.filterNot { it.id in appIds }) }, item.id)
        }
        return next
    }

    // --- Widget stacks ------------------------------------------------------------------------

    /** Adds a widget to a stack, turning a single widget into a stack first. */
    fun addToStack(state: LauncherState, itemId: Long, widget: ItemContent): LauncherState? {
        if (widget !is ItemContent.Widget && widget !is ItemContent.Builtin) return null
        val item = state.items.firstOrNull { it.id == itemId } ?: return null
        val stack = when (val c = item.content) {
            is ItemContent.Stack -> c.copy(widgets = c.widgets + widget)
            is ItemContent.Widget, is ItemContent.Builtin -> ItemContent.Stack(listOf(c, widget))
            else -> return null
        }
        return updateContent(state, itemId, stack)
    }

    /** Takes one widget out of a stack and returns it, so a hosted widget can be released. */
    fun removeFromStack(state: LauncherState, itemId: Long, index: Int): Pair<LauncherState, ItemContent?> {
        val item = state.items.firstOrNull { it.id == itemId } ?: return state to null
        val stack = item.content as? ItemContent.Stack ?: return state to null
        val removed = stack.widgets.getOrNull(index) ?: return state to null
        val left = stack.widgets.filterIndexed { i, _ -> i != index }
        val next = when (left.size) {
            0 -> remove(state, itemId)
            1 -> updateContent(state, itemId, left.single())
            else -> updateContent(state, itemId, stack.copy(widgets = left))
        }
        return next to removed
    }

    /** Replaces one widget inside a stack, e.g. after restyling it. */
    fun updateStackChild(state: LauncherState, itemId: Long, index: Int, content: ItemContent): LauncherState {
        val item = state.items.firstOrNull { it.id == itemId } ?: return state
        val stack = item.content as? ItemContent.Stack ?: return state
        if (index !in stack.widgets.indices) return state
        return updateContent(state, itemId, stack.copy(widgets = stack.widgets.mapIndexed { i, w -> if (i == index) content else w }))
    }

    /** Resizes an item, sliding it left/up if needed to stay on the grid. */
    fun resize(state: LauncherState, itemId: Long, spanX: Int, spanY: Int): LauncherState? {
        val item = state.items.firstOrNull { it.id == itemId } ?: return null
        if (item.page == DOCK_PAGE) return null
        val (sx, sy) = clampSpan(state, spanX, spanY)
        val x = item.x.coerceAtMost(state.theme.gridColumns - sx)
        val y = item.y.coerceAtMost(state.theme.gridRows - sy)
        val occ = occupancy(state, item.page, itemId)
        val spot = if (occ.fits(x, y, sx, sy)) x to y else occ.findFree(sx, sy) ?: return null
        return state.copy(items = state.items.map {
            if (it.id == itemId) it.copy(x = spot.first, y = spot.second, spanX = sx, spanY = sy) else it
        })
    }

    fun remove(state: LauncherState, itemId: Long): LauncherState =
        state.copy(items = state.items.filterNot { it.id == itemId })

    fun updateContent(state: LauncherState, itemId: Long, content: ItemContent): LauncherState =
        state.copy(items = state.items.map { if (it.id == itemId) it.copy(content = content) else it })

    fun addPage(state: LauncherState): LauncherState = state.copy(pageCount = state.pageCount + 1)

    /** Removes a page and everything on it; later pages shift down by one. */
    fun removePage(state: LauncherState, page: Int): LauncherState {
        if (state.pageCount <= 1 || page !in 0 until state.pageCount) return state
        val items = state.items.filterNot { it.page == page }
            .map { if (it.page > page) it.copy(page = it.page - 1) else it }
        return state.copy(items = items, pageCount = state.pageCount - 1)
    }

    /**
     * Makes every item fit the current grid and dock size. Items that no longer fit where they
     * were are moved to the nearest free spot, adding pages if needed. Nothing is ever dropped.
     */
    fun reflow(state: LauncherState): LauncherState {
        val t = state.theme
        val placed = mutableListOf<HomeItem>()
        val overflow = mutableListOf<HomeItem>()

        val dock = Occupancy(t.dockColumns, 1)
        state.items.filter { it.page == DOCK_PAGE }.sortedBy { it.x }.forEach { item ->
            val x = when {
                dock.fits(item.x, 0, 1, 1) -> item.x
                else -> dock.findFree(1, 1)?.first
            }
            if (x != null) {
                dock.mark(x, 0, 1, 1)
                placed += item.copy(x = x, y = 0, spanX = 1, spanY = 1)
            } else {
                overflow += item.copy(spanX = 1, spanY = 1)
            }
        }

        val pages = (0 until state.pageCount).associateWith { Occupancy(t.gridColumns, t.gridRows) }.toMutableMap()
        state.items.filter { it.page != DOCK_PAGE }
            .sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
            .forEach { item ->
                val occ = pages[item.page]
                if (occ == null) {
                    overflow += item
                    return@forEach
                }
                val sx = item.spanX.coerceIn(1, t.gridColumns)
                val sy = item.spanY.coerceIn(1, t.gridRows)
                val x = item.x.coerceIn(0, t.gridColumns - sx)
                val y = item.y.coerceIn(0, t.gridRows - sy)
                val spot = if (occ.fits(x, y, sx, sy)) x to y else occ.findFree(sx, sy)
                if (spot != null) {
                    occ.mark(spot.first, spot.second, sx, sy)
                    placed += item.copy(x = spot.first, y = spot.second, spanX = sx, spanY = sy)
                } else {
                    overflow += item.copy(spanX = sx, spanY = sy)
                }
            }

        var pageCount = state.pageCount.coerceAtLeast(1)
        overflow.forEach { item ->
            val sx = item.spanX.coerceIn(1, t.gridColumns)
            val sy = item.spanY.coerceIn(1, t.gridRows)
            var target: Spot? = null
            for (p in 0 until pageCount) {
                val occ = pages.getOrPut(p) { Occupancy(t.gridColumns, t.gridRows) }
                occ.findFree(sx, sy)?.let { (x, y) -> target = Spot(p, x, y) }
                if (target != null) break
            }
            val spot = target ?: Spot(pageCount, 0, 0).also {
                pages[pageCount] = Occupancy(t.gridColumns, t.gridRows)
                pageCount++
            }
            pages.getValue(spot.page).mark(spot.x, spot.y, sx, sy)
            placed += item.copy(page = spot.page, x = spot.x, y = spot.y, spanX = sx, spanY = sy)
        }
        return state.copy(items = placed.sortedBy { it.id }, pageCount = pageCount)
    }
}
