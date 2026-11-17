package app.intervals.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
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
) {
    val plan = PlayerPlan(saved.workout)
    private val cues = plan.cues(settings.sayNames, settings.beepLast3)
    private var nextCue = 0

    /** Seconds of the 3-2-1 still to show before starting (0 = running). */
    var countdown by mutableStateOf(if (settings.countdown) 3 else 0)
        private set
    var paused by mutableStateOf(false)
        private set
    private var baseMs = 0L // time run before the last resume
    private var resumedAt = 0L
    /** Updated by the ticker; the screen reads [elapsedMs] for smooth drawing. */
    var tick by mutableLongStateOf(0L)
        private set
    val startedAt = System.currentTimeMillis()
    private var job: Job? = null
    private var ended = false

    val elapsedMs: Long
        get() = if (countdown > 0) 0 else if (paused) baseMs else baseMs + (SystemClock.elapsedRealtime() - resumedAt)

    fun start() {
        job = scope.launch {
            while (countdown > 0) {
                sounds.beep(if (countdown == 1) 1 else 2)
                delay(1000)
                countdown--
            }
            resumedAt = SystemClock.elapsedRealtime()
            while (isActive && !ended) {
                val now = elapsedMs
                fireCues(now)
                if (plan.isOver(now)) { finish(true); break }
                tick = now
                delay(if (paused) 200 else 100)
            }
        }
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
        if (countdown > 0) return
        if (paused) { resumedAt = SystemClock.elapsedRealtime(); paused = false }
        else { baseMs = elapsedMs; paused = true }
    }

    private fun jumpTo(ms: Long) {
        baseMs = ms.coerceIn(0, plan.totalSec * 1000L)
        resumedAt = SystemClock.elapsedRealtime()
        nextCue = cues.indexOfFirst { it.atSec * 1000L >= baseMs }.let { if (it < 0) cues.size else it }
        tick = baseMs
    }

    fun previous() { if (countdown == 0) jumpTo(plan.previousStartMs(elapsedMs)) }
    fun next() { if (countdown == 0) jumpTo(plan.nextStartMs(elapsedMs)) }

    /** Stop early (the session is still logged as stopped). */
    fun stop() = finish(false)

    private fun finish(completed: Boolean) {
        if (ended) return
        ended = true
        val e = if (completed) plan.totalSec * 1000L else elapsedMs
        job?.cancel()
        onEnd(completed, e, if (completed) saved.workout.rounds else plan.roundsDone(e), startedAt)
    }

    fun close() {
        job?.cancel()
        sounds.close()
    }
}
