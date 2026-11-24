package app.intervals.core

/** Which side the next set is for, on a per-side exercise. */
enum class SideChoice { LEFT, RIGHT, BOTH }

/** Quick notes offered on a set; the user's own text goes in the note too. */
val QUICK_TAGS = listOf(WARMUP_TAG, "Max", "Bad form", "Assisted", "Failed", "+½ rep")

/**
 * The values the log screen starts from (mockup intervals-next-features.html, 1A): last time's set at the same place
 * (with 3 sets done today, last time's 4th), else the last set done today. With a [side], only that side's sets count:
 * the 2nd L today starts from last time's 2nd L.
 */
fun startValues(today: List<GymSet>, lastTime: List<GymSet>, side: Side? = null): GymSet? {
    val t = if (side == null) today else today.filter { it.side == side }
    val l = if (side == null) lastTime else lastTime.filter { it.side == side }
    return l.getOrNull(t.size) ?: t.lastOrNull() ?: if (side != null) startValues(today, lastTime) else null
}

/** The last set done in [variant], today's included; on [side] when there is one there. Null = never done. */
fun lastOfVariant(all: List<GymSet>, ex: Exercise, variant: String, side: Side? = null): GymSet? {
    val mine = all.filter { it.variantOf(ex) == variant }.sortedWith(compareBy({ it.day }, { it.order }))
    return side?.let { sd -> mine.lastOrNull { it.side == sd } } ?: mine.lastOrNull()
}

/** After a set on one side, the next is on the other; "both" stays both. */
fun SideChoice.afterSet(): SideChoice = when (this) {
    SideChoice.LEFT -> SideChoice.RIGHT
    SideChoice.RIGHT -> SideChoice.LEFT
    SideChoice.BOTH -> SideChoice.BOTH
}

/** The sides one "Log set" writes: one set, or one per side with the same values. */
fun SideChoice?.sides(): List<Side?> = when (this) {
    null -> listOf(null)
    SideChoice.LEFT -> listOf(Side.LEFT)
    SideChoice.RIGHT -> listOf(Side.RIGHT)
    SideChoice.BOTH -> listOf(Side.LEFT, Side.RIGHT)
}

/** Adds or removes a quick tag in a note ("Max · ring finger hurt"). */
fun toggleTag(note: String, tag: String): String {
    val parts = note.split(" · ").map(String::trim).filter(String::isNotEmpty)
    return (if (tag in parts) parts - tag else listOf(tag) + parts).joinToString(" · ")
}

/** A folding section of the add-exercise list. */
data class PickSection(val key: String, val title: String, val exercises: List<Exercise>)

/**
 * The add-exercise list, each exercise once, in this order: done on [day], recent (the 14 days before), one section per tag,
 * then everything else A–Z. Empty sections are left out.
 */
fun pickSections(
    exercises: List<Exercise>,
    doneOnDay: Set<Long>,
    lastUsed: Map<Long, String>,
    groups: List<GymGroup>,
    day: String,
): List<PickSection> {
    val shown = exercises
    val taken = mutableSetOf<Long>()
    fun take(list: List<Exercise>) = list.filter { taken.add(it.id) }
    val since = java.time.LocalDate.parse(day).minusDays(14).toString()
    val out = mutableListOf<PickSection>()
    out += PickSection("today", "Done today", take(shown.filter { it.id in doneOnDay }))
    out += PickSection("recent", "Recent", take(
        shown.filter { (lastUsed[it.id] ?: "") in since..day }.sortedByDescending { lastUsed[it.id] }
    ))
    val byId = shown.associateBy { it.id }
    groups.filter { it.isTag }.forEach { g ->
        out += PickSection("g${g.id}", g.name, take(g.items.mapNotNull { byId[it.exerciseId] }))
    }
    out += PickSection("rest", "Everything else A–Z", take(shown.sortedBy { it.name.lowercase() }))
    return out.filter { it.exercises.isNotEmpty() }
}

/** A hint to go up in load (mockup intervals-ideas-from-other-apps.html, 1): [reps] at [fromKg] on both [days], so try [toKg]. */
data class LoadHint(val fromKg: Double, val toKg: Double, val reps: Int, val days: List<String>)

/**
 * The next load to try, as a plain hint, never a target: the last two days before [today] (on [side], in [variant], warm-ups left
 * out) had the same top weight, the top set's reps held or went up, and no set at that weight was tagged Failed. Null otherwise,
 * and for exercises without both weight and reps. Assisted (negative) weights go up too: −20 → −17.5 is less help.
 */
fun nextLoadHint(history: List<GymSet>, ex: Exercise, today: String, side: Side?, variant: String?): LoadHint? {
    val f = ex.fieldsOf(variant)
    if (GymField.WEIGHT !in f || GymField.REPS !in f) return null
    val mine = history.filter {
        it.day < today && !it.isWarmup && it.weightKg != null && (side == null || it.side == side) && (variant == null || it.variantOf(ex) == variant)
    }
    val days = mine.groupBy { it.day }.toSortedMap().entries.toList().takeLast(2)
    if (days.size < 2) return null
    val tops = days.map { (_, s) -> val w = s.maxOf { it.weightKg!! }; w to s.filter { it.weightKg == w } }
    val w = tops[0].first
    // no weight (0 kg, body weight only) is left alone
    if (tops[1].first != w || w == 0.0) return null
    if (tops.any { (_, s) -> s.any { set -> set.note.split(" · ").any { it.trim().equals("Failed", ignoreCase = true) } } }) return null
    val before = tops[0].second.maxOf { it.reps ?: 0 }
    val last = tops[1].second.maxOf { it.reps ?: 0 }
    if (last == 0 || last < before) return null
    return LoadHint(w, w + ex.weightStep, last, days.map { it.key })
}

/**
 * The variant name a merge suggests: the words of [from] that [into] doesn't have ("Box Pike Push Up" into "Pike Push Up" → "box",
 * "Barbell Squat" into "Squat" → "barbell"). Null when nothing is left.
 */
fun variantSuggestion(from: String, into: String): String? {
    val have = into.lowercase().split(' ', '(', ')', '-').filter(String::isNotBlank).toSet()
    return from.lowercase().split(' ', '(', ')', '-').filter { it.isNotBlank() && it !in have }.joinToString(" ").ifEmpty { null }
}
