package app.intervals.core

/** A graph that can be drawn for an exercise (only those that fit its fields are offered). */
enum class GymMetric(val title: String) {
    MAX_WEIGHT("Max weight"),
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
    if (w) add(GymMetric.MAX_WEIGHT)
    if (w && r) add(GymMetric.VOLUME)
    if (r) { add(GymMetric.MAX_REPS); add(GymMetric.TOTAL_REPS) }
    if (GymField.TIME in ex.fields) add(GymMetric.MAX_TIME)
    add(GymMetric.REST)
}

/** The graph records are counted on for [variant]'s sets: the first that fits what it records (rest never). */
fun Exercise.mainMetric(variant: String?): GymMetric? = metricsFor(forVariant(variant)).firstOrNull { it != GymMetric.REST }

/**
 * The sides to look at one by one: none for a two-sided exercise; L and R for a per-side one, plus "no side" ([NoSide])
 * when some sets have none (e.g. holds merged in from an exercise logged without sides).
 */
object NoSide

fun sidesOf(ex: Exercise, sets: List<GymSet>): List<Any?> =
    if (!ex.perSide) listOf(null) else listOf<Any?>(Side.LEFT, Side.RIGHT) + (if (sets.any { it.side == null }) listOf(NoSide) else emptyList())

/** The sets of one entry of [sidesOf]. */
fun List<GymSet>.onSide(side: Any?): List<GymSet> = when (side) {
    null -> this
    NoSide -> filter { it.side == null }
    else -> filter { it.side == side }
}

/** One value of a metric over the sets of one day (null when the day has nothing for it). */
fun metricOf(metric: GymMetric, sets: List<GymSet>): Double? {
    if (sets.isEmpty()) return null
    return when (metric) {
        GymMetric.MAX_WEIGHT -> sets.mapNotNull { it.weightKg }.maxOrNull()
        GymMetric.VOLUME -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }.takeIf { it > 0 }
        GymMetric.TOTAL_REPS -> sets.sumOf { it.reps ?: 0 }.toDouble().takeIf { it > 0 }
        GymMetric.MAX_REPS -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        GymMetric.MAX_TIME -> sets.mapNotNull { it.timeSec }.maxOrNull()?.toDouble()
        GymMetric.REST -> sets.mapNotNull { it.restSec }.takeIf { it.isNotEmpty() }?.average()
    }
}

/**
 * The quick tag that makes a set a warm-up (mockup intervals-ideas-from-other-apps.html, 5): it stays in the log and the bars,
 * but is left out of records, graphs and set counts.
 */
const val WARMUP_TAG = "Warm-up"

val GymSet.isWarmup: Boolean get() = note.split(" · ").any { it.trim().equals(WARMUP_TAG, ignoreCase = true) }

/** The set's note without the warm-up tag ("" when that was all). */
val GymSet.noteWithoutWarmup: String get() = note.split(" · ").map(String::trim).filter { it.isNotEmpty() && !it.equals(WARMUP_TAG, ignoreCase = true) }.joinToString(" · ")

/**
 * What a set's records are kept within (mockup intervals-ideas-from-other-apps.html, 6): its variant and its specifications,
 * so a deep squat never hides a half-squat record and a thumb pinch has its own. A missing single choice counts as the
 * first choice (like a missing variant); a missing number is its own group.
 */
fun GymSet.recordKey(ex: Exercise): String =
    (listOf(variantOf(ex).orEmpty()) + ex.specs.map { sp -> specs[sp.name] ?: if (sp.isChoice && !sp.multiple) sp.choices.first() else "" })
        .joinToString("|")

/** For records: lower is better only for rest (a negative assisted weight is just a smaller number, higher is better). */
val GymMetric.lowerIsBetter: Boolean get() = this == GymMetric.REST

data class MetricPoint(val day: String, val value: Double, val record: Boolean)

/**
 * The metric per training day, oldest first; [side] limits to one side (per-side exercises).
 * A point is a record when it beats every earlier day.
 */
