package app.intervals.core

/** A ready-made workout offered by + New, in a [section] such as "Fingers" (mockup intervals-next-features.html, 3A). */
data class Preset(val section: String, val name: String, val summary: String, val workout: Workout)

private const val HANG_COLOR = 0xFFE91E63
private const val REST_COLOR = 0xFFFFC107

/** Edge and load, asked before the start and kept with the session (3B, 3C). */
private val FINGER_FIELDS = listOf(LogField("Edge", "mm"), LogField("Load", "kg"))

private fun hang(sec: Int) = Series("Hang", sec, HANG_COLOR, SeriesIcon.STAND, logFields = FINGER_FIELDS)
private fun rest(sec: Int) = Series("Rest", sec, REST_COLOR, SeriesIcon.COOL)

val PRESETS: List<Preset> = listOf(
    Preset(
        "Fingers", "Repeaters 7/3", "6 hangs of 7 s, 3 s rest · 6 sets · 3 min between",
        Workout("Repeaters 7/3", warmupSec = 60, rounds = 6, restBetweenRoundsSec = 180,
            blocks = listOf(Block.Repeat(6, listOf(hang(7), rest(3)), skipLast = true)), tags = setOf("Fingers")),
    ),
    Preset(
        "Fingers", "Max hangs 10 s", "10 s hang · 3 min rest · 5 sets",
        Workout("Max hangs 10 s", warmupSec = 60, rounds = 5, restBetweenRoundsSec = 180,
            blocks = listOf(Block.Single(hang(10))), tags = setOf("Fingers")),
    ),
    Preset(
        "Fingers", "Density hangs", "30 s hang · 1 min rest · 4 sets",
        Workout("Density hangs", warmupSec = 60, rounds = 4, restBetweenRoundsSec = 60,
            blocks = listOf(Block.Single(hang(30))), tags = setOf("Fingers")),
    ),
)
