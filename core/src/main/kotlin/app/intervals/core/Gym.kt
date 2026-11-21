package app.intervals.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Old built-in resistance bands, strongest first. Now bands are a specification ("Bands", see [withBandsAsSpec]); kept to read old data. */
@Serializable
enum class Band { GREEN, PURPLE, BLACK, RED, YELLOW }

/** What is written down for each set of an exercise. BANDS is old: converted to a "Bands" specification, never offered. */
@Serializable
enum class GymField { WEIGHT, REPS, TIME, DISTANCE, BANDS }

/**
 * Something you specify yourself on an exercise, e.g. "Pinch width" in mm, or "Grip" with choices (open, half crimp…).
 * A number when [choices] is empty, else one of the choices. Each set keeps its value, starting from the last set's.
 */
@Serializable
data class Spec(
    val name: String,
    /** Shown after a number, e.g. "mm"; unused for a choice. */
    val unit: String = "",
    /** Step of the − / + buttons for a number. */
    val step: Double = 1.0,
    val choices: List<String> = emptyList(),
    /** A choice where you can pick several, each up to ×2 (e.g. bands): the value is "green+purple+purple". */
    val multiple: Boolean = false,
    /** Optional color of each choice, 0xAARRGGBB (e.g. the band colors). */
    val colors: Map<String, Long> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "a specification needs a name" }
        require(step > 0) { "step must be > 0" }
    }

    val isChoice: Boolean get() = choices.isNotEmpty()

    /** For a "several" choice: each picked choice and how many times, in the choices' order. */
    fun counts(value: String?): List<Pair<String, Int>> {
        val parts = value.orEmpty().split('+').map(String::trim).filter(String::isNotEmpty)
        return choices.mapNotNull { c -> parts.count { it == c }.takeIf { it > 0 }?.let { c to it } } +
            parts.filter { it !in choices }.distinct().map { o -> o to parts.count { it == o } } // values no longer in the list are kept
    }

    /** Tapping a choice of a "several" spec: none → ×1 → ×2 → none; null when nothing is left. */
    fun tap(value: String?, choice: String): String? {
        val c = counts(value).toMap().toMutableMap()
        val n = ((c[choice] ?: 0) + 1) % 3
        if (n == 0) c.remove(choice) else c[choice] = n
        return choices.plus(c.keys.filter { it !in choices }).flatMap { k -> List(c[k] ?: 0) { k } }.joinToString("+").ifEmpty { null }
    }

    /** "40 mm", "half crimp", "green+purple×2+red". */
    fun text(value: String): String = when {
        multiple -> counts(value).joinToString("+") { (c, n) -> if (n > 1) "$c×$n" else c }
        isChoice || unit.isBlank() -> value
        else -> "$value $unit"
    }
}

/** A strength exercise you created (no preset list, no categories). */
@Serializable
data class Exercise(
    val id: Long = 0,
    val name: String,
    val fields: List<GymField>,
    /** Each set is done on one side (L or R); one exercise, never separate "(Left)" / "(Right)" ones. */
    val perSide: Boolean = false,
    /**
     * Old way to mark an assisted exercise (weights stored positive). Now an assisted set just has a negative weight;
     * only read to convert old data, see [withNegativeAssist]. shortcut: kept so old backups still load.
     */
    val assist: Boolean = false,
    /** Step of the − / + buttons for the weight, kg. */
    val weightStep: Double = 2.5,
    /** Rest target, seconds: the rest clock buzzes here and keeps counting. */
    val restSec: Int = 180,
    val notes: String = "",
    /** Your own specifications, e.g. pinch width; values are on each set. */
    val specs: List<Spec> = emptyList(),
    /**
     * Ways of doing it, e.g. Squat: deep / half / normal; each set keeps its own (mockup G19). In this order on the log screen.
     * A set without one counts as the first.
     */
    val variants: List<String> = emptyList(),
) {
    init {
        require(variants.map { it.lowercase() }.toSet().size == variants.size) { "two variants have the same name" }
        require(name.isNotBlank()) { "an exercise needs a name" }
        require(specs.map { it.name.lowercase() }.toSet().size == specs.size) { "two specifications have the same name" }
        require(fields.isNotEmpty()) { "an exercise needs at least one field" }
        require(weightStep > 0) { "weightStep must be > 0" }
        require(restSec >= 0) { "restSec must be >= 0" }
    }
}