fun metricSeries(metric: GymMetric, sets: List<GymSet>, side: Side? = null): List<MetricPoint> {
    var best: Double? = null
    return sets.filter { (side == null || it.side == side) && !it.isWarmup }
        .groupBy { it.day }.toSortedMap()
        .mapNotNull { (day, s) -> metricOf(metric, s)?.let { day to it } }
        .map { (day, v) ->
            val better = best == null || (if (metric.lowerIsBetter) v < best!! else v > best!!)
            val record = better && best != null && metric != GymMetric.REST
            if (better) best = v
            MetricPoint(day, v, record)
        }
}

/**
 * The first set matching a record on [day], once per side for a per-side exercise and once per [recordKey]
 * (variant and specifications). Warm-up sets never count.
 */
fun recordSets(exercise: Exercise, day: String, history: List<GymSet>): List<GymSet> {
    val real = history.filterNot { it.isWarmup }
    val sets = real.filter { it.day == day }
    if (sets.isEmpty()) return emptyList()
    val sides = sidesOf(exercise, real)
    val byKey = real.groupBy { it.recordKey(exercise) }
    return sets.groupBy { it.recordKey(exercise) }.flatMap { (key, mine) ->
        // each variant by what it records (a hold by its weight or time, pull-ups by weight)
        val metric = exercise.mainMetric(mine.first().variantOf(exercise)) ?: return@flatMap emptyList()
        sides.mapNotNull { side ->
            val point = metricSeries(metric, byKey.getValue(key).onSide(side)).firstOrNull { it.day == day && it.record } ?: return@mapNotNull null
            mine.onSide(side).firstOrNull { metricOf(metric, listOf(it)) == point.value }
        }
    }.sortedBy { it.order }
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

/** Heaviest weight (for assisted, the least negative) for each number of reps; [beaten] when more reps were done with as much. */
data class RepMax(val reps: Int, val weightKg: Double, val day: String, val beaten: Boolean)

fun repMaxes(sets: List<GymSet>): List<RepMax> {
    val best = sets.filter { it.reps != null && it.reps > 0 && it.weightKg != null && !it.isWarmup }
        .groupBy { it.reps!! }
        .mapValues { (_, s) -> s.maxWith(compareBy({ it.weightKg!! }, { -java.time.LocalDate.parse(it.day).toEpochDay() })) }
        .toSortedMap()
    val list = best.map { (r, s) -> r to s }
    return list.map { (r, s) ->
        val w = s.weightKg!!
        val beaten = list.any { (r2, s2) -> r2 > r && s2.weightKg!! >= w }
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
        val sides = sidesOf(ex, s)
        s.groupBy { it.recordKey(ex) }.values.flatMap { same ->
            val metric = ex.mainMetric(same.first().variantOf(ex)) ?: return@flatMap emptyList()
            sides.flatMap { side -> metricSeries(metric, same.onSide(side)).map { metric to it } }
        }
            .filter { it.second.record && it.second.day >= since }
            .maxByOrNull { it.second.day }
            ?.let { (metric, pt) -> GymRecord(ex, metric, pt.day, pt.value) }
    }.sortedByDescending { it.day }
}

data class ExerciseTraining(
    val exerciseId: Long,
    val sets: Int,
    val days: Int,
    val reps: Long?,
    val timeSec: Long?,
) {
    // If an exercise tracks both, rank by reps when it has any, or by its logged time otherwise.
    val timed: Boolean get() = timeSec != null && (reps ?: 0L) == 0L
    val work: Long? get() = if (timed) timeSec else reps
}

/** Totals for the supplied period, warm-ups left out. Sides and variants share the same exercise and training dates. */
fun exerciseTraining(sets: List<GymSet>): List<ExerciseTraining> =
    sets.filterNot { it.isWarmup }.groupBy { it.exerciseId }.map { (id, logged) ->
        val reps = logged.mapNotNull { it.reps }
        val seconds = logged.mapNotNull { it.timeSec }
        ExerciseTraining(
            exerciseId = id,
            sets = logged.size,
            days = logged.map { it.day }.toSet().size,
            reps = reps.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() },
            timeSec = seconds.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() },
        )
    }
