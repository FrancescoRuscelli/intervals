package app.intervals.core

import app.intervals.core.FitNotesImport.FnData
import app.intervals.core.FitNotesImport.FnExercise
import app.intervals.core.FitNotesImport.FnSet
import app.intervals.core.FitNotesImport.parseComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FitNotesImportTest {

    @Test
    fun commentsBecomeSideBandsAndNotes() {
        val g = Band.GREEN; val p = Band.PURPLE; val r = Band.RED; val k = Band.BLACK; val y = Band.YELLOW
        fun check(text: String, side: Side?, bands: List<Band>, note: String) =
            assertEquals(FitNotesImport.Comment(side, bands, note), parseComment(text), text)

        check("L", Side.LEFT, emptyList(), "")
        check("RIGHT", Side.RIGHT, emptyList(), "")
        check("L 🟢🟣🟣🔴", Side.LEFT, listOf(g, p, p, r), "")
        check("R🟢🟣🟣🔴", Side.RIGHT, listOf(g, p, p, r), "")
        check("All 🟢🟣🟣🔴", null, listOf(g, p, p, r), "")
        check("R Green+purple", Side.RIGHT, listOf(g, p), "")
        check("Nero + rosso", null, listOf(k, r), "")
        check("♥️+🟡", null, listOf(r, y), "")
        check("🐈‍⬛", null, listOf(k), "")
        // anything else is kept as written
        check("senza mignolo", null, emptyList(), "senza mignolo")
        check("R fatta male", Side.RIGHT, emptyList(), "fatta male")
        check("L +1/2 of a rep", Side.LEFT, emptyList(), "+1/2 of a rep")
        check("Assisted, ♥️ red", null, listOf(r), "Assisted, ♥️ red")
        check("Generic workout reminder for OAPU", null, emptyList(), "Generic workout reminder for OAPU")
        check("Anulare rotto", null, emptyList(), "Anulare rotto")
        check("Lean forward", null, emptyList(), "Lean forward")
    }

    @Test
    fun leftRightPairsMergeAndUnusedAreSkipped() {
        val data = FnData(
            exercises = listOf(
                FnExercise(1, "Block Pulls (Right Hand)", 0, "Pull a weight from above, 20mm", 1.0, null),
                FnExercise(2, "Block Pulls (Left Hand)", 0, "Pulls weight from above, 20mm", 1.0, null),
                FnExercise(3, "Deloaded One Arm Pull-Up (Right)", 0, null, null, 0),
                FnExercise(4, "Barbell Squat", 0, null, null, null),
                FnExercise(5, "Never Done", 0, null, null, null),
                FnExercise(6, "Handstand", 9, null, null, null),
                FnExercise(7, "Pistol Squat", 0, null, null, null),
                FnExercise(8, "Parallettes Pike Push Up", 7, null, null, null),
            ),
            sets = listOf(
                FnSet(10, 4, "2026-11-17", 80.0, 5, 0.0, 0),
                FnSet(11, 1, "2026-11-17", 50.0, 5, 0.0, 0),
                FnSet(12, 2, "2026-11-17", 45.0, 5, 0.0, 0),
                FnSet(13, 3, "2026-11-16", 20.0, 3, 0.0, 0),
                FnSet(14, 6, "2026-11-16", 0.0, 0, 0.0, 40),
                FnSet(15, 7, "2026-11-16", 0.0, 5, 0.0, 0),
                FnSet(16, 7, "2026-11-16", 0.0, 5, 0.0, 0),
                FnSet(17, 8, "2026-11-15", 0.0, 8, 0.0, 0),
            ),
            comments = mapOf(
                12L to "senza mignolo",
                15L to "L 🟢🟣🔴",
                16L to "R 🟢🟣🔴",
            ),
            defaultWeightStep = 2.5,
            defaultRestSec = 180,
        )
        val r = FitNotesImport.convert(data)

        assertEquals(1, r.skippedExercises)
        assertEquals(listOf("Block Pulls", "Deloaded One Arm Pull-Up", "Barbell Squat", "Handstand", "Pistol Squat", "Parallettes Pike Push Up"), r.exercises.map { it.name })
        assertEquals(listOf(FitNotesImport.MergedPair("Block Pulls", 1, 1)), r.pairs)
        assertEquals(listOf("Deloaded One Arm Pull-Up (Right)"), r.oneSided)
        assertEquals(3, r.days)
        assertEquals(8, r.sets.size)

        val ex = r.exercises.associateBy { it.name }
        val blocks = ex.getValue("Block Pulls")
        assertTrue(blocks.perSide)
        assertEquals(1.0, blocks.weightStep)
        assertEquals(180, blocks.restSec)
        assertEquals("Pull a weight from above, 20mm\nPulls weight from above, 20mm", blocks.notes)
        assertTrue(ex.getValue("Deloaded One Arm Pull-Up").assist)
        assertFalse(ex.getValue("Barbell Squat").perSide)
        assertEquals(listOf(GymField.TIME), ex.getValue("Handstand").fields)
        assertEquals(listOf(GymField.REPS), ex.getValue("Parallettes Pike Push Up").fields)
        // bands with weight 0: the bands are the load
        assertEquals(listOf(GymField.REPS, GymField.BANDS), ex.getValue("Pistol Squat").fields)
        assertTrue(ex.getValue("Pistol Squat").perSide)

        val day17 = r.sets.filter { it.day == "2026-11-17" }
        assertEquals(listOf(0, 1, 2), day17.map { it.order })
        assertEquals(listOf(null, Side.RIGHT, Side.LEFT), day17.map { it.side })
        assertEquals("senza mignolo", day17[2].note)
        val pistol = r.sets.filter { it.exerciseId == blocks.id + 4 }
        assertEquals(listOf(Side.LEFT, Side.RIGHT), pistol.map { it.side })
        assertEquals(null, pistol[0].weightKg)
        assertEquals(listOf(Band.GREEN, Band.PURPLE, Band.RED), pistol[0].bands)
        assertEquals(2, r.sideComments)
        assertEquals(2, r.bandComments)
        assertEquals(1, r.noteComments)
    }
}
