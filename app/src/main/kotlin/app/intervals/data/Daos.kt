package app.intervals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY position, id")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY position, id")
    suspend fun getAll(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workouts")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertAll(workouts: List<WorkoutEntity>): List<Long>

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("UPDATE workouts SET position = position + 1 WHERE position >= :from")
    suspend fun shiftDownFrom(from: Int)

    @Query("UPDATE workouts SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    fun observeBetween(from: Long, to: Long): Flow<List<SessionWithValues>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun getAll(): List<SessionWithValues>

    /** The most recent session of a workout that has logged values. */
    @Transaction
    @Query(
        """SELECT * FROM sessions WHERE workoutId = :workoutId
           AND id IN (SELECT sessionId FROM logged_values) ORDER BY startedAt DESC LIMIT 1"""
    )
    suspend fun lastWithValues(workoutId: Long): SessionWithValues?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Insert
    suspend fun insertValues(values: List<LoggedValueEntity>)

    @Query("UPDATE sessions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT json FROM settings WHERE id = 1")
    fun observe(): Flow<String?>

    @Query("SELECT json FROM settings WHERE id = 1")
    suspend fun get(): String?

    @Upsert
    suspend fun put(settings: SettingsEntity)
}

@Dao
interface GymDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM gym_sets WHERE day = :day ORDER BY position, id")
    fun observeDay(day: String): Flow<List<GymSetEntity>>

    /** Days with sets, newest first. */
    @Query("SELECT DISTINCT day FROM gym_sets ORDER BY day DESC")
    fun observeDays(): Flow<List<String>>

    /** The sets of an exercise on its last day before [day]. */
    @Query(
        """SELECT * FROM gym_sets WHERE exerciseId = :exerciseId AND day =
           (SELECT MAX(day) FROM gym_sets WHERE exerciseId = :exerciseId AND day < :day) ORDER BY position, id"""
    )
    suspend fun lastBefore(exerciseId: Long, day: String): List<GymSetEntity>

    /** Days with sets in [from, to) (yyyy-MM-dd), e.g. one month for the calendar. */
    @Query("SELECT DISTINCT day FROM gym_sets WHERE day >= :from AND day < :to")
    fun observeDaysBetween(from: String, to: String): Flow<List<String>>

    @Query("SELECT * FROM gym_sets ORDER BY day, position, id")
    suspend fun getSets(): List<GymSetEntity>

    @Query("SELECT * FROM gym_sets WHERE day >= :from ORDER BY day, position, id")
    fun observeSetsSince(from: String): Flow<List<GymSetEntity>>

    @Query("SELECT COUNT(*) FROM gym_sets")
    suspend fun countSets(): Int

    @Query("SELECT * FROM gym_sets WHERE exerciseId = :exerciseId ORDER BY day, position, id")
    fun observeExerciseAll(exerciseId: Long): Flow<List<GymSetEntity>>

    @Query("SELECT * FROM gym_sets WHERE exerciseId = :exerciseId AND day = :day ORDER BY position, id")
    fun observeExerciseDay(exerciseId: Long, day: String): Flow<List<GymSetEntity>>

    /** When each exercise was last done. */
    @Query("SELECT exerciseId, MAX(day) AS day FROM gym_sets GROUP BY exerciseId")
    fun observeLastUsed(): Flow<List<ExerciseLastDay>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM gym_sets WHERE day = :day")
    suspend fun nextPosition(day: String): Int

    @Insert
    suspend fun insertSet(set: GymSetEntity): Long

    @Update
    suspend fun updateSet(set: GymSetEntity)

    @Query("DELETE FROM gym_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insertSets(sets: List<GymSetEntity>)

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    /** Deletes the exercise; its sets go with it (cascade). */
    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    /** Moves every set of [from] to [into] (merge). */
    @Query("UPDATE gym_sets SET exerciseId = :into WHERE exerciseId = :from")
    suspend fun moveSets(from: Long, into: Long)

    @Query("SELECT exerciseId, COUNT(*) AS sets, MIN(day) AS firstDay FROM gym_sets GROUP BY exerciseId")
    fun observeCounts(): Flow<List<ExerciseCount>>
}

data class ExerciseLastDay(val exerciseId: Long, val day: String)

data class ExerciseCount(val exerciseId: Long, val sets: Int, val firstDay: String)
