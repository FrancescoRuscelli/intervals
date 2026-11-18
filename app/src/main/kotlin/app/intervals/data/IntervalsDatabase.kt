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
        ExerciseEntity::class, GymSetEntity::class,
    ],
    version = 2,
    // 2: gym tables added (new tables only, so Room migrates by itself and keeps the data)
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
