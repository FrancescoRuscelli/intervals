package app.intervals.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import app.intervals.core.loadText
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import app.intervals.core.loadBlocks
import app.intervals.core.boxText
import app.intervals.core.loadText
import app.intervals.core.variantOf
import app.intervals.core.lastOfVariant
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Band
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.GymSet
import app.intervals.core.QUICK_TAGS
import app.intervals.core.specColors
import app.intervals.core.forVariant
import app.intervals.core.shownSpecs
import app.intervals.core.commonFields
import app.intervals.core.nextLoadHint
import app.intervals.core.LoadHint
import app.intervals.core.Side
import app.intervals.core.SideChoice
import app.intervals.core.afterSet
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.letter
import app.intervals.core.sides
import app.intervals.core.startValues
import app.intervals.core.summary
import app.intervals.core.toggleTag
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.sideTint
import app.intervals.ui.sideColor
import app.intervals.ui.NumberStyle
import app.intervals.ui.list.BigButton
import java.time.LocalDate

/** Values being entered; only the exercise's fields are used. */
private data class Values(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val timeSec: Int = 0,
    val distance: Double = 0.0,
    /** Your specifications (spec name → value); a spec with no value yet is missing. */
    val specs: Map<String, String> = emptyMap(),
    /** The variant (e.g. "deep"); null = the exercise's first. */
    val variant: String? = null,
)

private fun GymSet.toValues() = Values(weightKg ?: 0.0, reps ?: 0, timeSec ?: 0, distance ?: 0.0, specs, variant)

private fun Values.into(s: GymSet, ex: Exercise) = s.copy(
    weightKg = weightKg.takeIf { GymField.WEIGHT in ex.fields },
    reps = reps.takeIf { GymField.REPS in ex.fields },
    timeSec = timeSec.takeIf { GymField.TIME in ex.fields },
    distance = distance.takeIf { GymField.DISTANCE in ex.fields },
    // only what this set shows: the band colors go when Help is not "band"
    specs = specs.filterKeys { k -> ex.shownSpecs(specs, variant?.takeIf { it in ex.variants } ?: ex.variants.firstOrNull()).any { it.name == k } },
    // always written, so reordering the variants later never changes what this set was
    variant = if (ex.variants.isEmpty()) null else variant?.takeIf { it in ex.variants } ?: ex.variants.first(),
)

