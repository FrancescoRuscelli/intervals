package app.intervals.data

import androidx.room.withTransaction
import app.intervals.core.AppSettings
import app.intervals.core.Backup
import app.intervals.core.BackupJson
import app.intervals.core.LoggedValue
import app.intervals.core.SavedWorkout
import app.intervals.core.Session
import app.intervals.core.Side
import app.intervals.core.Workout
import app.intervals.core.lastValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a restore brought back, to show the user. */
data class RestoreResult(val workouts: Int, val sessions: Int)

/** The only door to the stored data: the screens and the player use this, never the DAOs. */
class IntervalsRepository(
    private val db: IntervalsDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val workoutDao = db.workouts()
    private val sessionDao = db.sessions()
    private val settingsDao = db.settings()

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
        }
        return RestoreResult(backup.workouts.size, backup.sessions.size)
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
