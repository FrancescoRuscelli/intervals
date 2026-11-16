package app.intervals.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineTest {
    private val work = Series("work", 10, 0xFFF44336L)
    private val rest = Series("rest", 3, 0xFF4CAF50L)

    @Test
    fun restBetweenRoundsIsNotPlayedAfterLastRound() {
        // The example from the design discussion: 3s rest, 10s work, 3s rest, 10s work, 30s rest, x3.
        val w = Workout(
            name = "example",
            rounds = 3,
            restBetweenRoundsSec = 30,
            blocks = listOf(Block.Single(rest), Block.Single(work), Block.Single(rest), Block.Single(work)),
        )
        assertEquals(2 * 60 + 18, w.totalSec)
        val tl = w.timeline()
        assertEquals(SegmentKind.SERIES, tl.segments.last().kind)
        assertEquals(2, tl.segments.count { it.kind == SegmentKind.ROUND_REST })
    }

    @Test
    fun repeatGroupsAndPerSideExpand() {
        val hang = Series("hang", 7, 0xFFE91E63L, SeriesIcon.JUMP, perSide = PerSide(5))
        val w = Workout(
            name = "per side",
            warmupSec = 5,
            rounds = 2,
            restBetweenRoundsSec = 60,
            blocks = listOf(Block.Repeat(2, listOf(hang, rest))),
        )
        // round = 2 * (7 + 5 + 7 + 3) = 44; total = 5 + 2*44 + 60
        assertEquals(44, w.roundSec)
        assertEquals(153, w.totalSec)
        val sides = w.timeline().segments.filter { it.name == "hang" }.map { it.side }
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), sides)
    }

    @Test
    fun segmentAtFindsTheRunningSegment() {
        val w = Workout(name = "w", warmupSec = 5, blocks = listOf(Block.Single(work)))
        val tl = w.timeline()
        assertEquals(SegmentKind.WARMUP, tl.segmentAt(4.9)?.kind)
        assertEquals("work", tl.segmentAt(5.0)?.name)
        assertEquals(null, tl.segmentAt(15.0))
    }

    @Test
    fun formatting() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("26:29", formatDuration(1589))
        assertEquals("1:00:00", formatDuration(3600))
    }
}

class HybridImportTest {
    private fun sample() = HybridImport.parse(javaClass.getResource("/hybrid_sample.csv")!!.readText())

    @Test
    fun importsSampleWorkouts() {
        val r = sample()
        assertEquals(listOf("hangboard 70%", "hiit", "finger strenght v2", "max hang 20mm"), r.workouts.map { it.workout.name })
        assertTrue(r.warnings.isEmpty(), r.warnings.joinToString())
    }

    @Test
    fun finalRestBecomesRestBetweenRounds() {
        val hb = sample().workouts.first { it.workout.name == "hangboard 70%" }
        assertTrue(hb.convertedFinalRest)
        assertEquals(60, hb.workout.restBetweenRoundsSec)
        assertEquals(6 * 60 + 25, hb.oldTotalSec)
        assertEquals(5 * 60 + 25, hb.workout.totalSec)
        val mh = sample().workouts.first { it.workout.name == "max hang 20mm" }
        assertEquals(26 * 60 + 29, mh.workout.totalSec)
    }

    @Test
    fun repeatedPatternsBecomeRepeatGroups() {
        val hb = sample().workouts.first { it.workout.name == "hangboard 70%" }.workout
        // hang, rest, hang, rest, hang, rest, hang  ->  Repeat x3 (hang, rest), hang
        assertEquals(2, hb.blocks.size)
        val rep = hb.blocks[0] as Block.Repeat
        assertEquals(3, rep.times)
        assertEquals(listOf("hang", "rest"), rep.series.map { it.name })
        assertEquals("hang", (hb.blocks[1] as Block.Single).series.name)
        assertEquals(0xFFE91E63L, rep.series[0].color)
        assertEquals(SeriesIcon.JUMP, rep.series[0].icon)
    }

    @Test
    fun handlesCommasInNamesAndBadLines() {
        val r = HybridImport.parse("1,a, b,5,0,25,2\n2,1,work,10,,,0,run,-769226\ngarbage\n")
        assertEquals("a, b", r.workouts.single().workout.name)
        assertEquals(25, r.workouts.single().workout.totalSec)
        assertEquals(1, r.warnings.size)
    }

    /** Runs only with -Pintervals.csv=/path/to/export.csv: every total must be the old one minus the final rest. */
    @Test
    fun realExportIfProvided() {
        val path = System.getProperty("intervals.csv") ?: return
        val r = HybridImport.parse(File(path).readText())
        assertTrue(r.warnings.none { "does not match" in it }, r.warnings.joinToString("\n"))
        for (iw in r.workouts) {
            val expected = iw.oldTotalSec - if (iw.convertedFinalRest) iw.workout.restBetweenRoundsSec else 0
            assertEquals(expected, iw.workout.totalSec, iw.workout.name)
        }
        println("Imported ${r.workouts.size} workouts, ${r.workouts.count { it.convertedFinalRest }} with final rest converted")
    }
}