/** Log sets of one exercise on one day (mockup G2): tiles with − / +, Log set, rest clock counting up, the sets done. */
@Composable
fun GymLogScreen(vm: AppViewModel, exerciseId: Long, day: String, initialTab: Int = 0) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val ex = exercises.firstOrNull { it.id == exerciseId } ?: return
    val sets by remember(exerciseId, day) { vm.exerciseDay(exerciseId, day) }.collectAsStateWithLifecycle(null)
    val lastTime by produceState<List<GymSet>?>(null, exerciseId, day) { value = vm.lastSets(exerciseId, day) }
    val allSets by remember(exerciseId) { vm.exerciseAll(exerciseId) }.collectAsStateWithLifecycle(emptyList())

    var values by remember(exerciseId) { mutableStateOf<Values?>(null) }
    var side by remember(exerciseId) { mutableStateOf<SideChoice?>(null) }
    var editValues by remember(exerciseId, day) { mutableStateOf(Values()) }
    var editSide by remember(exerciseId, day) { mutableStateOf<Side?>(null) }
    var selectedIds by remember(exerciseId, day) { mutableStateOf(emptySet<Long>()) }
    var noteTargets by remember(exerciseId, day) { mutableStateOf(emptyList<GymSet>()) }
    // the sets just deleted, while Undo is offered
    var deleted by remember(exerciseId, day) { mutableStateOf(emptyList<GymSet>()) }
    LaunchedEffect(deleted) { if (deleted.isNotEmpty()) { kotlinx.coroutines.delay(6000); deleted = emptyList() } }
    var typing by remember { mutableStateOf<GymField?>(null) }
    // free timer for timed sets (mockup G18): started at (elapsedRealtime) while running; measured = stopped, time filled in
    var timerFrom by remember(exerciseId) { mutableStateOf<Long?>(null) }
    var measured by remember(exerciseId) { mutableStateOf(false) }
    var typingSpec by remember { mutableStateOf<app.intervals.core.Spec?>(null) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable(exerciseId) { mutableStateOf(initialTab) }
    val logScroll = rememberScrollState()

    // start from today's last set, else from last time; the side after the last one done
    LaunchedEffect(sets != null, lastTime != null) {
        val today = sets ?: return@LaunchedEffect
        val last = lastTime ?: return@LaunchedEffect
        if (values == null) values = startValues(today, last)?.toValues() ?: Values()
        if (side == null && ex.perSide) side = when (today.lastOrNull()?.side) {
            Side.LEFT -> SideChoice.RIGHT
            Side.RIGHT -> SideChoice.LEFT
            null -> SideChoice.RIGHT
        }
    }
    // after a set the values stay as just logged (following last time's sets jumped 0 → 10 → 15 kg on its own); on a per-side
    // exercise the next side takes its own last values today, else last time's (the "↑ Try" hint suggests going up)
    var seen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(sets?.size) {
        val today = sets ?: return@LaunchedEffect
        val last = lastTime ?: return@LaunchedEffect
        val before = seen
        seen = today.size
        if (before == null || today.size <= before || selectedIds.isNotEmpty()) return@LaunchedEffect
        val s = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> return@LaunchedEffect }
        (today.lastOrNull { it.side == s } ?: startValues(today, last, s))?.let { values = it.toValues() }
    }
    // each side starts from its own last set: switching to L brings back the last L values, R the last R
    LaunchedEffect(side) {
        val today = sets ?: return@LaunchedEffect
        val last = lastTime ?: return@LaunchedEffect
        val s = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> return@LaunchedEffect }
        // with variants, the side's last set in the variant in use
        val variant = ex.variants.takeIf { it.isNotEmpty() }?.let { vs -> values?.variant?.takeIf { it in vs } ?: vs.first() }
        (variant?.let { lastOfVariant(allSets, ex, it, s) } ?: startValues(today, last, s))?.let { values = it.toValues() }
    }
    val list = sets.orEmpty()
    val selectedSets = list.filter { it.id in selectedIds }
    val selecting = selectedSets.isNotEmpty()
    val bulk = selectedSets.size > 1
    val edit = selectedSets.singleOrNull()
    val v = if (edit != null) editValues else values ?: Values()
    // what the chosen variant records (mockup intervals-variant-fields.html, 3): a hold shows the timer, pull-ups the reps
    val exV = ex.forVariant(v.variant?.takeIf { it in ex.variants } ?: ex.variants.firstOrNull())
    fun change(f: (Values) -> Values) {
        if (edit != null) editValues = f(v) else values = f(v)
    }
    fun select(ids: Set<Long>) {
        if (ids.size == 1) {
            list.firstOrNull { it.id in ids }?.let { set ->
                editValues = set.toValues()
                editSide = set.side
            }
        }
        selectedIds = ids
    }
    fun tapSet(set: GymSet) {
        if (set.id in selectedIds) select(selectedIds - set.id)
        else select(selectedIds + set.id)
    }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    var openSpec by remember { mutableStateOf<app.intervals.core.Spec?>(null) }
    val setButtons: @Composable () -> Unit = { Column {
        // the variants just above the button (mockup intervals-variant-switch.html, A5a): a tap switches and brings back that variant's numbers
        // the other groups (choices) under the variants, one row of chips each (mockup intervals-variant-groups.html, 2)
        val curVariant = v.variant?.takeIf { it in ex.variants } ?: ex.variants.firstOrNull()
        val groups = ex.shownSpecs(v.specs, curVariant).filter { it.isChoice }
        if (ex.variants.isNotEmpty()) {
            val cur = v.variant?.takeIf { it in ex.variants } ?: ex.variants.first()
            // the last set of each variant, as that variant records it (90° hold: its time), without the variant's name
            val plain = remember(ex) { ex.copy(specs = emptyList()) }
            // no label over the variants: their names say what they are (mockup intervals-range-bands.html, B)
            VariantRow(ex.variants, cur, { vn -> lastOfVariant(allSets, ex, vn)?.let { l -> l.copy(weightKg = l.weightKg?.takeIf { it != 0.0 }) }?.summary(plain)?.removePrefix("$vn · ") ?: "—" }, enabled = !bulk) { picked ->
                val side1 = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> null }
                // editing a set changes only its variant; a new set takes the variant's own last numbers
                if (edit != null) change { it.copy(variant = picked) }
                else values = lastOfVariant(allSets, ex, picked, side1)?.toValues()?.copy(variant = picked) ?: v.copy(variant = picked)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (groups.isNotEmpty() && !bulk) {
            groups.forEach { g ->
                GroupChips(g, v.specs[g.name]) { value ->
                    change { it.copy(specs = if (value == null) it.specs - g.name else it.specs + (g.name to value)) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // with sets selected, their actions take the log buttons' place (mockup intervals-side-set-actions.html, B)
        if (selecting) {
            Text("${selectedSets.size} selected · tap sets to add or remove them", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.size(54.dp).clip(shape).background(p.surface).clickable(onClickLabel = "Cancel selection") { selectedIds = emptySet() },
                    contentAlignment = Alignment.Center) { Icon(AppIcons.Close, "Cancel selection", tint = p.text) }
                BigButton("Note", p.surface, p.text, { noteTargets = selectedSets }, Modifier.weight(1f))
                BigButton("Delete", p.surface, p.dangerText, {
                    deleted = selectedSets
                    vm.deleteGymSets(selectedSets.map { it.id })
                    selectedIds = emptySet()
                }, Modifier.weight(1f))
            }
            if (edit != null) Spacer(Modifier.height(8.dp))
        } else if (deleted.isNotEmpty()) {
            // no confirmation on delete: Undo for a few seconds instead
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Deleted " + (deleted.singleOrNull()?.let { s -> (s.side?.let { it.letter + " " } ?: "") + s.summary(ex) } ?: "${deleted.size} sets"),
                    color = p.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = { vm.undoDeleteGymSets(); deleted = emptyList() }) { Text("Undo", color = p.accentText, fontWeight = FontWeight.Bold) }
            }
        }
        if (edit != null) {
            BigButton("Update set", p.workTint, p.workText, {
                vm.updateGymSet(v.into(edit.copy(side = editSide), exV))
                selectedIds = emptySet()
            })
        } else if (!selecting) {
            val timing = timerFrom != null
            val variant = ex.variants.takeIf { it.isNotEmpty() }?.let { vs -> v.variant?.takeIf { it in vs } ?: vs.first() }
            // the button says the combination: "Log set · first half · band"
            val label = (listOfNotNull(variant) + groups.filter { !it.multiple }.mapNotNull { g -> v.specs[g.name] ?: g.choices.firstOrNull() }
                .filterIndexed { _, c -> c != "none" }).joinToString(" · ").ifEmpty { null }
            LogButtons(ex.perSide, side, label, timing = timing && !bulk, blocked = timing || bulk) { chosen ->
                vm.logSets(chosen.sides().map { s -> v.into(GymSet(exerciseId = ex.id, day = day, order = 0, side = s), exV) }, ex.restSec)
                side = chosen?.afterSet()
                measured = false
            }
        }
    } }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Text(ex.name, modifier = Modifier.weight(1f), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            // the rest clock (mockup intervals-rest-timer.html, option 3): tap to stop it
            vm.gymRest?.takeIf { it.day == day && it.visible && day == LocalDate.now().toString() }?.let { RestPill(it, vm::dismissGymRest) }
            IconButton(onClick = { vm.editExercise(exerciseId) }) { Icon(AppIcons.Edit, "Edit exercise", tint = p.muted) }
        }

        // Log · History · Graph · Records
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            listOf("Log", "History", "Graph", "Records").forEachIndexed { i, t ->
                val on = i == tab
                Column(Modifier.clickable {
                    if (tab != i) {
                        selectedIds = emptySet()
                        tab = i
                    }
                }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, color = if (on) p.text else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    Box(Modifier.width(36.dp).height(2.dp).background(if (on) p.accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.surface))
        // with variants or choice specs, History / Graph / Records can show one variant and / or one choice (mockup G19c;
        // records are kept per variant and spec, mockup intervals-ideas-from-other-apps.html, 6)
        var variantFilter by androidx.compose.runtime.saveable.rememberSaveable(exerciseId) { mutableStateOf<String?>(null) }
        var specFilter by androidx.compose.runtime.saveable.rememberSaveable(exerciseId) { mutableStateOf<String?>(null) }
        val choiceSpecs = ex.specs.filter { it.isChoice && !it.multiple }
        if (tab != 0 && (ex.variants.isNotEmpty() || choiceSpecs.isNotEmpty())) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) = Text(label, color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) p.tint else p.surface)
                        .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(18.dp))
                        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
                FilterChip("All", variantFilter == null && specFilter == null) { variantFilter = null; specFilter = null }
                ex.variants.forEach { v -> FilterChip(v, v == variantFilter) { variantFilter = if (v == variantFilter) null else v } }
                choiceSpecs.forEach { sp ->
                    sp.choices.forEach { c ->
                        val key = sp.name + "=" + c
                        FilterChip("${sp.name}: $c", key == specFilter) { specFilter = if (key == specFilter) null else key }
                    }
                }
            }
        }
        val shown = allSets.filter { s ->
            (variantFilter == null || s.variantOf(ex) == variantFilter) &&
                (specFilter == null || specFilter!!.substringBefore('=').let { name ->
                    val sp = choiceSpecs.firstOrNull { it.name == name }
                    (s.specs[name] ?: sp?.choices?.first()) == specFilter!!.substringAfter('=')
                })
        }
        // one variant: its own fields; All: only what every variant records (so a hold's time and a pull's reps never mix)
        val exShown = if (variantFilter != null) ex.forVariant(variantFilter) else ex.copy(fields = ex.commonFields(), variantFields = emptyMap())
        when (tab) {
            1 -> Box(Modifier.weight(1f)) {
                // the whole workout of each day: first to last set, from the rests of all its sets
                val everything by remember { vm.gymSetsBetween("0000-01-01", "9999-12-31") }.collectAsStateWithLifecycle(emptyList())
                val workoutSec = remember(everything) { everything.groupBy { it.day }.mapValues { (_, s) -> s.sumOf { it.restSec ?: 0 } } }
                ExerciseHistory(ex, shown, workoutSec) { d -> vm.openGymLog(exerciseId, d) }
            }
            2 -> Box(Modifier.weight(1f)) { ExerciseGraph(exShown, shown) }
            3 -> Box(Modifier.weight(1f)) { ExerciseRecords(exShown, shown) }
        }
        val hintSide = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> null }
        val hintVariant = ex.variants.takeIf { it.isNotEmpty() }?.let { vs -> v.variant?.takeIf { it in vs } ?: vs.first() }
        val hint = remember(allSets, hintSide, hintVariant, day) { nextLoadHint(allSets, ex, day, hintSide, hintVariant) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val hintPrefs = remember { ctx.getSharedPreferences("hints", android.content.Context.MODE_PRIVATE) }
        var dismissedHints by remember { mutableStateOf(hintPrefs.getStringSet("dismissed", emptySet()).orEmpty().toSet()) }
        // the sets scroll on their own, the last one at the bottom; the numbers sit in a fixed panel under them (mockup intervals-fixed-numbers.html, C)
        if (tab == 0) {
            LaunchedEffect(list.size) { logScroll.scrollTo(logScroll.maxValue) }
            LaunchedEffect(logScroll.maxValue) { if (!selecting) logScroll.scrollTo(logScroll.maxValue) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(logScroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (list.isNotEmpty()) {
                    val hintText = if (selecting) "TAP TO ADD OR REMOVE SETS" else "TAP TO EDIT · HOLD TO SELECT"
                    Text("SETS · ${list.size} · $hintText", color = p.muted, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold)
                    val hold = { set: GymSet -> if (set.id !in selectedIds) select(selectedIds + set.id) }
                    SetBlocks(list, ex, selectedIds, selecting, ::tapSet, hold)
                }
            }
            val running = timerFrom != null && !selecting
            Column(
                Modifier.fillMaxWidth().background(p.bg).border(1.dp, p.surface).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val sideNow = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> null }
                val lastSec = lastTime.orEmpty().let { l -> (sideNow?.let { sd -> l.firstOrNull { it.side == sd } } ?: l.firstOrNull())?.timeSec }
                val lastText = lastSec?.let { "last " + formatTime(it) + when (sideNow) { Side.LEFT -> " on the left"; Side.RIGHT -> " on the right"; null -> "" } }
                val onStart = { timerFrom = android.os.SystemClock.elapsedRealtime(); measured = false }
                if (running) {
                    // while holding, the big clock and STOP as before: the list does not matter now
                    TimerCard(
                        seconds = v.timeSec, startedAt = timerFrom, measured = measured, side = sideNow, lastText = lastText,
                        onType = { typing = GymField.TIME }, onStart = onStart,
                        onStop = { ms -> change { it.copy(timeSec = ((ms + 500) / 1000).toInt()) }; timerFrom = null; measured = true },
                        onNudge = { d -> change { it.copy(timeSec = (it.timeSec + d).coerceAtLeast(0)) } },
                    )
                } else {
                    // a set being edited keeps L / R here; a new set picks its side with the log buttons
                    if (ex.perSide && edit != null) {
                        val sides = listOf(Side.LEFT, Side.RIGHT)
                        Segmented(listOf("L", "R"), editSide?.let(sides::indexOf) ?: -1, listOf(p.pink, p.teal)) { editSide = sides[it] }
                    }
                    // measurements (numbers) as chips to tap; the choices are groups near the button
                    if (ex.specs.any { !it.isChoice }) SpecChips(ex.specs.filter { !it.isChoice }, v.specs, enabled = !bulk) { openSpec = it }
                    // a quiet hint to go up (mockup intervals-ideas-from-other-apps.html, 1): gone once a set at that load is logged
                    if (!selecting && hint != null && v.weightKg < hint.toKg &&
                        list.none { (hintSide == null || it.side == hintSide) && (it.weightKg ?: Double.NEGATIVE_INFINITY) >= hint.toKg }
                        && hintKey(ex.id, hintSide, hintVariant, hint) !in dismissedHints
                    ) {
                        // swipe it away (left or right) if you don't want it: it stays away for this exercise, side and weight
                        SwipeAway(onGone = {
                            val k = hintKey(ex.id, hintSide, hintVariant, hint)
                            dismissedHints = dismissedHints + k
                            hintPrefs.edit().putStringSet("dismissed", dismissedHints).apply()
                        }) { LoadHintRow(hint, hintSide) { change { it.copy(weightKg = hint.toKg) } } }
                    }
                    val fields = listOf(GymField.WEIGHT, GymField.REPS, GymField.DISTANCE).filter { it in exV.fields }
                    if (fields.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fields.forEach { f ->
                            val (label, text) = when (f) {
                                GymField.WEIGHT -> "kg" to formatKg(v.weightKg)
                                GymField.REPS -> "reps" to "${v.reps}"
                                else -> "distance" to formatKg(v.distance)
                            }
                            NumCell(label, text, Modifier.weight(1f), enabled = !bulk, onTap = { typing = f },
                                onMinus = { change { it.step(f, ex, -1) } }, onPlus = { change { it.step(f, ex, +1) } })
                        }
                    }
                    if (GymField.TIME in exV.fields) {
                        if (selecting) NumCell("time", formatTime(v.timeSec), Modifier.fillMaxWidth(), enabled = !bulk, onTap = { typing = GymField.TIME },
                            onMinus = { change { it.step(GymField.TIME, ex, -1) } }, onPlus = { change { it.step(GymField.TIME, ex, +1) } })
                        else TimeRow(v.timeSec, measured, lastText, onType = { typing = GymField.TIME }, onStart = onStart,
                            onNudge = { d -> change { it.copy(timeSec = (it.timeSec + d).coerceAtLeast(0)) } })
                    }
                    setButtons()
                }
            }
        }
    }

    openSpec?.let { sp ->
        AlertDialog(
            onDismissRequest = { openSpec = null },
            title = { Text(sp.name) },
            text = {
                SpecTiles(listOf(sp), v.specs, onType = { typingSpec = it }) { name, value ->
                    change { it.copy(specs = if (value == null) it.specs - name else it.specs + (name to value)) }
                    // one choice: picking it is all there is to do
                    if (sp.isChoice && !sp.multiple) openSpec = null
                }
            },
            confirmButton = { TextButton(onClick = { openSpec = null }) { Text("Done") } },
        )
    }
    if (typing == GymField.TIME) {
        app.intervals.ui.editor.DurationDialog("Time", v.timeSec, 0, { typing = null }) { t -> change { it.copy(timeSec = t) }; typing = null }
    } else typing?.let { f ->
        val current = when (f) {
            GymField.WEIGHT -> formatKg(v.weightKg); GymField.REPS -> "${v.reps}"; GymField.TIME -> "${v.timeSec}"; else -> formatKg(v.distance)
        }
        NumberDialog(
            when (f) { GymField.WEIGHT -> "Weight (kg, negative = assisted)"; GymField.REPS -> "Reps"; GymField.TIME -> "Time (seconds)"; else -> "Distance" },
            current, decimal = f == GymField.WEIGHT || f == GymField.DISTANCE, onDismiss = { typing = null }, negative = f == GymField.WEIGHT,
        ) { n ->
            change {
                when (f) {
                    GymField.WEIGHT -> it.copy(weightKg = n)
                    GymField.REPS -> it.copy(reps = n.toInt())
                    GymField.TIME -> it.copy(timeSec = n.toInt())
                    else -> it.copy(distance = n)
                }
            }
            typing = null
        }
    }
    typingSpec?.let { sp ->
        NumberDialog(sp.name + if (sp.unit.isNotBlank()) " (${sp.unit})" else "", v.specs[sp.name] ?: "", decimal = true,
            onDismiss = { typingSpec = null }) { n -> change { it.copy(specs = it.specs + (sp.name to formatKg(n))) }; typingSpec = null }
    }
    if (noteTargets.isNotEmpty()) {
        SetNotesDialog(noteTargets, ex, onDismiss = { noteTargets = emptyList() }) { note ->
            vm.saveGymSetNotes(noteTargets.map { it.id }, note)
            noteTargets = emptyList()
        }
    }
}

