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
}
