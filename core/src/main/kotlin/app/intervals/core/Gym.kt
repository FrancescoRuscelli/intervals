package app.intervals.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Resistance bands, strongest first (the order they are shown in). */
@Serializable
enum class Band { GREEN, PURPLE, BLACK, RED, YELLOW }

/** What is written down for each set of an exercise. */
@Serializable
enum class GymField { WEIGHT, REPS, TIME, DISTANCE, BANDS }

/** A strength exercise you created (no preset list, no categories). */
@Serializable
data class Exercise(
    val id: Long = 0,
    val name: String,
    val fields: List<GymField>,
    /** Each set is done on one side (L or R); one exercise, never separate "(Left)" / "(Right)" ones. */
    val perSide: Boolean = false,
    /** The weight is taken off (e.g. a deloaded pull-up), shown as −. */
    val assist: Boolean = false,
    /** Step of the − / + buttons for the weight, kg. */
    val weightStep: Double = 2.5,
    /** Rest target, seconds: the rest clock buzzes here and keeps counting. */
    val restSec: Int = 180,
    val notes: String = "",
) {
    init {
        require(name.isNotBlank()) { "an exercise needs a name" }
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
    /** kg; for an assist exercise, the weight taken off (stored positive). */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val timeSec: Int? = null,
    /** As FitNotes stored it; unit not used yet. */
    val distance: Double? = null,
    val bands: List<Band> = emptyList(),
    val note: String = "",
    /** Real rest before this set, seconds; null when unknown (e.g. imported). */
    val restSec: Int? = null,
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
) {
    init {
        require(name.isNotBlank()) { "a group needs a name" }
    }
}
