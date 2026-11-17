package app.intervals.core

enum class SegmentKind { WARMUP, SERIES, CHANGE_SIDE, ROUND_REST }

@kotlinx.serialization.Serializable
enum class Side { LEFT, RIGHT }

/** One step of the timeline the player runs through, with its absolute start time. */
data class Segment(
    val kind: SegmentKind,
    val name: String,
    val durationSec: Int,
    val color: Long,
    val icon: SeriesIcon,
    /** 1-based round this segment belongs to (warm-up counts as round 1). */
    val round: Int,
    val startSec: Int,
    val side: Side? = null,
) {
    val endSec: Int get() = startSec + durationSec
}

data class Timeline(val segments: List<Segment>) {
    val totalSec: Int get() = segments.lastOrNull()?.endSec ?: 0

    /** The segment running at [atSec] seconds from the start, or null when the workout is over. */
    fun segmentAt(atSec: Double): Segment? =
        if (atSec < 0) null else segments.firstOrNull { atSec < it.endSec }
}

private const val WARMUP_COLOR = 0xFFFF9800L
private const val CHANGE_COLOR = 0xFF9AA0AAL
private const val ROUND_REST_COLOR = 0xFF9AA0AAL

/**
 * Expands a workout into the exact list of timed segments:
 * warm-up, then for each round its blocks (repeat groups unrolled, per-side series split
 * into left / change / right), with the rest between rounds placed only between rounds.
 */
fun Workout.timeline(): Timeline {
    val out = mutableListOf<Segment>()
    var t = 0
    fun add(kind: SegmentKind, name: String, d: Int, color: Long, icon: SeriesIcon, round: Int, side: Side? = null) {
        if (d <= 0) return
        out += Segment(kind, name, d, color, icon, round, t, side)
        t += d
    }

    add(SegmentKind.WARMUP, "warm-up", warmupSec, WARMUP_COLOR, SeriesIcon.WARMUP, 1)
    val roundSeries = blocks.flatMap { block ->
        when (block) {
            is Block.Single -> listOf(block.series)
            is Block.Repeat -> block.unrolled()
        }
    }
    for (r in 1..rounds) {
        for (s in roundSeries) {
            val ps = s.perSide
            if (ps == null) {
                add(SegmentKind.SERIES, s.name, s.durationSec, s.color, s.icon, r)
            } else {
                add(SegmentKind.SERIES, s.name, s.durationSec, s.color, s.icon, r, Side.LEFT)
                add(SegmentKind.CHANGE_SIDE, "change side", ps.changeSec, CHANGE_COLOR, SeriesIcon.STAND, r)
                add(SegmentKind.SERIES, s.name, s.durationSec, s.color, s.icon, r, Side.RIGHT)
            }
        }
        if (r < rounds) {
            add(SegmentKind.ROUND_REST, "rest between rounds", restBetweenRoundsSec, ROUND_REST_COLOR, SeriesIcon.COOL, r)
        }
    }
    return Timeline(out)
}

/** Total length in seconds, exactly what the player will run. */
val Workout.totalSec: Int get() = timeline().totalSec

/** Length of one round (without the rest between rounds). */
val Workout.roundSec: Int
    get() = blocks.sumOf { block ->
        val one = { s: Series -> s.perSide?.let { 2 * s.durationSec + it.changeSec } ?: s.durationSec }
        when (block) {
            is Block.Single -> one(block.series)
            is Block.Repeat -> block.unrolled().sumOf(one)
        }
    }

fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
