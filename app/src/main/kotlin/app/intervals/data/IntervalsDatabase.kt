package app.intervals.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database, a file on the phone (no account, no network).
 * Schema history is exported to app/schemas/ so future versions can migrate without losing data.
 */
@Database(
    entities = [
        WorkoutEntity::class, SessionEntity::class, LoggedValueEntity::class, SettingsEntity::class,
        ExerciseEntity::class, GymSetEntity::class, GymGroupEntity::class, GymDayNoteEntity::class,
    ],
    version = 9,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9)],
    exportSchema = true,
)
abstract class IntervalsDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun sessions(): SessionDao
    abstract fun settings(): SettingsDao
    abstract fun gym(): GymDao

    companion object {
        fun create(context: Context): IntervalsDatabase =
            Room.databaseBuilder(context.applicationContext, IntervalsDatabase::class.java, "intervals.db").build()
    }
}
