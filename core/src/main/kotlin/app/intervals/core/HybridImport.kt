package app.intervals.core

/**
 * Importer for the CSV backup of the "Hybrid Interval Timer" app (com.hybrid.intervaltimer).
 *
 * The file has no header and no quoting, and mixes two kinds of rows:
 *  - workout:  id, name, warmupSec, cooldownSec, totalSec, rounds
 *  - interval: id, workoutId, name, durationSec, (empty), (empty), 0, icon, color (signed ARGB int)
 * Intervals appear in play order. The old total is warmup + sum(intervals) * rounds + cooldown.
 */
object HybridImport {

    data class ImportedWorkout(
        val oldId: String,
        val workout: Workout,
        /** Total length according to the old app (includes the useless final rest). */
        val oldTotalSec: Int,
        /** True when the last series was a rest and became "rest between rounds". */
        val convertedFinalRest: Boolean,
    )

    data class Result(val workouts: List<ImportedWorkout>, val warnings: List<String>)

    private val ICONS = mapOf(
        "jump" to SeriesIcon.JUMP,
        "run" to SeriesIcon.RUN,
        "stand" to SeriesIcon.STAND,
        "weight" to SeriesIcon.WEIGHT,
        "warmup" to SeriesIcon.WARMUP,
        "cooldown" to SeriesIcon.COOL,
    )

    private data class RawWorkout(val id: String, val name: String, val warmup: Int, val cooldown: Int, val total: Int, val rounds: Int)

    fun parse(csv: String): Result {
        val warnings = mutableListOf<String>()
        val workouts = mutableListOf<RawWorkout>()
        val intervals = mutableMapOf<String, MutableList<Series>>()

        csv.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEachIndexed
            val f = line.split(',')
            val lineNo = index + 1
            val isInterval = f.size >= 9 && f.last().trim().toLongOrNull() != null &&
                f[f.size - 2].trim().lowercase() in ICONS.keys + "" && f[f.size - 3].trim() == "0"
            try {
                if (isInterval) {
                    val name = f.subList(2, f.size - 6).joinToString(",").trim()
                    val duration = f[f.size - 6].trim().toInt()
                    val iconKey = f[f.size - 2].trim().lowercase()
                    val icon = ICONS[iconKey] ?: SeriesIcon.STAND.also {
                        warnings += "Line $lineNo: unknown icon '$iconKey', using 'stand'"
                    }
                    val color = f.last().trim().toLong() and 0xFFFFFFFFL
                    if (duration <= 0) {
                        warnings += "Line $lineNo: series '$name' has no duration, skipped"
                    } else {
                        intervals.getOrPut(f[1].trim()) { mutableListOf() } += Series(name, duration, color, icon)
                    }
                } else if (f.size >= 6) {
                    val tail = f.takeLast(4).map { it.trim().toInt() }
                    val name = f.subList(1, f.size - 4).joinToString(",").trim()
                    workouts += RawWorkout(f[0].trim(), name, tail[0], tail[1], tail[2], tail[3])
                } else {
                    warnings += "Line $lineNo: not recognised, skipped"
                }
            } catch (e: NumberFormatException) {
                warnings += "Line $lineNo: bad number, skipped"
            }
        }

