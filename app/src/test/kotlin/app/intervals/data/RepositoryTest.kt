package app.intervals.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.intervals.core.AppSettings
import app.intervals.core.BackupException
import app.intervals.core.Block
import app.intervals.core.DiscAnimation
import app.intervals.core.Exercise
import app.intervals.core.GroupItem
import app.intervals.core.GymField
import app.intervals.core.GymGroup
import app.intervals.core.LogField
import app.intervals.core.LoggedValue
import app.intervals.core.PerSide
import app.intervals.core.Series
import app.intervals.core.Session
import app.intervals.core.Side
import app.intervals.core.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real Room database in memory, on the PC. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepositoryTest {
    private lateinit var db: IntervalsDatabase
    private lateinit var repo: IntervalsRepository
    private var clock = 1_000L

    private val hang = Series("hang", 7, 0xFFE91E63L, perSide = PerSide(5), logFields = listOf(LogField("Load", "kg", true)))
    private val rest = Series("rest", 3, 0xFFFFC107L)
    private fun workout(name: String) =
        Workout(name, rounds = 3, restBetweenRoundsSec = 60, blocks = listOf(Block.Repeat(2, listOf(hang, rest)), Block.Single(hang)))

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), IntervalsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = IntervalsRepository(db) { clock++ }
    }

    @After
    fun close() = db.close()

    @Test
    fun retaggingExercisesPreservesWorkoutGroupsAndOtherMembers() = runTest {
        val fields = listOf(GymField.REPS)
        val squat = repo.addExercise(Exercise(name = "Squat", fields = fields))
        val press = repo.addExercise(Exercise(name = "Press", fields = fields))
        val dip = repo.addExercise(Exercise(name = "Dip", fields = fields))
        val planned = GroupItem(squat, planSets = 3)
        val tag = GymGroup(name = "Legs", items = listOf(planned, GroupItem(dip)), color = 0xFF8844CC)
        val workout = tag.copy(items = listOf(planned), isTag = false)
        val tagId = repo.addGroup(tag)
        val workoutId = repo.addGroup(workout)

        repeat(2) { repo.retagExercises(setOf(squat, press, Long.MAX_VALUE), setOf(" LEGS ", "Strength"), emptySet()) }
        repo.retagExercises(setOf(squat), emptySet(), setOf("Strength"))

        val groups = repo.groups.first()
        assertEquals(tag.copy(id = tagId, items = tag.items + GroupItem(press)), groups.first { it.id == tagId })
        assertEquals(workout.copy(id = workoutId), groups.first { it.id == workoutId })
        assertEquals(listOf(GroupItem(press)), groups.single { it.name == "Strength" }.items)
        assertEquals(3, groups.size)
    }

    private fun session(workoutId: Long?, startedAt: Long, values: List<LoggedValue> = emptyList()) = Session(
        workoutId = workoutId, workoutName = "hangboard", startedAt = startedAt, durationSec = 300,
        roundsDone = 3, roundsTotal = 3, completed = true, values = values,
    )

    @Test
    fun workoutsAreSavedInOrderAndDuplicatedBelowTheOriginal() = runTest {
        val a = repo.addWorkout(workout("a"))
        repo.addWorkout(workout("b"))
        val copy = repo.duplicateWorkout(a)!!
        assertEquals(listOf("a", "a (copy)", "b"), repo.workouts.first().map { it.workout.name })
        assertEquals(workout("a").blocks, repo.workout(copy)!!.workout.blocks)

        repo.reorderWorkouts(repo.workouts.first().map { it.id }.reversed())
        assertEquals(listOf("b", "a (copy)", "a"), repo.workouts.first().map { it.workout.name })

        repo.updateWorkout(a, workout("a").copy(rounds = 5))
        assertEquals(5, repo.workout(a)!!.workout.rounds)
        assertEquals(3, repo.workouts.first().size)
    }

    @Test
    fun importAddsAtTheEnd() = runTest {
        repo.addWorkout(workout("mine"))
        repo.importWorkouts(listOf(workout("old 1"), workout("old 2")))
        assertEquals(listOf("mine", "old 1", "old 2"), repo.workouts.first().map { it.workout.name })
    }

    @Test
    fun deletingAWorkoutKeepsItsSessions() = runTest {
        val id = repo.addWorkout(workout("hangboard"))
        repo.addSession(session(id, 5_000))
        repo.deleteWorkout(id)
        val s = repo.sessionsBetween(0, 10_000).first().single()
        assertNull(s.workoutId)
        assertEquals("hangboard", s.workoutName)
    }

    @Test
    fun lastValuesComeFromTheLastSessionThatLoggedAny() = runTest {
        val id = repo.addWorkout(workout("hangboard"))
        repo.addSession(session(id, 1_000, listOf(LoggedValue("hang", "Load", "kg", 1, Side.LEFT, 5.0))))
        repo.addSession(
            session(
                id, 2_000,
                listOf(
                    LoggedValue("hang", "Load", "kg", 1, Side.LEFT, 10.0),
                    LoggedValue("hang", "Load", "kg", 2, Side.LEFT, 12.5),
                    LoggedValue("hang", "Load", "kg", 2, Side.RIGHT, 10.0),
                ),
            )
        )
        repo.addSession(session(id, 3_000)) // nothing logged: ignored
        val last = repo.lastValues(id).associate { it.side to it.value }
        assertEquals(mapOf<Side?, Double>(Side.LEFT to 12.5, Side.RIGHT to 10.0), last)
    }

    @Test
    fun sessionsOfAMonthAndNotes() = runTest {
        val sid = repo.addSession(session(null, 5_000))
        repo.addSession(session(null, 50_000))
        repo.setNote(sid, "  felt strong  ")
        val inRange = repo.sessionsBetween(0, 10_000).first()
        assertEquals(listOf("felt strong"), inRange.map { it.note })
    }

    @Test
    fun settingsStartFromDefaults() = runTest {
        assertEquals(AppSettings(), repo.settings.first())
        repo.saveSettings(AppSettings(discAnimation = DiscAnimation.CRAZY_SPIN))
        assertEquals(DiscAnimation.CRAZY_SPIN, repo.settings.first().discAnimation)
    }

    @Test
    fun backupAndRestoreBringEverythingBack() = runTest {
        val a = repo.addWorkout(workout("a"))
        repo.addWorkout(workout("b"))
        repo.addSession(session(a, 1_000, listOf(LoggedValue("hang", "Load", "kg", 1, Side.LEFT, 10.0))).copy(note = "ok"))
        repo.saveSettings(AppSettings(lockControls = true))
        val file = repo.exportBackup()
        val before = Triple(repo.workouts.first(), repo.sessionsBetween(0, Long.MAX_VALUE).first(), repo.settings.first())

        // change everything, then restore
        repo.deleteWorkout(a)
        repo.addWorkout(workout("c"))
        repo.saveSettings(AppSettings())
        val result = repo.restoreBackup(file)

        assertEquals(RestoreResult(workouts = 2, sessions = 1), result)
        assertEquals(before, Triple(repo.workouts.first(), repo.sessionsBetween(0, Long.MAX_VALUE).first(), repo.settings.first()))
        // ids survive, so the journal still points to the workout
        assertEquals(a, repo.sessionsBetween(0, Long.MAX_VALUE).first().single().workoutId)
    }

    @Test
    fun aBadFileChangesNothing() = runTest {
        repo.addWorkout(workout("keep me"))
        assertFailsWith<BackupException> { repo.restoreBackup("""{"format":"intervals-backup","version":1}""") }
        assertFailsWith<BackupException> { repo.restoreBackup("not a backup") }
        assertTrue(repo.workouts.first().single().workout.name == "keep me")
    }

    @Test
    fun importAgainUpdatesOnlyUneditedWorkouts() = runTest {
        repo.importWorkouts(listOf(workout("a"), workout("b")))
        val b = repo.workouts.first().first { it.workout.name == "b" }
        repo.updateWorkout(b.id, b.workout.copy(rounds = 9)) // edited by the user
        val (added, updated) = repo.importOrUpdate(listOf(workout("a").copy(rounds = 5), workout("b"), workout("c")))
        assertEquals(2, added) // b (edited, so a new copy) and c
        assertEquals(1, updated)
        val all = repo.workouts.first()
        assertEquals(listOf("a", "b", "b", "c"), all.map { it.workout.name })
        assertEquals(5, all[0].workout.rounds)
        assertEquals(9, all[1].workout.rounds)
    }
}
