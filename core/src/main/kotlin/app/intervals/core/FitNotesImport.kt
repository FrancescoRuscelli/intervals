package app.intervals.core

/**
 * Turns the content of a FitNotes backup (`.fitnotes`, a SQLite file read by the app) into exercises and sets.
 * - "(Left)" / "(Right)" pairs become one per-side exercise; the side goes on each set.
 * - Set comments become data: "L" / "R" → side, band emojis or color words → bands, the rest a note as written.
 * - Exercises never used are skipped; categories, routines, body weight are not read at all.
 */
object FitNotesImport {

    /** A row of FitNotes' `exercise` table. */
    data class FnExercise(
        val id: Long,
        val name: String,
        val typeId: Int,
        val notes: String?,
        val weightIncrement: Double?,
        val restSec: Int?,
    )

    /** A row of FitNotes' `training_log` table (weight already in kg). */
    data class FnSet(
        val id: Long,
        val exerciseId: Long,
        val date: String,
        val weightKg: Double,
        val reps: Int,
        val distance: Double,
        val durationSec: Int,
    )

    data class FnData(
        val exercises: List<FnExercise>,
        val sets: List<FnSet>,
        /** Set id → its comment. */
        val comments: Map<Long, String>,
        val defaultWeightStep: Double? = null,
        val defaultRestSec: Int? = null,
    )

    data class MergedPair(val name: String, val rightSets: Int, val leftSets: Int)

    data class Result(
        /** Ids 1, 2, 3…; sets point to them. */
        val exercises: List<Exercise>,
        val sets: List<GymSet>,
        val days: Int,
        val pairs: List<MergedPair>,
        /** One-side-only exercises (e.g. "Block Claw Pull (Right)") made per-side. */
        val oneSided: List<String>,
        val sideComments: Int,
        val bandComments: Int,
        val noteComments: Int,
        val skippedExercises: Int,
    )

    /** What a comment says. */
    data class Comment(val side: Side?, val bands: List<Band>, val note: String)

    private val sideSuffix = Regex("""^(.*?)\s*\((left|right)(?:\s+hand)?\)\s*$""", RegexOption.IGNORE_CASE)

