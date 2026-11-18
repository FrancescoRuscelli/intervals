package app.intervals.core

/** "plan: 4 sets · 3 × 8 @ 40 kg", "no plan". */
fun GroupItem.planLabel(): String {
    val parts = listOfNotNull(planSets?.let { "$it set" + if (it == 1) "" else "s" }, planNote.takeIf { it.isNotBlank() })
    return if (parts.isEmpty()) "no plan" else "plan: " + parts.joinToString(" · ")
}

/** Group order suggests where to log next. An exercise without a count is started once it has a set. */
fun nextGroupItem(items: List<GroupItem>, setsByExercise: Map<Long, List<GymSet>>): GroupItem? =
    items.firstOrNull { setsByExercise[it.exerciseId].orEmpty().size < (it.planSets ?: 1) }

/**
 * Groups suggested from the log: the same exercises done together on at least [minDays] days.
 * The most frequent combinations first; a combination is skipped when all its exercises are already in a chosen one.
 * Items are in the order they are usually done; the name comes from the exercise with the most sets ("Deadlift day").
 */
fun suggestGroups(sets: List<GymSet>, exercises: List<Exercise>, minDays: Int = 3, max: Int = 6): List<GymGroup> {
    val byId = exercises.associateBy { it.id }
    val days = sets.groupBy { it.day }
    val combos = days.values.map { s -> s.map { it.exerciseId }.toSet() }.filter { it.size >= 2 }
        .groupingBy { it }.eachCount()
        .filter { it.value >= minDays }
        .toList().sortedWith(compareByDescending<Pair<Set<Long>, Int>> { it.second }.thenByDescending { it.first.size })
    val chosen = mutableListOf<Set<Long>>()
    for ((combo, _) in combos) {
        if (chosen.any { it.containsAll(combo) }) continue
        chosen += combo
        if (chosen.size == max) break
    }
    // usual order: average position of the exercise's first set in the day
    val firstPos = sets.groupBy { it.day to it.exerciseId }.mapValues { (_, s) -> s.minOf { it.order } }
    val avgPos = firstPos.entries.groupBy({ it.key.second }, { it.value }).mapValues { (_, v) -> v.average() }
    val setCount = sets.groupingBy { it.exerciseId }.eachCount()
    val taken = mutableSetOf<String>()
    return chosen.mapNotNull { combo ->
        val ids = combo.filter { it in byId }.sortedBy { avgPos[it] ?: 0.0 }
        if (ids.size < 2) return@mapNotNull null
        val main = ids.maxBy { setCount[it] ?: 0 }
        var name = byId.getValue(main).name + " day"
        var k = 2
        while (!taken.add(name)) name = byId.getValue(main).name + " day ${k++}"
        GymGroup(name = name, items = ids.map { GroupItem(it) })
    }
}

/** Soft group colors, kept apart from the app colors (purple, pink, yellow, teal). */
val GROUP_COLORS = listOf(0xFF8BC34AL, 0xFF5B8DEFL, 0xFFFF8A50L, 0xFFC8A97EL, 0xFF90A4AEL, 0xFFBA68C8L, 0xFF4DB6ACL, 0xFFE57373L)

/** Exercises in no group. */
const val NO_GROUP_COLOR = 0xFF5A5F68L

/** The color of a group: its own, else by its place in the list. */
fun GymGroup.colorAt(index: Int): Long = color ?: GROUP_COLORS[index % GROUP_COLORS.size]

/** The group an exercise belongs to (the first one containing it), or null. */
fun groupOf(exerciseId: Long, groups: List<GymGroup>): GymGroup? = groups.firstOrNull { g -> g.items.any { it.exerciseId == exerciseId } }

/** One piece of a day strip: an exercise, its sets that day, its group color. */
data class StripPiece(val exerciseId: Long, val sets: Int, val color: Long)

/** The day split by exercise (in the order done), sized by sets, colored by group. */
fun dayStrip(sets: List<GymSet>, groups: List<GymGroup>): List<StripPiece> {
    val colorOf = groups.withIndex().flatMap { (i, g) -> g.items.map { it.exerciseId to g.colorAt(i) } }
        .distinctBy { it.first }.toMap()
    return sets.sortedBy { it.order }.groupBy { it.exerciseId }
        .map { (id, s) -> StripPiece(id, s.size, colorOf[id] ?: NO_GROUP_COLOR) }
}

/** Names of the groups trained on a day, most sets first ("Legs · Shoulders"). */
fun dayGroups(sets: List<GymSet>, groups: List<GymGroup>): List<GymGroup> =
    sets.groupBy { groupOf(it.exerciseId, groups) }.filterKeys { it != null }
        .entries.sortedByDescending { it.value.size }.mapNotNull { it.key }

/** Records set on [day]: exercises whose main graph beat every earlier day ([all] = their full history). */
fun recordsOn(day: String, all: List<GymSet>, exercises: List<Exercise>): Int {
    val byEx = all.filter { it.day <= day }.groupBy { it.exerciseId }
    return exercises.count { ex ->
        val s = byEx[ex.id] ?: return@count false
        recordSets(ex, day, s).isNotEmpty()
    }
}
