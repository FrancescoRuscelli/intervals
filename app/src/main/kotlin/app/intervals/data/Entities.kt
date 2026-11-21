package app.intervals.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A workout. Its content (blocks, series, tags...) is stored as JSON in [json] (see BackupJson.encodeWorkout):
 * it is always read and written whole, and the list is small, so no need for one table per level.
 */
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Place in the list, 0 first. */
    val position: Int,
    val name: String,
    val json: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        // deleting a workout keeps its sessions in the journal (with the name), just unlinked
        ForeignKey(WorkoutEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("startedAt"), Index("workoutId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val durationSec: Int,
    val roundsDone: Int,
    val roundsTotal: Int,
    val completed: Boolean,
    val note: String,
)

@Entity(
    tableName = "logged_values",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class LoggedValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val series: String,
    val field: String,
    val unit: String,
    val round: Int,
    /** "LEFT", "RIGHT" or null for both sides. */
    val side: String?,
    val value: Double,
)

/** One row (id 1) with all the settings as JSON. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
)

data class SessionWithValues(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val values: List<LoggedValueEntity>,
)

// ---- gym ----

/** A gym exercise; its settings (fields, per side, step, rest…) as JSON, like workouts. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val json: String,
)

@Entity(
    tableName = "gym_sets",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId"), Index("day")],
)
data class GymSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    /** yyyy-MM-dd */
    val day: String,
    val position: Int,
    /** "LEFT", "RIGHT" or null. */
    val side: String?,
    val weightKg: Double?,
    val reps: Int?,
    val timeSec: Int?,
    val distance: Double?,
    /** Band names separated by commas, e.g. "GREEN,PURPLE,PURPLE"; empty = none. */
    val bands: String,
    val note: String,
    val restSec: Int?,
    /** Your specifications as JSON, e.g. {"Width":"40"}; empty = none (added in v5). */
    @androidx.room.ColumnInfo(defaultValue = "") val specs: String = "",
    /** The exercise variant, e.g. "deep"; null = the exercise's first (added in v6). */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val variant: String? = null,
    /** Kept for compatibility with the v7 database already installed on the phone. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val sessionName: String? = null,
)

/** A category tag or workout group (name + ordered exercises with optional plans, as JSON). */
@Entity(tableName = "gym_groups")
data class GymGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val name: String,
    val json: String,
)

@Entity(tableName = "gym_day_notes")
data class GymDayNoteEntity(@PrimaryKey val day: String, val note: String)