    fun convert(data: FnData): Result {
        val defaultStep = data.defaultWeightStep?.takeIf { it > 0 } ?: 2.5
        val defaultRest = data.defaultRestSec?.takeIf { it > 0 } ?: 180
        val setsByExercise = data.sets.groupBy { it.exerciseId }
        val used = data.exercises.filter { it.id in setsByExercise }

        // group the used exercises: "X (Left)" and "X (Right)" go together under X
        class Part(val ex: FnExercise, val side: Side?)
        val groups = LinkedHashMap<String, MutableList<Part>>()
        for (e in used.sortedBy { it.id }) {
            val m = sideSuffix.matchEntire(e.name.trim())
            val base = m?.groupValues?.get(1)?.trim() ?: e.name.trim()
            val side = m?.groupValues?.get(2)?.let { if (it.equals("left", true)) Side.LEFT else Side.RIGHT }
            groups.getOrPut(base.lowercase()) { mutableListOf() } += Part(e, side)
        }

        val exercises = mutableListOf<Exercise>()
        val sets = mutableListOf<Pair<FnSet, GymSet>>()
        val pairs = mutableListOf<MergedPair>()
        val oneSided = mutableListOf<String>()
        var sideComments = 0
        var bandComments = 0
        var noteComments = 0

        for ((_, parts) in groups) {
            val id = exercises.size + 1L
            val partSets = parts.associateWith { setsByExercise[it.ex.id].orEmpty() }
            val main = parts.maxBy { partSets.getValue(it).size }
            val name = sideSuffix.matchEntire(main.ex.name.trim())?.groupValues?.get(1)?.trim() ?: main.ex.name.trim()
            val bySide = parts.any { it.side != null }
            if (bySide) {
                val r = parts.filter { it.side == Side.RIGHT }.sumOf { partSets.getValue(it).size }
                val l = parts.filter { it.side == Side.LEFT }.sumOf { partSets.getValue(it).size }
                if (r > 0 && l > 0) pairs += MergedPair(name, r, l) else oneSided += main.ex.name.trim()
            }

            val fromThis = mutableListOf<GymSet>()
            for (part in parts) for (s in partSets.getValue(part)) {
                val c = data.comments[s.id]?.let(::parseComment)
                if (c != null) {
                    if (c.side != null) sideComments++
                    if (c.bands.isNotEmpty()) bandComments++
                    if (c.note.isNotEmpty()) noteComments++
                }
                fromThis += GymSet(
                    exerciseId = id,
                    day = s.date.take(10),
                    order = 0, // set below, per day
                    side = part.side ?: c?.side,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    timeSec = s.durationSec,
                    distance = s.distance,
                    bands = c?.bands.orEmpty(),
                    note = c?.note.orEmpty(),
                ).also { sets += s to it }
            }

            val fields = fieldsFor(parts.map { it.ex.typeId }.toSet(), fromThis)
            // keep only the fields the exercise has
            for (i in sets.indices) {
                val (fn, g) = sets[i]
                if (g.exerciseId != id) continue
                sets[i] = fn to g.copy(
                    weightKg = g.weightKg.takeIf { GymField.WEIGHT in fields },
                    reps = g.reps.takeIf { GymField.REPS in fields },
                    timeSec = g.timeSec.takeIf { GymField.TIME in fields },
                    distance = g.distance.takeIf { GymField.DISTANCE in fields },
                )
            }
            exercises += Exercise(
                id = id,
                name = name,
                fields = fields,
                perSide = bySide || fromThis.any { it.side != null },
                weightStep = parts.firstNotNullOfOrNull { it.ex.weightIncrement?.takeIf { s -> s > 0 } } ?: defaultStep,
                restSec = parts.firstNotNullOfOrNull { it.ex.restSec?.takeIf { s -> s > 0 } } ?: defaultRest,
                notes = parts.mapNotNull { it.ex.notes?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString("\n"),
            )
        }

        // shortcut: assisted exercises guessed from the name ("Deloaded", "Assisted"), FitNotes has no such flag:
        // their weight was taken off, so it becomes negative
        val assisted = exercises.filter { Regex("deloaded|assisted", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }.map { it.id }.toSet()
        for (i in sets.indices) {
            val (fn, g) = sets[i]
            if (g.exerciseId in assisted && (g.weightKg ?: 0.0) > 0) sets[i] = fn to g.copy(weightKg = -g.weightKg!!)
        }

        // order inside each day = the order they were logged in FitNotes
        val ordered = sets.groupBy { it.second.day }.flatMap { (_, day) ->
            day.sortedBy { it.first.id }.mapIndexed { i, (_, g) -> g.copy(order = i) }
        }.sortedWith(compareBy({ it.day }, { it.order }))

        return Result(
            exercises = exercises,
            sets = ordered,
            days = ordered.map { it.day }.distinct().size,
            pairs = pairs,
            oneSided = oneSided,
            sideComments = sideComments,
            bandComments = bandComments,
            noteComments = noteComments,
            skippedExercises = data.exercises.size - used.size,
        )
    }

    /** FitNotes exercise types seen in real data: 0 weight + reps, 3 weight + time, 7 reps, 9 time. */
    private fun fieldsFor(types: Set<Int>, sets: List<GymSet>): List<GymField> {
        val f = mutableSetOf<GymField>()
        for (t in types) when (t) {
            0 -> f += listOf(GymField.WEIGHT, GymField.REPS)
            3 -> f += listOf(GymField.WEIGHT, GymField.TIME)
            7 -> f += GymField.REPS
            9 -> f += GymField.TIME
            else -> { // unknown type: whatever the sets actually have
                if (sets.any { (it.weightKg ?: 0.0) != 0.0 }) f += GymField.WEIGHT
                if (sets.any { (it.reps ?: 0) != 0 }) f += GymField.REPS
                if (sets.any { (it.timeSec ?: 0) != 0 }) f += GymField.TIME
                if (sets.any { (it.distance ?: 0.0) != 0.0 }) f += GymField.DISTANCE
            }
        }
        if (sets.any { it.bands.isNotEmpty() }) {
            f += GymField.BANDS
            // band exercises logged with weight 0: the bands are the load
            if (sets.all { (it.weightKg ?: 0.0) == 0.0 }) f -= GymField.WEIGHT
        }
        if (f.isEmpty()) f += GymField.REPS
        return GymField.entries.filter { it in f }
    }

    // ---- comments ----

    private const val VS16 = "️"
    private val bandEmojis = listOf(
        "🐈‍⬛" to Band.BLACK, // black cat
        "🟢" to Band.GREEN,
        "🟣" to Band.PURPLE,
        "🔴" to Band.RED,
        "♥" to Band.RED, // heart
        "🍒" to Band.RED, // cherries
        "⚫" to Band.BLACK,
        "🖤" to Band.BLACK, // black heart
        "🟡" to Band.YELLOW,
    )
    private val bandWords = listOf(
        Regex("""\b(green|verde)\b""", RegexOption.IGNORE_CASE) to Band.GREEN,
        Regex("""\b(purple|viola)\b""", RegexOption.IGNORE_CASE) to Band.PURPLE,
        Regex("""\b(red|ross[oa])\b""", RegexOption.IGNORE_CASE) to Band.RED,
        Regex("""\b(black|ner[oa])\b""", RegexOption.IGNORE_CASE) to Band.BLACK,
        Regex("""\b(yellow|giall[oa])\b""", RegexOption.IGNORE_CASE) to Band.YELLOW,
    )
    private val leadingSide = Regex("""^(left|right|l|r)(?![a-z])""", RegexOption.IGNORE_CASE)

    fun parseComment(text: String): Comment {
        var rest = text.trim()
        val side = leadingSide.find(rest)?.let { m ->
            rest = rest.substring(m.range.last + 1).trim()
            if (m.value.startsWith("l", ignoreCase = true)) Side.LEFT else Side.RIGHT
        }

        // emojis win over words ("Assisted, ♥️ red" is one red band)
        val found = mutableListOf<Pair<Int, Band>>()
        var scan = rest
        for ((emoji, band) in bandEmojis) {
            var i = scan.indexOf(emoji)
            while (i >= 0) {
                found += i to band
                scan = scan.substring(0, i) + " ".repeat(emoji.length) + scan.substring(i + emoji.length)
                i = scan.indexOf(emoji)
            }
        }
        val bands = if (found.isNotEmpty()) {
            found.sortedBy { it.first }.map { it.second }
        } else {
            bandWords.flatMap { (re, band) -> re.findAll(rest).map { it.range.first to band } }
                .sortedBy { it.first }.map { it.second }
        }

        // a note only when something besides the side and the bands is written; then kept as written
        var leftover = scan.replace(VS16, "")
        bandWords.forEach { (re, _) -> leftover = re.replace(leftover, "") }
        leftover = leftover.replace(Regex("""\b(all|band|bands)\b""", RegexOption.IGNORE_CASE), "")
        val onlyBands = bands.isNotEmpty() && leftover.none { it.isLetterOrDigit() }
        val note = if (onlyBands || rest.isEmpty()) "" else rest
        return Comment(side, bands, note)
    }
}
