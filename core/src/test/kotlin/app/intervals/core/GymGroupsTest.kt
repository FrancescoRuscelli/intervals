package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GymGroupsTest {
    private val f = listOf(GymField.WEIGHT, GymField.REPS)
    private val sq = Exercise(1, "Deep Squat", f)
    private val dl = Exercise(2, "Deadlift", f)
    private val bg = Exercise(3, "Bulgarian Bar Squat", f)
    private val ohp = Exercise(4, "Overhead Press", f)
    private val pike = Exercise(5, "Box Pike Push Up", listOf(GymField.REPS))
    private fun day(d: String, vararg ids: Long) = ids.mapIndexed { i, id -> GymSet(exerciseId = id, day = d, order = i * 3, weightKg = 10.0, reps = 5) } +
        GymSet(exerciseId = ids[0], day = d, order = 1, weightKg = 10.0, reps = 5) // the first exercise has more sets

    @Test
    fun suggestsFrequentCombinations() {
        val sets = listOf(
            day("2026-11-04", 1, 3, 2), day("2026-11-11", 1, 3, 2), day("2026-11-18", 1, 3, 2), // legs ×3
            day("2026-10-28", 2, 1), // deadlift + squat once: inside legs anyway
            day("2026-11-06", 4, 5), day("2026-11-13", 4, 5), day("2026-11-20", 4, 5), // shoulders ×3
            day("2026-11-01", 4, 2), day("2026-11-02", 4, 2), // only twice: not suggested
        ).flatten()
        val g = suggestGroups(sets, listOf(sq, dl, bg, ohp, pike))
        assertEquals(listOf("Deep Squat day", "Overhead Press day"), g.map { it.name })
        assertEquals(listOf(1L, 3L, 2L), g[0].items.map { it.exerciseId }) // usual order
        assertEquals(listOf(4L, 5L), g[1].items.map { it.exerciseId })
    }

    @Test
    fun planLabels() {
        assertEquals("no plan", GroupItem(1).planLabel())
        assertEquals("plan: 4 sets", GroupItem(1, 4).planLabel())
        assertEquals("plan: 1 set · 3 × 8 @ 40 kg", GroupItem(1, 1, "3 × 8 @ 40 kg").planLabel())
    }

    @Test
    fun groupRunSuggestsFirstExerciseBelowItsPlan() {
        val items = listOf(GroupItem(1), GroupItem(2, 2), GroupItem(3))
        val sets = day("2026-11-18", 1, 2, 3).groupBy { it.exerciseId }
        assertEquals(items[0], nextGroupItem(items, emptyMap()))
        assertEquals(items[1], nextGroupItem(items, sets))
        val deadlift = sets.getValue(2L).single()
        assertEquals(null, nextGroupItem(items, sets + (2L to listOf(deadlift, deadlift.copy(order = 9)))))
    }

    @Test
    fun stripColorsAndRecords() {
        val legs = GymGroup(1, "Legs", listOf(GroupItem(1), GroupItem(2)))
        val sh = GymGroup(2, "Shoulders", listOf(GroupItem(4)), color = 0xFF123456)
        val workout = GymGroup(3, "Wednesday", listOf(GroupItem(1), GroupItem(4)), isTag = false)
        val sets = day("2026-11-17", 1, 4, 2) + GymSet(exerciseId = 9, day = "2026-11-17", order = 9, reps = 5)
        val groups = listOf(workout, legs, sh)
        val strip = dayStrip(sets, groups)
        assertEquals(listOf(1L, 4L, 2L, 9L), strip.map { it.exerciseId })
        assertEquals(listOf(2, 1, 1, 1), strip.map { it.sets })
        assertEquals(listOf(GROUP_COLORS[0], 0xFF123456, GROUP_COLORS[0], NO_GROUP_COLOR), strip.map { it.color })
        assertEquals(listOf("Legs", "Shoulders"), dayGroups(sets, groups).map { it.name })
        val history = day("2026-11-10", 1) + day("2026-11-17", 1).map { it.copy(weightKg = 20.0) }
        assertEquals(1, recordsOn("2026-11-17", history, listOf(sq)))
        assertEquals(0, recordsOn("2026-11-10", history, listOf(sq)))
        // the set that made it, so the Journal can put the cup on its bar
        val numbered = history.mapIndexed { i, s -> s.copy(id = i + 1L) }
        val made = recordSetsOn("2026-11-17", numbered, listOf(sq))
        assertEquals(listOf("2026-11-17"), made.map { it.day })
        assertEquals(listOf(true), dayBars(numbered.filter { it.day == "2026-11-17" }, sq, made.map { it.id }.toSet()).map { it.record }.distinct().filter { it })
    }

    @Test
    fun pastRecordsKnowTheDayTheyWereBeaten() {
        // Pinch, real: left 12 → 13.75 (5 Apr) → 15 (21 Apr) → 16 (7 Jul); right 17 → 21 (7 Jul)
        val pinch = Exercise(38, "Pinch", listOf(GymField.WEIGHT, GymField.REPS), perSide = true)
        var id = 0L
        val s = { d: String, side: Side, kg: Double -> GymSet(id = ++id, exerciseId = 38, day = d, order = id.toInt(), side = side, weightKg = kg, reps = 1) }
        val all = listOf(
            s("2026-01-31", Side.LEFT, 12.0), s("2026-01-31", Side.RIGHT, 17.0),
            s("2026-04-05", Side.LEFT, 13.75), s("2026-04-21", Side.LEFT, 15.0), s("2026-04-27", Side.LEFT, 15.0),
            s("2026-07-07", Side.RIGHT, 21.0), s("2026-07-07", Side.LEFT, 16.0),
        )
        assertEquals(mapOf(3L to "2026-04-21"), recordMarksOn("2026-04-05", all, listOf(pinch)))
        assertEquals(mapOf(4L to "2026-07-07"), recordMarksOn("2026-04-21", all, listOf(pinch)))
        // a tie is not a record
        assertEquals(emptyMap(), recordMarksOn("2026-04-27", all, listOf(pinch)))
        // both hands' best now: no day beat them
        assertEquals(mapOf(6L to null, 7L to null), recordMarksOn("2026-07-07", all, listOf(pinch)))
        val bars = dayBars(all.filter { it.day == "2026-04-05" }, pinch, setOf(3L), setOf(3L))
        assertEquals(listOf(true), bars.map { it.beaten })
    }
}
