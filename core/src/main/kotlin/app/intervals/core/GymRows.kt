package app.intervals.core

/**
 * One row of an exercise's table on a day: identical sets one after the other are one row with a count,
 * e.g. sets 2–4 "80 kg × 5" ×3, or "L R" with the same values ×2. A set with a note keeps its own row.
 */
data class SetRow(
    /** "1", "2–4", or the sides "L", "L R". */
    val label: String,
    val set: GymSet,
    val count: Int,
)

fun dayRows(sets: List<GymSet>): List<SetRow> {
    class Acc(val from: Int, var to: Int, val sides: MutableList<Side>, val set: GymSet, var count: Int)
    val out = mutableListOf<Acc>()
    sets.forEachIndexed { i, s ->
        val last = out.lastOrNull()
        if (last != null && last.set.note.isEmpty() && s.note.isEmpty() &&
            (last.set.side == null) == (s.side == null) && last.set.sameValues(s)
        ) {
            last.to = i + 1
            last.count++
            s.side?.let { if (it !in last.sides) last.sides += it }
        } else {
            out += Acc(i + 1, i + 1, listOfNotNull(s.side).toMutableList(), s, 1)
        }
    }
    return out.map { a ->
        val label = when {
            a.sides.isNotEmpty() -> a.sides.joinToString(" ") { it.letter }
            a.count > 1 -> "${a.from}–${a.to}"
            else -> "${a.from}"
        }
        SetRow(label, a.set, a.count)
    }
}

val Side.letter: String get() = if (this == Side.LEFT) "L" else "R"

fun GymSet.sameValues(o: GymSet) =
    weightKg == o.weightKg && reps == o.reps && timeSec == o.timeSec && distance == o.distance && bands == o.bands

/** The best set: heaviest (least assist), then most reps, longest, most bands. */
fun topSet(sets: List<GymSet>, ex: Exercise): GymSet? = sets.maxWithOrNull(
    compareBy<GymSet>({ (it.weightKg ?: 0.0) * if (ex.assist) -1 else 1 }, { it.reps ?: 0 }, { it.timeSec ?: 0 }, { it.bands.size })
)

/** 80, 22.5, 1.25 */
fun formatKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()

/** 0:40, 2:05 */
fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

private val bandEmoji = mapOf(
    Band.GREEN to "🟢", Band.PURPLE to "🟣", Band.BLACK to "⚫",
    Band.RED to "🔴", Band.YELLOW to "🟡",
)

/** One-line text of a set, e.g. "80 × 5", "−20 × 3", "🟢🟣 × 5", "+10 × 0:07", "0:40". */
fun GymSet.summary(ex: Exercise): String = buildList {
    if (GymField.WEIGHT in ex.fields) weightKg?.let { add((if (ex.assist && it != 0.0) "−" else "") + formatKg(it)) }
    if (GymField.BANDS in ex.fields && bands.isNotEmpty()) add(bands.sortedBy { it.ordinal }.joinToString("") { bandEmoji.getValue(it) })
    if (GymField.REPS in ex.fields) reps?.let { add("$it") }
    if (GymField.TIME in ex.fields) timeSec?.let { add(formatTime(it)) }
    if (GymField.DISTANCE in ex.fields) distance?.let { add(formatKg(it)) }
}.joinToString(" × ")
