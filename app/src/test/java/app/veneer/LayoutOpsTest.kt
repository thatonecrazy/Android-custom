package app.veneer

import app.veneer.data.BuiltinKind
import app.veneer.data.DOCK_PAGE
import app.veneer.data.ItemContent
import app.veneer.data.LauncherState
import app.veneer.data.LayoutOps
import app.veneer.data.LayoutOps.Spot
import app.veneer.data.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {

    private fun app(n: Int) = ItemContent.App("pkg$n/.Main", 0)
    private val clock = ItemContent.Builtin(BuiltinKind.Clock)
    private val empty = LauncherState(theme = ThemeConfig(gridColumns = 4, gridRows = 5, dockColumns = 4))

    private fun noOverlaps(state: LauncherState) {
        state.items.groupBy { it.page }.forEach { (page, items) ->
            val cols = if (page == DOCK_PAGE) state.theme.dockColumns else state.theme.gridColumns
            val rows = if (page == DOCK_PAGE) 1 else state.theme.gridRows
            val seen = HashSet<Pair<Int, Int>>()
            items.forEach { item ->
                assertTrue("item ${item.id} off the grid", item.x >= 0 && item.y >= 0 && item.x + item.spanX <= cols && item.y + item.spanY <= rows)
                for (x in item.x until item.x + item.spanX) for (y in item.y until item.y + item.spanY) {
                    assertTrue("overlap at page $page ($x,$y)", seen.add(x to y))
                }
            }
        }
    }

    @Test
    fun addFillsRowsThenAddsAPage() {
        var s = empty
        repeat(21) { s = LayoutOps.add(s, app(it)) }
        assertEquals(2, s.pageCount)
        assertEquals(20, s.items.count { it.page == 0 })
        assertEquals(Spot(1, 0, 0), s.items.last().let { Spot(it.page, it.x, it.y) })
        noOverlaps(s)
    }

    @Test
    fun widgetsGoWhereTheyFit() {
        var s = LayoutOps.add(empty, app(0))
        s = LayoutOps.add(s, clock, 4, 2)
        val widget = s.items.last()
        assertEquals(0 to 1, widget.x to widget.y)
        noOverlaps(s)
    }

    @Test
    fun spansAreClampedToTheGrid() {
        val s = LayoutOps.add(empty, clock, 9, 9)
        assertEquals(4 to 5, s.items.single().spanX to s.items.single().spanY)
    }

    @Test
    fun moveToFreeCell() {
        var s = LayoutOps.add(empty, app(0))
        val id = s.items.single().id
        s = LayoutOps.move(s, id, Spot(0, 3, 4))!!
        assertEquals(3 to 4, s.items.single().x to s.items.single().y)
    }

    @Test
    fun singleCellItemsSwap() {
        var s = LayoutOps.add(empty, app(0))
        s = LayoutOps.add(s, app(1))
        val (a, b) = s.items
        s = LayoutOps.move(s, a.id, Spot(0, b.x, b.y))!!
        assertEquals(b.x, s.items.first { it.id == a.id }.x)
        assertEquals(a.x, s.items.first { it.id == b.id }.x)
        noOverlaps(s)
    }

    @Test
    fun widgetCannotLandOnTopOfOthers() {
        var s = LayoutOps.add(empty, app(0))
        s = LayoutOps.addAt(s, clock, Spot(0, 0, 2), 4, 2)
        val widget = s.items.last()
        assertNull(LayoutOps.move(s, widget.id, Spot(0, 0, 0)))
    }

    @Test
    fun onlySingleCellsGoInTheDock() {
        var s = LayoutOps.addAt(empty, clock, Spot(0, 0, 0), 2, 1)
        assertNull(LayoutOps.move(s, s.items.single().id, Spot(DOCK_PAGE, 0, 0)))
        s = LayoutOps.add(s, app(1))
        val appId = s.items.last().id
        assertNotNull(LayoutOps.move(s, appId, Spot(DOCK_PAGE, 2, 0)))
    }

    @Test
    fun resizeSlidesBackOntoTheGrid() {
        var s = LayoutOps.addAt(empty, clock, Spot(0, 3, 4), 1, 1)
        s = LayoutOps.resize(s, s.items.single().id, 2, 2)!!
        val w = s.items.single()
        assertEquals(listOf(2, 3, 2, 2), listOf(w.x, w.y, w.spanX, w.spanY))
    }

    @Test
    fun shrinkingTheGridKeepsEverything() {
        var s = empty
        repeat(20) { s = LayoutOps.add(s, app(it)) }
        s = LayoutOps.addAt(s, app(99), Spot(DOCK_PAGE, 3, 0), 1, 1)
        val smaller = s.copy(theme = s.theme.copy(gridColumns = 3, gridRows = 4, dockColumns = 3))
        val reflowed = LayoutOps.reflow(smaller)
        assertEquals(s.items.size, reflowed.items.size)
        assertTrue(reflowed.pageCount >= 2)
        noOverlaps(reflowed)
    }

    @Test
    fun removingAPageShiftsLaterPages() {
        var s = empty.copy(pageCount = 3)
        s = LayoutOps.addAt(s, app(0), Spot(0, 0, 0), 1, 1)
        s = LayoutOps.addAt(s, app(1), Spot(1, 0, 0), 1, 1)
        s = LayoutOps.addAt(s, app(2), Spot(2, 1, 1), 1, 1)
        s = LayoutOps.removePage(s, 1)
        assertEquals(2, s.pageCount)
        assertEquals(listOf(0, 1), s.items.map { it.page })
        assertEquals(1, s.items.last().x)
    }
}
