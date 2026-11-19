package app.intervals.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.intervals.core.Band
import app.intervals.core.FitNotesImport
import app.intervals.core.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GymImportTest {
    private lateinit var db: IntervalsDatabase
    private lateinit var repo: IntervalsRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IntervalsDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = IntervalsRepository(db) { 1_000L }
    }

    @After
    fun close() = db.close()

    /** A tiny FitNotes-shaped SQLite file. */
    private fun fakeFitNotes(): File {
        val f = File.createTempFile("fitnotes", ".fitnotes").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            d.execSQL("CREATE TABLE exercise(_id INTEGER PRIMARY KEY, name TEXT, category_id INTEGER, exercise_type_id INTEGER, notes TEXT, weight_increment INTEGER, default_rest_time INTEGER)")
            d.execSQL("CREATE TABLE training_log(_id INTEGER PRIMARY KEY, exercise_id INTEGER, date DATE, metric_weight INTEGER, reps INTEGER, distance INTEGER, duration_seconds INTEGER)")
            d.execSQL("CREATE TABLE Comment(_id INTEGER PRIMARY KEY, date DATE, owner_type_id INTEGER, owner_id INTEGER, comment TEXT)")
            d.execSQL("CREATE TABLE settings(_id INTEGER PRIMARY KEY, weight_increment INTEGER, rest_timer_seconds INTEGER)")
            d.execSQL("INSERT INTO settings VALUES (1, 2.5, 120)")
            d.execSQL("INSERT INTO exercise VALUES (1, 'Pinch (Left)', 11, 0, NULL, 1, NULL), (2, 'Pinch (Right)', 11, 0, NULL, 1, NULL), (3, 'Pistol Squat', 6, 0, NULL, NULL, NULL)")
            d.execSQL("INSERT INTO training_log VALUES (1, 2, '2026-11-17', 20.5, 5, 0, 0), (2, 1, '2026-11-17', 18, 5, 0, 0), (3, 3, '2026-11-16', 0, 5, 0, 0)")
            d.execSQL("INSERT INTO Comment VALUES (1, '2026-11-16', 1, 3, 'L 🟢🟣🟣🔴')")
        }
        return f
    }

    @Test
    fun importReplacesGymAndSurvivesBackup() = runTest {
        val r = FitNotesImport.convert(FitNotesReader.read(fakeFitNotes()))
        assertEquals(listOf("Pinch", "Pistol Squat"), r.exercises.map { it.name })
        assertEquals(120, r.exercises[0].restSec)
        assertEquals(3, repo.replaceGym(r))
        repo.saveGymDayNote("2026-11-17", "  Good form  ")
        // importing again replaces, never doubles
        repo.replaceGym(r)
        assertEquals(3, repo.gymSetCount())
        assertEquals("Good form", repo.gymDayNote("2026-11-17").first())

        assertEquals(listOf("2026-11-17", "2026-11-16"), repo.gymDays.first())
        val day = repo.gymDay("2026-11-17").first()
        assertEquals(listOf(Side.RIGHT, Side.LEFT), day.map { it.side })
        assertEquals(20.5, day[0].weightKg)
        val pistol = repo.gymDay("2026-11-16").first().single()
        assertEquals("green+purple+purple+red", pistol.specs["Bands"])
        assertEquals(pistol, repo.lastSets(pistol.exerciseId, "2026-11-17").single())

        // backup and restore carry the gym
        val text = repo.exportBackup()
        repo.saveGymDayNote("2026-11-17", "   ")
        assertEquals("", repo.gymDayNote("2026-11-17").first())
        repo.saveGymDayNote("2026-11-18", "Another day")
        repo.replaceGym(FitNotesImport.convert(FitNotesImport.FnData(emptyList(), emptyList(), emptyMap())))
        assertEquals(0, repo.gymSetCount())
        assertEquals(3, repo.restoreBackup(text).gymSets)
        assertEquals(day, repo.gymDay("2026-11-17").first())
        assertEquals(r.exercises, repo.exercises.first().sortedBy { it.id })
        assertEquals("Good form", repo.gymDayNote("2026-11-17").first())
        assertEquals("", repo.gymDayNote("2026-11-18").first())
    }

    @Test
    fun oldDataIsUpgradedOnce() = runTest {
        // what the phone had before: an assisted exercise (positive weights) and one with built-in bands
        val oap = repo.addExercise(app.intervals.core.Exercise(name = "Deloaded OAP", fields = listOf(app.intervals.core.GymField.WEIGHT, app.intervals.core.GymField.REPS), assist = true))
        val pistol = repo.addExercise(app.intervals.core.Exercise(name = "Pistol", fields = listOf(app.intervals.core.GymField.REPS, app.intervals.core.GymField.BANDS)))
        repo.addSets(listOf(
            app.intervals.core.GymSet(exerciseId = oap, day = "2026-11-17", order = 0, weightKg = 20.0, reps = 3),
            app.intervals.core.GymSet(exerciseId = pistol, day = "2026-11-17", order = 0, reps = 5, bands = listOf(Band.RED, Band.GREEN)),
        ))
        assertEquals(4, repo.upgradeGym())
        assertEquals(-20.0, repo.exerciseDay(oap, "2026-11-17").first().single().weightKg)
        val p = repo.exerciseDay(pistol, "2026-11-17").first().single()
        assertEquals("green+red", p.specs["Bands"])
        assertEquals(emptyList(), p.bands)
        val ex = repo.exercises.first().associateBy { it.id }
        assertEquals(listOf(app.intervals.core.GymField.REPS), ex.getValue(pistol).fields)
        assertEquals(false, ex.getValue(oap).assist)
        // the second time there is nothing to do
        assertEquals(0, repo.upgradeGym())
    }

    @Test
    fun loggingSetsAndExercises() = runTest {
        repo.replaceGym(FitNotesImport.convert(FitNotesReader.read(fakeFitNotes())))
        val id = repo.addExercise(app.intervals.core.Exercise(name = "Hip Thrust", fields = listOf(app.intervals.core.GymField.WEIGHT, app.intervals.core.GymField.REPS)))
        val s = app.intervals.core.GymSet(exerciseId = id, day = "2026-11-17", order = 0, weightKg = 60.0, reps = 8, restSec = 125)
        val ids = repo.addSets(listOf(s, s.copy(weightKg = 70.0)))
        // after the two imported sets of that day
        assertEquals(listOf(2, 3), repo.exerciseDay(id, "2026-11-17").first().map { it.order })
        assertEquals("2026-11-17", repo.lastUsed.first()[id])

        repo.updateSet(repo.exerciseDay(id, "2026-11-17").first()[0].copy(note = "Max"))
        repo.deleteSet(ids[1])
        val left = repo.exerciseDay(id, "2026-11-17").first().single()
        assertEquals("Max", left.note)
        assertEquals(125, left.restSec)
        assertEquals(4, repo.gymSetCount())
    }

    @Test
    fun groupsAndBackup() = runTest {
        val f = listOf(app.intervals.core.GymField.WEIGHT, app.intervals.core.GymField.REPS)
        val a = repo.addExercise(app.intervals.core.Exercise(name = "Deep Squat", fields = f))
        val b = repo.addExercise(app.intervals.core.Exercise(name = "Deadlift", fields = f))
        val gid = repo.addGroup(app.intervals.core.GymGroup(name = "Legs", items = listOf(app.intervals.core.GroupItem(a), app.intervals.core.GroupItem(b, 4))))
        repo.updateGroup(repo.groups.first().single().copy(name = "Legs A"))
        assertEquals(listOf("Legs A"), repo.groups.first().map { it.name })
        assertEquals(4, repo.groups.first().single().items[1].planSets)
        val text = repo.exportBackup()
        repo.deleteGroup(gid)
        assertEquals(0, repo.groups.first().size)
        repo.restoreBackup(text)
        assertEquals(listOf("Legs A"), repo.groups.first().map { it.name })
    }

    @Test
    fun mergeAndDeleteExercises() = runTest {
        val f = listOf(app.intervals.core.GymField.WEIGHT, app.intervals.core.GymField.REPS)
        val a = repo.addExercise(app.intervals.core.Exercise(name = "Block Pulls", fields = f))
        val b = repo.addExercise(app.intervals.core.Exercise(name = "Block Pull (5sec)", fields = f))
        val s = app.intervals.core.GymSet(exerciseId = b, day = "2026-03-02", order = 0, weightKg = 40.0, reps = 7, note = "5 s")
        repo.addSets(listOf(s, s.copy(weightKg = 42.0)))
        repo.addSets(listOf(s.copy(exerciseId = a, day = "2026-03-03")))
        repo.mergeExercises(from = b, into = a)
        assertEquals(3, repo.exerciseAll(a).first().size)
        assertEquals(listOf("Block Pulls"), repo.exercises.first().map { it.name })
        assertEquals(3 to "2026-03-02", repo.exerciseCounts.first()[a])
        repo.updateExercise(repo.exercises.first().single().copy(name = "Block Pull 20mm", weightStep = 1.0))
        assertEquals(1.0, repo.exercises.first().single().weightStep)
        repo.deleteExercise(a)
        assertEquals(0, repo.gymSetCount())
    }

    @Test
    fun otherFilesAreRefused() {
        val f = File.createTempFile("notfitnotes", ".fitnotes").apply { writeText("hello") }
        assertFailsWith<NotFitNotesException> { FitNotesReader.read(f) }
        val empty = File.createTempFile("othersqlite", ".db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(empty, null).use { it.execSQL("CREATE TABLE t(a)") }
        assertFailsWith<NotFitNotesException> { FitNotesReader.read(empty) }
    }

    /** Runs only with -Pfitnotes=/path/to/backup.fitnotes */
    @Test
    fun realBackupIfProvided() = runTest {
        val path = System.getProperty("fitnotes") ?: return@runTest
        val r = FitNotesImport.convert(FitNotesReader.read(File(path)))
        assertEquals(r.sets.size, repo.replaceGym(r))
        assertTrue(r.exercises.isNotEmpty())
        println(
            "FitNotes: ${r.days} days, ${r.sets.size} sets, ${r.exercises.size} exercises, ${r.skippedExercises} skipped; " +
                "pairs ${r.pairs}; one-sided ${r.oneSided}; comments: side ${r.sideComments}, bands ${r.bandComments}, notes ${r.noteComments}"
        )
        r.exercises.forEach { println("  ${it.name}: ${it.fields}${if (it.perSide) " per side" else ""}${if (it.assist) " assist" else ""}, step ${it.weightStep}, rest ${it.restSec}") }
        r.sets.filter { it.note.isNotEmpty() }.map { it.note }.distinct().forEach { println("  note: $it") }
    }
}
