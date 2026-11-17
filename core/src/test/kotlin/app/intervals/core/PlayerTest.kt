package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTest {
    private val hang = Series("hang", 5, 0xFFE91E63, SeriesIcon.JUMP)
    private val rest = Series("rest", 4, 0xFFFFC107)
    // warm-up 3 · (hang 5, rest 4) · rest between rounds 10 · (hang 5, rest 4) = 31 s
    private val w = Workout("t", warmupSec = 3, rounds = 2, restBetweenRoundsSec = 10, blocks = listOf(Block.Single(hang), Block.Single(rest)))
    private val plan = PlayerPlan(w)

    @Test
    fun stateAlongTheWay() {
        assertEquals(31, plan.totalSec)
        val a = plan.stateAt(0)!!
        assertEquals(SegmentKind.WARMUP, a.segment.kind)
        assertEquals(3, a.secLeft)
        val b = plan.stateAt(3_500)!!
        assertEquals("hang", b.segment.name)
        assertEquals(5, b.secLeft) // 4.5 s left shows 5
        assertEquals(1, b.round)
        assertEquals(1, b.inRound)
        assertEquals(3, b.ofRound) // hang, rest, rest between rounds
        assertEquals("rest", b.next?.name)
        val c = plan.stateAt(13_000)!!
        assertEquals(SegmentKind.ROUND_REST, c.segment.kind)
        assertEquals(1, plan.roundsDone(13_000))
        val d = plan.stateAt(30_999)!!
        assertEquals(2, d.round)
        assertEquals(1, d.secLeft)
        assertNull(plan.stateAt(31_000))
        assertTrue(plan.isOver(31_000))
    }

    @Test
    fun cuesHaveNamesBeepsAndEnd() {
        val cues = plan.cues(sayNames = true, beeps = true)
        assertEquals(TimedCue(0, Cue.Start(plan.timeline.segments[0])), cues.first())
        assertEquals(Cue.Finished, cues.last().cue)
        // hang 5 s from 3 to 8: beeps at 5, 6, 7
        assertTrue(cues.containsAll(listOf(TimedCue(5, Cue.Beep(3)), TimedCue(6, Cue.Beep(2)), TimedCue(7, Cue.Beep(1)))))
        val quiet = plan.cues(sayNames = false, beeps = false)
        assertEquals(listOf(Cue.Finished), quiet.map { it.cue })
    }

    @Test
    fun seriesCanSilenceItself() {
        val w2 = w.copy(blocks = listOf(Block.Single(hang.copy(sayName = false, beepLast3 = false)), Block.Single(rest)))
        val cues = PlayerPlan(w2).cues(sayNames = true, beeps = true)
        assertTrue(cues.none { it.cue is Cue.Start && (it.cue as Cue.Start).segment.name == "hang" })
    }

    @Test
    fun previousAndNext() {
        assertEquals(8_000, plan.nextStartMs(4_000))
        assertEquals(3_000, plan.previousStartMs(6_000)) // into hang 3 s: back to its start
        assertEquals(0, plan.previousStartMs(4_000)) // first 2 s of hang: back to the warm-up
    }

    @Test
    fun spokenWords() {
        val seg = Segment(SegmentKind.SERIES, "hang", 5, 0, SeriesIcon.JUMP, 1, 0, Side.LEFT)
        assertEquals("hang, left", seg.spoken())
        assertEquals("Change side", seg.copy(kind = SegmentKind.CHANGE_SIDE).spoken())
    }
}
