package app.intervals.ui.gym

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Band
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The daily training log and the exercise library. */
@Composable
fun GymScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val days by vm.gymDays.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewFitNotes) }
    val list = days ?: return
    val today = LocalDate.now().toString()
    val pages = remember(list, today) { if (list.firstOrNull() == today) list else listOf(today) + list }
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    val pager = rememberPagerState { pages.size }
    var library by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.gymFocusDay, pages) {
        val i = vm.gymFocusDay?.let(pages::indexOf) ?: return@LaunchedEffect
        library = false
        if (i >= 0) pager.scrollToPage(i)
        vm.gymFocusDay = null
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp)).background(p.surface).selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Log", "Library").forEachIndexed { index, label ->
                val selected = library == (index == 1)
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) p.tint else Color.Transparent)
                        .selectable(selected, role = Role.Tab) { library = index == 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) p.accentText else p.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (library) {
            val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
            val shown = remember(exercises, query) { exercises.filter { it.name.contains(query.trim(), ignoreCase = true) } }
            OutlinedTextField(
                query, { query = it }, label = { Text("Search exercises") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                if (shown.isEmpty()) item {
                    Text(if (query.isBlank()) "No exercises yet." else "No matching exercises.", color = p.muted)
                }
                items(shown, key = { it.id }) { ex ->
                    ExerciseRow(ex, null, lastUsed[ex.id]) { vm.openGymLog(ex.id, today) }
                }
            }
        } else {
            HorizontalPager(
                pager, reverseLayout = true, key = { pages[it] }, modifier = Modifier.weight(1f),
                // one day per swipe, even a fast one
                flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
            ) { i ->
                DayPage(vm, pages[i], i, pages.size, pager, byId,
                    onImport = if (list.isEmpty()) ({ pick.launch(arrayOf("*/*")) }) else null)
            }
        }
    }
}

@Composable
private fun DayPage(
    vm: AppViewModel, day: String, index: Int, count: Int, pager: PagerState,
    byId: Map<Long, Exercise>, onImport: (() -> Unit)?,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
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
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayTitle(day), color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val n = sets?.size ?: 0
                Text(if (n == 0) "No sets" else "$n set${if (n == 1) "" else "s"} · ${loggedById.size} exercise${if (loggedById.size == 1) "" else "s"}", color = p.muted, fontSize = 13.sp)
            }
            IconButton(enabled = index < count - 1, onClick = { scope.launch { pager.animateScrollToPage(index + 1) } }) {
                Icon(AppIcons.Back, "Day before", tint = if (index < count - 1) p.text else p.faint)
            }
            IconButton(enabled = index > 0, onClick = { scope.launch { pager.animateScrollToPage(index - 1) } }) {
                Icon(AppIcons.Back, "Day after", tint = if (index > 0) p.text else p.faint, modifier = Modifier.rotate(180f))
            }
            IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(loggedById.entries.toList(), key = { it.key }) { (id, exSets) ->
                byId[id]?.let { ExerciseCard(vm, it, exSets, day) }
            }
            if (sets?.isEmpty() == true) item {
                Text("Nothing logged on this day.", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (sets != null) item {
                BigButton("+ Add exercise", p.tint, p.accentText, { vm.openGymPick(day) })
            }
            if (onImport != null) item {
                BigButton("Import from FitNotes", p.surface, p.text, onImport)
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

/** The columns of the table, in the order shown. */
private fun columns(ex: Exercise): List<GymField> =
    listOf(GymField.WEIGHT, GymField.REPS, GymField.TIME, GymField.DISTANCE).filter { it in ex.fields }

private fun header(f: GymField, ex: Exercise) = when (f) {
    GymField.WEIGHT -> "kg"
    GymField.BANDS -> "bands"
    GymField.REPS -> "reps"
    GymField.TIME -> "time"
    GymField.DISTANCE -> "distance"
}

@Composable
private fun ExerciseCard(vm: AppViewModel, ex: Exercise, sets: List<GymSet>, day: String) {
    val p = LocalPalette.current
    val cols = columns(ex)
    val rows = remember(sets) { dayRows(sets) }
    val last by produceState<List<GymSet>?>(null, ex.id, day) { value = vm.lastSets(ex.id, day) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
            .clickable(onClickLabel = "Log ${ex.name}") { vm.openGymLog(ex.id, day) }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ex.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (sets.size == 1) "1 set" else "${sets.size} sets",
                color = p.workText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Set", color = p.muted, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            cols.forEach { Text(header(it, ex), color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
            Spacer(Modifier.width(40.dp))
        }
        rows.forEach { SetRowView(it, ex, cols) }
        last?.let { l ->
            val top = topSet(l, ex)
            Text(
                if (top == null) "first time" else "last time ${l.size} sets · ${top.summary(ex)}",
                color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SetRowView(row: SetRow, ex: Exercise, cols: List<GymField>) {
    val p = LocalPalette.current
    val s = row.set
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.label, color = p.labelColor(row.label, p.soft), fontSize = 15.sp, style = NumberStyle, modifier = Modifier.width(56.dp))
            cols.forEach { f ->
                Box(Modifier.weight(1f)) {
                    when (f) {
                        else -> Text(cell(f, s, ex), color = p.text, fontSize = 17.sp, style = NumberStyle)
                    }
                }
            }
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
                if (row.count > 1) Text("×${row.count}", color = p.workText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (s.note.isNotEmpty()) {
            Text(s.note, color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 56.dp))
        }
    }
}

private fun cell(f: GymField, s: GymSet, ex: Exercise): String = when (f) {
    GymField.WEIGHT -> s.weightKg?.let(::formatKg) ?: "–"
    GymField.REPS -> s.reps?.toString() ?: "–"
    GymField.TIME -> s.timeSec?.let(::formatTime) ?: "–"
    GymField.DISTANCE -> s.distance?.let(::formatKg) ?: "–"
    GymField.BANDS -> ""
}
