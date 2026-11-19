package app.intervals.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import app.intervals.core.loadBlocks
import app.intervals.core.boxText
import app.intervals.core.variantOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    specs = specs.filterKeys { k -> ex.specs.any { it.name == k } },
    // always written, so reordering the variants later never changes what this set was
    variant = if (ex.variants.isEmpty()) null else variant?.takeIf { it in ex.variants } ?: ex.variants.first(),
)

/** Log sets of one exercise on one day (mockup G2): tiles with − / +, Log set, rest clock counting up, the sets done. */
@Composable
fun GymLogScreen(vm: AppViewModel, exerciseId: Long, day: String) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val ex = exercises.firstOrNull { it.id == exerciseId } ?: return
    val sets by remember(exerciseId, day) { vm.exerciseDay(exerciseId, day) }.collectAsStateWithLifecycle(null)
    val lastTime by produceState<List<GymSet>?>(null, exerciseId, day) { value = vm.lastSets(exerciseId, day) }

    var values by remember(exerciseId) { mutableStateOf<Values?>(null) }
    var side by remember(exerciseId) { mutableStateOf<SideChoice?>(null) }
    var editing by remember { mutableStateOf<GymSet?>(null) }
    var noteFor by remember { mutableStateOf<GymSet?>(null) }
    var typing by remember { mutableStateOf<GymField?>(null) }
    // free timer for timed sets (mockup G18): started at (elapsedRealtime) while running; measured = stopped, time filled in
    var timerFrom by remember(exerciseId) { mutableStateOf<Long?>(null) }
    var measured by remember(exerciseId) { mutableStateOf(false) }
    var typingSpec by remember { mutableStateOf<app.intervals.core.Spec?>(null) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable(exerciseId) { mutableStateOf(0) }

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
    // each side starts from its own last set: switching to L brings back the last L values, R the last R
    LaunchedEffect(side) {
        val today = sets ?: return@LaunchedEffect
        val last = lastTime ?: return@LaunchedEffect
        val s = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> return@LaunchedEffect }
        if (editing == null) startValues(today, last, s)?.let { values = it.toValues() }
    }
    val v = values ?: Values()
    fun change(f: (Values) -> Values) { values = f(v) }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Column(Modifier.weight(1f)) {
                Text(ex.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                val l = lastTime
                Text(
                    when {
                        l == null -> ""
                        l.isEmpty() -> "First time"
                        else -> "Last · ${shortDay(l.first().day)} · " + l.take(4).joinToString(", ") { s ->
                            (s.side?.let { it.letter + " " } ?: "") + s.summary(ex)
                        } + if (l.size > 4) " …" else ""
                    },
                    color = p.muted, fontSize = 13.sp, maxLines = 2,
                )
            }
            IconButton(onClick = { vm.editExercise(exerciseId) }) { Icon(AppIcons.Edit, "Edit exercise", tint = p.muted) }
        }

        // Log · History · Graph · Records
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            listOf("Log", "History", "Graph", "Records").forEachIndexed { i, t ->
                val on = i == tab
                Column(Modifier.clickable { tab = i }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, color = if (on) p.text else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    Box(Modifier.width(36.dp).height(2.dp).background(if (on) p.accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.surface))
        val allSets by remember(exerciseId) { vm.exerciseAll(exerciseId) }.collectAsStateWithLifecycle(emptyList())
        // with variants, History / Graph / Records can show one variant (mockup G19c)
        var variantFilter by androidx.compose.runtime.saveable.rememberSaveable(exerciseId) { mutableStateOf<String?>(null) }
        if (tab != 0 && ex.variants.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + ex.variants).forEach { f ->
                    val on = f == variantFilter
                    Text(f ?: "All", color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) p.tint else p.surface)
                            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(18.dp))
                            .clickable { variantFilter = f }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        val shown = if (variantFilter == null) allSets else allSets.filter { it.variantOf(ex) == variantFilter }
        when (tab) {
            1 -> Box(Modifier.weight(1f)) { ExerciseHistory(ex, shown) { d -> vm.openGymLog(exerciseId, d) } }
            2 -> Box(Modifier.weight(1f)) { ExerciseGraph(ex, shown) }
            3 -> Box(Modifier.weight(1f)) { ExerciseRecords(ex, shown) }
        }
        if (tab == 0) LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ex.variants.isNotEmpty()) item {
                val cur = v.variant?.takeIf { it in ex.variants } ?: ex.variants.first()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VARIANT \u00B7 STAYS ON THE LAST ONE", color = p.muted, fontSize = 12.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.SemiBold)
                    Segmented(ex.variants, ex.variants.indexOf(cur)) { i -> change { it.copy(variant = ex.variants[i]) } }
                }
            }
            if (ex.perSide && editing == null) item {
                Segmented(listOf("L", "R", "Both"), side?.ordinal ?: 0, listOf(p.pink, p.teal, p.accent)) { side = SideChoice.entries[it] }
            }
            if (ex.specs.isNotEmpty()) item {
                SpecTiles(ex.specs, v.specs, onType = { typingSpec = it }) { name, value ->
                    change { it.copy(specs = if (value == null) it.specs - name else it.specs + (name to value)) }
                }
            }
            item {
                // time has its own card with the timer, below
                val tiles = listOf(GymField.WEIGHT, GymField.REPS, GymField.DISTANCE).filter { it in ex.fields }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.forEach { f ->
                        val (label, text, unit) = when (f) {
                            GymField.WEIGHT -> Triple("Weight", formatKg(v.weightKg), "kg")
                            GymField.REPS -> Triple("Reps", "${v.reps}", "")
                            else -> Triple("Distance", formatKg(v.distance), "")
                        }
                        StepTile(label, text, unit, Modifier.weight(1f),
                            onTap = { typing = f },
                            onMinus = { change { it.step(f, ex, -1) } },
                            onPlus = { change { it.step(f, ex, +1) } })
                    }
                }
            }
            if (GymField.TIME in ex.fields) item {
                val sideNow = when (side) { SideChoice.LEFT -> Side.LEFT; SideChoice.RIGHT -> Side.RIGHT; else -> null }
                val last = lastTime.orEmpty().let { l -> (sideNow?.let { sd -> l.firstOrNull { it.side == sd } } ?: l.firstOrNull())?.timeSec }
                TimerCard(
                    seconds = v.timeSec, startedAt = timerFrom, measured = measured, side = sideNow,
                    lastText = last?.let { "last time " + formatTime(it) + when (sideNow) { Side.LEFT -> " on the left"; Side.RIGHT -> " on the right"; null -> "" } },
                    onType = { typing = GymField.TIME },
                    onStart = { timerFrom = android.os.SystemClock.elapsedRealtime(); measured = false },
                    onStop = { ms -> change { it.copy(timeSec = ((ms + 500) / 1000).toInt()) }; timerFrom = null; measured = true },
                    onNudge = { d -> change { it.copy(timeSec = (it.timeSec + d).coerceAtLeast(0)) } },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val e = editing
                    if (e == null) {
                        val timing = timerFrom != null
                        BigButton(
                            if (timing) "Stop the timer first"
                            else when (side) { SideChoice.LEFT -> "Log set · L"; SideChoice.RIGHT -> "Log set · R"; SideChoice.BOTH -> "Log L + R"; null -> "Log set" } +
                                (if (ex.variants.isNotEmpty()) " \u00B7 " + (v.variant?.takeIf { it in ex.variants } ?: ex.variants.first()) else ""),
                            if (timing) p.tint else p.accent, if (timing) p.faint else p.onAccent,
                            {
                                if (!timing) {
                                    vm.logSets(side.sides().map { s -> v.into(GymSet(exerciseId = ex.id, day = day, order = 0, side = s), ex) }, ex.restSec)
                                    side = side?.afterSet()
                                    measured = false
                                }
                            },
                            Modifier.weight(1f),
                        )
                    } else {
                        BigButton("Cancel", p.tint, p.accentText, { editing = null; values = (sets?.lastOrNull() ?: e).toValues() }, Modifier.weight(1f))
                        BigButton("Update set", p.accent, p.onAccent, { vm.updateGymSet(v.into(e, ex)); editing = null }, Modifier.weight(1.4f))
                    }
                }
            }
            if (day == LocalDate.now().toString() && editing == null) item {
                vm.gymRest?.takeIf { it.day == day && it.visible }?.let { RestClock(it, vm::dismissGymRest) }
            }

            val list = sets.orEmpty()
            if (list.isNotEmpty()) item {
                Text("SETS · ${list.size}", color = p.muted, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (list.isNotEmpty()) item {
                if (ex.variants.isEmpty()) SetBlocks(list, ex, if (editing == null) side else null, editing?.id) { noteFor = it }
                else VariantBlocks(list, ex, v.variant?.takeIf { it in ex.variants } ?: ex.variants.first(), if (editing == null) side else null, editing?.id) { noteFor = it }
            }
        }
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
    noteFor?.let { s ->
        SetDialog(s, ex, onDismiss = { noteFor = null },
            onSave = { vm.updateGymSet(it); noteFor = null },
            onEditValues = { editing = s; values = s.toValues(); noteFor = null },
            onDelete = { vm.deleteGymSet(s.id); noteFor = null })
    }
}

