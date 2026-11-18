package app.intervals.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
) {
    /** True while the player screen is visible: then the clock ticks at every whole second (the rings animate per frame on their own). */
    @Volatile var uiVisible = true
    private var lastIndex = -1
    val plan = PlayerPlan(saved.workout)
    private val cues = plan.cues(settings.sayNames, settings.beepLast3)
    private var nextCue = 0

    /** True during the opening animation: nothing runs yet. */
    var opening by mutableStateOf(openingMs > 0)
        private set
    /** Waiting to start: the opening animation is playing. */
    val starting: Boolean get() = opening
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
                val idx = plan.stateAt(now)?.index ?: -1
                if (idx != lastIndex) { lastIndex = idx; onChange() }
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
            if (now - c.atSec * 1000L > 1500) continue
            when (val cue = c.cue) {
                is Cue.Start -> sounds.say(cue.segment.spoken())
                is Cue.Beep -> sounds.beep(cue.n)
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
