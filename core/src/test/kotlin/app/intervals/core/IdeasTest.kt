package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Warm-ups, records per variant and spec, the next-load hint (mockup intervals-ideas-from-other-apps.html, 1, 5, 6). */
class IdeasTest {
    private val squat = Exercise(1, "Squat", listOf(GymField.WEIGHT, GymField.REPS), variants = listOf("deep", "half", "normal"))
    private val pinch = Exercise(2, "Pinch", listOf(GymField.WEIGHT, GymField.REPS), perSide = true, weightStep = 1.0,
        specs = listOf(Spec("grip", choices = listOf("normal", "thumb"))))
    private var id = 0L
    private fun s(ex: Exercise, day: String, kg: Double, reps: Int, variant: String? = null, side: Side? = null, note: String = "", grip: String? = null) =
        GymSet(++id, ex.id, day, id.toInt(), side, kg, reps, note = note, variant = variant, specs = grip?.let { mapOf("grip" to it) } ?: emptyMap())

    @Test
    fun warmUpsAreNeverRecordsAndNotCounted() {
        val a = s(squat, "2026-11-01", 80.0, 5)
        val warm = s(squat, "2026-11-08", 100.0, 5, note = "Warm-up")
        val b = s(squat, "2026-11-08", 85.0, 5)
        assertTrue(warm.isWarmup)
        assertEquals(listOf(b), recordSets(squat, "2026-11-08", listOf(a, warm, b)))
        assertEquals(listOf(80.0, 85.0), metricSeries(GymMetric.MAX_WEIGHT, listOf(a, warm, b)).map { it.value })
        assertEquals(2, exerciseTraining(listOf(a, warm, b)).single().sets)
        assertEquals("", s(squat, "2026-11-08", 1.0, 1, note = "Warm-up").noteWithoutWarmup)
        assertEquals("knee", s(squat, "2026-11-08", 1.0, 1, note = "Warm-up · knee").noteWithoutWarmup)
        assertTrue(dayBars(listOf(warm, b), squat).let { it[0].warmup && !it[0].note && !it[1].warmup })
    }

    @Test
    fun recordsAreKeptPerVariantAndChoice() {
        val deep = s(squat, "2026-11-01", 100.0, 5, "deep")
        val half = s(squat, "2026-11-08", 90.0, 5, "half")
        val half2 = s(squat, "2026-11-15", 95.0, 5, "half")
        // 95 half beats 90 half, even though 100 deep is heavier; the first half day is not a record (nothing to beat)
        assertEquals(emptyList(), recordSets(squat, "2026-11-08", listOf(deep, half)))
        assertEquals(listOf(half2), recordSets(squat, "2026-11-15", listOf(deep, half, half2)))
        // a set without a grip counts as the first choice (normal); thumb has its own records
        val n1 = s(pinch, "2026-04-05", 14.0, 1, side = Side.LEFT)
        val t1 = s(pinch, "2026-04-12", 10.0, 1, side = Side.LEFT, grip = "thumb")
        val t2 = s(pinch, "2026-04-19", 11.0, 1, side = Side.LEFT, grip = "thumb")
        val n2 = s(pinch, "2026-04-26", 15.0, 1, side = Side.LEFT, grip = "normal")
        val all = listOf(n1, t1, t2, n2)
        assertEquals(listOf(t2), recordSets(pinch, "2026-04-19", all))
        assertEquals(listOf(n2), recordSets(pinch, "2026-04-26", all))
        assertEquals(mapOf(t2.id to null), recordMarksOn("2026-04-19", all, listOf(pinch)))
    }

