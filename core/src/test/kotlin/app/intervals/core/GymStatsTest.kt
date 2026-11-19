package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GymStatsTest {
    private val dl = Exercise(1, "Deadlift", listOf(GymField.WEIGHT, GymField.REPS))
    private fun s(day: String, w: Double?, r: Int?, side: Side? = null, t: Int? = null, order: Int = 0, rest: Int? = null) =
        GymSet(exerciseId = 1, day = day, order = order, side = side, weightKg = w, reps = r, timeSec = t, restSec = rest)

    private val sets = listOf(
        s("2026-10-20", 110.0, 5, order = 0), s("2026-10-20", 120.0, 1, order = 1),
        s("2026-10-28", 120.0, 5, order = 0), s("2026-10-28", 100.0, 5, order = 1),
        s("2026-11-11", 110.0, 5, order = 0),
    )

    @Test
    fun metricsFitTheExercise() {
        assertEquals(listOf(GymMetric.MAX_WEIGHT, GymMetric.VOLUME, GymMetric.MAX_REPS, GymMetric.TOTAL_REPS, GymMetric.REST), metricsFor(dl))
        val hang = Exercise(2, "Hang", listOf(GymField.WEIGHT, GymField.TIME))
        assertEquals(listOf(GymMetric.MAX_WEIGHT, GymMetric.MAX_TIME, GymMetric.REST), metricsFor(hang))
        val hold = Exercise(3, "Handstand", listOf(GymField.TIME))
        assertEquals(listOf(GymMetric.MAX_TIME, GymMetric.REST), metricsFor(hold))
    }

    @Test
    fun seriesWithRecords() {
        val e = metricSeries(GymMetric.MAX_WEIGHT, sets)
        assertEquals(listOf("2026-10-20", "2026-10-28", "2026-11-11"), e.map { it.day })
        assertEquals(listOf(120.0, 120.0, 110.0), e.map { it.value })
        // equalling the best is not a record
        assertEquals(listOf(false, false, false), e.map { it.record })
        val vol = metricSeries(GymMetric.VOLUME, sets)
        assertEquals(listOf(670.0, 1100.0, 550.0), vol.map { it.value })
    }

    @Test
    fun assistRecordsGoDown() {
        // assisted = negative weights: −20 beats −25 as the heaviest
        val a = listOf(s("2026-01-01", -25.0, 3), s("2026-01-05", -20.0, 3), s("2026-01-09", -22.5, 3))
        val pts = metricSeries(GymMetric.MAX_WEIGHT, a)
        assertEquals(listOf(false, true, false), pts.map { it.record })
    }

    @Test
    fun sidesApart() {
        val p = listOf(s("2026-03-10", 47.0, 5, Side.RIGHT), s("2026-03-10", 10.0, 5, Side.LEFT), s("2026-07-07", 50.0, 5, Side.LEFT))
        assertEquals(listOf(10.0, 50.0), metricSeries(GymMetric.MAX_WEIGHT, p, Side.LEFT).map { it.value })
        assertEquals(listOf(47.0), metricSeries(GymMetric.MAX_WEIGHT, p, Side.RIGHT).map { it.value })
    }

    @Test
    fun dayRecordsUseEachSidesBestSetAndExcludeTies() {
        val previous = "2026-11-17"
        val day = "2026-11-18"
        val bestLeft = s(day, 12.0, 5, Side.LEFT, order = 1)
        val history = listOf(
            s(previous, 10.0, 5, Side.LEFT), s(previous, 15.0, 5, Side.RIGHT),
            s(day, 11.0, 5, Side.LEFT), bestLeft, bestLeft.copy(order = 2), s(day, 15.0, 5, Side.RIGHT, order = 3),
        )
        val exercise = dl.copy(perSide = true)
        assertEquals(listOf(bestLeft), recordSets(exercise, day, history))
        assertEquals(emptyList(), recordSets(exercise, previous, history))
        assertEquals(emptyList(), recordSets(dl.copy(fields = listOf(GymField.DISTANCE)), day, history))
    }

    @Test
    fun repMaxes() {
        val rm = repMaxes(sets)
        assertEquals(listOf(1, 5), rm.map { it.reps })
        assertTrue(rm.first { it.reps == 1 }.beaten) // 120 × 5 beats 120 × 1
        assertFalse(rm.first { it.reps == 5 }.beaten)
    }

    @Test
    fun trendAndHistory() {
        val (a, b) = trend(listOf(1.0, 2.0, 3.0))!!
        assertEquals(1.0, a, 0.001)
        assertEquals(3.0, b, 0.001)
        assertEquals(listOf("2026-11-11", "2026-10-28", "2026-10-20"), historyDays(sets).map { it.first })
        val rest = metricSeries(GymMetric.REST, listOf(s("2026-01-01", 1.0, 1, rest = 100), s("2026-01-01", 1.0, 1, rest = 200)))
        assertEquals(150.0, rest.single().value, 0.001)
    }

    @Test
    fun gymOverview() {
        val weeks = gymDaysPerWeek(listOf("2026-11-16", "2026-11-17", "2026-11-04"), 3, java.time.LocalDate.parse("2026-11-18"))
        assertEquals(listOf(1, 0, 2), weeks.map { it.second })
        // records are real ones now: the heaviest weight
        val rec = recentRecords(listOf(dl), sets + s("2026-11-11", 125.0, 1, order = 1), "2026-10-25")
        assertEquals(listOf("2026-11-11"), rec.map { it.day })
        assertEquals(GymMetric.MAX_WEIGHT, rec.single().metric)
        assertEquals(listOf(1L to 5), setsPerExercise(sets))
    }
}
