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

/**
 * What changed in the logged values since [before] (the previous session of the same workout), mockup
 * intervals-next-features.html 3C: "Load +2.5 kg · same Edge", and +1 / −1 / 0 for up, down or mixed. Null when
 * there is nothing to compare or nothing changed.
 */
fun valuesChange(now: Session, before: Session?): Pair<String, Int>? {
    if (before == null) return null
    val a = now.lastValues().associateBy { Triple(it.series, it.field, it.side) }
    val b = before.lastValues().associateBy { Triple(it.series, it.field, it.side) }
    val common = a.keys.intersect(b.keys)
    if (common.isEmpty()) return null
    val changed = common.filter { a.getValue(it).value != b.getValue(it).value }
    if (changed.isEmpty()) return null
    fun num(v: Double) = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
    val parts = changed.map { k ->
        val d = a.getValue(k).value - b.getValue(k).value
        val unit = a.getValue(k).unit
        "${k.second} ${if (d > 0) "+" else "−"}${num(kotlin.math.abs(d))}${if (unit.isEmpty()) "" else " $unit"}"
    }
    val same = (common - changed.toSet()).map { it.second }.distinct().map { "same $it" }
    val ups = changed.count { a.getValue(it).value > b.getValue(it).value }
    val dir = when (ups) { changed.size -> 1; 0 -> -1; else -> 0 }
    return (parts + same).joinToString(" · ") to dir
}
