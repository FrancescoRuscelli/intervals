package app.intervals.data

import app.intervals.core.suggestGroups
import app.intervals.core.GymGroup
import androidx.room.withTransaction
import app.intervals.core.AppSettings
import app.intervals.core.Backup
import app.intervals.core.BackupJson
import app.intervals.core.Band
import app.intervals.core.Exercise
import app.intervals.core.FitNotesImport
import app.intervals.core.GymSet
import app.intervals.core.LoggedValue
import app.intervals.core.SavedWorkout
import app.intervals.core.Session
import app.intervals.core.Side
import app.intervals.core.Workout
import app.intervals.core.lastValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a restore brought back, to show the user. */
data class RestoreResult(val workouts: Int, val sessions: Int, val gymSets: Int = 0)

/** The only door to the stored data: the screens and the player use this, never the DAOs. */
class IntervalsRepository(
    private val db: IntervalsDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val workoutDao = db.workouts()
    private val sessionDao = db.sessions()
    private val settingsDao = db.settings()
    private val gymDao = db.gym()

    // ---- workouts ----

    val workouts: Flow<List<SavedWorkout>> = workoutDao.observeAll().map { list -> list.map { it.toSaved() } }

    suspend fun workout(id: Long): SavedWorkout? = workoutDao.get(id)?.toSaved()

    /** Adds at the end of the list; returns the new id. */
    suspend fun addWorkout(workout: Workout): Long = db.withTransaction {
        val t = now()
        workoutDao.insert(workout.toEntity(position = workoutDao.nextPosition(), createdAt = t, updatedAt = t))
    }

    suspend fun updateWorkout(id: Long, workout: Workout) {
        val old = workoutDao.get(id) ?: return
        workoutDao.update(workout.toEntity(old.position, old.createdAt, now(), id))
    }

    /** Sessions of a deleted workout stay in the journal, with its name. */
    suspend fun deleteWorkout(id: Long) = workoutDao.delete(id)

    /** Puts a copy right below the original, named "… (copy)"; returns the copy's id. */
    suspend fun duplicateWorkout(id: Long): Long? = db.withTransaction {
        val original = workoutDao.get(id) ?: return@withTransaction null
        val copy = BackupJson.decodeWorkout(original.json).let { it.copy(name = it.name + " (copy)") }
        workoutDao.shiftDownFrom(original.position + 1)
        val t = now()
        workoutDao.insert(copy.toEntity(original.position + 1, t, t))
    }

    /** Saves the list order, e.g. after a drag; [ids] in their new order. */
    suspend fun reorderWorkouts(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { i, id -> workoutDao.setPosition(id, i) }
    }

    /** Adds imported workouts at the end, in their order; returns their ids. */
    suspend fun importWorkouts(workouts: List<Workout>): List<Long> = db.withTransaction {
        val start = workoutDao.nextPosition()
        val t = now()
        workoutDao.insertAll(workouts.mapIndexed { i, w -> w.toEntity(start + i, t, t) })
    }

    /**
     * Import again: a workout with the same name that was never edited in the app is updated in place
     * (same id, place and journal links); the others are added at the end. Returns (added, updated).
     */
    suspend fun importOrUpdate(workouts: List<Workout>): Pair<Int, Int> = db.withTransaction {
        val existing = workoutDao.getAll().filter { it.createdAt == it.updatedAt }.toMutableList()
        val toAdd = mutableListOf<Workout>()
        var updated = 0
        val t = now()
        for (w in workouts) {
            val old = existing.firstOrNull { it.name == w.name }
            if (old != null) {
                existing.remove(old)
                // keep createdAt == updatedAt: it still counts as "not edited by you"
                workoutDao.update(w.toEntity(old.position, t, t, old.id))
                updated++
            } else {
                toAdd += w
            }
        }
        val start = workoutDao.nextPosition()
        workoutDao.insertAll(toAdd.mapIndexed { i, w -> w.toEntity(start + i, t, t) })
        toAdd.size to updated
    }

    // ---- journal ----

    /** Sessions started in [fromMillis, toMillis), e.g. one month for the calendar. */
    fun sessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<Session>> =
        sessionDao.observeBetween(fromMillis, toMillis).map { list -> list.map { it.toSession() } }

    /** Logs a session with its values; returns its id. */
    suspend fun addSession(session: Session): Long = db.withTransaction {
        val id = sessionDao.insert(session.toEntity(id = 0))
        sessionDao.insertValues(session.values.map { it.toEntity(id) })
        id
    }

    suspend fun setNote(sessionId: Long, note: String) = sessionDao.setNote(sessionId, note.trim())

    suspend fun deleteSession(sessionId: Long) = sessionDao.delete(sessionId)

    /** Values to start the next session of this workout from: the last round of the last session that logged any. */
    suspend fun lastValues(workoutId: Long): List<LoggedValue> =
        sessionDao.lastWithValues(workoutId)?.toSession()?.lastValues().orEmpty()

    // ---- gym ----

    val exercises: Flow<List<Exercise>> = gymDao.observeExercises().map { list -> list.map { it.toExercise() } }

    /** Days with sets, newest first (yyyy-MM-dd). */
    val gymDays: Flow<List<String>> = gymDao.observeDays()

    fun gymDaysBetween(from: String, to: String): Flow<List<String>> = gymDao.observeDaysBetween(from, to)

    fun gymDay(day: String): Flow<List<GymSet>> = gymDao.observeDay(day).map { list -> list.map { it.toSet() } }

    fun gymDayNote(day: String): Flow<String> = gymDao.observeDayNote(day).map { it.orEmpty() }

    suspend fun saveGymDayNote(day: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) gymDao.deleteDayNote(day)
        else gymDao.putDayNote(GymDayNoteEntity(day, trimmed))
    }

    /** The sets of [exerciseId] on the last day it was done before [day]. */
    suspend fun lastSets(exerciseId: Long, day: String): List<GymSet> = gymDao.lastBefore(exerciseId, day).map { it.toSet() }

    /** Gym sets in [from, to) (yyyy-MM-dd), e.g. one month for the calendar strips. */
    fun gymSetsBetween(from: String, to: String): Flow<List<GymSet>> = gymDao.observeSetsBetween(from, to).map { list -> list.map { it.toSet() } }

    /** All gym sets from [from] (yyyy-MM-dd) on, for Stats. */
    fun gymSetsSince(from: String): Flow<List<GymSet>> = gymDao.observeSetsSince(from).map { list -> list.map { it.toSet() } }

    /** Every set of one exercise, oldest first: history, graphs, records. */
    fun exerciseAll(exerciseId: Long): Flow<List<GymSet>> = gymDao.observeExerciseAll(exerciseId).map { list -> list.map { it.toSet() } }

    fun exerciseDay(exerciseId: Long, day: String): Flow<List<GymSet>> =
        gymDao.observeExerciseDay(exerciseId, day).map { list -> list.map { it.toSet() } }

    /** Exercise id → the last day it was done. */
    val lastUsed: Flow<Map<Long, String>> = gymDao.observeLastUsed().map { list -> list.associate { it.exerciseId to it.day } }

    /** Adds sets at the end of their day, in order; returns their ids. */
    suspend fun addSets(sets: List<GymSet>): List<Long> = db.withTransaction {
        sets.map { s -> gymDao.insertSet(s.copy(id = 0, order = gymDao.nextPosition(s.day)).toEntity()) }
    }

    suspend fun updateSet(set: GymSet) = gymDao.updateSet(set.toEntity())

    suspend fun setGymNotes(ids: List<Long>, note: String) = gymDao.setNotes(ids, note.trim())

    suspend fun deleteSets(ids: List<Long>) = gymDao.deleteSets(ids)

    /** Returns the new exercise's id. */
    suspend fun addExercise(exercise: Exercise): Long = gymDao.insertExercise(exercise.copy(id = 0).toEntity())

    suspend fun updateExercise(exercise: Exercise) = gymDao.updateExercise(exercise.toEntity())

    /**
     * Converts old gym data once (see `upgradeGym` in core): assisted exercises get negative weights, built-in bands become the
     * "Bands" specification. Only what changed is written; nothing to do the second time. Returns the rows changed.
     */
    suspend fun upgradeGym(): Int = db.withTransaction {
        val ex = gymDao.getExercises().map { it.toExercise() }
        if (ex.none { it.assist || app.intervals.core.GymField.BANDS in it.fields }) return@withTransaction 0
        val sets = gymDao.getSets().map { it.toSet() }
        val (ex2, sets2) = app.intervals.core.upgradeGym(ex, sets)
        val exChanged = ex2.zip(ex).filter { (a, b) -> a != b }.map { it.first }
        val setsChanged = sets2.zip(sets).filter { (a, b) -> a != b }.map { it.first }
        exChanged.forEach { gymDao.updateExercise(it.toEntity()) }
        setsChanged.forEach { gymDao.updateSet(it.toEntity()) }
        exChanged.size + setsChanged.size
    }

    // ---- gym groups ----

    val groups: Flow<List<GymGroup>> = gymDao.observeGroups().map { list -> list.map { it.toGroup() } }

    suspend fun addGroup(group: GymGroup): Long {
        require(group.name.isNotBlank()) { "a group needs a name" }
        return gymDao.insertGroup(group.copy(id = 0).toEntity(gymDao.nextGroupPosition()))
    }

    suspend fun updateGroup(group: GymGroup) = db.withTransaction {
        require(group.name.isNotBlank()) { "a group needs a name" }
        val pos = gymDao.getGroups().firstOrNull { it.id == group.id }?.position ?: gymDao.nextGroupPosition()
        gymDao.updateGroup(group.toEntity(pos))
    }

    suspend fun deleteGroup(id: Long) = gymDao.deleteGroup(id)

    /** Deletes the exercise and all its sets. */
    suspend fun deleteExercise(id: Long) = db.withTransaction {
        gymDao.deleteExercise(id)
        removeFromGroups(id)
    }

    private suspend fun removeFromGroups(id: Long) {
        for (e in gymDao.getGroups()) {
            val g = e.toGroup()
            if (g.items.none { it.exerciseId == id }) continue
            gymDao.updateGroup(g.copy(items = g.items.filterNot { it.exerciseId == id }).toEntity(e.position))
        }
    }

    /**
     * Saves an exercise whose variants may have changed: renamed variants carry their sets along ([renames] old → new),
     * and sets without a variant get what they counted as before ([wasFirst], the old first variant), so reordering is safe.
     */
    suspend fun saveExercise(exercise: Exercise, renames: Map<String, String>, wasFirst: String?) = db.withTransaction {
        renames.forEach { (old, new) -> if (old != new) gymDao.renameVariant(exercise.id, old, new) }
        val first = (wasFirst?.let { renames[it] ?: it }) ?: exercise.variants.firstOrNull()
        if (exercise.variants.isNotEmpty() && first != null) gymDao.fillVariant(exercise.id, first)
        gymDao.updateExercise(exercise.toEntity())
    }

    /** Sets per variant of an exercise (null = sets without one). */
    fun variantCounts(exerciseId: Long): Flow<Map<String?, Int>> =
        gymDao.observeVariantCounts(exerciseId).map { list -> list.associate { it.variant to it.sets } }

    /** Number of sets and first day of each exercise. */
    val exerciseCounts: Flow<Map<Long, Pair<Int, String>>> =
        gymDao.observeCounts().map { list -> list.associate { it.exerciseId to (it.sets to it.firstDay) } }

    suspend fun gymSetCount(): Int = gymDao.countSets()

    /** Replaces the whole gym log with an import (exercises keep the importer's ids). Returns the sets written. */
    suspend fun replaceGym(result: FitNotesImport.Result): Int = db.withTransaction {
        gymDao.deleteAllExercises() // sets go with them (cascade)
        gymDao.deleteAllGroups()
        gymDao.insertExercises(result.exercises.map { it.toEntity() })
        gymDao.insertSets(result.sets.map { it.toEntity() })
        // category tags suggested from the days: exercises done together often (rename or delete them freely)
        gymDao.insertGroups(suggestGroups(result.sets, result.exercises).mapIndexed { i, g -> g.toEntity(i) })
        result.sets.size
    }

    // ---- settings ----

    val settings: Flow<AppSettings> = settingsDao.observe().map { BackupJson.decodeSettings(it) }

    suspend fun saveSettings(settings: AppSettings) =
        settingsDao.put(SettingsEntity(json = BackupJson.encodeSettings(settings)))

    // ---- backup ----

    /** Everything as the text of a backup file. */
    suspend fun exportBackup(): String = db.withTransaction {
        BackupJson.encode(
            Backup(
                exportedAt = now(),
                workouts = workoutDao.getAll().map { it.toSaved() },
                sessions = sessionDao.getAll().map { it.toSession() },
                settings = BackupJson.decodeSettings(settingsDao.get()),
                exercises = gymDao.getExercises().map { it.toExercise() },
                gymSets = gymDao.getSets().map { it.toSet() },
                groups = gymDao.getGroups().map { it.toGroup() },
                gymDayNotes = gymDao.getDayNotes().associate { it.day to it.note },
            )
        )
    }

    /**
     * Replaces everything with the content of a backup file. The file is fully checked first:
     * if it can't be read ([app.intervals.core.BackupException]), nothing is changed.
     */
    suspend fun restoreBackup(text: String): RestoreResult {
        val backup = BackupJson.decode(text)
        val ids = backup.workouts.map { it.id }.toSet()
        db.withTransaction {
            sessionDao.deleteAll()
            workoutDao.deleteAll()
            workoutDao.insertAll(backup.workouts.map { it.workout.toEntity(it.position, it.createdAt, it.updatedAt, it.id) })
            for (s in backup.sessions) {
                // a session pointing to a workout that isn't in the file is kept, unlinked
                val linked = s.workoutId?.takeIf { it in ids }
                val id = sessionDao.insert(s.copy(workoutId = linked).toEntity(id = s.id))
                sessionDao.insertValues(s.values.map { it.toEntity(id) })
            }
            settingsDao.put(SettingsEntity(json = BackupJson.encodeSettings(backup.settings)))
            gymDao.deleteAllExercises()
            gymDao.insertExercises(backup.exercises.map { it.toEntity() })
            gymDao.insertSets(backup.gymSets.map { it.toEntity() })
            gymDao.deleteAllGroups()
            gymDao.insertGroups(backup.groups.mapIndexed { i, g -> g.toEntity(i) })
            gymDao.deleteAllDayNotes()
            gymDao.insertDayNotes(backup.gymDayNotes.map { (day, note) -> GymDayNoteEntity(day, note) })
        }
        return RestoreResult(backup.workouts.size, backup.sessions.size, backup.gymSets.size)
    }
}