/** One set as logged. Only the fields of its exercise are filled. */
@Serializable
data class GymSet(
    val id: Long = 0,
    val exerciseId: Long,
    /** The training day, ISO yyyy-MM-dd. */
    val day: String,
    /** Place in the day (all exercises), 0 first. */
    val order: Int,
    val side: Side? = null,
    /** kg; negative = assisted (weight taken off, e.g. −20 on a deloaded pull-up). */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val timeSec: Int? = null,
    /** As FitNotes stored it; unit not used yet. */
    val distance: Double? = null,
    /** Old built-in bands; now in [specs] under "Bands" (see [withBandsAsSpec]). */
    val bands: List<Band> = emptyList(),
    val note: String = "",
    /** Real rest before this set, seconds; null when unknown (e.g. imported). */
    val restSec: Int? = null,
    /** Spec name → value as shown ("40" for a number, "half crimp" for a choice). */
    val specs: Map<String, String> = emptyMap(),
    /** The exercise variant (e.g. "deep"); null = the exercise's first variant, or none. */
    val variant: String? = null,
) {
    init {
        LocalDate.parse(day) // throws on a bad date
        require(order >= 0) { "order must be >= 0" }
        require(reps == null || reps >= 0) { "reps must be >= 0" }
        require(timeSec == null || timeSec >= 0) { "timeSec must be >= 0" }
    }
}

/** The set's variant: its own, else the exercise's first; null when the exercise has no variants. */
fun GymSet.variantOf(ex: Exercise): String? = if (ex.variants.isEmpty()) null else variant ?: ex.variants.first()

/** One exercise in a group, with an optional plan (just a reminder: going over or stopping early is fine). */
@Serializable
data class GroupItem(
    val exerciseId: Long,
    /** Planned sets; null = no plan. */
    val planSets: Int? = null,
    /** Free text of the plan, e.g. "3 × 8 @ 40 kg". */
    val planNote: String = "",
) {
    init {
        require(planSets == null || planSets >= 1) { "planSets must be >= 1" }
    }
}

/** A category tag or an exact ordered workout group. Missing [isTag] in old data means a category tag. */
@Serializable
data class GymGroup(
    val id: Long = 0,
    val name: String,
    val items: List<GroupItem>,
    /** 0xAARRGGBB used in the Journal day strip; null = picked from [GROUP_COLORS] by position. */
    val color: Long? = null,
    /** Legacy groups are category tags; workout groups are exact ordered routines. */
    val isTag: Boolean = true,
)

/**
 * Converts the old "assist" flag: the exercise's positive weights become negative and the flag goes off.
 * Exercises without the flag, and sets already negative, are left as they are, so running it twice changes nothing.
 */
fun withNegativeAssist(exercises: List<Exercise>, sets: List<GymSet>): Pair<List<Exercise>, List<GymSet>> {
    val assisted = exercises.filter { it.assist }.map { it.id }.toSet()
    if (assisted.isEmpty()) return exercises to sets
    return exercises.map { if (it.assist) it.copy(assist = false) else it } to
        sets.map { s -> if (s.exerciseId in assisted && (s.weightKg ?: 0.0) > 0) s.copy(weightKg = -s.weightKg!!) else s }
}

/** The "Bands" specification old band data becomes: the five old bands, strongest first, with their colors (all changeable). */
val BANDS_SPEC = Spec(
    "Bands", choices = listOf("green", "purple", "black", "red", "yellow"), multiple = true,
    colors = mapOf("green" to 0xFF43A047, "purple" to 0xFF8E44D8, "black" to 0xFF16181C, "red" to 0xFFE53935, "yellow" to 0xFFFFC107),
)

/**
 * Converts the old built-in bands: an exercise with the BANDS field gets the "Bands" specification instead, and its sets' bands
 * become that spec's value ("green+purple+purple"). Running it twice changes nothing.
 */
fun withBandsAsSpec(exercises: List<Exercise>, sets: List<GymSet>): Pair<List<Exercise>, List<GymSet>> {
    val old = exercises.filter { GymField.BANDS in it.fields }
    if (old.isEmpty() && sets.none { it.bands.isNotEmpty() }) return exercises to sets
    val ids = old.map { it.id }.toSet()
    val ex = exercises.map { e ->
        if (e.id !in ids) e
        else e.copy(
            fields = (e.fields - GymField.BANDS).ifEmpty { listOf(GymField.REPS) },
            specs = if (e.specs.any { it.name.equals(BANDS_SPEC.name, ignoreCase = true) }) e.specs else e.specs + BANDS_SPEC,
        )
    }
    val name = { id: Long -> ex.first { it.id == id }.specs.first { it.name.equals(BANDS_SPEC.name, ignoreCase = true) }.name }
    val s = sets.map { set ->
        if (set.bands.isEmpty()) set
        else if (set.exerciseId !in ids) set.copy(bands = emptyList()) // bands on an exercise without the field: nowhere to show them
        else set.copy(bands = emptyList(), specs = set.specs + (name(set.exerciseId) to set.bands.sortedBy { it.ordinal }.joinToString("+") { it.name.lowercase() }))
    }
    return ex to s
}

/** Every conversion of old gym data (assist flag, built-in bands), in order. */
fun upgradeGym(exercises: List<Exercise>, sets: List<GymSet>): Pair<List<Exercise>, List<GymSet>> =
    withNegativeAssist(exercises, sets).let { (e, s) -> withBandsAsSpec(e, s) }
