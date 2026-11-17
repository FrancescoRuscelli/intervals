package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {
    private val hang = Series(
        "hang", 7, 0xFFE91E63L, SeriesIcon.JUMP, perSide = PerSide(5),
        logFields = listOf(LogField("Load", "kg", leftRightApart = true), LogField("Edge", "mm")),
    )
    private val rest = Series("rest", 3, 0xFFFFC107L, beepLast3 = false)
    private val workout = Workout(
        name = "hangboard 70%", warmupSec = 5, rounds = 4, restBetweenRoundsSec = 60,
        blocks = listOf(Block.Repeat(3, listOf(hang, rest)), Block.Single(hang)), tags = setOf("Hangboard"),
    )
    private val session = Session(
        id = 7, workoutId = 1, workoutName = "hangboard 70%", startedAt = 1_794_900_000_000, durationSec = 325,
        roundsDone = 2, roundsTotal = 4, completed = false, note = "fingers tired",
        values = listOf(
            LoggedValue("hang", "Load", "kg", 1, Side.LEFT, 10.0),
            LoggedValue("hang", "Load", "kg", 1, Side.RIGHT, 8.0),
            LoggedValue("hang", "Load", "kg", 2, Side.LEFT, 12.5),
            LoggedValue("hang", "Load", "kg", 2, Side.RIGHT, 10.0),
            LoggedValue("hang", "Edge", "mm", 1, null, 20.0),
        ),
    )
    private val backup = Backup(
        exportedAt = 1_794_900_500_000,
        workouts = listOf(SavedWorkout(1, 0, workout, 1_794_000_000_000, 1_794_100_000_000)),
        sessions = listOf(session),
        settings = AppSettings(theme = ThemeMode.DARK, discAnimation = DiscAnimation.CRAZY_SPIN, volume = 0.5f),
    )

    @Test
    fun backupRoundTrips() {
        val text = BackupJson.encode(backup)
        assertTrue("\"format\": \"intervals-backup\"" in text, "readable, with its format name")
        assertEquals(backup, BackupJson.decode(text))
    }

    @Test
    fun workoutRoundTripsWithRepeatGroupsPerSideAndLogFields() {
        assertEquals(workout, BackupJson.decodeWorkout(BackupJson.encodeWorkout(workout)))
    }

    @Test
    fun importedWorkoutsSurviveABackup() {
        val imported = HybridImport.parse(javaClass.getResource("/hybrid_sample.csv")!!.readText()).workouts
        val b = Backup(
            exportedAt = 0,
            workouts = imported.mapIndexed { i, iw -> SavedWorkout(i + 1L, i, iw.workout, 0, 0) },
            sessions = emptyList(),
        )
        assertEquals(b, BackupJson.decode(BackupJson.encode(b)))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = BackupJson.encode(backup).replaceFirst("{", "{\n  \"addedLater\": 42,")
        assertEquals(backup, BackupJson.decode(text))
    }

    @Test
    fun refusesOtherFilesAndNewerVersions() {
        assertFailsWith<BackupException> { BackupJson.decode("hello") }
        assertFailsWith<BackupException> { BackupJson.decode("""{"some": "json"}""") }
        assertFailsWith<BackupException> { BackupJson.decode(BackupJson.encode(backup.copy(format = "other"))) }
        val newer = assertFailsWith<BackupException> { BackupJson.decode(BackupJson.encode(backup.copy(version = 99))) }
        assertTrue("newer version" in newer.message!!)
        // invalid content (a series of 0 s) is reported, not crashed on
        val broken = BackupJson.encode(backup).replace("\"durationSec\": 3,", "\"durationSec\": 0,")
        assertFailsWith<BackupException> { BackupJson.decode(broken) }
        val twice = backup.copy(workouts = backup.workouts + backup.workouts)
        assertFailsWith<BackupException> { BackupJson.decode(BackupJson.encode(twice)) }
    }

    @Test
    fun brokenSettingsFallBackToDefaults() {
        assertEquals(AppSettings(), BackupJson.decodeSettings(null))
        assertEquals(AppSettings(), BackupJson.decodeSettings("not json"))
        val s = AppSettings(lockControls = true)
        assertEquals(s, BackupJson.decodeSettings(BackupJson.encodeSettings(s)))
    }

    @Test
    fun lastValuesKeepTheLastRoundPerSeriesFieldAndSide() {
        val last = session.lastValues().associate { (it.field to it.side) to it.value }
        assertEquals(mapOf(("Load" to Side.LEFT) to 12.5, ("Load" to Side.RIGHT) to 10.0, ("Edge" to null) to 20.0), last)
    }
}