        val result = workouts.mapNotNull { w ->
            val series = intervals[w.id].orEmpty()
            if (series.isEmpty()) {
                warnings += "'${w.name}': has no series, skipped"
                return@mapNotNull null
            }
            val computedOld = w.warmup + w.cooldown + series.sumOf { it.durationSec } * w.rounds
            if (computedOld != w.total) {
                warnings += "'${w.name}': old total ${formatDuration(w.total)} does not match its series (${formatDuration(computedOld)})"
            }
            if (w.cooldown > 0) {
                warnings += "'${w.name}': cool-down of ${formatDuration(w.cooldown)} dropped (not used in Intervals)"
            }

            val last = series.last()
            val convert = series.size > 1 && isRestName(last.name)
            val roundSeries = if (convert) series.dropLast(1) else series
            ImportedWorkout(
                oldId = w.id,
                workout = Workout(
                    name = w.name,
                    warmupSec = w.warmup,
                    rounds = maxOf(1, w.rounds),
                    restBetweenRoundsSec = if (convert) last.durationSec else 0,
                    blocks = groupRepeats(mergeSides(roundSeries)),
                ),
                oldTotalSec = w.total,
                convertedFinalRest = convert,
            )
        }
        return Result(result, warnings)
    }

    private fun isRestName(name: String): Boolean {
        val n = name.lowercase()
        return "rest" in n || "change" in n
    }

    private fun isChangeName(name: String): Boolean {
        val n = name.lowercase()
        return "change" in n || "cambi" in n || "switch" in n
    }

    /**
     * The old app had no "per side": it was written as X, change, X.
     * Those three become one per-side series X (change time from the middle one).
     */
    fun mergeSides(series: List<Series>): List<Series> {
        val out = mutableListOf<Series>()
        var i = 0
        while (i < series.size) {
            val a = series[i]
            val mid = series.getOrNull(i + 1)
            val b = series.getOrNull(i + 2)
            if (mid != null && b != null && a == b && a.perSide == null && isChangeName(mid.name)) {
                out += a.copy(perSide = PerSide(mid.durationSec))
                i += 3
            } else {
                out += a
                i++
            }
        }
        return out
    }

    /**
     * Finds consecutive repetitions of the same pattern of series and turns them into repeat groups.
     * Greedy from the start: at each position takes the pattern that covers the most series
     * (on a tie, the shortest pattern). Series are equal when name, duration, color and icon match.
     */
    fun groupRepeats(series: List<Series>): List<Block> {
        val out = mutableListOf<Block>()
        var i = 0
        val n = series.size
        while (i < n) {
            var bestLen = 1
            var bestTimes = 1
            for (len in 1..(n - i) / 2) {
                val pattern = series.subList(i, i + len)
                var times = 1
                while (i + (times + 1) * len <= n && series.subList(i + times * len, i + (times + 1) * len) == pattern) times++
                if (times >= 2 && len * times > bestLen * bestTimes) {
                    bestLen = len
                    bestTimes = times
                }
            }
            if (bestTimes >= 2) {
                out += Block.Repeat(bestTimes, series.subList(i, i + bestLen).toList())
                i += bestLen * bestTimes
            } else {
                out += Block.Single(series[i])
                i++
            }
        }
        return foldTails(out)
    }

    /**
     * "A, rest" repeated, then a last "A" without the rest (the old app's way of skipping the last rest)
     * becomes one group with skipLast. Also "A, rest, A" alone becomes Repeat ×2 skipping the last rest.
     */
    private fun foldTails(blocks: List<Block>): List<Block> {
        val out = blocks.toMutableList()
        var j = 0
        while (j < out.size) {
            val b = out[j]
            if (b is Block.Repeat && !b.skipLast && b.series.size >= 2 && isRestName(b.series.last().name)) {
                val tail = b.series.dropLast(1)
                val next = out.subList(j + 1, minOf(out.size, j + 1 + tail.size)).map { (it as? Block.Single)?.series }
                if (next == tail) {
                    repeat(tail.size) { out.removeAt(j + 1) }
                    out[j] = Block.Repeat(b.times + 1, b.series, skipLast = true)
                }
            } else if (b is Block.Single && j + 2 < out.size) {
                val r = (out[j + 1] as? Block.Single)?.series
                val again = (out[j + 2] as? Block.Single)?.series
                if (r != null && isRestName(r.name) && again == b.series) {
                    out[j] = Block.Repeat(2, listOf(b.series, r), skipLast = true)
                    out.removeAt(j + 1)
                    out.removeAt(j + 1)
                }
            }
            j++
        }
        return out
    }
}
