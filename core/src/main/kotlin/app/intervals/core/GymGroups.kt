package app.intervals.core

/** "plan: 4 sets · 3 × 8 @ 40 kg", "no plan". */
fun GroupItem.planLabel(): String {
    val parts = listOfNotNull(planSets?.let { "$it set" + if (it == 1) "" else "s" }, planNote.takeIf { it.isNotBlank() })
    return if (parts.isEmpty()) "no plan" else "plan: " + parts.joinToString(" · ")
}

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
