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
            val bySide = fv.groupBy { it.side }.mapValues { (_, x) -> x.maxBy { it.round }.value }
            val both = bySide[null]
            if (both != null) "$field ${num(both)}$unit"
            else "$field L ${bySide[Side.LEFT]?.let(::num) ?: "–"} · R ${bySide[Side.RIGHT]?.let(::num) ?: "–"}$unit"
        }
        "$series · $fields"
    }
}
