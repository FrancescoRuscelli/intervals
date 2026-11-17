package app.intervals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's database, a file on the phone (no account, no network).
 * Schema history is exported to app/schemas/ so future versions can migrate without losing data.
 */
@Database(
    entities = [WorkoutEntity::class, SessionEntity::class, LoggedValueEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IntervalsDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun sessions(): SessionDao
    abstract fun settings(): SettingsDao

    companion object {
        fun create(context: Context): IntervalsDatabase =
            Room.databaseBuilder(context.applicationContext, IntervalsDatabase::class.java, "intervals.db").build()
    }
}