// ---- mapping between the stored rows and the core model ----

private fun Workout.toEntity(position: Int, createdAt: Long, updatedAt: Long, id: Long = 0) =
    WorkoutEntity(id, position, name, BackupJson.encodeWorkout(this), createdAt, updatedAt)

private fun WorkoutEntity.toSaved() =
    SavedWorkout(id, position, BackupJson.decodeWorkout(json), createdAt, updatedAt)

private fun Session.toEntity(id: Long) =
    SessionEntity(id, workoutId, workoutName, startedAt, durationSec, roundsDone, roundsTotal, completed, note)

private fun LoggedValue.toEntity(sessionId: Long) =
    LoggedValueEntity(0, sessionId, series, field, unit, round, side?.name, value)

private fun SessionWithValues.toSession() = Session(
    id = session.id,
    workoutId = session.workoutId,
    workoutName = session.workoutName,
    startedAt = session.startedAt,
    durationSec = session.durationSec,
    roundsDone = session.roundsDone,
    roundsTotal = session.roundsTotal,
    completed = session.completed,
    note = session.note,
    values = values.sortedBy { it.id }.map {
        LoggedValue(it.series, it.field, it.unit, it.round, it.side?.let(Side::valueOf), it.value)
    },
)

private fun Exercise.toEntity() = ExerciseEntity(id, name, BackupJson.encodeExercise(this))

private val groupJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun GymGroup.toEntity(position: Int) = GymGroupEntity(id, position, name, groupJson.encodeToString(GymGroup.serializer(), this))

private fun GymGroupEntity.toGroup() = groupJson.decodeFromString(GymGroup.serializer(), json).copy(id = id, name = name)

private fun ExerciseEntity.toExercise() = BackupJson.decodeExercise(json).copy(id = id, name = name)

private fun GymSet.toEntity() = GymSetEntity(
    id, exerciseId, day, order, side?.name, weightKg, reps, timeSec, distance,
    bands.joinToString(",") { it.name }, note, restSec, BackupJson.encodeSpecs(specs), variant,
)

private fun GymSetEntity.toSet() = GymSet(
    id = id, exerciseId = exerciseId, day = day, order = position, side = side?.let(Side::valueOf),
    weightKg = weightKg, reps = reps, timeSec = timeSec, distance = distance,
    bands = bands.split(',').filter(String::isNotEmpty).map(Band::valueOf), note = note, restSec = restSec,
    specs = BackupJson.decodeSpecs(specs), variant = variant,
)
