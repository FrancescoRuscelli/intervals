package app.intervals.core

/** Which side the next set is for, on a per-side exercise. */
enum class SideChoice { LEFT, RIGHT, BOTH }

/** Quick notes offered on a set; the user's own text goes in the note too. */
val QUICK_TAGS = listOf("Max", "Bad form", "Assisted", "Failed", "+½ rep")

/** The values the log screen starts from: the last set done today, else the first set of last time. */
fun startValues(today: List<GymSet>, lastTime: List<GymSet>): GymSet? = today.lastOrNull() ?: lastTime.firstOrNull()

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