    @Test
    fun nextLoadHint() {
        val day1 = listOf(s(pinch, "2026-07-07", 16.0, 3, side = Side.LEFT), s(pinch, "2026-07-07", 21.0, 3, side = Side.RIGHT))
        val day2 = listOf(s(pinch, "2026-07-14", 16.0, 3, side = Side.LEFT), s(pinch, "2026-07-14", 20.0, 3, side = Side.RIGHT))
        assertEquals(LoadHint(16.0, 17.0, 3, listOf("2026-07-07", "2026-07-14")), nextLoadHint(day1 + day2, pinch, "2026-07-21", Side.LEFT, null))
        // the right went down, so nothing to suggest there
        assertNull(nextLoadHint(day1 + day2, pinch, "2026-07-21", Side.RIGHT, null))
        // fewer reps at the same weight, or a failed set: no hint
        val fewer = listOf(s(pinch, "2026-07-14", 16.0, 2, side = Side.LEFT))
        assertNull(nextLoadHint(day1 + fewer, pinch, "2026-07-21", Side.LEFT, null))
        val failed = listOf(s(pinch, "2026-07-14", 16.0, 3, side = Side.LEFT, note = "Failed"))
        assertNull(nextLoadHint(day1 + failed, pinch, "2026-07-21", Side.LEFT, null))
        // only one day: no hint
        assertNull(nextLoadHint(day1, pinch, "2026-07-21", Side.LEFT, null))
        // assisted goes towards less help
        val pull = Exercise(3, "Deloaded One Arm Pull-Up", listOf(GymField.WEIGHT, GymField.REPS))
        val p = listOf(s(pull, "2026-01-01", -20.0, 5), s(pull, "2026-01-08", -20.0, 6))
        assertEquals(-17.5, nextLoadHint(p, pull, "2026-01-15", null, null)?.toKg)
    }
}

class IntervalIdeasTest {
    private fun session(day: Int, load: Double, completed: Boolean = true, letGo: List<LetGo> = emptyList(), side: Side? = null) = Session(
        workoutId = 1, workoutName = "max hang 20mm", startedAt = day * 86_400_000L, durationSec = 600, roundsDone = 2, roundsTotal = 2,
        completed = completed, letGo = letGo, values = listOf(LoggedValue("hang", "Load", "kg", 1, side, load), LoggedValue("hang", "Load", "kg", 2, side, load)),
    )
    private val dayOf = { ms: Long -> java.time.LocalDate.ofEpochDay(ms / 86_400_000L).toString() }

    @Test
    fun loadHintAfterTwoCleanSessionsAtTheSameLoad() {
        assertEquals(listOf(11.0), intervalLoadHints(listOf(session(2, 10.0), session(1, 10.0))).map { it.value })
        assertEquals(emptyList(), intervalLoadHints(listOf(session(2, 10.0), session(1, 9.0))))
        assertEquals(emptyList(), intervalLoadHints(listOf(session(2, 10.0, completed = false), session(1, 10.0))))
        val let = listOf(LetGo("hang", 2, 5, heldSec = 5))
        assertEquals(emptyList(), intervalLoadHints(listOf(session(2, 10.0, letGo = let), session(1, 10.0))))
    }

    @Test
    fun letGoText() {
        val s = session(1, 10.0, letGo = listOf(LetGo("hang", 2, 5, heldSec = 5))).copy(workDone = 12)
        assertEquals("hang 11/12 · let go in round 2", s.letGoText())
        assertNull(session(1, 10.0).letGoText())
    }

    @Test
    fun progressPointsAndRecords() {
        val sessions = listOf(session(1, 8.0), session(8, 10.0), session(15, 9.0), session(22, 12.0))
        val key = valueKeys(sessions).single()
        val pts = valueSeries(sessions, key, dayOf)
        assertEquals(listOf(8.0, 10.0, 9.0, 12.0), pts.map { it.value })
        assertEquals(listOf(false, true, false, true), pts.map { it.record })
        val recs = valueRecords(pts)
        assertEquals(listOf(12.0, 10.0, 8.0), recs.map { it.value })
        assertEquals(listOf(null, pts[3].day, pts[1].day), recs.map { it.beatenOn })
    }

    @Test
    fun choicesShowTheirText() {
        val a = Session(workoutName = "w", startedAt = 0, durationSec = 1, roundsDone = 1, roundsTotal = 1, completed = true,
            values = listOf(LoggedValue("hang", "Grip", "", 1, null, 1.0, "half crimp")))
        assertEquals("hang · Grip half crimp", a.valuesLine())
        val b = a.copy(values = listOf(LoggedValue("hang", "Grip", "", 1, null, 0.0, "open")))
        assertEquals("Grip half crimp" to 0, valuesChange(a, b))
    }
}

class MergeVariantTest {
    @Test
    fun suggestsTheMissingWords() {
        assertEquals("box", variantSuggestion("Box Pike Push Up", "Pike Push Up"))
        assertEquals("barbell", variantSuggestion("Barbell Squat", "Squat"))
        assertEquals("second half", variantSuggestion("Pull Up (Second Half)", "Pull Up"))
        assertNull(variantSuggestion("Squat", "squat"))
    }
}

