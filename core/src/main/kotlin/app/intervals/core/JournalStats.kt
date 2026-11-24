package app.intervals.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Local day of a session start. */
fun Session.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

data class PeriodSummary(val sessions: Int, val days: Int, val totalSec: Int, val completed: Int)

fun summary(sessions: List<Session>, zone: ZoneId): PeriodSummary = PeriodSummary(
    sessions = sessions.size,
    days = sessions.map { it.day(zone) }.distinct().size,
    totalSec = sessions.sumOf { it.durationSec },
    completed = sessions.count { it.completed },
)

/** Monday of the week containing [d]. */
fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Minutes trained per week, the last [weeks] weeks up to the one containing [today], oldest first. */
fun minutesPerWeek(sessions: List<Session>, weeks: Int, today: LocalDate, zone: ZoneId): List<Pair<LocalDate, Int>> {
    val last = weekStart(today)
    val byWeek = sessions.groupBy { weekStart(it.day(zone)) }.mapValues { (_, s) -> s.sumOf { it.durationSec } }
    return (weeks - 1 downTo 0).map { k ->
        val w = last.minusWeeks(k.toLong())
        w to ((byWeek[w] ?: 0) + 30) / 60
    }
}

/** Minutes per day of a month (1-based day index), for the month bars. */
fun minutesPerDay(sessions: List<Session>, month: java.time.YearMonth, zone: ZoneId): List<Int> {
    val byDay = sessions.filter { java.time.YearMonth.from(it.day(zone)) == month }
        .groupBy { it.day(zone).dayOfMonth }.mapValues { (_, s) -> s.sumOf { it.durationSec } }
    return (1..month.lengthOfMonth()).map { ((byDay[it] ?: 0) + 30) / 60 }
}

data class WorkoutCount(val name: String, val times: Int, val totalSec: Int)

/** Most done workouts, by number of sessions then time. */
fun topWorkouts(sessions: List<Session>, n: Int): List<WorkoutCount> =
    sessions.groupBy { it.workoutName }
        .map { (name, s) -> WorkoutCount(name, s.size, s.sumOf { it.durationSec }) }
        .sortedWith(compareByDescending<WorkoutCount> { it.times }.thenByDescending { it.totalSec })
        .take(n)

private fun num(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/**
 * Logged values in one short line, last value of each field:
 * "hang · Load L 10 · R 8 kg · Edge 20 mm".
 */
fun Session.valuesLine(): String? {
    if (values.isEmpty()) return null
    return values.groupBy { it.series }.entries.joinToString("  ·  ") { (series, vs) ->
        val fields = vs.groupBy { it.field }.entries.joinToString(" · ") { (field, fv) ->
            val unit = fv.first().unit.let { if (it.isBlank()) "" else " $it" }
            // a choice shows its text (Grip half crimp), a number its value
            val bySide = fv.groupBy { it.side }.mapValues { (_, x) -> x.maxBy { it.round }.let { v -> v.text ?: num(v.value) } }
            val u = if (fv.any { it.text != null }) "" else unit
            val both = bySide[null]
            if (both != null) "$field $both$u"
            else "$field L ${bySide[Side.LEFT] ?: "–"} · R ${bySide[Side.RIGHT] ?: "–"}$u"
        }
        "$series · $fields"
    }
}

/** A logged number of a workout over time (mockup intervals-ideas-from-other-apps.html, 3): series · field · side. */
data class ValueKey(val series: String, val field: String, val unit: String, val side: Side?)

/** The numbers a workout logs (choices left out), in the order first logged. */
fun valueKeys(sessions: List<Session>): List<ValueKey> =
    sessions.sortedBy { it.startedAt }.flatMap { s -> s.values.filter { it.text == null }.map { ValueKey(it.series, it.field, it.unit, it.side) } }.distinct()

/**
 * One point per training day (the highest value of any round that day), oldest first, a record when it beats every
 * earlier day, like the gym graphs. [dayOf] turns a session's start into its day (yyyy-MM-dd).
 */
fun valueSeries(sessions: List<Session>, key: ValueKey, dayOf: (Long) -> String): List<MetricPoint> {
    var best: Double? = null
    return sessions.flatMap { s ->
        s.values.filter { it.text == null && it.series == key.series && it.field == key.field && it.side == key.side }.map { dayOf(s.startedAt) to it.value }
    }.groupBy({ it.first }, { it.second }).toSortedMap().map { (day, vs) ->
        val v = vs.max()
        val record = best != null && v > best!!
        if (best == null || v > best!!) best = v
        MetricPoint(day, v, record)
    }
}

/** A record of [valueSeries] and the day a later one beat it (null = still the best). */
data class ValueRecord(val day: String, val value: Double, val beatenOn: String?)

/** The records, newest first: the first one is the best now. The first day counts as the starting best. */
fun valueRecords(points: List<MetricPoint>): List<ValueRecord> {
    val recs = points.filterIndexed { i, p -> i == 0 || p.record }
    return recs.mapIndexed { i, p -> ValueRecord(p.day, p.value, recs.getOrNull(i + 1)?.day) }.reversed()
}
