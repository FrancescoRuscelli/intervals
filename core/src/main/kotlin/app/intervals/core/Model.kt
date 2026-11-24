package app.intervals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Icon shown next to a series. Matches the icons of the old Hybrid Interval Timer app. */
@Serializable
enum class SeriesIcon { JUMP, RUN, STAND, WEIGHT, WARMUP, COOL }

/** A series done once per side: left, a pause to change side, then right. */
@Serializable
data class PerSide(
    val changeSec: Int,
    /** Color of the right side (0xAARRGGBB); null = same as the left. */
    val rightColor: Long? = null,
) {
    init {
        require(changeSec >= 0) { "changeSec must be >= 0" }
    }
}

/**
 * A value the user writes down for a series, e.g. Load (kg) or Edge (mm).
 * Asked in the rest between rounds and at the end; saved per round in the journal.
 */
@Serializable
data class LogField(
    val name: String,
    val unit: String = "",
    /** One value for the left side and one for the right (only meaningful for a per-side series). */
    val leftRightApart: Boolean = false,
    /**
     * A choice instead of a number (mockup intervals-ideas-from-other-apps.html, 9), e.g. Grip: open / half crimp / full crimp.
     * The value logged is the choice's text ([LoggedValue.text]).
     */
    val choices: List<String> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a log field needs a name" }
    }

    val isChoice: Boolean get() = choices.isNotEmpty()
}

/** One timed step of a round, e.g. "hang 0:05". */
@Serializable
data class Series(
    val name: String,
    val durationSec: Int,
    /** Color as 0xAARRGGBB. */
    val color: Long,
    val icon: SeriesIcon = SeriesIcon.STAND,
    val perSide: PerSide? = null,
    val logFields: List<LogField> = emptyList(),
    /** Say the name when it starts. */
    val sayName: Boolean = true,
    /** Beep in the last 3 seconds. */
    val beepLast3: Boolean = true,
) {
    init {
        require(durationSec > 0) { "durationSec must be > 0" }
    }
}

/** What a round is made of: single series, or a group of series repeated N times. */
@Serializable
sealed interface Block {
    @Serializable
    @SerialName("single")
    data class Single(val series: Series) : Block

    @Serializable
    @SerialName("repeat")
    data class Repeat(
        val times: Int,
        val series: List<Series>,
        /** The last time through, the group's last series (usually a rest) is skipped. */
        val skipLast: Boolean = false,
    ) : Block {
        init {
            require(times >= 1) { "times must be >= 1" }
            require(series.isNotEmpty()) { "a repeat group needs at least one series" }
            require(!skipLast || series.size >= 2) { "skipLast needs at least two series" }
        }

        /** The series in play order, all repetitions unrolled. */
        fun unrolled(): List<Series> =
            (0 until times).flatMap { k -> if (skipLast && k == times - 1) series.dropLast(1) else series }
    }
}

@Serializable
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

/** A workout as saved in the app: with its id and its place in the list. */
@Serializable
data class SavedWorkout(
    val id: Long,
    val position: Int,
    val workout: Workout,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val updatedAt: Long,
)
