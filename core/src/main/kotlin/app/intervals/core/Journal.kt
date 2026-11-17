package app.intervals.core

import kotlinx.serialization.Serializable

/** One value written down during a session, e.g. hang · Load · round 2 · left = 12.5 (kg). */
@Serializable
data class LoggedValue(
    /** Name of the series it belongs to. */
    val series: String,
    val field: String,
    val unit: String = "",
    /** 1-based round. */
    val round: Int,
    /** Null when the value is the same for both sides. */
    val side: Side? = null,
    val value: Double,
) {
    init {
        require(round >= 1) { "round must be >= 1" }
    }
}

/** A workout done (or stopped), logged automatically in the journal. */
@Serializable
data class Session(
    val id: Long = 0,
    /** Null when the workout was deleted afterwards; the name is kept. */
    val workoutId: Long? = null,
    val workoutName: String,
    /** Epoch milliseconds. */
    val startedAt: Long,
    /** Time actually spent, pauses excluded. */
    val durationSec: Int,
    val roundsDone: Int,
    val roundsTotal: Int,
    val completed: Boolean,
    val note: String = "",
    val values: List<LoggedValue> = emptyList(),
) {
    init {
        require(durationSec >= 0) { "durationSec must be >= 0" }
        require(roundsTotal >= 1) { "roundsTotal must be >= 1" }
        require(roundsDone in 0..roundsTotal) { "roundsDone must be in 0..roundsTotal" }
    }
}

/**
 * The values to start the next session from: for each series, field and side, the value of the last round logged.
 * Rounds are dropped, so the result says e.g. "hang · Load · left = 12.5".
 */
fun Session.lastValues(): List<LoggedValue> =
    values.groupBy { Triple(it.series, it.field, it.side) }
        .map { (_, v) -> v.maxBy { it.round } }
