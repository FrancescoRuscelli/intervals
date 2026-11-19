package app.intervals.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GymRowsTest {
    private val squat = Exercise(1, "Squat", listOf(GymField.WEIGHT, GymField.REPS))
    private fun set(kg: Double, reps: Int, side: Side? = null, note: String = "") =
        GymSet(exerciseId = 1, day = "2026-11-17", order = 0, side = side, weightKg = kg, reps = reps, note = note)

    @Test
    fun identicalSetsInARowBecomeOneRow() {
        val rows = dayRows(listOf(set(60.0, 5), set(80.0, 5), set(80.0, 5), set(80.0, 5), set(85.0, 3)))
        assertEquals(listOf("1" to 1, "2–4" to 3, "5" to 1), rows.map { it.label to it.count })
        assertEquals("85 × 3", topSet(rows.map { it.set }, squat)!!.summary(squat))
    }

    @Test
    fun sidesAndNotes() {
        val rows = dayRows(
            listOf(set(50.0, 5, Side.RIGHT), set(50.0, 5, Side.LEFT), set(50.0, 5, Side.RIGHT, "senza mignolo"), set(45.0, 5, Side.LEFT))
        )
        assertEquals(listOf("R L" to 2, "R" to 1, "L" to 1), rows.map { it.label to it.count })
        assertEquals("senza mignolo", rows[1].set.note)
    }

    @Test
    fun assistTopIsTheLeastHelp() {
        val oap = Exercise(2, "Deloaded One Arm Pull-Up", listOf(GymField.WEIGHT, GymField.REPS), perSide = true, assist = true)
        val sets = listOf(set(25.0, 3), set(20.0, 3), set(22.5, 3))
        assertEquals("−20 × 3", topSet(sets, oap)!!.summary(oap))
        assertEquals("1.25", formatKg(1.25))
        assertEquals("2:05", formatTime(125))
    }

    @Test
    fun specsComeFirstAndKeepRowsApart() {
        val pinch = Exercise(3, "Pinch", listOf(GymField.WEIGHT, GymField.REPS), perSide = true,
            specs = listOf(Spec("Width", "mm", step = 5.0), Spec("Grip", choices = listOf("open", "thumb"))))
        val a = set(20.0, 5).copy(specs = mapOf("Width" to "40", "Grip" to "thumb"))
        assertEquals("40 mm · thumb · 20 × 5", a.summary(pinch))
        // a spec not set on the set is simply left out
        assertEquals("40 mm · 20 × 5", a.copy(specs = mapOf("Width" to "40")).summary(pinch))
        // same weight and reps on another width: two rows, not ×2
        val rows = dayRows(listOf(a, a.copy(specs = mapOf("Width" to "50", "Grip" to "thumb")), a))
        assertEquals(listOf(1, 1, 1), rows.map { it.count })
        assertEquals(listOf(2), dayRows(listOf(a, a)).map { it.count })
    }

    @Test
    fun blocksByLoadPerSide() {
        val bp = Exercise(4, "Block Pulls", listOf(GymField.WEIGHT, GymField.REPS), perSide = true)
        val r = { kg: Double, n: Int -> set(kg, n, Side.RIGHT) }
        val l = { kg: Double, n: Int -> set(kg, n, Side.LEFT) }
        val day = listOf(r(50.0, 5), l(45.0, 5), r(50.0, 5), l(45.0, 4), r(52.0, 3), l(47.0, 3), r(52.0, 2))
        val right = loadBlocks(day.filter { it.side == Side.RIGHT }, bp)
        assertEquals(listOf("50 kg" to listOf("5", "5"), "52 kg" to listOf("3", "2")), right.map { it.label to it.sets.map { s -> s.boxText(bp) } })
        // time-only: the boxes are the times, no load
        val hs = Exercise(5, "Handstand", listOf(GymField.TIME))
        val t = GymSet(exerciseId = 5, day = "2026-11-17", order = 0, timeSec = 40)
        assertEquals(listOf("Sets" to listOf("0:40", "0:40")), loadBlocks(listOf(t, t), hs).map { it.label to it.sets.map { s -> s.boxText(hs) } })
        // assist and specs go in the label
        val oap = Exercise(6, "OAP", listOf(GymField.WEIGHT, GymField.REPS), assist = true, specs = listOf(Spec("Ring", choices = listOf("low", "high"))))
        assertEquals("high · −20 kg", set(20.0, 3).copy(specs = mapOf("Ring" to "high")).loadText(oap))
    }
}
