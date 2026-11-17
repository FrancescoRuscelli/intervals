package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutEditsTest {
    private val hang = Series("hang", 5, 0xFFE91E63, SeriesIcon.JUMP)
    private val rest = Series("rest", 5, 0xFFFFC107)
    private val flat = Workout("w", blocks = listOf(Block.Single(hang), Block.Single(rest), Block.Single(hang)))

    @Test
    fun groupAdjacentSingles() {
        val w = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 3)
        assertEquals(listOf(Block.Repeat(3, listOf(hang, rest)), Block.Single(hang)), w.blocks)
        assertEquals(35, w.roundSec)
    }

    @Test
    fun cannotGroupGapsOrGroupedSeries() {
        assertFalse(flat.canGroup(listOf(SeriesPos(0), SeriesPos(2))))
        val g = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 2)
        assertFalse(g.canGroup(listOf(SeriesPos(0, 0))))
        assertTrue(g.canGroup(listOf(SeriesPos(1))))
    }

    @Test
    fun deleteRemovesEmptyGroups() {
        val g = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 2)
        val w = g.deleteSeries(listOf(SeriesPos(0, 0), SeriesPos(0, 1)))
        assertEquals(listOf(Block.Single(hang)), w.blocks)
    }

    @Test
    fun duplicateInsideGroupStaysInGroup() {
        val g = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 2)
        val w = g.duplicateSeries(listOf(SeriesPos(0, 1)))
        assertEquals(Block.Repeat(2, listOf(hang, rest, rest)), w.blocks[0])
        assertEquals(2, w.blocks.size)
    }

    @Test
    fun duplicateSinglesGoAfterTheLastSelected() {
        val w = flat.duplicateSeries(listOf(SeriesPos(0), SeriesPos(1)))
        assertEquals(listOf(hang, rest, hang, rest, hang), w.positions().map { w.seriesAt(it) })
    }

    @Test
    fun ungroupAndRepeatTimes() {
        val g = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 2).setRepeatTimes(0, 4)
        assertEquals(4, (g.blocks[0] as Block.Repeat).times)
        assertEquals(flat.blocks, g.ungroup(0).blocks)
    }

    @Test
    fun moveAndReplace() {
        val w = flat.moveBlock(2, -1).replaceSeries(SeriesPos(0), rest)
        assertEquals(listOf(rest, hang, rest), w.positions().map { w.seriesAt(it) })
        assertEquals(flat, flat.moveBlock(0, -1))
    }

    @Test
    fun editorRowsAndBar() {
        val g = flat.groupSeries(listOf(SeriesPos(0), SeriesPos(1)), 3)
        val rows = g.editorRows()
        assertEquals(4, rows.size) // header + 2 inside + single
        assertTrue(rows[0] is EditorRow.RepeatHeader)
        assertEquals(10, (rows[0] as EditorRow.RepeatHeader).cycleSec)
        assertEquals(30, (rows[0] as EditorRow.RepeatHeader).totalSec)
        assertEquals(7, g.roundBar().size)
        assertEquals(7, g.seriesCount)
        val perSide = Workout("p", blocks = listOf(Block.Single(hang.copy(perSide = PerSide(5)))))
        assertEquals(listOf(5 to hang.color, 5 to 0xFF00BFA5L, 5 to hang.color), perSide.roundBar())
    }
}