class VariantFieldsTest {
    private val wr = listOf(GymField.WEIGHT, GymField.REPS)
    private val oap = Exercise(25, "Deloaded One Arm Pull-Up", wr, perSide = true, variants = listOf("full", "90° hold"),
        variantFields = mapOf("90° hold" to listOf(GymField.WEIGHT, GymField.TIME)))
    private fun pull(id: Long, day: String, kg: Double, reps: Int, side: Side) = GymSet(id, 25, day, id.toInt(), side, kg, reps, variant = "full")
    private fun hold(id: Long, day: String, kg: Double, sec: Int) = GymSet(id, 25, day, id.toInt(), null, kg, timeSec = sec, variant = "90° hold")

    @Test
    fun eachVariantShowsWhatItRecords() {
        assertEquals(wr, oap.fieldsOf("full"))
        assertEquals(wr, oap.fieldsOf(null))
        assertEquals(listOf(GymField.WEIGHT, GymField.TIME), oap.fieldsOf("90° hold"))
        assertEquals(listOf(GymField.WEIGHT), oap.commonFields())
        val p = pull(1, "2026-01-02", -20.0, 5, Side.LEFT)
        val h = hold(2, "2026-01-02", 0.0, 10)
        assertEquals("5", p.boxText(oap))
        assertEquals("0:10", h.boxText(oap))
        assertEquals("90° hold · 0 × 0:10", h.summary(oap))
        assertEquals(listOf(5.0, 10.0), dayBars(listOf(p, h), oap).map { it.value })
    }

    @Test
    fun setsWithoutASideHaveTheirOwnRecords() {
        val all = listOf(hold(1, "2025-09-30", 0.0, 5), hold(2, "2025-10-07", 5.0, 5), pull(3, "2025-10-07", -20.0, 5, Side.LEFT), pull(4, "2025-10-14", -17.5, 5, Side.LEFT))
        // the hold at 5 kg beats the hold at 0; the pull at −17.5 beats −20 on the left
        assertEquals(listOf(2L), recordSets(oap, "2025-10-07", all).map { it.id })
        assertEquals(listOf(4L), recordSets(oap, "2025-10-14", all).map { it.id })
        assertEquals(listOf<Any?>(Side.LEFT, Side.RIGHT, NoSide), sidesOf(oap, all))
        assertEquals(mapOf(2L to null), recordMarksOn("2025-10-07", all, listOf(oap)))
    }
}

class VariantGroupsTest {
    private val help = Spec("Help", choices = listOf("none", "band", "deload"))
    private val bands = BANDS_SPEC.copy(onlyWhen = "Help=band")
    private val oap = Exercise(68, "One Arm Pull Up", listOf(GymField.WEIGHT, GymField.REPS), perSide = true,
        variants = listOf("full", "first half", "90° hold"), variantsName = "Range", specs = listOf(help, bands))

    @Test
    fun bandColorsOnlyWithBand() {
        assertEquals(listOf("Help"), oap.shownSpecs(emptyMap(), "full").map { it.name })
        assertEquals(listOf("Help", "Bands"), oap.shownSpecs(mapOf("Help" to "band"), "first half").map { it.name })
        val rangeOnly = oap.copy(specs = listOf(help.copy(onlyWhen = "Range=first half")))
        assertEquals(emptyList(), rangeOnly.shownSpecs(emptyMap(), "full").map { it.name })
        assertEquals(listOf("Help"), rangeOnly.shownSpecs(emptyMap(), "first half").map { it.name })
    }

    @Test
    fun combinationsKeepTheirOwnRecords() {
        fun s(id: Long, day: String, kg: Double, v: String, sp: Map<String, String> = emptyMap()) =
            GymSet(id, 68, day, id.toInt(), Side.LEFT, kg, 3, variant = v, specs = sp)
        val all = listOf(s(1, "2026-01-01", 0.0, "first half"), s(2, "2026-01-08", 0.0, "first half", mapOf("Help" to "band", "Bands" to "black")),
            s(3, "2026-01-15", 2.5, "first half", mapOf("Help" to "band", "Bands" to "black")), s(4, "2026-01-15", -20.0, "full", mapOf("Help" to "deload")))
        // the black band day 2 is not a record (nothing before it in that combination); 2.5 kg with black beats it on day 3
        assertEquals(emptyList(), recordSets(oap, "2026-01-08", all))
        assertEquals(listOf(3L), recordSets(oap, "2026-01-15", all).map { it.id })
    }
}
