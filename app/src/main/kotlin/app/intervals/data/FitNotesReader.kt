package app.intervals.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import app.intervals.core.FitNotesImport
import java.io.File

class NotFitNotesException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads a FitNotes backup (`.fitnotes` = a SQLite file), read-only, into what [FitNotesImport] needs. */
object FitNotesReader {

    fun read(file: File): FitNotesImport.FnData {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            throw NotFitNotesException("This is not a FitNotes backup (.fitnotes).", e)
        }
        return try {
            db.use { readFrom(it) }
        } catch (e: SQLiteException) { // e.g. "file is not a database", only found at the first query
            throw NotFitNotesException("This is not a FitNotes backup (.fitnotes).", e)
        }
    }

    private fun readFrom(db: SQLiteDatabase): FitNotesImport.FnData {
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        if (!tables.containsAll(listOf("exercise", "training_log"))) {
            throw NotFitNotesException("This is not a FitNotes backup (.fitnotes).")
        }
        val exercises = db.rawQuery(
            "SELECT _id, name, exercise_type_id, notes, weight_increment, default_rest_time FROM exercise", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    FitNotesImport.FnExercise(
                        id = c.getLong(0),
                        name = c.getString(1),
                        typeId = c.getInt(2),
                        notes = if (c.isNull(3)) null else c.getString(3),
                        weightIncrement = if (c.isNull(4)) null else c.getDouble(4),
                        restSec = if (c.isNull(5)) null else c.getInt(5),
                    )
                )
            }
        }
        val sets = db.rawQuery(
            "SELECT _id, exercise_id, date, metric_weight, reps, distance, duration_seconds FROM training_log", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    FitNotesImport.FnSet(
                        id = c.getLong(0),
                        exerciseId = c.getLong(1),
                        date = c.getString(2),
                        weightKg = c.getDouble(3),
                        reps = c.getInt(4),
                        distance = c.getDouble(5),
                        durationSec = c.getInt(6),
                    )
                )
            }
        }
        // owner type 1 = a comment on a set (owner_id = training_log._id)
        val comments = if ("Comment" !in tables) emptyMap() else
            db.rawQuery("SELECT owner_id, comment FROM Comment WHERE owner_type_id = 1", null).use { c ->
                buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
            }
        val (step, rest) = if ("settings" !in tables) null to null else
            db.rawQuery("SELECT weight_increment, rest_timer_seconds FROM settings LIMIT 1", null).use { c ->
                if (!c.moveToFirst()) null to null
                else (if (c.isNull(0)) null else c.getDouble(0)) to (if (c.isNull(1)) null else c.getInt(1))
            }
        return FitNotesImport.FnData(exercises, sets, comments, step, rest)
    }
}
