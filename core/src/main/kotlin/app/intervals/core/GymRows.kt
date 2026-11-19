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
    /** Every set folded into this row, in order. */
    val sets: List<GymSet> = listOf(set),
) {
    /** The real rest before the row's sets: "2:10", or the range "2:05–2:30" when they differ; null when none was timed. */
    val restText: String?
        get() {
            val r = sets.mapNotNull { it.restSec }
            if (r.isEmpty()) return null
            return if (r.min() == r.max()) formatTime(r.min()) else formatTime(r.min()) + "–" + formatTime(r.max())
        }
}

fun dayRows(sets: List<GymSet>): List<SetRow> {
    class Acc(val from: Int, var to: Int, val sides: MutableList<Side>, val set: GymSet, var count: Int, val all: MutableList<GymSet> = mutableListOf(set))
    val out = mutableListOf<Acc>()
    sets.forEachIndexed { i, s ->
        val last = out.lastOrNull()
        if (last != null && last.set.note.isEmpty() && s.note.isEmpty() &&
            (last.set.side == null) == (s.side == null) && last.set.sameValues(s)
        ) {
            last.to = i + 1
            last.count++
            last.all += s
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
        SetRow(label, a.set, a.count, a.all)
    }
}

val Side.letter: String get() = if (this == Side.LEFT) "L" else "R"

fun GymSet.sameValues(o: GymSet) =
    weightKg == o.weightKg && reps == o.reps && timeSec == o.timeSec && distance == o.distance && bands == o.bands && specs == o.specs

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

/** One-line text of a set, e.g. "80 × 5", "−20 × 3", "🟢🟣 × 5", "+10 × 0:07", "0:40"; specs first: "40 mm · 20 × 5". */
fun GymSet.summary(ex: Exercise): String = listOfNotNull(specsText(ex).ifEmpty { null }, valuesText(ex).ifEmpty { null }).joinToString(" · ")

/** The set's specifications in the exercise's order, e.g. "40 mm · half crimp"; empty when none. */
fun GymSet.specsText(ex: Exercise): String =
    ex.specs.mapNotNull { sp -> specs[sp.name]?.takeIf(String::isNotBlank)?.let(sp::text) }.joinToString(" · ")

private fun GymSet.valuesText(ex: Exercise): String = buildList {
    if (GymField.WEIGHT in ex.fields) weightKg?.let { add((if (ex.assist && it != 0.0) "−" else "") + formatKg(it)) }
    if (GymField.BANDS in ex.fields && bands.isNotEmpty()) add(bands.sortedBy { it.ordinal }.joinToString("") { bandEmoji.getValue(it) })
    if (GymField.REPS in ex.fields) reps?.let { add("$it") }
    if (GymField.TIME in ex.fields) timeSec?.let { add(formatTime(it)) }
    if (GymField.DISTANCE in ex.fields) distance?.let { add(formatKg(it)) }
}.joinToString(" × ")

/** One block of an exercise's day (mockup G17f): the sets done at the same load, e.g. "45 kg · 40 mm" with reps 5 · 5 · 4. */
data class LoadBlock(val label: String, val sets: List<GymSet>)

/** What each box of a block shows: the reps, else the time, else the distance. */
fun boxField(ex: Exercise): GymField? = listOf(GymField.REPS, GymField.TIME, GymField.DISTANCE).firstOrNull { it in ex.fields }

fun GymSet.boxText(ex: Exercise): String = when (boxField(ex)) {
    GymField.REPS -> reps?.toString() ?: "–"
    GymField.TIME -> timeSec?.let(::formatTime) ?: "–"
    GymField.DISTANCE -> distance?.let(::formatKg) ?: "–"
    else -> "✓"
}

/** The load of a set without the box value: "45 kg", "−20 kg", "40 mm · 🟢🟣", "+10 kg · 0:07"… empty when there is none. */
fun GymSet.loadText(ex: Exercise): String {
    val box = boxField(ex)
    return buildList {
        specsText(ex).takeIf(String::isNotEmpty)?.let(::add)
        if (GymField.WEIGHT in ex.fields) weightKg?.let { add((if (ex.assist && it != 0.0) "−" else "") + formatKg(it) + " kg") }
        if (GymField.BANDS in ex.fields && bands.isNotEmpty()) add(bands.sortedBy { it.ordinal }.joinToString("") { bandEmoji.getValue(it) })
        if (GymField.TIME in ex.fields && box != GymField.TIME) timeSec?.let { add(formatTime(it)) }
        if (GymField.DISTANCE in ex.fields && box != GymField.DISTANCE) distance?.let { add(formatKg(it)) }
    }.joinToString(" · ")
}

/** One side's sets (or all of a two-sided exercise) as blocks by load, in the order each load first appears. */
fun loadBlocks(sets: List<GymSet>, ex: Exercise): List<LoadBlock> =
    sets.groupBy { it.loadText(ex) }.map { (label, s) -> LoadBlock(label.ifEmpty { "Sets" }, s) }
