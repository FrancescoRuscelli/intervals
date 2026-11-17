package app.intervals.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.intervals.IntervalsApp
import app.intervals.core.AppSettings
import app.intervals.core.Block
import app.intervals.core.HybridImport
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
        repo.importWorkouts(result.workouts.map { it.workout })
        val rests = result.workouts.count { it.convertedFinalRest }
        message = "Imported ${result.workouts.size} workouts" + if (rests > 0) " · $rests final rests became rest between rounds" else ""
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

    fun player(@Suppress("UNUSED_PARAMETER") id: Long) {
        message = "The player comes in the next step"
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
