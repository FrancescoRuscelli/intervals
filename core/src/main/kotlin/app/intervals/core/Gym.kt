package app.intervals.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Resistance bands, strongest first (the order they are shown in). */
@Serializable
enum class Band { GREEN, PURPLE, BLACK, RED, YELLOW }

/** What is written down for each set of an exercise. */
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
) {
    init {
        require(name.isNotBlank()) { "a specification needs a name" }
        require(step > 0) { "step must be > 0" }
    }

    val isChoice: Boolean get() = choices.isNotEmpty()

    /** "40 mm", "half crimp". */
    fun text(value: String): String = if (isChoice || unit.isBlank()) value else "$value $unit"
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
) {
    init {
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
    val bands: List<Band> = emptyList(),
    val note: String = "",
    /** Real rest before this set, seconds; null when unknown (e.g. imported). */
    val restSec: Int? = null,
    /** Spec name → value as shown ("40" for a number, "half crimp" for a choice). */
    val specs: Map<String, String> = emptyMap(),
) {
    init {
        LocalDate.parse(day) // throws on a bad date
        require(order >= 0) { "order must be >= 0" }
        require(reps == null || reps >= 0) { "reps must be >= 0" }
        require(timeSec == null || timeSec >= 0) { "timeSec must be >= 0" }
    }
}

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

/** A group of exercises you start together, e.g. "Legs". */
@Serializable
data class GymGroup(
    val id: Long = 0,
    val name: String,
    val items: List<GroupItem>,
    /** 0xAARRGGBB used in the Journal day strip; null = picked from [GROUP_COLORS] by position. */
    val color: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "a group needs a name" }
    }
}

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
