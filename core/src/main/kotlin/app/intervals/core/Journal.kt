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
    /** For a choice field (Grip: half crimp), the choice; [value] is then its place in the list, only as a fallback. */
    val text: String? = null,
) {
    init {
        require(round >= 1) { "round must be >= 1" }
    }
}

/**
 * A work series you came off early (mockup intervals-ideas-from-other-apps.html, 2): the timer kept running.
 * [rep] is the work series' number in its round (as the "3/6" chip), [heldSec] how long it was held.
 */
@Serializable
data class LetGo(val series: String, val round: Int, val rep: Int, val side: Side? = null, val heldSec: Int)

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
    /** The work series you let go of early; empty when none (or not known). */
    val letGo: List<LetGo> = emptyList(),
    /** Work series played in the session (both sides of a per-side one count as two); 0 = not known. */
    val workDone: Int = 0,
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
    // a choice that changed reads "Grip open"; numbers "Load +2.5 kg"
    val choices = common.filter { a.getValue(it).text != null || b.getValue(it).text != null }
    val changedChoices = choices.filter { a.getValue(it).text != b.getValue(it).text }
    val numbers = common - choices.toSet()
    val changed = numbers.filter { a.getValue(it).value != b.getValue(it).value }
    if (changed.isEmpty() && changedChoices.isEmpty()) return null
    fun num(v: Double) = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
    val parts = changed.map { k ->
        val d = a.getValue(k).value - b.getValue(k).value
        val unit = a.getValue(k).unit
        "${k.second} ${if (d > 0) "+" else "−"}${num(kotlin.math.abs(d))}${if (unit.isEmpty()) "" else " $unit"}"
    }
    val picked = changedChoices.map { k -> "${k.second} ${a.getValue(k).text.orEmpty()}" }
    val same = (common - changed.toSet() - changedChoices.toSet()).map { it.second }.distinct().map { "same $it" }
    val ups = changed.count { a.getValue(it).value > b.getValue(it).value }
    val dir = when {
        changed.isEmpty() -> 0
        ups == changed.size -> 1
        ups == 0 -> -1
        else -> 0
    }
    return (parts + picked + same).joinToString(" · ") to dir
}

/**
 * "hang 11/12 · let go in round 2" when a work series was let go of early; null when none was.
 * The name is the series' when all let-gos are the same series, else "work".
 */
fun Session.letGoText(): String? {
    if (letGo.isEmpty()) return null
    val name = letGo.map { it.series }.distinct().singleOrNull() ?: "work"
    val count = if (workDone > 0) " ${workDone - letGo.size}/$workDone" else ""
    val rounds = letGo.map { it.round }.distinct().sorted()
    return "$name$count · let go in round${if (rounds.size > 1) "s" else ""} ${rounds.joinToString(", ")}"
}

/** Step of a suggested load increase on interval workouts, kg (mockup intervals-ideas-from-other-apps.html, 1). */
const val INTERVAL_LOAD_STEP = 1.0

/**
 * Loads to try next time (mockup intervals-ideas-from-other-apps.html, 1), from [recent] sessions of one workout, newest first:
 * a value in kg that was the same in the last two sessions with values, both completed with nothing let go, comes back
 * [INTERVAL_LOAD_STEP] higher. Choices and other units are left alone. Empty when there is nothing to suggest.
 */
fun intervalLoadHints(recent: List<Session>): List<LoggedValue> {
    val two = recent.filter { it.values.isNotEmpty() }.take(2)
    if (two.size < 2 || two.any { !it.completed || it.letGo.isNotEmpty() }) return emptyList()
    val now = two[0].lastValues().associateBy { Triple(it.series, it.field, it.side) }
    val before = two[1].lastValues().associateBy { Triple(it.series, it.field, it.side) }
    return now.filter { (k, v) -> v.text == null && v.unit.equals("kg", ignoreCase = true) && before[k]?.value == v.value && before[k]?.text == null }
        .values.map { it.copy(value = it.value + INTERVAL_LOAD_STEP) }
}