/** − / + by the exercise's step (weight), 1 (reps), 5 s (time); never below 0. */
private fun Values.step(f: GymField, ex: Exercise, dir: Int): Values = when (f) {
    // the weight can go below 0: assisted (weight taken off)
    GymField.WEIGHT -> copy(weightKg = weightKg + dir * ex.weightStep)
    GymField.REPS -> copy(reps = (reps + dir).coerceAtLeast(0))
    GymField.TIME -> copy(timeSec = (timeSec + dir * 5).coerceAtLeast(0))
    GymField.DISTANCE -> copy(distance = (distance + dir).coerceAtLeast(0.0))
    GymField.BANDS -> this
}

internal fun shortDay(day: String): String {
    val d = LocalDate.parse(day)
    val pattern = if (d.year == LocalDate.now().year) "EEE d MMM" else "d MMM yyyy"
    return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
}

@Composable
private fun StepTile(label: String, value: String, unit: String, modifier: Modifier, onTap: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = p.muted, fontSize = 13.sp, maxLines = 1)
        // every value the same size (21 and 18.5 alike), on a row of fixed height so all tiles line up; very long values shrink a bit
        Row(
            Modifier.height(52.dp).clickable(onClickLabel = "Type $label", onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, color = p.text, fontSize = (if (value.length <= 6) 34 else 26).sp, style = NumberStyle, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) Text(" $unit", color = p.muted, fontSize = 15.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 10.dp))
        }
        // − and + are two equal halves of the tile
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(AppIcons.Minus, "$label less", onMinus), Triple(AppIcons.Plus, "$label more", onPlus)).forEach { (icon, desc, action) ->
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).clickable(onClickLabel = desc, onClick = action),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, desc, tint = p.accentText, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

