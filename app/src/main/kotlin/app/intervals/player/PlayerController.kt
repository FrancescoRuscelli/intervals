package app.intervals.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import app.intervals.core.AppSettings
import app.intervals.core.Cue
import app.intervals.core.PlayerPlan
import app.intervals.core.SavedWorkout
import app.intervals.core.spoken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs one workout: keeps the clock (pauses excluded), plays the cues on time,
 * and reports the end. Time comes from [SystemClock.elapsedRealtime], so it stays right even if the UI lags.
 */
class PlayerController(
    val saved: SavedWorkout,
    private val settings: AppSettings,
    private val sounds: Sounds,
    private val scope: CoroutineScope,
    private val onEnd: (completed: Boolean, elapsedMs: Long, roundsDone: Int, startedAt: Long) -> Unit,
    /** Called when the segment changes, on pause / resume and on jumps (refreshes the notification). */
    private val onChange: () -> Unit = {},
    /** Time for the player's opening animation before the workout clock starts (the warm-up is the get-ready). */
    private val openingMs: Long = 0L,
    /** The values set before the start (3B); shown on the disc and changeable in the rests between rounds. */
    setup: Map<FieldKey, Double> = emptyMap(),
) {
    /** The workout's logged values, live: changed in the rests between rounds, they count from the next round. */
    val keys: List<FieldKey> = fieldKeys(saved)
    val values = mutableStateMapOf<FieldKey, Double>().apply { keys.forEach { put(it, setup[it] ?: 0.0) } }
    /** The values each round was started with (mockup intervals-ideas-from-other-apps.html, 12). */
    val perRound = mutableMapOf<Int, Map<FieldKey, Double>>()
    /** Work series let go of early (mockup ideas, 2), by segment index. */
    val letGo = mutableStateMapOf<Int, app.intervals.core.LetGo>()

    /** What is shown small on the disc during [seg] (mockup ideas, 9): "20 mm · half crimp · 10 kg", this side's values only. */
    fun detailFor(seg: app.intervals.core.Segment): String? =
        keys.filter { it.series == seg.name && (it.side == null || it.side == seg.side) }
            .map { it.text(values[it] ?: 0.0) }.filter { it.isNotEmpty() }
            .joinToString(" · ").ifEmpty { null }

    /** Marks the running work series as let go of (the timer keeps going); a second tap takes it back. */
    fun toggleLetGo() {
        if (starting || ended) return
        val now = elapsedMs
        val st = plan.stateAt(now) ?: return
        if (!plan.isWork(st.segment)) return
        if (letGo.remove(st.index) != null) return
        val rep = plan.workCount(st)?.first ?: 1
        letGo[st.index] = app.intervals.core.LetGo(st.segment.name, st.round, rep, st.segment.side, ((now / 1000) - st.segment.startSec).toInt())
    }

    /** True while the player screen is visible: then the clock ticks at every whole second (the rings animate per frame on their own). */
    @Volatile var uiVisible = true
    private var lastIndex = -1
    /** Replaced when the rest between rounds is made longer or shorter (mockup intervals-gestures.html, D). */
    var plan by mutableStateOf(PlayerPlan(saved.workout))
        private set
    private var cues = plan.cues(settings.sayNames, settings.beepLast3)
    private var nextCue = 0

    /** True during the opening animation: nothing runs yet. */
    var opening by mutableStateOf(openingMs > 0)
        private set
    /** Waiting to start: the opening animation is playing. */
    val starting: Boolean get() = opening
    /** Speaker tapped in the player: no voice and no beeps until tapped again (this workout only). */
    var muted by mutableStateOf(false)
    var paused by mutableStateOf(false)
        private set
    var pauseSeconds by mutableIntStateOf(0)
        private set
    private var pausedAt: Long? = null
    private var baseMs = 0L // time run before the last resume
    private var resumedAt = 0L
    /** Updated by the ticker; the screen reads [elapsedMs] for smooth drawing. */
    var tick by mutableLongStateOf(0L)
        private set
    val startedAt = System.currentTimeMillis()
    private var job: Job? = null
    private var ended = false

    val elapsedMs: Long
        get() = if (starting) 0 else if (paused) baseMs else baseMs + (SystemClock.elapsedRealtime() - resumedAt)

    fun start() {
        job = scope.launch {
            // the disc's opening animation; the clock starts once the disc is in place
            if (opening) { delay(openingMs); opening = false }
            resumedAt = SystemClock.elapsedRealtime()
            while (isActive && !ended) {
                val now = elapsedMs
                fireCues(now)
                if (plan.isOver(now)) { finish(true); break }
                tick = now
                pausedAt?.let { pauseSeconds = ((SystemClock.elapsedRealtime() - it) / 1000).toInt() }
                val stNow = plan.stateAt(now)
                val idx = stNow?.index ?: -1
                if (idx != lastIndex) {
                    lastIndex = idx
                    // a round takes the values it starts with; changes made in the rest before it count from here
                    if (stNow != null && stNow.segment.kind != app.intervals.core.SegmentKind.ROUND_REST) perRound[stNow.round] = values.toMap()
                    onChange()
                }
                delay(nextWakeMs(now))
            }
        }
    }

    /** Screen visible: at each whole second (the numbers change). Screen off: sleep until the next cue or segment change (energy). */
    private fun nextWakeMs(now: Long): Long {
        if (paused) return if (uiVisible) 200 else 1000
        if (uiVisible) return (1000 - now % 1000).coerceAtLeast(20)
        val cueAt = cues.getOrNull(nextCue)?.atSec?.times(1000L) ?: Long.MAX_VALUE
        val segEnd = plan.stateAt(now)?.segment?.endSec?.times(1000L) ?: Long.MAX_VALUE
        return (minOf(cueAt, segEnd) - now).coerceIn(20, 5000)
    }

    private fun fireCues(now: Long) {
        // cues more than 1.5 s late (after a jump) are skipped, not played in a burst
        while (nextCue < cues.size && cues[nextCue].atSec * 1000L <= now) {
            val c = cues[nextCue++]
            if (now - c.atSec * 1000L > 1500 || muted) continue
            when (val cue = c.cue) {
                is Cue.Start -> sounds.say(cue.segment.spoken())
                is Cue.Beep -> sounds.beep(cue.n)
                Cue.SegmentDone -> sounds.segmentDone()
                Cue.Finished -> sounds.finished()
            }
        }
    }

    fun togglePause() {
        if (starting || ended) return
        val now = SystemClock.elapsedRealtime()
        if (paused) {
            resumedAt = now
            pausedAt = null
            pauseSeconds = 0
            paused = false
        } else {
            baseMs += now - resumedAt
            pausedAt = now
            paused = true
        }
        onChange()
    }

    // the plan as the workout was saved: the step of a rest comes from its own length, not from what it was changed to
    private val savedPlan = plan

    /** The step for the rest running now (see [app.intervals.core.restStepSec]), or 0 when no rest is running. */
    fun restStep(): Int {
        val st = plan.stateAt(elapsedMs) ?: return 0
        if (!plan.isRest(st.segment)) return 0
        val d = savedPlan.timeline.segments.getOrNull(st.index)?.durationSec ?: st.segment.durationSec
        return app.intervals.core.restStepSec(d)
    }

    /** During a rest (rest series, change side, rest between rounds): [deltaSec] more or less, this time only. Returns the seconds really changed. */
    fun changeRest(deltaSec: Int): Int {
        if (starting || ended) return 0
        val now = elapsedMs
        val st = plan.stateAt(now) ?: return 0
        if (!plan.isRest(st.segment)) return 0
        val longer = plan.withLonger(st.index, deltaSec, now) ?: return 0
        val change = longer.totalSec - plan.totalSec
        plan = longer
        cues = longer.cues(settings.sayNames, settings.beepLast3)
        nextCue = cues.indexOfFirst { it.atSec * 1000L > now }.let { if (it < 0) cues.size else it }
        tick = now
        onChange()
        return change
    }

    /** Back to [ms] (Undo after a swipe on the disc). */
    fun seek(ms: Long) { if (!starting && !ended) jumpTo(ms) }

    private fun jumpTo(ms: Long) {
        baseMs = ms.coerceIn(0, plan.totalSec * 1000L)
        resumedAt = SystemClock.elapsedRealtime()
        nextCue = cues.indexOfFirst { it.atSec * 1000L >= baseMs }.let { if (it < 0) cues.size else it }
        tick = baseMs
        onChange()
    }

    fun previous() { if (!starting) jumpTo(plan.previousStartMs(elapsedMs)) }
    fun next() { if (!starting) jumpTo(plan.nextStartMs(elapsedMs)) }

    /** Stop early (the session is still logged as stopped). */
    fun stop() = finish(false)

    /** True for the ~1.7 s of the coin flip after a completed workout (the screen shows it, then Finished opens). */
    var celebrating by mutableStateOf(false)
        private set

    private fun finish(completed: Boolean) {
        if (ended) return
        ended = true
        val e = if (completed) plan.totalSec * 1000L else elapsedMs
        job?.cancel()
        val rounds = if (completed) saved.workout.rounds else plan.roundsDone(e)
        if (completed) {
            baseMs = e; paused = true // freeze the clock on the last moment
            celebrating = true
            scope.launch { delay(1700); onEnd(true, e, rounds, startedAt) }
        } else {
            onEnd(false, e, rounds, startedAt)
        }
    }

    fun close() {
        job?.cancel()
        sounds.close()
    }
}
