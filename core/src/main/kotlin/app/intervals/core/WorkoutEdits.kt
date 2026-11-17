package app.intervals.core

/**
 * Where a series sits in a workout: the block, and inside a repeat group the series index.
 * For a single series [inner] is -1.
 */
data class SeriesPos(val block: Int, val inner: Int = -1)

/** One line of the editor list, in order: a single series, a repeat header, or a series inside a repeat. */
sealed interface EditorRow {
    val block: Int

    data class SingleRow(override val block: Int, val series: Series) : EditorRow
    data class RepeatHeader(override val block: Int, val times: Int, val size: Int) : EditorRow
    data class InRepeat(override val block: Int, val inner: Int, val series: Series) : EditorRow
}

val EditorRow.pos: SeriesPos?
    get() = when (this) {
        is EditorRow.SingleRow -> SeriesPos(block)
        is EditorRow.InRepeat -> SeriesPos(block, inner)
        is EditorRow.RepeatHeader -> null
    }

fun Workout.editorRows(): List<EditorRow> = buildList {
    blocks.forEachIndexed { b, block ->
        when (block) {
            is Block.Single -> add(EditorRow.SingleRow(b, block.series))
            is Block.Repeat -> {
                add(EditorRow.RepeatHeader(b, block.times, block.series.size))
                block.series.forEachIndexed { i, s -> add(EditorRow.InRepeat(b, i, s)) }
            }
        }
    }
}

/** Every series position, in order (repeat groups not unrolled). */
fun Workout.positions(): List<SeriesPos> = editorRows().mapNotNull { it.pos }

fun Workout.seriesAt(p: SeriesPos): Series = when (val b = blocks[p.block]) {
    is Block.Single -> b.series
    is Block.Repeat -> b.series[p.inner]
}

/** Adds a series at the end of the round. */
fun Workout.addSeries(s: Series): Workout = copy(blocks = blocks + Block.Single(s))

fun Workout.replaceSeries(p: SeriesPos, s: Series): Workout = copy(
    blocks = blocks.mapIndexed { i, b ->
        if (i != p.block) b else when (b) {
            is Block.Single -> Block.Single(s)
            is Block.Repeat -> b.copy(series = b.series.mapIndexed { j, x -> if (j == p.inner) s else x })
        }
    },
)

/** Removes series; a repeat group left empty disappears. */
fun Workout.deleteSeries(ps: Collection<SeriesPos>): Workout {
    val drop = ps.toSet()
    val out = blocks.mapIndexedNotNull { i, b ->
        when (b) {
            is Block.Single -> if (SeriesPos(i) in drop) null else b
            is Block.Repeat -> {
                val kept = b.series.filterIndexed { j, _ -> SeriesPos(i, j) !in drop }
                if (kept.isEmpty()) null else b.copy(series = kept)
            }
        }
    }
    return copy(blocks = out)
}

/** Copies of the series, placed right after the last selected one (inside the same group if it is in one). */
fun Workout.duplicateSeries(ps: Collection<SeriesPos>): Workout {
    if (ps.isEmpty()) return this
    val order = positions()
    val sel = order.filter { it in ps.toSet() }
    val copies = sel.map { seriesAt(it) }
    val last = sel.last()
    return copy(
        blocks = blocks.flatMapIndexed { i, b ->
            when {
                i != last.block -> listOf(b)
                b is Block.Repeat -> listOf(
                    b.copy(series = b.series.take(last.inner + 1) + copies + b.series.drop(last.inner + 1)),
                )
                else -> listOf(b) + copies.map { Block.Single(it) }
            }
        },
    )
}

/**
 * True when the selection can become one repeat group:
 * single series only (not already in a group), next to each other.
 */
fun Workout.canGroup(ps: Collection<SeriesPos>): Boolean {
    if (ps.isEmpty() || ps.any { it.inner != -1 }) return false
    val idx = ps.map { it.block }.sorted()
    return idx.zipWithNext().all { (a, b) -> b == a + 1 } && idx.all { blocks.getOrNull(it) is Block.Single }
}

/** Turns adjacent single series into "Repeat ×times". */
fun Workout.groupSeries(ps: Collection<SeriesPos>, times: Int): Workout {
    require(canGroup(ps)) { "only adjacent single series can be grouped" }
    require(times >= 1) { "times must be >= 1" }
    val idx = ps.map { it.block }.sorted()
    val group = Block.Repeat(times, idx.map { (blocks[it] as Block.Single).series })
    return copy(blocks = blocks.take(idx.first()) + group + blocks.drop(idx.last() + 1))
}

fun Workout.setRepeatTimes(block: Int, times: Int): Workout {
    require(times >= 1) { "times must be >= 1" }
    return copy(blocks = blocks.mapIndexed { i, b -> if (i == block && b is Block.Repeat) b.copy(times = times) else b })
}

/** Back to single series, played once each. */
fun Workout.ungroup(block: Int): Workout = copy(
    blocks = blocks.flatMapIndexed { i, b ->
        if (i == block && b is Block.Repeat) b.series.map { Block.Single(it) } else listOf(b)
    },
)

/** Moves a whole block (a single series or a group) up or down by one place. */
fun Workout.moveBlock(block: Int, by: Int): Workout {
    val to = block + by
    if (to !in blocks.indices || block !in blocks.indices) return this
    val list = blocks.toMutableList()
    val b = list.removeAt(block)
    list.add(to, b)
    return copy(blocks = list)
}

/** The colors of one round, as (seconds, color) pieces: what the mini bars in the list and editor draw. */
fun Workout.roundBar(): List<Pair<Int, Long>> = blocks.flatMap { b ->
    val one = { s: Series ->
        val ps = s.perSide
        if (ps == null) listOf(s.durationSec to s.color)
        else listOfNotNull(s.durationSec to s.color, (ps.changeSec to 0xFF00BFA5L).takeIf { ps.changeSec > 0 }, s.durationSec to s.color)
    }
    when (b) {
        is Block.Single -> one(b.series)
        is Block.Repeat -> List(b.times) { b.series.flatMap(one) }.flatten()
    }
}

/** Number of series in one round, repeat groups unrolled (per-side counts once). */
val Workout.seriesCount: Int
    get() = blocks.sumOf { b -> when (b) { is Block.Single -> 1; is Block.Repeat -> b.times * b.series.size } }
