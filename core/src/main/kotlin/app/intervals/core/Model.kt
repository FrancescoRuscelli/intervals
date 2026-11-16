package app.intervals.core

/** Icon shown next to a series. Matches the icons of the old Hybrid Interval Timer app. */
enum class SeriesIcon { JUMP, RUN, STAND, WEIGHT, WARMUP, COOL }

/** A series done once per side: left, a pause to change side, then right. */
data class PerSide(val changeSec: Int) {
    init {
        require(changeSec >= 0) { "changeSec must be >= 0" }
    }
}

/** One timed step of a round, e.g. "hang 0:05". */
data class Series(
    val name: String,
    val durationSec: Int,
    /** Color as 0xAARRGGBB. */
    val color: Long,
    val icon: SeriesIcon = SeriesIcon.STAND,
    val perSide: PerSide? = null,
) {
    init {
        require(durationSec > 0) { "durationSec must be > 0" }
    }
}

/** What a round is made of: single series, or a group of series repeated N times. */
sealed interface Block {
    data class Single(val series: Series) : Block

    data class Repeat(val times: Int, val series: List<Series>) : Block {
        init {
            require(times >= 1) { "times must be >= 1" }
            require(series.isNotEmpty()) { "a repeat group needs at least one series" }
        }
    }
}

data class Workout(
    val name: String,
    val warmupSec: Int = 0,
    val rounds: Int = 1,
    /** Played between rounds only, never after the last round. */
    val restBetweenRoundsSec: Int = 0,
    val blocks: List<Block>,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(warmupSec >= 0) { "warmupSec must be >= 0" }
        require(rounds >= 1) { "rounds must be >= 1" }
        require(restBetweenRoundsSec >= 0) { "restBetweenRoundsSec must be >= 0" }
    }
}
