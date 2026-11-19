package app.intervals.core

/** Which side the next set is for, on a per-side exercise. */
enum class SideChoice { LEFT, RIGHT, BOTH }

/** Quick notes offered on a set; the user's own text goes in the note too. */
val QUICK_TAGS = listOf("Max", "Bad form", "Assisted", "Failed", "+½ rep")

/**
 * The values the log screen starts from: the last set done today, else the first set of last time.
 * With a [side], that side's sets come first: L starts from the last L, R from the last R.
 */
fun startValues(today: List<GymSet>, lastTime: List<GymSet>, side: Side? = null): GymSet? =
    side?.let { s -> today.lastOrNull { it.side == s } ?: lastTime.firstOrNull { it.side == s } }
        ?: today.lastOrNull() ?: lastTime.firstOrNull()

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

/** Tapping a band dot: none → ×1 → ×2 → none. Kept strongest first. */
fun List<Band>.tap(band: Band): List<Band> {
    val n = count { it == band }
    val others = filter { it != band }
    return (others + List((n + 1) % 3) { band }).sortedBy { it.ordinal }
}

/** Adds or removes a quick tag in a note ("Max · ring finger hurt"). */
fun toggleTag(note: String, tag: String): String {
    val parts = note.split(" · ").map(String::trim).filter(String::isNotEmpty)
    return (if (tag in parts) parts - tag else listOf(tag) + parts).joinToString(" · ")
}

/** Filter of the add-exercise list. */
enum class SideFilter { ALL, ONE_SIDE, BOTH_SIDES }

/** A folding section of the add-exercise list. */
data class PickSection(val key: String, val title: String, val exercises: List<Exercise>)

/**
 * The add-exercise list, each exercise once, in this order: done on [day], recent (the 14 days before), one section per group,
 * then everything else A–Z. Empty sections are left out.
 */
fun pickSections(
    exercises: List<Exercise>,
    doneOnDay: Set<Long>,
    lastUsed: Map<Long, String>,
    groups: List<GymGroup>,
    day: String,
    filter: SideFilter = SideFilter.ALL,
): List<PickSection> {
    val shown = exercises.filter {
        when (filter) {
            SideFilter.ALL -> true
            SideFilter.ONE_SIDE -> it.perSide
            SideFilter.BOTH_SIDES -> !it.perSide
        }
    }
    val byId = shown.associateBy { it.id }
    val taken = mutableSetOf<Long>()
    fun take(list: List<Exercise>) = list.filter { taken.add(it.id) }
    val since = java.time.LocalDate.parse(day).minusDays(14).toString()
    val out = mutableListOf<PickSection>()
    out += PickSection("today", "Done today", take(shown.filter { it.id in doneOnDay }))
    out += PickSection("recent", "Recent", take(
        shown.filter { (lastUsed[it.id] ?: "") in since..day }.sortedByDescending { lastUsed[it.id] }
    ))
    groups.forEach { g -> out += PickSection("g${g.id}", g.name, take(g.items.mapNotNull { byId[it.exerciseId] })) }
    out += PickSection("rest", "Everything else A–Z", take(shown.sortedBy { it.name.lowercase() }))
    return out.filter { it.exercises.isNotEmpty() }
}
