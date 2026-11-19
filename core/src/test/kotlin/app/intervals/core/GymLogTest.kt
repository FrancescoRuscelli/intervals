package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GymLogTest {
    private fun set(kg: Double) = GymSet(exerciseId = 1, day = "2026-11-17", order = 0, weightKg = kg, reps = 5)

    @Test
    fun startsFromTodayElseLastTime() {
        assertEquals(80.0, startValues(listOf(set(60.0), set(80.0)), listOf(set(40.0)))?.weightKg)
        assertEquals(40.0, startValues(emptyList(), listOf(set(40.0), set(90.0)))?.weightKg)
        assertEquals(null, startValues(emptyList(), emptyList()))
    }

    @Test
    fun eachSideStartsFromItsOwnLastSet() {
        val l = set(45.0).copy(side = Side.LEFT); val r = set(50.0).copy(side = Side.RIGHT)
        assertEquals(45.0, startValues(listOf(l, r), emptyList(), Side.LEFT)?.weightKg)
        assertEquals(50.0, startValues(listOf(l, r), emptyList(), Side.RIGHT)?.weightKg)
        // today only R so far: L comes from last time's L
        assertEquals(40.0, startValues(listOf(r), listOf(set(40.0).copy(side = Side.LEFT), r), Side.LEFT)?.weightKg)
        // never done on that side: anything
        assertEquals(50.0, startValues(listOf(r), emptyList(), Side.LEFT)?.weightKg)
    }

    @Test
    fun sidesAlternateAndBothWritesTwo() {
        assertEquals(SideChoice.RIGHT, SideChoice.LEFT.afterSet())
        assertEquals(SideChoice.BOTH, SideChoice.BOTH.afterSet())
        assertEquals(listOf(Side.LEFT, Side.RIGHT), SideChoice.BOTH.sides())
        assertEquals(listOf<Side?>(null), (null as SideChoice?).sides())
    }

    @Test
    fun bandTapCyclesAndKeepsOrder() {
        val g = Band.GREEN; val p = Band.PURPLE; val r = Band.RED
        assertEquals(listOf(g, r), listOf(r).tap(g))
        assertEquals(listOf(g, p, p, r), listOf(g, p, r).tap(p))
        assertEquals(listOf(g, r), listOf(g, p, p, r).tap(p))
    }

    @Test
    fun tagsToggleInTheNote() {
        assertEquals("Max", toggleTag("", "Max"))
        assertEquals("Failed · ring finger", toggleTag("ring finger", "Failed"))
        assertEquals("ring finger", toggleTag("Failed · ring finger", "Failed"))
    }
}