/**
 * The day's sets as blocks by load (mockup G17f). Per-side: two columns L | R, the side you're on ([active]) highlighted
 * and the other dimmed; sets without a side go below, full width. Each box is one set (reps, or time); tap it to open the set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetBlocks(sets: List<GymSet>, ex: Exercise, active: SideChoice?, editingId: Long?, onOpen: (GymSet) -> Unit) {
    if (!ex.perSide) {
        SideColumn(null, sets, ex, highlighted = false, dimmed = false, editingId, onOpen, Modifier.fillMaxWidth())
        return
    }
    val left = sets.filter { it.side == Side.LEFT }
    val right = sets.filter { it.side == Side.RIGHT }
    val none = sets.filter { it.side == null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Side.LEFT to left, Side.RIGHT to right).forEach { (sd, list) ->
                val on = active == SideChoice.BOTH || (active == SideChoice.LEFT && sd == Side.LEFT) || (active == SideChoice.RIGHT && sd == Side.RIGHT)
                SideColumn(sd, list, ex, highlighted = on, dimmed = active != null && !on, editingId, onOpen, Modifier.weight(1f),
                    next = on && active != SideChoice.BOTH)
            }
        }
        if (none.isNotEmpty()) SideColumn(null, none, ex, highlighted = false, dimmed = false, editingId, onOpen, Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideColumn(
    side: Side?, sets: List<GymSet>, ex: Exercise, highlighted: Boolean, dimmed: Boolean, editingId: Long?,
    onOpen: (GymSet) -> Unit, modifier: Modifier, next: Boolean = false,
) {
    val p = LocalPalette.current
    Column(
        // the side's own color: pink L, teal R (purple only without a side)
        modifier.alpha(if (dimmed) 0.55f else 1f).clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) side?.let { p.sideTint(it) } ?: p.group else p.surface)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) side?.let { p.sideColor(it) } ?: p.accent else p.line, RoundedCornerShape(16.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (side != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(p.sideColor(side)), contentAlignment = Alignment.Center) {
                Text(side.letter, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("  ${sets.size} set${if (sets.size == 1) "" else "s"}", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (next) Text("next", color = p.sideColor(side), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (sets.isEmpty()) Text("none yet", color = p.faint, fontSize = 13.sp)
        loadBlocks(sets, ex).forEach { b ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(b.label, color = p.text, fontSize = 17.sp, style = NumberStyle, maxLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.sets.forEach { s ->
                        val editing = s.id == editingId
                        Box(
                            Modifier.heightIn(min = 36.dp).widthIn(min = 36.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (editing) p.accent else p.bg)
                                .border(1.dp, if (s.note.isNotEmpty()) p.pink else p.line, RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = "Set ${s.boxText(ex)}" + if (s.note.isNotEmpty()) ", note: ${s.note}" else "") { onOpen(s) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s.boxText(ex), color = if (editing) p.onAccent else p.text, fontSize = 16.sp, style = NumberStyle)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetDialog(s: GymSet, ex: Exercise, onDismiss: () -> Unit, onSave: (GymSet) -> Unit, onEditValues: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    var note by remember(s.id) { mutableStateOf(s.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((s.side?.let { it.letter + " · " } ?: "") + s.summary(ex) + (s.restSec?.let { " · rested ${formatTime(it)}" } ?: "")) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEditValues) { Text("Edit values") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete set", color = p.dangerText) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s.copy(note = note.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this set?") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = p.dangerText) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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

@Composable
/** [colors] = the selected background of each option (default: purple). */
private fun Segmented(options: List<String>, selected: Int, colors: List<Color>? = null, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if (i == selected) colors?.getOrNull(i) ?: p.accent else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == selected) p.onAccent else p.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
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
                Text(sp.name + if (sp.multiple) " \u00B7 tap again for \u00D72" else "", color = p.muted, fontSize = 13.sp)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sp.choices.forEach { c ->
                        // "several" (e.g. bands): tap = ×1, ×2, none; else one choice, tap again to clear
                        val n = if (sp.multiple) sp.counts(values[sp.name]).firstOrNull { it.first == c }?.second ?: 0 else if (values[sp.name] == c) 1 else 0
                        val on = n > 0
                        val color = sp.colors[c]?.let { Color(it) }
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.accent else p.bg)
                                .clickable { onChange(sp.name, if (sp.multiple) sp.tap(values[sp.name], c) else if (on) null else c) }
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
        } else {
            Box(
                Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(20.dp)).background(if (running) p.pink else p.teal)
                    .clickable(onClickLabel = if (running) "Stop the timer" else "Start the timer") { if (running) onStop(elapsed) else onStart() },
                contentAlignment = Alignment.Center,
            ) { Text(if (running) "STOP" else "START", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
        }
    }
}

/** The day's sets split by variant (mockup G19a): one box per variant, the one you're on outlined with "now". */
@Composable
private fun VariantBlocks(sets: List<GymSet>, ex: Exercise, current: String, active: SideChoice?, editingId: Long?, onOpen: (GymSet) -> Unit) {
    val p = LocalPalette.current
    val byVariant = sets.groupBy { it.variantOf(ex) ?: "" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (ex.variants + byVariant.keys.filter { it !in ex.variants }).filter { it in byVariant }.forEach { name ->
            val now = name == current
            val list = byVariant.getValue(name)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (now) p.group else Color.Transparent)
                    .border(if (now) 2.dp else 1.dp, if (now) p.accent else p.line, RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = if (now) p.onAccent else p.accentText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (now) p.accent else p.tint).padding(horizontal = 10.dp, vertical = 3.dp))
                    Text("  ${list.size} set${if (list.size == 1) "" else "s"}", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (now) Text("now", color = p.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                SetBlocks(list, ex, if (now) active else null, editingId, onOpen)
            }
        }
    }
}
