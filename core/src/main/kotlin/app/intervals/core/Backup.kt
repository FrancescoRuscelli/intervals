package app.intervals.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
        if (backup.groups.any { g -> g.items.any { it.exerciseId !in exIds } }) throw BackupException("The backup file is damaged: a group has an unknown exercise.")
        return backup
    }

    fun encodeWorkout(workout: Workout): String = compact.encodeToString(Workout.serializer(), workout)
    fun decodeWorkout(json: String): Workout = compact.decodeFromString(Workout.serializer(), json)

    fun encodeExercise(exercise: Exercise): String = compact.encodeToString(Exercise.serializer(), exercise)
    fun decodeExercise(json: String): Exercise = compact.decodeFromString(Exercise.serializer(), json)

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
