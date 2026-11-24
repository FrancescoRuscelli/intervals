package app.intervals.ui.gym

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.intervals.core.isWarmup
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import app.intervals.core.specColors
import app.intervals.core.specsText
import app.intervals.core.loadText
import app.intervals.core.boxText
import app.intervals.core.Side
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.GymSet
import app.intervals.core.SetRow
import app.intervals.core.dayRows
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.summary
import app.intervals.core.topSet
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.labelColor
import app.intervals.ui.NumberStyle
import app.intervals.ui.list.BigButton
import app.intervals.ui.list.TagsDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Exercise library and the day opened from Journal or an active workout. */
@Composable
fun GymScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val route = vm.gymDayRoute
    if (route != null) {
        val byId = remember(exercises) { exercises.associateBy { it.id } }
        BackHandler(enabled = vm.overlays.isEmpty(), onBack = vm::closeGymDay)
        key(route.day) { DayPage(vm, route.day, byId) }
        return
    }

    val days by vm.gymDays.collectAsStateWithLifecycle()
    val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewFitNotes) }
    // each exercise's tags under its name, as in Add exercise (mockup intervals-tag-dots.html, 3)
    val tagsOf = remember(groups) { exerciseTags(groups) }
    // the search finds a tag too ("legs" lists the leg exercises)
    val shown = remember(exercises, query, tagsOf) {
        val q = query.trim()
        exercises.filter { it.name.contains(q, ignoreCase = true) || tagsOf[it.id]?.any { (t, _) -> t.contains(q, ignoreCase = true) } == true }
    }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var askTags by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting && vm.overlays.isEmpty()) { selection = emptySet() }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selection = emptySet() }) { Icon(AppIcons.Close, "Cancel selection", tint = p.text) }
                Text("${selection.size} selected", color = p.text, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { askTags = true }) { Text("Tag…", color = p.accentText) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Gym", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::openSettings) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
            }
            val running = today in vm.activeGroups
            BigButton(
                if (running) "Resume workout" else "+ Add exercise", p.tint, p.accentText,
                { if (running) vm.openGymDay(today) else vm.openGymPick(today) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        OutlinedTextField(
            query, { query = it }, label = { Text("Search exercises") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (shown.isEmpty()) item {
                Text(if (query.isBlank()) "No exercises yet." else "No matching exercises.", color = p.muted)
            }
            items(shown, key = { it.id }) { ex ->
                ExerciseRow(ex, null, lastUsed[ex.id], selected = ex.id in selection, tags = tagsOf[ex.id].orEmpty(), onLongClick = { selection += ex.id }) {
                    if (selecting) selection = if (ex.id in selection) selection - ex.id else selection + ex.id
                    else vm.openGymLog(ex.id, today)
                }
            }
            if (days?.isEmpty() == true && !selecting) item {
                BigButton("Import from FitNotes", p.surface, p.text, { pick.launch(arrayOf("*/*")) })
            }
        }
    }

    if (askTags) {
        val tags = groups.filter { it.isTag }.groupBy { it.name.lowercase() }.values
        TagsDialog(
            title = if (selection.size == 1) "Tags for 1 exercise" else "Tags for ${selection.size} exercises",
            allTags = tags.map { it.first().name },
            selected = selection.map { id ->
                tags.filter { sameName -> sameName.any { tag -> tag.items.any { it.exerciseId == id } } }
                    .map { it.first().name }.toSet()
            },
            onDismiss = { askTags = false },
            onDone = { add, remove ->
                vm.retagExercises(selection, add, remove)
                askTags = false
                selection = emptySet()
            },
        )
    }
}

@Composable
private fun DayPage(
    vm: AppViewModel, day: String, byId: Map<Long, Exercise>,
) {
    val p = LocalPalette.current
    val sets by remember(day) { vm.gymDay(day) }.collectAsStateWithLifecycle(null)
    val loggedById = remember(sets) { sets.orEmpty().groupBy { it.exerciseId } }
    val allGroups by vm.groups.collectAsStateWithLifecycle()
    val run = vm.activeGroups[day]
    val group = run?.let { allGroups.firstOrNull { group -> group.id == it.id } }
    if (run != null && group != null) {
        if (sets != null) GymGroupRun(vm, day, group, run, byId, loggedById)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeGymDay) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Column(Modifier.weight(1f)) {
                Text(dayTitle(day), color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val n = sets?.size ?: 0
                Text(if (n == 0) "No sets" else "$n set${if (n == 1) "" else "s"} · ${loggedById.size} exercise${if (loggedById.size == 1) "" else "s"}", color = p.muted, fontSize = 13.sp)
            }
            IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
        }
        val ordered = remember(sets) { sets.orEmpty().sortedBy { it.order } }
        // hold an exercise, then Move sets…: the sets logged under the wrong exercise (mockup intervals-move-sets.html, C)
        var moving by remember(day) { mutableStateOf<Long?>(null) }
        moving?.let { id ->
            val from = byId[id]
            val its = ordered.filter { it.exerciseId == id }
            if (from == null || its.isEmpty()) moving = null
            else MoveSetsSheet(from, its, byId.values.toList(), day, onDismiss = { moving = null }) { ids, into, variant ->
                vm.moveGymSets(ids, into, variant)
                moving = null
            }
        }
        val stripColor = remember(sets, allGroups) { app.intervals.core.dayStrip(ordered, allGroups).associate { it.exerciseId to it.color } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ordered.isNotEmpty()) item {
                DayTimeline(ordered, byId, stripColor, onMove = { moving = it }) { vm.openGymLog(it, day) }
            }
            if (sets?.isEmpty() == true) item {
                Text("Nothing logged on this day.", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (sets != null) item {
                BigButton("+ Add exercise", p.tint, p.accentText, { vm.openGymPick(day) })
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val dayYearFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

private fun dayTitle(day: String): String {
    val d = LocalDate.parse(day)
    val today = LocalDate.now()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(dayFormat)
        else -> d.format(dayYearFormat)
    }
}

/**
 * The day as a timeline (mockup intervals-gym-day-ideas.html, E): every set in the order done, under its exercise's
 * name with a pink summary on the right; the L and R sets of a round share one row. The rest before a set (or a round)
 * is on the right, yellow over 5 min. A strip in the exercise's tag color runs down the left. Tapping an exercise or a
 * set opens the exercise to log or edit.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DayTimeline(sets: List<GymSet>, byId: Map<Long, Exercise>, stripColor: Map<Long, Long>, onMove: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val p = LocalPalette.current
    // the run whose menu is open (hold an exercise or one of its sets)
    var menu by remember { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val runs = remember(sets) {
        buildList<MutableList<GymSet>> { sets.forEach { s -> if (lastOrNull()?.last()?.exerciseId == s.exerciseId) last() += s else add(mutableListOf(s)) } }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(vertical = 8.dp)) {
        runs.forEachIndexed { i, run ->
            val ex = byId[run.first().exerciseId] ?: return@forEachIndexed
            val strip = stripColor[ex.id]?.let { Color(it) } ?: p.accent
            val banded = ex.specs.any { it.colors.isNotEmpty() }
            val rows = pairSides(run)
            // one band (or spec) on every set: said once in the summary, not on each row
            val common = run.map { it.withoutColors(ex).specsText(ex) }.distinct().singleOrNull()?.takeIf { it.isNotEmpty() }
            // the loads get one width, so the reps line up in a column
            val loadWidth = with(density) { run.maxOf { measurer.measure(loadOf(it, ex, banded, common), TextStyle(fontSize = 13.sp)).size.width }.toDp() + 2.dp }
            Box {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClickLabel = "Log ${ex.name}", onLongClickLabel = "More", onClick = { onOpen(ex.id) }, onLongClick = { menu = i })
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ex.name, color = p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(runSummary(run, rows, ex, common), color = p.workText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = menu == i, onDismissRequest = { menu = null }, containerColor = p.sheet) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Column { Text("Move sets…", color = p.accentText, fontWeight = FontWeight.Bold); Text("to another exercise; pick which", color = p.muted, fontSize = 12.sp) } },
                        onClick = { menu = null; onMove(ex.id) },
                    )
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Open ${ex.name}", color = p.text) }, onClick = { menu = null; onOpen(ex.id) })
                }
            }
            // the rests get one column on every row (empty where there is none), so the L / R halves line up
            val restWidth = if (rows.any { r -> (r.minBy { it.order }.restSec ?: 0) > 0 })
                with(density) { measurer.measure("rest 10:00", TextStyle(fontSize = 12.sp)).size.width.toDp() + 10.dp } else 0.dp
            rows.forEach { r -> TimelineRow(r, ex, strip, banded, common, loadWidth, restWidth, onHold = { menu = i }) { onOpen(ex.id) } }
        }
    }
}

/** The sets of a run as rows: an L set and the R set right after it (or R then L) share a row. */
internal fun pairSides(run: List<GymSet>): List<List<GymSet>> = buildList {
    var i = 0
    while (i < run.size) {
        val s = run[i]
        val next = run.getOrNull(i + 1)
        // a round: an L and an R right after each other, in either order; L always on the left
        if (s.side != null && next?.side != null && next.side != s.side) { add(if (s.side == Side.LEFT) listOf(s, next) else listOf(next, s)); i += 2 }
        else { add(listOf(s)); i++ }
    }
}

/** "4 rounds · black", "L 5 · R 7" (uneven sides), "9 × 1 · 4 bands", "6 sets · 35 reps". */
private fun runSummary(all: List<GymSet>, rows: List<List<GymSet>>, ex: Exercise, common: String?): String {
    // warm-ups are left out of the count and only mentioned at the end
    val run = all.filterNot { it.isWarmup }.ifEmpty { all }
    val warm = (all.size - run.size).takeIf { it > 0 }?.let { "+ $it warm-up" }
    val left = run.count { it.side == Side.LEFT }
    val right = run.count { it.side == Side.RIGHT }
    val head = when {
        rows.all { it.size == 2 } -> "${rows.size} round${if (rows.size == 1) "" else "s"}"
        left + right > 0 -> "L $left · R $right"
        run.size > 1 && run.map { it.boxText(ex) }.distinct().size == 1 -> "${run.size} × ${run.first().boxText(ex)}"
        GymField.REPS in ex.fields -> "${run.size} set${if (run.size == 1) "" else "s"} · ${run.sumOf { it.reps ?: 0 }} reps"
        else -> "${run.size} set${if (run.size == 1) "" else "s"}"
    }
    val bands = run.map { it.specColors(ex) }.filter { it.isNotEmpty() }.distinct().size
    val tail = when {
        common != null -> common
        bands > 1 -> "$bands bands"
        else -> null
    }
    return listOfNotNull(head, tail).joinToString(" · ") + (warm?.let { " $it" } ?: "")
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(row: List<GymSet>, ex: Exercise, strip: Color, banded: Boolean, common: String?, loadWidth: Dp, restWidth: Dp, onHold: () -> Unit, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val first = row.first()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).combinedClickable(onLongClickLabel = "More", onClick = onOpen, onLongClick = onHold).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(strip))
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // each set shows its own band dots (in SetPart), not the first set's for the whole row
            if (first.side != null) {
                // L on the left, R on the right, a line between; a set without its other side leaves that half empty
                val l = row.firstOrNull { it.side == Side.LEFT }
                val r = row.firstOrNull { it.side == Side.RIGHT }
                Box(Modifier.weight(1f)) { l?.let { SetPart(it, ex, banded, common, loadWidth, Modifier) } }
                Box(Modifier.width(1.dp).height(18.dp).background(p.line))
                Box(Modifier.weight(1f).padding(start = 4.dp)) { r?.let { SetPart(it, ex, banded, common, loadWidth, Modifier) } }
            } else SetPart(first, ex, banded, common, loadWidth, Modifier.weight(1f))
        }
        // the rest before the row: before the set done first
        if (restWidth > 0.dp) {
            val r = row.minBy { it.order }.restSec?.takeIf { it > 0 }
            Text(r?.let { "rest ${formatTime(it)}" }.orEmpty(), color = if ((r ?: 0) > 300) p.recordText else p.muted, fontSize = 12.sp, style = NumberStyle,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(restWidth))
        }
    }
}

/** The muted load of a set on the timeline ("15 kg", "purple", "no band"); the band said in the summary is left out. */
private fun loadOf(s: GymSet, ex: Exercise, banded: Boolean, common: String?): String {
    // the bands are dots, not words ("black" was written while "red" was a dot)
    val plain = s.withoutColors(ex)
    val specs = plain.specsText(ex)
    return if (common != null && specs == common) plain.loadText(ex).removePrefix(common).removePrefix(" · ") else plain.loadText(ex)
}

/** The set without its colored specs (bands), which are drawn as dots. */
internal fun GymSet.withoutColors(ex: Exercise): GymSet {
    val colored = ex.specs.filter { it.colors.isNotEmpty() }.map { it.name }.toSet()
    // 0 kg is body weight: left out like the bands' names
    return copy(specs = specs - colored, weightKg = weightKg?.takeIf { it != 0.0 })
}

/** A band's color as drawn on the dark screens: black becomes a dark grey, so it shows (as in the Journal bars). */
internal fun bandDotColor(c: Long): Color = Color(c).let { if (it.luminance() < 0.03f) Color(0xFF3A3D44) else it }

/** One set on a timeline row: side, load, the reps (or time) in bold, the note. */
@Composable
private fun SetPart(s: GymSet, ex: Exercise, banded: Boolean, common: String?, loadWidth: Dp, modifier: Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.side?.let { Text(if (it == Side.LEFT) "L" else "R", color = p.accentText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        // this set's bands as dots; an empty ring for a set without one on a band exercise
        val colors = s.specColors(ex)
        if (colors.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEach { c -> Box(Modifier.size(11.dp).clip(CircleShape).background(bandDotColor(c)).border(1.dp, p.soft, CircleShape)) }
        } else if (banded) Box(Modifier.size(11.dp).border(1.dp, p.muted, CircleShape))
        val load = loadOf(s, ex, banded, common)
        if (loadWidth > 0.dp) Text(load, color = p.muted, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(loadWidth))
        Text((if (s.weightKg != null && s.weightKg != 0.0 && GymField.WEIGHT in ex.fields) "× " else "") + s.boxText(ex), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = NumberStyle)
        if (s.note.isNotBlank()) Text("“${s.note.trim()}”", color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}
