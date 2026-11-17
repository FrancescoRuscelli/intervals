package app.intervals.core

import kotlin.math.ceil

/** Everything the player screen shows at one moment. */
data class PlayState(
    val index: Int,
    val segment: Segment,
    /** Whole seconds left in this segment, as shown (5, 4, 3, 2, 1). */
    val secLeft: Int,
    /** 0..1 of this segment done. */
    val fraction: Double,
    val round: Int,
    val rounds: Int,
    /** 1-based position of this segment among the segments of its round (warm-up and round rest count as their own). */
    val inRound: Int,
    val ofRound: Int,
    val next: Segment?,
    val totalLeftSec: Int,
    /** 0..1 of the whole workout done. */
    val progress: Double,
)

/** Sounds and words the player says, in order. */
sealed interface Cue {
    /** A segment starts: say its name (and side). */
    data class Start(val segment: Segment) : Cue
    /** Beep with n seconds left (3, 2, 1). */
    data class Beep(val n: Int) : Cue
    data object Finished : Cue
}

/** A cue and the second (from the start) when it plays. */
data class TimedCue(val atSec: Int, val cue: Cue)

/**
 * The pure part of the player: what is on screen at [elapsedMs] and which cues play when.
 * The app only keeps the clock (pauses excluded) and asks this.
 */
class PlayerPlan(val workout: Workout) {
    val timeline: Timeline = workout.timeline()
    val totalSec: Int = timeline.totalSec
    private val segs = timeline.segments

    fun stateAt(elapsedMs: Long): PlayState? {
        if (segs.isEmpty()) return null
        val t = elapsedMs / 1000.0
        val i = segs.indexOfFirst { t < it.endSec }
        if (i < 0) return null
        val s = segs[i]
        val roundSegs = segs.withIndex().filter { it.value.round == s.round && it.value.kind != SegmentKind.WARMUP }
        val inRound = roundSegs.indexOfFirst { it.index == i } + 1
        return PlayState(
            index = i,
            segment = s,
            secLeft = ceil(s.endSec - t).toInt().coerceAtLeast(1),
            fraction = ((t - s.startSec) / s.durationSec).coerceIn(0.0, 1.0),
            round = s.round,
            rounds = workout.rounds,
            inRound = if (s.kind == SegmentKind.WARMUP) 0 else inRound,
            ofRound = roundSegs.size,
            next = segs.getOrNull(i + 1),
            totalLeftSec = ceil(totalSec - t).toInt().coerceAtLeast(0),
            progress = (t / totalSec).coerceIn(0.0, 1.0),
        )
    }

    /** True when the workout is over at [elapsedMs]. */
    fun isOver(elapsedMs: Long): Boolean = elapsedMs >= totalSec * 1000L

    /**
     * All cues of the workout, sorted. [sayNames] / [beeps] are the app settings;
     * each series can also turn its own name / beeps off.
     */
    fun cues(sayNames: Boolean, beeps: Boolean): List<TimedCue> = buildList {
        val byName = workout.blocks.flatMap { b ->
            when (b) {
                is Block.Single -> listOf(b.series)
                is Block.Repeat -> b.series
            }
        }.associateBy { it.name }
        for (s in segs) {
            val own = byName[s.name].takeIf { s.kind == SegmentKind.SERIES }
            if (sayNames && (own?.sayName ?: true)) add(TimedCue(s.startSec, Cue.Start(s)))
            if (beeps && (own?.beepLast3 ?: true)) {
                for (n in 3 downTo 1) if (s.durationSec > n) add(TimedCue(s.endSec - n, Cue.Beep(n)))
            }
        }
        add(TimedCue(totalSec, Cue.Finished))
    }.sortedBy { it.atSec }

    /** Where to jump for "previous": the start of this segment, or of the previous one if we are in its first 2 s. */
    fun previousStartMs(elapsedMs: Long): Long {
        val st = stateAt(elapsedMs) ?: return (segs.lastOrNull()?.startSec ?: 0) * 1000L
        val intoMs = elapsedMs - st.segment.startSec * 1000L
        val target = if (intoMs < 2000 && st.index > 0) segs[st.index - 1] else st.segment
        return target.startSec * 1000L
    }

    /** Where to jump for "next": the start of the next segment (or the end). */
    fun nextStartMs(elapsedMs: Long): Long {
        val st = stateAt(elapsedMs) ?: return totalSec * 1000L
        return st.segment.endSec * 1000L
    }

    /** Segments of [round] (warm-up excluded): what the outer ring of the disc draws. */
    fun roundSegments(round: Int): List<Segment> = segs.filter { it.round == round && it.kind != SegmentKind.WARMUP }

    /** Rounds fully done before [elapsedMs]. */
    fun roundsDone(elapsedMs: Long): Int {
        val st = stateAt(elapsedMs) ?: return workout.rounds
        return if (st.segment.kind == SegmentKind.ROUND_REST) st.round else st.round - 1
    }
}

/** What the voice says when a segment starts. */
fun Segment.spoken(): String = when (kind) {
    SegmentKind.WARMUP -> "Warm up"
    SegmentKind.CHANGE_SIDE -> "Change side"
    SegmentKind.ROUND_REST -> "Rest"
    SegmentKind.SERIES -> when (side) {
        Side.LEFT -> "$name, left"
        Side.RIGHT -> "$name, right"
        null -> name
    }
}
