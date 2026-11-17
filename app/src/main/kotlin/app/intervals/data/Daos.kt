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