/** − / + by the exercise's weight step, or by 1 for reps and seconds. */
private fun Values.step(f: GymField, ex: Exercise, dir: Int): Values = when (f) {
    // the weight can go below 0: assisted (weight taken off)
    GymField.WEIGHT -> copy(weightKg = weightKg + dir * ex.weightStep)
    GymField.REPS -> copy(reps = (reps + dir).coerceAtLeast(0))
    GymField.TIME -> copy(timeSec = (timeSec + dir).coerceAtLeast(0))
    GymField.DISTANCE -> copy(distance = (distance + dir).coerceAtLeast(0.0))
    GymField.BANDS -> this
}

internal fun shortDay(day: String): String {
    val d = LocalDate.parse(day)
    val pattern = if (d.year == LocalDate.now().year) "EEE d MMM" else "d MMM yyyy"
    return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
}

/** One number of the fixed panel: − value +, the value tapped to type it (mockup intervals-fixed-numbers.html, C). */
@Composable
private fun NumCell(label: String, value: String, modifier: Modifier, enabled: Boolean, onTap: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(14.dp)).background(p.surface).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(AppIcons.Minus, "$label less", enabled, onMinus)
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = enabled, onClickLabel = "Type $label", onClick = onTap),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = p.muted, fontSize = 11.sp, maxLines = 1)
            Text(value, color = p.text, fontSize = if (value.length <= 5) 26.sp else 21.sp, style = NumberStyle, maxLines = 1, softWrap = false)
        }
        StepButton(AppIcons.Plus, "$label more", enabled, onPlus)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(p.tint).clickable(enabled = enabled, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, desc, tint = p.accentText, modifier = Modifier.size(20.dp)) }
}

