package app.intervals.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.intervals.core.AppSettings
import app.intervals.core.Block
import app.intervals.core.HybridImport
import app.intervals.core.LoggedValue
import app.intervals.core.Session
import app.intervals.player.FieldKey
import app.intervals.player.FinishedInfo
import app.intervals.player.PlayerController
import app.intervals.player.PlayerService
import app.intervals.IntervalsApp
import app.intervals.player.Sounds
import app.intervals.core.SavedWorkout
import app.intervals.core.Series
import app.intervals.core.SeriesIcon
import app.intervals.core.SeriesPos
import app.intervals.core.Workout
import app.intervals.core.addSeries
import app.intervals.core.replaceSeries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { INTERVALS, GYM, JOURNAL, STATS, SETTINGS }

/** Screens shown on top of the tabs, newest last. */
sealed interface Overlay {
    data object EditWorkout : Overlay
    data object Player : Overlay
    data object Finished : Overlay
    /** [pos] null = a new series. */
    data class EditSeries(val pos: SeriesPos?, val template: Series) : Overlay
}

/** The workout being edited: [id] null for a new one. */
data class Draft(val id: Long?, val workout: Workout, val original: Workout) {
    val changed: Boolean get() = workout != original
}

val WORK_SERIES = Series("work", 30, 0xFFE91E63, SeriesIcon.JUMP)
val REST_SERIES = Series("rest", 15, 0xFFFFC107, SeriesIcon.STAND)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as IntervalsApp).repository

    val workouts: StateFlow<List<SavedWorkout>?> =
        repo.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    var tab by mutableStateOf(Tab.INTERVALS)
    val overlays = mutableStateListOf<Overlay>()
    var draft by mutableStateOf<Draft?>(null)
        private set
    /** One-line message shown at the bottom (snackbar). */
    var message by mutableStateOf<String?>(null)

    fun back(): Boolean {
        if (overlays.isEmpty()) return false
        if (overlays.last() == Overlay.Player || overlays.last() == Overlay.Finished) return true // handled by those screens
        val top = overlays.removeAt(overlays.lastIndex)
        if (top == Overlay.EditWorkout) draft = null
        return true
    }

    // ---- list ----

    fun newWorkout() {
        val w = Workout(name = "New workout", rounds = 3, blocks = listOf(Block.Single(WORK_SERIES), Block.Single(REST_SERIES)))
        draft = Draft(null, w, w.copy(name = ""))
        overlays += Overlay.EditWorkout
    }

    fun edit(saved: SavedWorkout) {
        draft = Draft(saved.id, saved.workout, saved.workout)
        overlays += Overlay.EditWorkout
    }

    fun duplicate(id: Long) = viewModelScope.launch {
        repo.duplicateWorkout(id)
        message = "Duplicated"
    }

    fun delete(id: Long, name: String) = viewModelScope.launch {
        repo.deleteWorkout(id)
        message = "Deleted “$name” · its journal entries stay"
    }

    /** Gives [add] and takes away [remove] tags on all the [ids] workouts. */
    fun retag(ids: Set<Long>, add: Set<String>, remove: Set<String>) = viewModelScope.launch {
        val list = workouts.value.orEmpty().filter { it.id in ids }
        list.forEach { sw ->
            val tags = sw.workout.tags - remove + add
            if (tags != sw.workout.tags) repo.updateWorkout(sw.id, sw.workout.copy(tags = tags))
        }
        message = "Tags updated on ${list.size} workouts"
    }

    fun duplicateMany(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { repo.duplicateWorkout(it) }
        message = "Duplicated ${ids.size}"
    }

    fun deleteMany(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { repo.deleteWorkout(it) }
        message = "Deleted ${ids.size} · their journal entries stay"
    }

    /** Saves a new order of the whole list. */
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderWorkouts(ids) }

    fun move(ids: List<Long>, from: Int, to: Int) = viewModelScope.launch {
        if (to !in ids.indices) return@launch
        val list = ids.toMutableList()
        list.add(to, list.removeAt(from))
        repo.reorderWorkouts(list)
    }

    fun importHybrid(csv: String) = viewModelScope.launch {
        val result = runCatching { HybridImport.parse(csv) }.getOrNull()
        if (result == null || result.workouts.isEmpty()) {
            message = "Nothing to import: is it the Hybrid Interval Timer backup (.csv)?"
            return@launch
        }
        val (added, updated) = repo.importOrUpdate(result.workouts.map { it.workout })
        val rests = result.workouts.count { it.convertedFinalRest }
        message = buildList {
            if (added > 0) add("Imported $added workouts")
            if (updated > 0) add("updated $updated not edited by you")
            if (rests > 0) add("$rests final rests became rest between rounds")
        }.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    // ---- editor ----

    fun updateDraft(change: (Workout) -> Workout) {
        val d = draft ?: return
        draft = d.copy(workout = change(d.workout))
    }

    fun saveDraft() = viewModelScope.launch {
        val d = draft ?: return@launch
        val w = d.workout.copy(name = d.workout.name.trim().ifEmpty { "Workout" })
        if (d.id == null) repo.addWorkout(w) else repo.updateWorkout(d.id, w)
        draft = null
        overlays.remove(Overlay.EditWorkout)
        message = "Saved"
    }

    fun deleteDraft() = viewModelScope.launch {
        val d = draft ?: return@launch
        if (d.id != null) repo.deleteWorkout(d.id)
        draft = null
        overlays.clear()
        message = "Deleted “${d.workout.name}” · its journal entries stay"
    }

    fun openSeries(pos: SeriesPos?, template: Series) {
        overlays += Overlay.EditSeries(pos, template)
    }

    fun applySeries(pos: SeriesPos?, s: Series) {
        updateDraft { w -> if (pos == null) w.addSeries(s) else w.replaceSeries(pos, s) }
        overlays.removeAll { it is Overlay.EditSeries }
    }

    // ---- player ----

    private val ia get() = getApplication<IntervalsApp>()
    val player: PlayerController? get() = ia.player
    val finished: FinishedInfo? get() = ia.finished

    init {
        // the activity came back while a workout runs (or waits to be saved): show it again
        if (ia.player != null && Overlay.Player !in overlays) overlays += Overlay.Player
        else if (ia.finished != null && Overlay.Finished !in overlays) overlays += Overlay.Finished
    }

    fun player(id: Long) {
        val saved = workouts.value?.firstOrNull { it.id == id } ?: return
        startPlayer(saved)
    }

    private fun startPlayer(saved: SavedWorkout) {
        if (saved.workout.blocks.isEmpty()) { message = "Add a series first"; return }
        val s = settings.value
        val sounds = Sounds(getApplication(), s.volume, s.duckMusic, s.voiceLanguage)
        lateinit var c: PlayerController
        c = PlayerController(saved, s, sounds, ia.appScope, onEnd = { completed, elapsed, rounds, startedAt ->
            ia.appScope.launch {
                val last = repo.lastValues(saved.id)
                ia.finished = FinishedInfo(saved, completed, elapsed, rounds, startedAt, last)
                ia.player?.close()
                ia.player = null
                PlayerService.stop(ia)
                overlays.remove(Overlay.Player)
                if (Overlay.Finished !in overlays) overlays += Overlay.Finished
            }
        }, onChange = { PlayerService.update(ia, c) })
        ia.player = c
        overlays += Overlay.Player
        PlayerService.start(ia)
        c.start()
    }

    fun saveSession(note: String, values: Map<FieldKey, Double>, repeat: Boolean) = viewModelScope.launch {
        val f = finished ?: return@launch
        val rounds = f.roundsDone.coerceAtLeast(1)
        val logged = values.flatMap { (k, v) ->
            (1..rounds).map { r -> LoggedValue(k.series, k.field, k.unit, r, k.side, v) }
        }
        repo.addSession(
            Session(
                workoutId = f.saved.id, workoutName = f.saved.workout.name, startedAt = f.startedAt,
                durationSec = (f.elapsedMs / 1000).toInt(), roundsDone = f.roundsDone, roundsTotal = f.saved.workout.rounds,
                completed = f.completed, note = note.trim(), values = logged,
            ),
        )
        ia.finished = null
        overlays.remove(Overlay.Finished)
        message = "Saved to the journal"
        if (repeat) startPlayer(f.saved)
    }


    fun saveSettings(s: AppSettings) = viewModelScope.launch { repo.saveSettings(s) }

    /** Writes the backup with [write] (true = written). */
    fun exportBackup(write: (String) -> Boolean) = viewModelScope.launch {
        val text = repo.exportBackup()
        message = if (write(text)) "Backup saved" else "Could not save the backup"
    }

    fun restoreBackup(text: String) = viewModelScope.launch {
        message = try {
            val r = repo.restoreBackup(text)
            "Restored ${r.workouts} workouts and ${r.sessions} sessions"
        } catch (e: Exception) {
            "Not restored: ${e.message ?: "the file is not an Intervals backup"}. Nothing was changed."
        }
    }
}
