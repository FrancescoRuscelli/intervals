package app.intervals.ui

import app.intervals.core.intervalLoadHints
import android.app.Application
import android.database.SQLException
import android.os.SystemClock
import app.intervals.player.valueFor
import kotlinx.coroutines.flow.first
import app.intervals.core.Side
import app.intervals.core.letter
import app.intervals.core.summary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.intervals.RestAlarm
import app.intervals.core.AppSettings
import app.intervals.core.Exercise
import app.intervals.core.GymGroup
import app.intervals.core.FitNotesImport
import app.intervals.core.GymSet
import app.intervals.data.FitNotesReader
import app.intervals.data.NotFitNotesException
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

enum class Tab { JOURNAL, INTERVALS, GYM }

/** Screens shown on top of the tabs, newest last. */
sealed interface Overlay {
    data object EditWorkout : Overlay
    data object Player : Overlay
    data object Finished : Overlay
    data object GymImport : Overlay
    data object Settings : Overlay
    /** + New: the presets (Fingers…) or an empty workout. */
    data object NewWorkout : Overlay
    /** Before a workout that logs values (edge, load…): set them, then start (3B). */
    data class Setup(val workoutId: Long) : Overlay
    /** [tab]: 0 Log, 1 History, 2 Graph, 3 Records. */
    data class GymLog(val exerciseId: Long, val day: String, val tab: Int = 0) : Overlay
    /** Stats on its own screen, opened on the Journal's month (yyyy-MM). */
    data class Stats(val month: String) : Overlay
    /** An interval workout's logged values over time (mockup intervals-ideas-from-other-apps.html, 3). */
    data class WorkoutProgress(val workoutId: Long) : Overlay
    /** What a Stats tile or row counts, as a list: [kind] sessions, gymDays, exercises or records, days in [from, to). */
    data class StatsList(val kind: String, val from: String, val to: String, val name: String? = null) : Overlay
    data class GymPick(val day: String) : Overlay
    data class EditExercise(val exerciseId: Long) : Overlay
    /** [groupId] null = a new tag or workout group. */
    data class EditGroup(val groupId: Long?, val isTag: Boolean = false, val items: List<Long> = emptyList()) : Overlay
    data class GymFinish(
        val day: String, val group: GymGroup, val start: LocalTime, val end: LocalTime, val elapsedSec: Int,
    ) : Overlay
    /** [pos] null = a new series. */
    data class EditSeries(val pos: SeriesPos?, val template: Series) : Overlay
}

/** A FitNotes import to confirm; [existingSets] already in the app are replaced. */
data class GymImportPreview(val result: FitNotesImport.Result, val fileName: String, val existingSets: Int)

data class ActiveGymGroup(val id: Long, val startedAt: Long, val startTime: LocalTime)

data class GymDayRoute(val day: String, val returnToJournal: Boolean)

