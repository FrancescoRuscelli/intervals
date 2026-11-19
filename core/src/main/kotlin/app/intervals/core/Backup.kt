package app.intervals.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Everything the app stores, as written to a backup file. */
@kotlinx.serialization.Serializable
data class Backup(
    val format: String = BackupJson.FORMAT,
    val version: Int = BackupJson.VERSION,
    /** Epoch milliseconds. */
    val exportedAt: Long,
    val workouts: List<SavedWorkout>,
    val sessions: List<Session>,
    val settings: AppSettings = AppSettings(),
    /** Gym log; missing in backups made before the gym existed. */
    val exercises: List<Exercise> = emptyList(),
    val gymSets: List<GymSet> = emptyList(),
    /** Groups of exercises; missing in older backups. */
    val groups: List<GymGroup> = emptyList(),
    val gymDayNotes: Map<String, String> = emptyMap(),
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON for backups (readable, versioned) and for storing one workout or the settings in the database.
 * Unknown keys are ignored, so a backup from a newer minor change still opens; a newer [VERSION] is refused.
 */
object BackupJson {
    const val FORMAT = "intervals-backup"
    const val VERSION = 1

    private val pretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: Backup): String = pretty.encodeToString(Backup.serializer(), backup)

    /** Reads a backup file. Throws [BackupException] with a message for the user when it can't. */
    fun decode(text: String): Backup {
        val backup = try {
            pretty.decodeFromString(Backup.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This is not an Intervals backup file.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("The backup file is damaged: ${e.message}", e)
        }
        if (backup.format != FORMAT) throw BackupException("This is not an Intervals backup file.")
        if (backup.version > VERSION) {
            throw BackupException("This backup was made by a newer version of Intervals. Update the app first.")
        }
        val ids = backup.workouts.map { it.id }
        if (ids.size != ids.toSet().size) throw BackupException("The backup file is damaged: two workouts have the same id.")
        val exIds = backup.exercises.map { it.id }.toSet()
        if (exIds.size != backup.exercises.size) throw BackupException("The backup file is damaged: two exercises have the same id.")
        if (backup.gymSets.any { it.exerciseId !in exIds }) throw BackupException("The backup file is damaged: a set has no exercise.")
        // backups from before negative weights: assisted exercises get negative weights
        val (exercises, gymSets) = withNegativeAssist(backup.exercises, backup.gymSets)
        if (exercises !== backup.exercises) return backup.copy(exercises = exercises, gymSets = gymSets)
        if (backup.groups.any { g -> g.items.any { it.exerciseId !in exIds } }) throw BackupException("The backup file is damaged: a group has an unknown exercise.")
        try {
            backup.gymDayNotes.keys.forEach { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            throw BackupException("The backup file is damaged: a gym note has an invalid date.", e)
        }
        return backup
    }

    fun encodeWorkout(workout: Workout): String = compact.encodeToString(Workout.serializer(), workout)
    fun decodeWorkout(json: String): Workout = compact.decodeFromString(Workout.serializer(), json)

    fun encodeExercise(exercise: Exercise): String = compact.encodeToString(Exercise.serializer(), exercise)
    fun decodeExercise(json: String): Exercise = compact.decodeFromString(Exercise.serializer(), json)

    /** A set's specifications for the database; empty map = "". */
    fun encodeSpecs(specs: Map<String, String>): String =
        if (specs.isEmpty()) "" else compact.encodeToString(MapSerializer(String.serializer(), String.serializer()), specs)

    fun decodeSpecs(json: String): Map<String, String> =
        if (json.isBlank()) emptyMap() else compact.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)

    fun encodeSettings(settings: AppSettings): String = compact.encodeToString(AppSettings.serializer(), settings)

    /** Settings that can't be read (e.g. corrupted) fall back to the defaults instead of breaking the app. */
    fun decodeSettings(json: String?): AppSettings =
        if (json == null) AppSettings()
        else try {
            compact.decodeFromString(AppSettings.serializer(), json)
        } catch (e: SerializationException) {
            AppSettings()
        } catch (e: IllegalArgumentException) {
            AppSettings()
        }
}
