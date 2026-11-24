package app.intervals.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalStatsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun s(name: String, date: String, hour: Int, sec: Int, done: Boolean = true, values: List<LoggedValue> = emptyList()) =
        Session(workoutName = name, startedAt = at(date, hour), durationSec = sec, roundsDone = 1, roundsTotal = 1, completed = done, values = values)

    private val list = listOf(
        s("hang", "2026-11-16", 18, 600),
        s("hang", "2026-11-16", 20, 300, done = false),
        s("hiit", "2026-11-10", 9, 1800),
        s("hang", "2026-11-02", 9, 330),
    )

    @Test
    fun summaryAndDays() {
        assertEquals(PeriodSummary(sessions = 4, days = 3, totalSec = 3030, completed = 3), summary(list, zone))
        assertEquals(LocalDate.parse("2026-11-16"), list[0].day(zone))
    }

    @Test
    fun weeksMondayFirst() {
        val weeks = minutesPerWeek(list, 3, LocalDate.parse("2026-11-17"), zone)
        assertEquals(listOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-16")), weeks.map { it.first })
        assertEquals(listOf(6, 30, 15), weeks.map { it.second })
        assertEquals(30, minutesPerDay(list, YearMonth.of(2026, 11), zone).size)
    }

    @Test
    fun top() {
        assertEquals(listOf(WorkoutCount("hang", 3, 1230), WorkoutCount("hiit", 1, 1800)), topWorkouts(list, 5))
    }

    @Test
    fun valuesLine() {
        val v = listOf(
            LoggedValue("hang", "Load", "kg", 1, Side.LEFT, 10.0),
            LoggedValue("hang", "Load", "kg", 1, Side.RIGHT, 8.0),
            LoggedValue("hang", "Load", "kg", 2, Side.LEFT, 12.5),
            LoggedValue("hang", "Edge", "mm", 1, null, 20.0),
        )
        assertEquals("hang · Load L 12.5 · R 8 kg · Edge 20 mm", s("hang", "2026-11-16", 18, 600, values = v).valuesLine())
    }

    @Test
    fun valuesChangeSaysWhatMovedAndWhatStayed() {
        fun session(load: Double, edge: Double, at: Long) = Session(workoutName = "Repeaters 7/3", startedAt = at, durationSec = 60, roundsDone = 1,
            roundsTotal = 1, completed = true, values = listOf(LoggedValue("Hang", "Edge", "mm", 1, null, edge), LoggedValue("Hang", "Load", "kg", 1, null, load)))
        assertEquals("Load +2.5 kg · same Edge" to 1, valuesChange(session(5.0, 20.0, 2), session(2.5, 20.0, 1)))
        assertEquals(null, valuesChange(session(5.0, 20.0, 2), session(5.0, 20.0, 1)))
        assertEquals(null, valuesChange(session(5.0, 20.0, 2), null))
        assertEquals(-1, valuesChange(session(0.0, 15.0, 2), session(5.0, 20.0, 1))?.second)
        // the presets are valid workouts
        assertEquals(3, PRESETS.count { it.section == "Fingers" })
    }
}