data class GymRest(val day: String, val startedAt: Long, val targetSec: Int, val visible: Boolean = true, val exerciseId: Long = 0)

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

    var tab by mutableStateOf(Tab.JOURNAL)
        private set

    fun selectTab(destination: Tab) {
        gymDayRoute = null
        tab = destination
    }

    /** A day requested by another screen. The Journal consumes it. */
    var journalFocusDay by mutableStateOf<String?>(null)

    fun openJournalDay(day: String) {
        overlays.removeAll { it is Overlay.Stats || it is Overlay.StatsList || it is Overlay.WorkoutProgress }
        journalFocusDay = day
        selectTab(Tab.JOURNAL)
    }

    fun openSettings() { overlays += Overlay.Settings }

    fun openWorkoutProgress(workoutId: Long) { overlays += Overlay.WorkoutProgress(workoutId) }
    val overlays = mutableStateListOf<Overlay>()
    var draft by mutableStateOf<Draft?>(null)
        private set
    /** One-line message shown at the bottom (snackbar). */
    var message by mutableStateOf<String?>(null)

    /** Set together with [message] when the snackbar should offer Undo. */
    var undo: (() -> Unit)? = null

    fun back(): Boolean {
        if (overlays.isEmpty()) return false
        if (overlays.last() == Overlay.Player || overlays.last() == Overlay.Finished) return true // handled by those screens
        if (overlays.last() is Overlay.GymFinish && gymFinishSaving) return true
        val top = overlays.removeAt(overlays.lastIndex)
        if (top == Overlay.EditWorkout) draft = null
        if (top == Overlay.GymImport) gymImport = null
        return true
    }

    // ---- list ----

    /** + New: first the presets to start from (mockup intervals-next-features.html, 3A). */
    fun newWorkout() { overlays += Overlay.NewWorkout }

    /** Opens the editor on a new workout: empty, or a copy of a preset to save as yours. */
    fun newWorkoutFrom(template: Workout?) {
        overlays.remove(Overlay.NewWorkout)
        val w = template ?: Workout(name = "", blocks = emptyList())
        draft = Draft(null, w, Workout(name = "", blocks = emptyList()))
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

    // ---- journal ----

    /** Latest session of each workout (by workout id), for the "last time" line on the cards. */
    val lastByWorkout: StateFlow<Map<Long, Session>> =
        repo.sessionsBetween(0, Long.MAX_VALUE)
            .map { list -> list.filter { it.workoutId != null }.groupBy { it.workoutId!! }.mapValues { (_, s) -> s.maxBy { it.startedAt } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Loads to try next time on each workout (mockup intervals-ideas-from-other-apps.html, 1), from its last two sessions. */
    val loadHints: StateFlow<Map<Long, List<LoggedValue>>> =
        repo.sessionsBetween(0, Long.MAX_VALUE)
            .map { list ->
                list.filter { it.workoutId != null }.groupBy { it.workoutId!! }
                    .mapValues { (_, s) -> intervalLoadHints(s.sortedByDescending { it.startedAt }) }
                    .filterValues { it.isNotEmpty() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun sessions(fromMillis: Long, toMillis: Long) = repo.sessionsBetween(fromMillis, toMillis)

    fun setNote(sessionId: Long, note: String) = viewModelScope.launch { repo.setNote(sessionId, note) }

    fun deleteSession(sessionId: Long) = viewModelScope.launch {
        repo.deleteSession(sessionId)
        message = "Session deleted"
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
        viewModelScope.launch { repo.upgradeGym() }
        viewModelScope.launch { RestAlarm.stops.collect { gymRest = gymRest?.copy(visible = false) } }
        viewModelScope.launch { RestAlarm.targets.collect { t -> gymRest = gymRest?.copy(targetSec = t) } }
    }

    fun player(id: Long) {
        val saved = workouts.value?.firstOrNull { it.id == id } ?: return
        // a workout that logs values asks them first, prefilled from last time
        if (app.intervals.player.fieldKeys(saved).isNotEmpty()) { if (ia.player == null) overlays += Overlay.Setup(id) }
        else startPlayer(saved)
    }

    suspend fun lastValues(workoutId: Long) = repo.lastValues(workoutId)

    /** Every session of a workout, newest first (Progress, load hints). */
    fun sessionsOf(workoutId: Long) = repo.sessionsOf(workoutId)

    /** Starts after the setup screen with [setup] as this session's values. */
    fun startWithSetup(id: Long, setup: Map<app.intervals.player.FieldKey, Double>) {
        val saved = workouts.value?.firstOrNull { it.id == id } ?: return
        overlays.removeAll { it is Overlay.Setup }
        startPlayer(saved, setup)
    }

    private fun startPlayer(saved: SavedWorkout, setup: Map<app.intervals.player.FieldKey, Double> = emptyMap()) {
        if (ia.player != null) return
        if (saved.workout.blocks.isEmpty()) { message = "Add a series first"; return }
        val s = settings.value
        val sounds = Sounds(getApplication(), s.volume, s.duckMusic, s.voiceLanguage, s.endSound, s.masterVoice)
        lateinit var c: PlayerController
        c = PlayerController(saved, s, sounds, ia.appScope, onEnd = { completed, elapsed, rounds, startedAt ->
            ia.appScope.launch {
                // the values the run ended with (set before the start, changed between rounds); the last session's to compare
                val last = repo.lastValues(saved.id)
                val now = c.values.toMap()
                val letGo = c.letGo.values.sortedWith(compareBy({ it.round }, { it.rep }))
                val workDone = c.plan.workStarted(elapsed)
                // the loads to try next time: this run (if clean) and the session before both at the same kg
                val previous = repo.sessionsOf(saved.id).first()
                val thisRun = app.intervals.core.Session(workoutName = saved.workout.name, startedAt = startedAt, durationSec = 0,
                    roundsDone = rounds, roundsTotal = saved.workout.rounds, completed = completed, letGo = letGo,
                    values = now.map { (k, v) -> k.logged(1, v) })
                val hints = app.intervals.core.intervalLoadHints(listOf(thisRun) + previous)
                    .mapNotNull { h -> c.keys.firstOrNull { it.matches(h) }?.let { it to h.value } }.toMap()
                val segs = c.plan.timeline.segments
                val letGoAt = c.letGo.keys.map { idx -> segs.take(idx).count { c.plan.isWork(it) } }.toSet()
                ia.finished = FinishedInfo(saved, completed, elapsed, rounds, startedAt, last, now, c.perRound.toMap(), letGo, workDone, hints, letGoAt)
                ia.player?.close()
                ia.player = null
                PlayerService.stop(ia)
                overlays.remove(Overlay.Player)
                if (Overlay.Finished !in overlays) overlays += Overlay.Finished
            }
        }, onChange = { PlayerService.update(ia, c) }, openingMs = 1000L, setup = setup)
        ia.player = c
        overlays += Overlay.Player
        PlayerService.start(ia)
        c.start()
    }

    fun saveSession(note: String, values: Map<FieldKey, Double>, repeat: Boolean) = viewModelScope.launch {
        val f = finished ?: return@launch
        val rounds = f.roundsDone.coerceAtLeast(1)
        // each round keeps the values it was done with, unless the value was changed on Finished (then all rounds take it)
        val logged = values.flatMap { (k, v) ->
            val edited = f.now[k] != v
            (1..rounds).map { r -> k.logged(r, if (edited) v else f.perRound[r]?.get(k) ?: v) }
        }
        repo.addSession(
            Session(
                workoutId = f.saved.id, workoutName = f.saved.workout.name, startedAt = f.startedAt,
                durationSec = (f.elapsedMs / 1000).toInt(), roundsDone = f.roundsDone, roundsTotal = f.saved.workout.rounds,
                completed = f.completed, note = note.trim(), values = logged, letGo = f.letGo, workDone = f.workDone,
            ),
        )
        ia.finished = null
        overlays.remove(Overlay.Finished)
        message = "Saved to the journal"
        if (repeat) startPlayer(f.saved, values)
    }

    /** Finished, but not kept: nothing goes in the journal. */
    fun discardSession() {
        ia.finished = null
        overlays.remove(Overlay.Finished)
        message = "Not saved"
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

    // ---- gym ----

    val exercises: StateFlow<List<Exercise>> = repo.exercises.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Days with sets, newest first; null while loading. */
    val gymDays: StateFlow<List<String>?> = repo.gymDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun gymDay(day: String): Flow<List<GymSet>> = repo.gymDay(day)

    fun gymDayNote(day: String) = repo.gymDayNote(day)

    fun gymDayNotes() = repo.gymDayNotes()

    /** Exercise id → last day done, for "recent" in the picker. */
    val lastUsed: StateFlow<Map<Long, String>> = repo.lastUsed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun exerciseAll(exerciseId: Long) = repo.exerciseAll(exerciseId)
    fun gymSetsSince(from: String) = repo.gymSetsSince(from)
    fun gymSetsBetween(from: String, to: String) = repo.gymSetsBetween(from, to)

    fun exerciseDay(exerciseId: Long, day: String): Flow<List<GymSet>> = repo.exerciseDay(exerciseId, day)

    fun openGymLog(exerciseId: Long, day: String, tab: Int = 0) { overlays += Overlay.GymLog(exerciseId, day, tab) }

    /** From the rest notification: that exercise's log, unless it is already on top or a workout runs. */
    fun openRestExercise(exerciseId: Long, day: String) {
        val top = overlays.lastOrNull()
        if (top == Overlay.Player || top == Overlay.Finished) return
        if (top is Overlay.GymLog && top.exerciseId == exerciseId && top.day == day) return
        overlays.removeAll { it is Overlay.GymLog && it.exerciseId == exerciseId && it.day == day }
        openGymLog(exerciseId, day)
    }

    fun openStats(month: java.time.YearMonth) { overlays += Overlay.Stats(month.toString()) }

    fun openStatsList(kind: String, from: java.time.LocalDate, to: java.time.LocalDate, name: String? = null) {
        overlays += Overlay.StatsList(kind, from.toString(), to.toString(), name)
    }

    fun editExercise(id: Long) { overlays += Overlay.EditExercise(id) }

    val exerciseCounts: StateFlow<Map<Long, Pair<Int, String>>> =
        repo.exerciseCounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** [renames]: variants renamed (old → new); [wasFirst]: the first variant before editing (sets without one keep it). */
    fun saveExercise(ex: Exercise, renames: Map<String, String> = emptyMap(), wasFirst: String? = null) = viewModelScope.launch {
        repo.saveExercise(ex, renames, wasFirst)
        overlays.removeAll { it is Overlay.EditExercise }
        message = "Saved"
    }

    fun deleteExercise(ex: Exercise) = viewModelScope.launch {
        repo.deleteExercise(ex.id)
        overlays.removeAll { (it is Overlay.EditExercise && it.exerciseId == ex.id) || (it is Overlay.GymLog && it.exerciseId == ex.id) }
        message = "Deleted “${ex.name}” and its sets"
    }

    fun mergeExercise(from: Exercise, into: Exercise, asVariant: String? = null, ownVariant: String? = null) = viewModelScope.launch {
        repo.mergeExercises(from.id, into.id, asVariant, ownVariant)
        overlays.removeAll { it is Overlay.GymLog && it.exerciseId == from.id }
        message = "“${from.name}” merged into “${into.name}”"
    }

    fun variantCounts(exerciseId: Long) = repo.variantCounts(exerciseId)

    fun openGymPick(day: String) { overlays += Overlay.GymPick(day) }

    // ---- gym groups ----

    val groups: StateFlow<List<GymGroup>> = repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The group started on each day (kept in memory: after an app restart the plan is gone, the sets stay). */
    val activeGroups = androidx.compose.runtime.mutableStateMapOf<String, ActiveGymGroup>()

    fun startGroup(groupId: Long, day: String) {
        if (groups.value.none { it.id == groupId && !it.isTag }) return
        if (activeGroups[day]?.id != groupId) {
            activeGroups[day] = ActiveGymGroup(groupId, SystemClock.elapsedRealtime(), LocalTime.now())
        }
        overlays.removeAll { it is Overlay.GymPick }
        openGymDay(day)
    }

    fun endGroup(day: String) {
        val run = activeGroups.remove(day) ?: return
        val rest = gymRest ?: return
        if (rest.day == day && rest.startedAt >= run.startedAt) dismissGymRest()
    }

    var gymFinishSaving by mutableStateOf(false)
        private set
    var gymFinishError by mutableStateOf(false)
        private set

    fun finishGymGroup(day: String, group: GymGroup) = viewModelScope.launch {
        val run = activeGroups[day]?.takeIf { it.id == group.id } ?: return@launch
        val elapsed = ((SystemClock.elapsedRealtime() - run.startedAt) / 1000).toInt()
        val finish = Overlay.GymFinish(day, group, run.startTime, LocalTime.now(), elapsed)
        logMutex.withLock {
            if (tab != Tab.GYM || overlays.isNotEmpty() || activeGroups[day] != run) return@withLock
            val rest = gymRest
            if (rest != null && rest.day == day && rest.startedAt >= run.startedAt) dismissGymRest()
            gymFinishError = false
            overlays += finish
        }
    }

    fun saveGymFinish(finish: Overlay.GymFinish, note: String) = viewModelScope.launch {
        if (gymFinishSaving || overlays.lastOrNull() != finish) return@launch
        gymFinishSaving = true
        gymFinishError = false
        try {
            repo.saveGymDayNote(finish.day, note)
            endGroup(finish.day)
            overlays.remove(finish)
            openJournalDay(finish.day)
        } catch (_: SQLException) {
            gymFinishError = true
        } finally {
            gymFinishSaving = false
        }
    }

    fun editGroup(groupId: Long?, isTag: Boolean = false, items: List<Long> = emptyList()) { overlays += Overlay.EditGroup(groupId, isTag, items) }

    fun retagExercises(ids: Set<Long>, add: Set<String>, remove: Set<String>) = viewModelScope.launch {
        try {
            repo.retagExercises(ids, add, remove)
        } catch (_: SQLException) {
            message = "Could not update exercise tags"
        }
    }

    fun saveGroup(group: GymGroup) = viewModelScope.launch {
        if (group.id == 0L) repo.addGroup(group) else repo.updateGroup(group)
        overlays.removeAll { it is Overlay.EditGroup }
        message = "Saved “${group.name}”"
    }

    fun deleteGroup(group: GymGroup) = viewModelScope.launch {
        repo.deleteGroup(group.id)
        activeGroups.filterValues { it.id == group.id }.keys.forEach(::endGroup)
        overlays.removeAll { it is Overlay.EditGroup }
        message = "Deleted “${group.name}”"
    }

    var gymRest by mutableStateOf<GymRest?>(null)
        private set
    private val logMutex = Mutex()
    // the notification's bar to the target: redrawn every 5 s while the rest runs and the app is alive (it lives in the app's scope)
    private var restBar: kotlinx.coroutines.Job? = null

    init {
        // a rest still running from before the app was closed (mockup intervals-ideas-from-other-apps.html, 4)
        RestAlarm.saved(getApplication())?.let { r ->
            val age = SystemClock.elapsedRealtime() - r.startedAt
            if (r.day == LocalDate.now().toString() && age < 3 * 3600_000L && gymRest == null) {
                gymRest = GymRest(r.day, r.startedAt, r.targetSec, true, r.exerciseId)
                // and its notification again (gone after an update or when its channel changed)
                RestAlarm.start(getApplication(), r.title, r.startedAt, r.targetSec, r.next, r.exerciseId, r.day)
                tickRestBar()
            }
        }
    }

    fun dismissGymRest() {
        restBar?.cancel()
        gymRest = gymRest?.copy(visible = false)
        RestAlarm.cancel(getApplication())
    }

    /** Logs sets (one, or one per side); on today, the real rest since the last set is saved on the first one. */
    fun logSets(sets: List<GymSet>, targetSec: Int) = viewModelScope.launch {
        val now = SystemClock.elapsedRealtime()
        val day = sets.firstOrNull()?.day ?: return@launch
        val today = day == LocalDate.now().toString()
        val run = activeGroups[day]
        // the rest is also kept by RestAlarm, so one across an app restart is still saved
        logMutex.withLock {
            val rest = gymRest?.takeIf { today && it.day == day }
                ?.let { (now - it.startedAt) / 1000 }?.takeIf { it in 0 until 3 * 3600L }?.toInt()
            repo.addSets(sets.mapIndexed { i, s -> s.copy(restSec = if (i == 0) rest else null) })
            val ex = exercises.value.firstOrNull { it.id == sets.first().exerciseId }
            if (today) startGymRest(GymRest(day, now, targetSec, visible = run == activeGroups[day], exerciseId = ex?.id ?: 0), ex?.name.orEmpty(),
                ex?.let { nextSetText(it, day, sets) }.orEmpty())
        }
    }

    /** The rest clock in the app, and outside it the notification and the buzz (RestAlarm). */
    private fun startGymRest(rest: GymRest, exercise: String, next: String = "") {
        gymRest = rest
        val app = getApplication<Application>()
        if (rest.visible) { RestAlarm.start(app, exercise, rest.startedAt, rest.targetSec, next, rest.exerciseId, rest.day); tickRestBar() } else RestAlarm.cancel(app)
    }

    private fun tickRestBar() {
        restBar?.cancel()
        restBar = ia.appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (!RestAlarm.refresh(ia)) break
            }
        }
    }

    /** "set 4 · 90 × 5", "L 16 × 3": what the log screen will offer next (for the rest notification). */
    private suspend fun nextSetText(ex: Exercise, day: String, logged: List<GymSet>): String {
        val today = repo.exerciseDay(ex.id, day).first()
        val last = repo.lastSets(ex.id, day)
        val side = if (ex.perSide && logged.size == 1) when (logged.first().side) { Side.LEFT -> Side.RIGHT; Side.RIGHT -> Side.LEFT; null -> null } else null
        // as the log shows it: no 0 kg (body weight), the bands by name only here (a notification has no dots)
        val next = app.intervals.core.startValues(today, last, side)?.let { it.copy(weightKg = it.weightKg?.takeIf { w -> w != 0.0 }) } ?: return ""
        val plain = ex.copy(specs = emptyList())
        return if (side != null) "${side.letter} ${next.summary(plain)}" else "set ${today.size + 1} · ${next.summary(plain)}"
    }

    fun updateGymSet(set: GymSet) = viewModelScope.launch { repo.updateSet(set) }

    fun saveGymSetNotes(ids: List<Long>, note: String) = viewModelScope.launch {
        if (ids.size == 1) repo.setGymNotes(ids, note) else repo.appendGymSetNote(ids, note)
    }

    // the rows of the last delete, for Undo
    private var deletedSets: List<app.intervals.data.GymSetEntity> = emptyList()

    fun deleteGymSets(ids: List<Long>) = viewModelScope.launch { deletedSets = repo.deleteSets(ids) }

    /** Moves sets logged under the wrong exercise (mockup intervals-move-sets.html, C); the snackbar offers Undo. */
    fun moveGymSets(ids: List<Long>, into: Exercise, variant: String?) = viewModelScope.launch {
        val before = repo.moveSetsTo(ids, into.id, variant)
        undo = { viewModelScope.launch { repo.putBackSets(before) } }
        message = "Moved ${ids.size} set${if (ids.size == 1) "" else "s"} to ${into.name}" + (variant?.let { " · $it" } ?: "")
    }

    fun undoDeleteGymSets() = viewModelScope.launch {
        repo.restoreSets(deletedSets)
        deletedSets = emptyList()
    }

    /** Creates an exercise and opens its log in place of the picker. */
    fun createExercise(exercise: Exercise, day: String) = viewModelScope.launch {
        val id = repo.addExercise(exercise)
        overlays.removeAll { it is Overlay.GymPick }
        openGymLog(id, day)
    }

    /** Picked an exercise: its log replaces the picker. */
    fun pickExercise(exerciseId: Long, day: String) {
        overlays.removeAll { it is Overlay.GymPick }
        openGymLog(exerciseId, day)
    }

    var gymDayRoute by mutableStateOf<GymDayRoute?>(null)
        private set

    fun openGymDay(day: String) {
        gymDayRoute = GymDayRoute(day, tab == Tab.JOURNAL || gymDayRoute?.returnToJournal == true)
        tab = Tab.GYM
    }

    fun closeGymDay() {
        val route = gymDayRoute ?: return
        gymDayRoute = null
        if (route.returnToJournal) openJournalDay(route.day)
    }

    suspend fun lastSets(exerciseId: Long, day: String): List<GymSet> = repo.lastSets(exerciseId, day)

    /** The numbers and side on each set screen ("id|day") as last left, so coming back finds them unchanged.
     *  shortcut: kept in memory only, lost if Android closes the app; save them if that turns out to matter. */
    val gymDrafts = mutableMapOf<String, Pair<GymSet, app.intervals.core.SideChoice?>>()

    /** The FitNotes import waiting for the user to press Import. */
    var gymImport by mutableStateOf<GymImportPreview?>(null)
        private set

    /** Reads a FitNotes backup and shows what it would import; nothing is changed yet. */
    fun previewFitNotes(uri: android.net.Uri) = viewModelScope.launch {
        val app = getApplication<android.app.Application>()
        val result = try {
            withContext(Dispatchers.IO) {
                // SQLite needs a real file: copy the picked one to the cache, read it, delete it
                val tmp = java.io.File.createTempFile("fitnotes", ".db", app.cacheDir)
                try {
                    app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        ?: throw NotFitNotesException("Could not read the file")
                    FitNotesImport.convert(FitNotesReader.read(tmp))
                } finally {
                    tmp.delete()
                }
            }
        } catch (e: NotFitNotesException) {
            message = e.message
            return@launch
        } catch (e: java.io.IOException) {
            message = "Could not read the file"
            return@launch
        }
        if (result.sets.isEmpty()) {
            message = "Nothing to import: the FitNotes backup has no sets"
            return@launch
        }
        val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "FitNotes backup"
        gymImport = GymImportPreview(result, name, repo.gymSetCount())
        overlays += Overlay.GymImport
    }

    fun confirmGymImport() = viewModelScope.launch {
        val preview = gymImport ?: return@launch
        val n = repo.replaceGym(preview.result)
        cancelGymImport()
        selectTab(Tab.GYM)
        message = "Imported ${preview.result.days} days, $n sets, ${preview.result.exercises.size} exercises"
    }

    fun cancelGymImport() {
        gymImport = null
        overlays.remove(Overlay.GymImport)
    }
}
