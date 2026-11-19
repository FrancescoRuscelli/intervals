package app.intervals.core

/** A graph that can be drawn for an exercise (only those that fit its fields are offered). */
enum class GymMetric(val title: String) {
    MAX_WEIGHT("Max weight"),
    LEAST_ASSIST("Least assist"),
    VOLUME("Volume"),
    TOTAL_REPS("Total reps"),
    MAX_REPS("Max reps"),
    MAX_TIME("Longest time"),
    REST("Rest between sets"),
}

/** The graphs that make sense for [ex], the most useful first. */
fun metricsFor(ex: Exercise): List<GymMetric> = buildList {
    val w = GymField.WEIGHT in ex.fields
    val r = GymField.REPS in ex.fields
    if (w) add(if (ex.assist) GymMetric.LEAST_ASSIST else GymMetric.MAX_WEIGHT)
    if (w && r && !ex.assist) add(GymMetric.VOLUME)
    if (r) { add(GymMetric.MAX_REPS); add(GymMetric.TOTAL_REPS) }
    if (GymField.TIME in ex.fields) add(GymMetric.MAX_TIME)
    add(GymMetric.REST)
}

/** One value of a metric over the sets of one day (null when the day has nothing for it). */
fun metricOf(metric: GymMetric, sets: List<GymSet>): Double? {
    if (sets.isEmpty()) return null
    return when (metric) {
        GymMetric.MAX_WEIGHT -> sets.mapNotNull { it.weightKg }.maxOrNull()
        GymMetric.LEAST_ASSIST -> sets.mapNotNull { it.weightKg }.minOrNull()
        GymMetric.VOLUME -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }.takeIf { it > 0 }
        GymMetric.TOTAL_REPS -> sets.sumOf { it.reps ?: 0 }.toDouble().takeIf { it > 0 }
        GymMetric.MAX_REPS -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        GymMetric.MAX_TIME -> sets.mapNotNull { it.timeSec }.maxOrNull()?.toDouble()
        GymMetric.REST -> sets.mapNotNull { it.restSec }.takeIf { it.isNotEmpty() }?.average()
    }
}

/** For records: lower is better only for assist. */
val GymMetric.lowerIsBetter: Boolean get() = this == GymMetric.LEAST_ASSIST || this == GymMetric.REST

data class MetricPoint(val day: String, val value: Double, val record: Boolean)

/**
 * The metric per training day, oldest first; [side] limits to one side (per-side exercises).
 * A point is a record when it beats every earlier day.
 */
fun metricSeries(metric: GymMetric, sets: List<GymSet>, side: Side? = null): List<MetricPoint> {
    var best: Double? = null
    return sets.filter { side == null || it.side == side }
        .groupBy { it.day }.toSortedMap()
        .mapNotNull { (day, s) -> metricOf(metric, s)?.let { day to it } }
        .map { (day, v) ->
            val better = best == null || (if (metric.lowerIsBetter) v < best!! else v > best!!)
            val record = better && best != null && metric != GymMetric.REST
            if (better) best = v
            MetricPoint(day, v, record)
        }
}

/** The first set matching a record on [day], once per side for a per-side exercise. */
fun recordSets(exercise: Exercise, day: String, history: List<GymSet>): List<GymSet> {
    val metric = metricsFor(exercise).firstOrNull { it != GymMetric.REST } ?: return emptyList()
    val sets = history.filter { it.day == day }
    if (sets.isEmpty()) return emptyList()
    val sides = if (exercise.perSide) listOf(Side.LEFT, Side.RIGHT) else listOf(null)
    return sides.mapNotNull { side ->
        val point = metricSeries(metric, history, side).firstOrNull { it.day == day && it.record } ?: return@mapNotNull null
        sets.firstOrNull { (side == null || it.side == side) && metricOf(metric, listOf(it)) == point.value }
    }
}

/** Straight line through the points (least squares), as values at the first and last point. */
fun trend(values: List<Double>): Pair<Double, Double>? {
    val n = values.size
    if (n < 3) return null
    val mx = (n - 1) / 2.0
    val my = values.average()
    var num = 0.0
    var den = 0.0
    values.forEachIndexed { i, v -> num += (i - mx) * (v - my); den += (i - mx) * (i - mx) }
    val k = num / den
    val a = my - k * mx
    return a to a + k * (n - 1)
}

/** Heaviest (or least assist) weight for each number of reps; [beaten] when more reps were done with as much. */
data class RepMax(val reps: Int, val weightKg: Double, val day: String, val beaten: Boolean)

fun repMaxes(sets: List<GymSet>, assist: Boolean): List<RepMax> {
    val best = sets.filter { it.reps != null && it.reps > 0 && it.weightKg != null }
        .groupBy { it.reps!! }
        .mapValues { (_, s) -> if (assist) s.minWith(compareBy({ it.weightKg!! }, { it.day })) else s.maxWith(compareBy({ it.weightKg!! }, { -java.time.LocalDate.parse(it.day).toEpochDay() })) }
        .toSortedMap()
    val list = best.map { (r, s) -> r to s }
    return list.map { (r, s) ->
        val w = s.weightKg!!
        val beaten = list.any { (r2, s2) -> r2 > r && if (assist) s2.weightKg!! <= w else s2.weightKg!! >= w }
        RepMax(r, w, s.day, beaten)
    }
}

/** The training days of an exercise, newest first, each with its sets in order. */
fun historyDays(sets: List<GymSet>): List<Pair<String, List<GymSet>>> =
    sets.groupBy { it.day }.toSortedMap(compareByDescending { it }).map { (d, s) -> d to s.sortedBy { it.order } }

/** Gym days per week, the last [weeks] weeks up to the one containing [today], oldest first (weeks start on Monday). */
fun gymDaysPerWeek(days: Collection<String>, weeks: Int, today: java.time.LocalDate): List<Pair<java.time.LocalDate, Int>> {
    val last = weekStart(today)
    val byWeek = days.map { weekStart(java.time.LocalDate.parse(it)) }.groupingBy { it }.eachCount()
    return (weeks - 1 downTo 0).map { k -> last.minusWeeks(k.toLong()).let { it to (byWeek[it] ?: 0) } }
}

data class GymRecord(val exercise: Exercise, val metric: GymMetric, val day: String, val value: Double)

/** Records set on or after [since] (one per exercise, its main graph), newest first. */
fun recentRecords(exercises: List<Exercise>, sets: List<GymSet>, since: String): List<GymRecord> {
    val byEx = sets.groupBy { it.exerciseId }
    return exercises.mapNotNull { ex ->
        val s = byEx[ex.id] ?: return@mapNotNull null
        val metric = metricsFor(ex).firstOrNull { it != GymMetric.REST } ?: return@mapNotNull null
        val sides: List<Side?> = if (ex.perSide) listOf(Side.LEFT, Side.RIGHT) else listOf(null)
        sides.flatMap { side -> metricSeries(metric, s, side) }
            .filter { it.record && it.day >= since }
            .maxByOrNull { it.day }
            ?.let { GymRecord(ex, metric, it.day, it.value) }
    }.sortedByDescending { it.day }
}

/** Sets per exercise, most first. */
fun setsPerExercise(sets: List<GymSet>): List<Pair<Long, Int>> =
    sets.groupingBy { it.exerciseId }.eachCount().toList().sortedByDescending { it.second }