/** The time in the fixed panel when not holding: ready = START; measured = − 1 s, + 1 s and time again. Tap the time to type it. */
@Composable
private fun TimeRow(seconds: Int, measured: Boolean, lastText: String?, onType: () -> Unit, onStart: () -> Unit, onNudge: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClickLabel = "Type the time", onClick = onType), verticalArrangement = Arrangement.Center) {
            Text(if (measured) "time · measured" else "time" + (lastText?.let { " · $it" } ?: ""), color = p.muted, fontSize = 11.sp, maxLines = 1)
            Text(formatTime(seconds), color = if (measured) p.text else p.soft, fontSize = 26.sp, style = NumberStyle, maxLines = 1)
        }
        if (measured) {
            listOf("−1" to -1, "+1" to 1).forEach { (label, d) ->
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(p.tint).clickable(onClickLabel = "$label second") { onNudge(d) },
                    contentAlignment = Alignment.Center) { Text(label, color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(11.dp)).background(p.bg).clickable(onClickLabel = "Time again", onClick = onStart).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center) { Text("Again", color = p.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        } else {
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(11.dp)).background(p.teal).clickable(onClickLabel = "Start the timer", onClick = onStart).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center) { Text("START", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun StepTile(
    label: String, value: String, unit: String, modifier: Modifier, enabled: Boolean = true,
    onTap: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit,
) {
    val p = LocalPalette.current
    Column(modifier.alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = p.muted, fontSize = 13.sp, maxLines = 1)
        // every value the same size (21 and 18.5 alike), on a row of fixed height so all tiles line up; very long values shrink a bit
        Row(
            Modifier.height(52.dp).clickable(enabled = enabled, onClickLabel = "Type $label", onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, color = p.text, fontSize = (if (value.length <= 6) 34 else 26).sp, style = NumberStyle, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) Text(" $unit", color = p.muted, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 10.dp))
        }
        // − and + are two equal halves of the tile
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(AppIcons.Minus, "$label less", onMinus), Triple(AppIcons.Plus, "$label more", onPlus)).forEach { (icon, desc, action) ->
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).clickable(enabled = enabled, onClickLabel = desc, onClick = action),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, desc, tint = p.accentText, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

/**
 * The logged sets (mockup intervals-log-sets-ideas.html). Per-side exercises (U): one row per round, L on the left and
 * R on the right with a line between; a set without its other side leaves that half empty; the counts of each side
 * above. Other exercises (W1): a block per load in the order done, the load in a fixed-width column so the squares
 * line up, the rest before each set under its square (yellow over 5 min). A note is a folded corner on the square.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetBlocks(
    sets: List<GymSet>, ex: Exercise, markedIds: Set<Long>, selecting: Boolean,
    onTap: (GymSet) -> Unit, onHold: (GymSet) -> Unit,
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val square: @Composable (GymSet, Boolean) -> Unit = { set, withRest -> SetSquare(set, ex, set.id in markedIds, selecting, withRest, onTap, onHold) }
    if (sets.any { it.side != null }) {
        val rows = remember(sets) { pairSides(sets) }
        val left = sets.count { it.side == Side.LEFT }
        val right = sets.count { it.side == Side.RIGHT }
        // colored specs (bands) as dots, not names (the words "black+red" were too much)
        val labelWidth = with(density) {
            sets.maxOf { measurer.measure(it.plainLoad(ex), TextStyle(fontSize = 13.sp)).size.width.toDp() + 12.dp * it.specColors(ex).size + (if (it.specColors(ex).isNotEmpty()) 6.dp else 0.dp) }.value.dp + 4.dp
        }.coerceAtMost(110.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("L $left · R $right" + if (rows.all { it.size == 2 }) " · ${rows.size} round${if (rows.size == 1) "" else "s"}" else "",
                color = p.muted, fontSize = 12.sp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                rows.forEachIndexed { i, row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = p.muted, fontSize = 11.sp, style = NumberStyle, modifier = Modifier.width(18.dp))
                        listOf(Side.LEFT, Side.RIGHT).forEachIndexed { k, side ->
                            if (k == 1) Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(32.dp).background(p.line))
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.firstOrNull { it.side == side }?.let { set ->
                                    Text(side.letter, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.sideTint(side)).padding(horizontal = 7.dp, vertical = 3.dp))
                                    if (set.plainLoad(ex).isNotEmpty() || set.specColors(ex).isNotEmpty()) Row(Modifier.width(labelWidth), verticalAlignment = Alignment.CenterVertically) {
                                        ColorDots(set.specColors(ex))
                                        Text(set.plainLoad(ex), color = p.soft, fontSize = 13.sp, maxLines = 1, softWrap = false)
                                    }
                                    square(set, false)
                                }
                            }
                        }
                        val rest = row.minBy { it.order }.restSec?.takeIf { it > 0 }
                        Text(rest?.let(::formatTime).orEmpty(), color = if ((rest ?: 0) > 300) p.recordText else p.muted, fontSize = 10.sp,
                            style = NumberStyle, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                    }
                }
            }
        }
        return
    }
    val blocks = remember(sets, ex) { loadBlocks(sets, ex) }
    val label = { b: app.intervals.core.LoadBlock -> b.sets.first().plainLoad(ex) + (b.sets.first().variantOf(ex)?.let { " $it" } ?: "") }
    val labelWidth = with(density) {
        blocks.maxOf { measurer.measure(label(it), TextStyle(fontSize = 15.sp)).size.width.toDp() + 12.dp * it.sets.first().specColors(ex).size + (if (it.sets.first().specColors(ex).isNotEmpty()) 6.dp else 0.dp) }.value.dp + 4.dp
    }.coerceAtMost(130.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            val variant = block.sets.first().variantOf(ex)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Row(Modifier.width(labelWidth).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDots(block.sets.first().specColors(ex))
                    Text(
                        buildAnnotatedString {
                            append(block.sets.first().plainLoad(ex))
                            variant?.let { withStyle(SpanStyle(color = p.muted, fontSize = 13.sp)) { append(" $it") } }
                        },
                        color = p.text, fontSize = 15.sp, style = NumberStyle, maxLines = 2,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.sets.forEach { square(it, true) }
                }
            }
        }
    }
}

/** One set's 44 dp square: tap to edit (or to add / remove while selecting), hold to select; the rest under it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetSquare(
    set: GymSet, ex: Exercise, marked: Boolean, selecting: Boolean, withRest: Boolean,
    onTap: (GymSet) -> Unit, onHold: (GymSet) -> Unit,
) {
    val p = LocalPalette.current
    val action = if (!selecting) "Edit" else if (marked) "Deselect" else "Select"
    val noteMark = p.text
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (marked) p.workTint else p.bg)
                .border(1.dp, if (marked) p.pink else p.line, RoundedCornerShape(10.dp))
                .drawWithContent {
                    drawContent()
                    // a note: the top-right corner folded, as on the Journal's bars
                    if (set.note.isNotEmpty()) {
                        val c = 9.dp.toPx()
                        drawPath(Path().apply { moveTo(size.width - c, 0f); lineTo(size.width, 0f); lineTo(size.width, c); close() }, noteMark)
                    }
                }
                .semantics { if (selecting) selected = marked }
                .combinedClickable(
                    onClickLabel = "$action set ${set.summary(ex)}${set.side?.let { ", " + it.name.lowercase() }.orEmpty()}" +
                        if (set.note.isNotEmpty()) ", note ${set.note}" else "",
                    onLongClickLabel = "Select set",
                    hapticFeedbackEnabled = true,
                    onClick = { onTap(set) }, onLongClick = { onHold(set) },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(set.boxText(ex), color = if (marked) p.workText else p.text, fontSize = 16.sp, style = NumberStyle)
        }
        if (withRest) {
            val rest = set.restSec?.takeIf { it > 0 }
            Text(rest?.let(::formatTime) ?: " ", color = if ((rest ?: 0) > 300) p.recordText else p.muted, fontSize = 10.sp, style = NumberStyle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetNotesDialog(sets: List<GymSet>, ex: Exercise, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val p = LocalPalette.current
    val single = sets.singleOrNull()
    var note by remember(sets) { mutableStateOf(single?.note.orEmpty()) }
    val title = single?.let { s ->
        (s.side?.let { it.letter + " · " } ?: "") + s.summary(ex) + (s.restSec?.let { " · rested ${formatTime(it)}" } ?: "")
    } ?: "Add a note to ${sets.size} sets"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_TAGS.forEach { tag ->
                        val on = tag in note.split(" · ")
                        Text(tag, color = if (on) p.accentText else p.soft, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.surface)
                                .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
                                .clickable { note = toggleTag(note, tag) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = single != null || note.isNotBlank(), onClick = { onSave(note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks a number; [decimal] allows 22.5. */
@Composable
private fun NumberDialog(title: String, initial: String, decimal: Boolean, onDismiss: () -> Unit, negative: Boolean = false, onDone: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    // a weight can be negative (assisted); "-" or the real minus sign both work
    val n = text.replace(',', '.').replace('\u2212', '-').toDoubleOrNull()?.takeIf { it >= 0 || negative }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (negative) KeyboardType.Text else if (decimal) KeyboardType.Decimal else KeyboardType.Number))
        },
        confirmButton = { TextButton(enabled = n != null, onClick = { n?.let(onDone) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The exercise's variants in one row, each with its last set under the name; past three the row scrolls, the fourth cut to show it goes on. */
@Composable
private fun VariantRow(variants: List<String>, current: String, last: (String) -> String, enabled: Boolean, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    // keep the variant in use on screen
    LaunchedEffect(current) {
        val i = variants.indexOf(current)
        if (state.layoutInfo.visibleItemsInfo.none { it.index == i && it.offset >= 0 }) state.animateScrollToItem(i)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
        val gap = 6.dp
        val w = if (variants.size <= 3) (maxWidth - gap * (variants.size - 1)) / variants.size else (maxWidth - gap * 3) / 3.4f
        androidx.compose.foundation.lazy.LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(gap)) {
            items(variants.size) { i ->
                val name = variants[i]
                val on = name == current
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.width(w).heightIn(min = 48.dp).clip(shape).background(if (on) p.accent else p.surface).border(1.dp, if (on) p.accent else p.line, shape)
                        .semantics { selected = on }.clickable(enabled = enabled, onClickLabel = "Switch to $name") { onPick(name) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, color = if (on) p.onAccent else p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(last(name), color = if (on) p.onAccent.copy(alpha = 0.8f) else p.muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
/** [colors] = the selected background of each option (default: purple). */
private fun Segmented(options: List<String>, selected: Int, colors: List<Color>? = null, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if (i == selected) colors?.getOrNull(i) ?: p.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == selected) p.onAccent else p.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * The specifications as one row of chips (mockup intervals-set-screen-ideas.html, A): what stays the same between sets,
 * e.g. "20 mm ✎ · half crimp ✎"; tap one to change it. A number without a unit keeps its name ("Ring 3").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecChips(specs: List<app.intervals.core.Spec>, values: Map<String, String>, enabled: Boolean, onOpen: (app.intervals.core.Spec) -> Unit) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        specs.forEach { sp ->
            val value = values[sp.name]?.takeIf(String::isNotBlank)
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.alpha(if (enabled) 1f else 0.45f).clip(shape).background(p.surface).border(1.dp, p.line, shape)
                    .clickable(enabled = enabled, onClickLabel = "Change ${sp.name}") { onOpen(sp) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        value == null -> "${sp.name} –"
                        !sp.isChoice && sp.unit.isBlank() -> "${sp.name} $value"
                        else -> sp.text(value)
                    },
                    color = if (value == null) p.muted else p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Icon(AppIcons.Edit, null, tint = p.muted, modifier = Modifier.padding(start = 6.dp).size(14.dp))
            }
        }
    }
}

/**
 * The log buttons (mockup intervals-set-screen-ideas.html, B1): per side Log L · L + R · Log R, the next side lit and each
 * in its side's color; otherwise one Log set. Blocked while the timer runs or sets are selected.
 */
@Composable
private fun LogButtons(perSide: Boolean, next: SideChoice?, variant: String?, timing: Boolean, blocked: Boolean, onLog: (SideChoice?) -> Unit) {
    val p = LocalPalette.current
    when {
        blocked -> BigButton(if (timing) "Stop the timer first" else "Log set", p.tint, p.faint, {}, enabled = false)
        !perSide -> BigButton("Log set" + (variant?.let { " · $it" } ?: ""), p.accent, p.onAccent, { onLog(null) })
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(SideChoice.LEFT, "Log L", p.pink), Triple(SideChoice.BOTH, "L + R", p.accent), Triple(SideChoice.RIGHT, "Log R", p.teal))
                .forEach { (choice, label, color) ->
                    val lit = choice == next
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.weight(if (choice == SideChoice.BOTH) 0.75f else 1f).height(54.dp).clip(shape)
                            .background(if (lit) color else p.surface).border(1.dp, if (lit) color else p.line, shape)
                            .clickable(onClickLabel = label) { onLog(choice) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = when {
                                !lit -> if (choice == SideChoice.BOTH) p.accentText else color
                                color.luminance() > 0.4f -> Color(0xFF04241F)
                                else -> Color.White
                            },
                        )
                    }
                }
        }
    }
}

/**
 * Your specifications: a number gets a tile with − / + (tap it to type), a choice a row of chips (tap again to clear).
 * [onChange] gets null to clear a value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecTiles(
    specs: List<app.intervals.core.Spec>,
    values: Map<String, String>,
    onType: (app.intervals.core.Spec) -> Unit,
    onChange: (String, String?) -> Unit,
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val numbers = specs.filter { !it.isChoice }
        numbers.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { sp ->
                    val cur = values[sp.name]?.replace(',', '.')?.toDoubleOrNull()
                    StepTile(sp.name, cur?.let(::formatKg) ?: "–", sp.unit, Modifier.weight(1f),
                        onTap = { onType(sp) },
                        onMinus = { onChange(sp.name, formatKg(((cur ?: 0.0) - sp.step).coerceAtLeast(0.0))) },
                        onPlus = { onChange(sp.name, formatKg((cur ?: 0.0) + sp.step)) })
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        specs.filter { it.isChoice }.forEach { sp ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp)) {
                Text(sp.name + if (sp.multiple) " \u00B7 hold for one more" else "", color = p.muted, fontSize = 13.sp)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sp.choices.forEach { c ->
                        // "several" (e.g. bands): tap = on / off, long press = one more; else one choice, tap again to clear
                        val n = if (sp.multiple) sp.counts(values[sp.name]).firstOrNull { it.first == c }?.second ?: 0 else if (values[sp.name] == c) 1 else 0
                        val on = n > 0
                        val color = sp.colors[c]?.let { Color(it) }
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.accent else p.bg)
                                .combinedClickable(
                                    onClick = { onChange(sp.name, if (sp.multiple) sp.tap(values[sp.name], c) else if (on) null else c) },
                                    onLongClick = if (sp.multiple) ({ onChange(sp.name, sp.tap(values[sp.name], c, more = true)) }) else null,
                                    onLongClickLabel = if (sp.multiple) "Add one more $c" else null,
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (color != null) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(color).border(1.dp, Color(0x55FFFFFF), CircleShape))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(c + if (n > 1) " \u00D7$n" else "", color = if (on) p.onAccent else p.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The time of a timed set (mockup G18): ready = the value (tap to type) + START; running = a counter with tenths in the side's
 * color, the last time underneath, STOP, screen kept on; stopped = the measured time (rounded to the second), − / + 1 s, Time again.
 */
@Composable
private fun TimerCard(
    seconds: Int, startedAt: Long?, measured: Boolean, side: Side?, lastText: String?,
    onType: () -> Unit, onStart: () -> Unit, onStop: (Long) -> Unit, onNudge: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val running = startedAt != null
    val tint = side?.let { p.sideColor(it) } ?: p.accent
    val elapsed by produceState(0L, startedAt) {
        if (startedAt == null) return@produceState
        while (true) {
            value = android.os.SystemClock.elapsedRealtime() - startedAt
            delay(100)
        }
    }
    // the phone must not sleep in the middle of a hold
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (running) tint.copy(alpha = 0.14f) else p.surface)
            .border(if (running) 2.dp else 1.dp, if (running) tint else p.line, RoundedCornerShape(20.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when { running -> "Holding" + when (side) { Side.LEFT -> " \u00B7 left"; Side.RIGHT -> " \u00B7 right"; null -> "" }; measured -> "Time \u00B7 measured"; else -> "Time" },
            color = p.muted, fontSize = 13.sp,
        )
        if (running) {
            val sec = (elapsed / 1000).toInt()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatTime(sec), color = p.text, fontSize = 64.sp, style = NumberStyle, maxLines = 1)
                Text("." + (elapsed % 1000 / 100), color = tint, fontSize = 28.sp, style = NumberStyle, modifier = Modifier.padding(bottom = 8.dp))
            }
        } else {
            Text(formatTime(seconds), color = if (measured) p.text else p.soft, fontSize = 64.sp, style = NumberStyle, maxLines = 1,
                modifier = Modifier.clickable(onClickLabel = "Type the time", onClick = onType))
        }
        Text(
            when {
                running -> lastText ?: "first time"
                measured -> "rounded to the second \u00B7 adjust if you started late"
                else -> "Start when you begin, Stop when you let go \u00B7 or tap the time to type it"
            },
            color = p.muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (measured && !running) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("\u2212 1 s" to -1, "+ 1 s" to 1).forEach { (label, d) ->
                    Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).clickable(onClickLabel = label) { onNudge(d) },
                        contentAlignment = Alignment.Center) { Text(label, color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
                Box(Modifier.weight(1.2f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).clickable(onClick = onStart),
                    contentAlignment = Alignment.Center) { Text("Time again", color = p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        } else if (running) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(20.dp)).background(p.surface)
                        .clickable(onClickLabel = "Restart the timer", onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) { Text("RESTART", color = p.soft, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(2f).height(68.dp).clip(RoundedCornerShape(20.dp)).background(p.pink)
                        .clickable(onClickLabel = "Stop the timer") { onStop(elapsed) },
                    contentAlignment = Alignment.Center,
                ) { Text("STOP", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(20.dp)).background(p.teal)
                    .clickable(onClickLabel = "Start the timer", onClick = onStart),
                contentAlignment = Alignment.Center,
            ) { Text("START", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
        }
    }
}

/** "↑ Try 17 kg on the left · 3 reps at 16 kg on 7 Jul and 14 Jul" in teal; a tap puts 17 in the weight tile. */
@Composable
private fun LoadHintRow(hint: LoadHint, side: Side?, onUse: () -> Unit) {
    val p = LocalPalette.current
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
    val days = hint.days.joinToString(" and ") { LocalDate.parse(it).format(fmt) }
    val where = when (side) { Side.LEFT -> " on the left"; Side.RIGHT -> " on the right"; null -> "" }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.restTint).clickable(onClickLabel = "Use ${formatKg(hint.toKg)} kg", onClick = onUse)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("↑ Try ${formatKg(hint.toKg)} kg$where", color = p.restText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("${hint.reps} rep${if (hint.reps == 1) "" else "s"} at ${formatKg(hint.fromKg)} kg on $days. Tap to use.", color = p.soft, fontSize = 12.sp)
    }
}

/** The name of a group of choices above its chips ("RANGE", "HELP"). */
@Composable
private fun GroupLabel(name: String) {
    Text(name.uppercase(), color = LocalPalette.current.muted, fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

/**
 * One group of choices on the log screen, tight like the variants above it (mockup intervals-variant-groups.html, 2): a small
 * label and a row of chips. A group whose options all have a color (bands) is a row of dots only: the picked ones ringed,
 * "×2" on a doubled one, hold for one more. A single choice: tap again to clear it.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupChips(g: app.intervals.core.Spec, value: String?, onChange: (String?) -> Unit) {
    val p = LocalPalette.current
    val dots = g.choices.isNotEmpty() && g.choices.all { it in g.colors }
    // a group of colors is one slim line: its name at the left, the dots after it (mockup intervals-range-bands.html, B)
    val content: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(if (dots) 8.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            g.choices.forEach { c ->
                val n = if (g.multiple) g.counts(value).firstOrNull { it.first == c }?.second ?: 0 else if (value == c) 1 else 0
                val on = n > 0
                val tap = { onChange(if (g.multiple) g.tap(value, c) else if (on) null else c) }
                val more = if (g.multiple) ({ onChange(g.tap(value, c, more = true)) }) else null
                if (dots) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .border(if (on) 2.5.dp else 0.dp, if (on) p.text else Color.Transparent, CircleShape)
                            .semantics { selected = on; contentDescription = c + if (n > 1) " ×$n" else "" }
                            .combinedClickable(onClick = tap, onLongClick = more, onLongClickLabel = if (more != null) "One more $c" else null)
                            .padding(if (on) 6.dp else 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(g.colors.getValue(c))).border(1.dp, Color(0x55FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (n > 1) Text("×$n", color = if (Color(g.colors.getValue(c)).luminance() > 0.4f) Color(0xFF111111) else Color.White,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val shape = RoundedCornerShape(10.dp)
                    Text(
                        c + if (n > 1) " ×$n" else "", color = if (on) p.onAccent else p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.heightIn(min = 36.dp).clip(shape).background(if (on) p.accent else p.surface)
                            .border(1.dp, if (on) p.accent else p.line, shape).semantics { selected = on }
                            .combinedClickable(onClick = tap, onLongClick = more).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
    if (dots) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(g.name.uppercase(), color = p.muted, fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        content()
    } else Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        GroupLabel(g.name)
        content()
    }
}

/** The load text without the colored specs (bands), which are drawn as dots by [ColorDots]. */
private fun GymSet.plainLoad(ex: Exercise): String {
    // 0 kg is body weight: nothing to say (it stacked "0" over "kg" in the narrow column)
    val colored = ex.specs.filter { it.colors.isNotEmpty() }.map { it.name }.toSet()
    return copy(specs = specs - colored, weightKg = weightKg?.takeIf { it != 0.0 }).loadText(ex)
}

/** A small dot per band (a doubled one twice), before the load. */
@Composable
private fun ColorDots(colors: List<Long>) {
    if (colors.isEmpty()) return
    Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        colors.forEach { c -> Box(Modifier.size(10.dp).clip(CircleShape).background(bandDotColor(c)).border(1.dp, Color(0x66FFFFFF), CircleShape)) }
    }
}

/** What a swiped-away load hint is remembered by: exercise, side, variant and the weight it suggested. */
private fun hintKey(exerciseId: Long, side: Side?, variant: String?, hint: LoadHint) = "$exerciseId|${side ?: ""}|${variant ?: ""}|${hint.toKg}"

/** A row you can swipe away to either side. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeAway(onGone: () -> Unit, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v != androidx.compose.material3.SwipeToDismissBoxValue.Settled) { onGone(); true } else false },
    )
    androidx.compose.material3.SwipeToDismissBox(state, backgroundContent = {}) { content() }
}
