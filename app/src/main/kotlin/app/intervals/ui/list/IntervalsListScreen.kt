@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.intervals.ui.list

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Block
import app.intervals.core.SavedWorkout
import app.intervals.core.Session
import app.intervals.core.day
import app.intervals.core.valuesLine
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import app.intervals.core.Workout
import app.intervals.core.formatDuration
import app.intervals.core.roundBar
import app.intervals.core.seriesCount
import app.intervals.core.totalSec
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import kotlin.math.roundToInt

@Composable
fun IntervalsListScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val all by vm.workouts.collectAsStateWithLifecycle()
    val lastRuns by vm.lastByWorkout.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var reordering by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var askTags by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val context = LocalContext.current
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text == null) vm.message = "Could not read the file" else vm.importHybrid(text)
        }
    }
    BackHandler(enabled = selecting || reordering) { selected = emptySet(); reordering = false }

    val list = all ?: return
    if (reordering) {
        ReorderList(list, onDone = { ids -> vm.reorder(ids); reordering = false })
        return
    }
    val tags = list.flatMap { it.workout.tags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    val shown = list.filter {
        (tag == null || tag in it.workout.tags) && (query.isBlank() || it.workout.name.contains(query.trim(), ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(AppIcons.Close, "Cancel selection", tint = p.text) }
                    Text("${selected.size} selected", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = shown.map { it.id }.toSet() }) { Text("All", color = p.accentText) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Intervals", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("${list.size} workouts", color = p.muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { pickCsv.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) {
                        Icon(AppIcons.Import, contentDescription = "Import from Hybrid Interval Timer", tint = p.text)
                    }
                    IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(AppIcons.More, "More options", tint = p.text) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Reorder") }, onClick = { menu = false; reordering = true })
                            DropdownMenuItem(text = { Text("Select…") }, onClick = { menu = false; selected = setOfNotNull(shown.firstOrNull()?.id) })
                        }
                    }
                }
            }

            if (list.isEmpty()) {
                EmptyState(onImport = { pickCsv.launch(arrayOf("text/*", "*/*")) }, onNew = vm::newWorkout)
                return@Column
            }

            SearchField(query) { query = it }
            if (tags.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TagChip("All ${list.size}", tag == null) { tag = null }
                    tags.forEach { (t, n) -> TagChip("$t $n", tag == t) { tag = if (tag == t) null else t } }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (selecting) 16.dp else 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { w ->
                    WorkoutCard(
                        w,
                        last = lastRuns[w.id],
                        selecting = selecting,
                        selected = w.id in selected,
                        // tap = start; the button on the right = edit
                        onOpen = {
                            if (selecting) selected = if (w.id in selected) selected - w.id else selected + w.id
                            else vm.player(w.id)
                        },
                        onLongPress = { selected = selected + w.id },
                        onEdit = { vm.edit(w) },
                    )
                }
            }
            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().background(p.sheet).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BigButton("Tag…", p.accent, p.onAccent, { askTags = true }, Modifier.weight(1.2f))
                    BigButton("Duplicate", p.tint, p.accentText, { vm.duplicateMany(selected); selected = emptySet() }, Modifier.weight(1f))
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(p.tint).clickable { askDelete = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Delete, "Delete selected", tint = p.dangerText) }
                }
            }
        }
        if (list.isNotEmpty() && !selecting) {
            ExtendedFloatingActionButton(
                onClick = vm::newWorkout,
                containerColor = p.accent,
                contentColor = p.onAccent,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(AppIcons.Plus, null, Modifier.size(22.dp)) },
                text = { Text("New workout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            )
        }
    }

    if (askTags) {
        val chosen = list.filter { it.id in selected }
        TagsDialog(
            title = "Tags for ${chosen.size} workouts",
            allTags = tags.map { it.first },
            selected = chosen.map { it.workout.tags },
            onDismiss = { askTags = false },
            onDone = { add, remove -> vm.retag(selected, add, remove); askTags = false; selected = emptySet() },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete ${selected.size} workouts?") },
            text = { Text("Their sessions stay in the journal.") },
            confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteMany(selected); selected = emptySet() }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * A tag is ticked when all selected items have it, half-ticked (–) when only some do.
 * Ticking gives it to all; unticking takes it from all; a half one left alone stays as it is.
 */
@Composable
internal fun TagsDialog(title: String, allTags: List<String>, selected: List<Set<String>>, onDismiss: () -> Unit, onDone: (Set<String>, Set<String>) -> Unit) {
    val p = LocalPalette.current
    // null = leave as it is (some have it), true = all, false = none
    val state = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean?>().apply {
            allTags.forEach { t ->
                val n = selected.count { t in it }
                put(t, when (n) { 0 -> false; selected.size -> true; else -> null })
            }
        }
    }
    val order = remember { mutableStateListOf<String>().apply { addAll(allTags) } }
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                order.forEach { t ->
                    val v = state[t]
                    Row(Modifier.fillMaxWidth().height(44.dp).clickable { state[t] = v != true }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.TriStateCheckbox(
                            state = when (v) { true -> androidx.compose.ui.state.ToggleableState.On; false -> androidx.compose.ui.state.ToggleableState.Off; null -> androidx.compose.ui.state.ToggleableState.Indeterminate },
                            onClick = { state[t] = v != true },
                            colors = CheckboxDefaults.colors(checkedColor = p.accent),
                        )
                        Text(t, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (v == null) Text("some", color = p.muted, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newTag, { newTag = it }, label = { Text("New tag") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = newTag.isNotBlank(), onClick = {
                        val t = newTag.trim()
                        if (t !in order) order += t
                        state[t] = true
                        newTag = ""
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val extra = newTag.trim().takeIf { it.isNotEmpty() }
                val add = state.filterValues { it == true }.keys + listOfNotNull(extra)
                val remove = state.filterValues { it == false }.keys
                onDone(add, remove)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Drag the handles to put the workouts in your own order; Done saves it. */
@Composable
private fun ReorderList(list: List<SavedWorkout>, onDone: (List<Long>) -> Unit) {
    val p = LocalPalette.current
    val items = remember(list) { mutableStateListOf<SavedWorkout>().apply { addAll(list) } }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reorder", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Drag the handles", color = p.muted, fontSize = 13.sp)
            }
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(p.accent).clickable { onDone(items.map { it.id }) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Done", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp)) {
            items.forEach { w ->
                val isDragged = dragging == w.id
                Row(
                    Modifier.zIndex(if (isDragged) 1f else 0f)
                        .offset { IntOffset(0, if (isDragged) dragY.roundToInt() else 0) }
                        .fillMaxWidth().height(58.dp)
                        .then(if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(14.dp)) else Modifier)
                        .clip(RoundedCornerShape(14.dp)).background(if (isDragged) p.tint else p.surface)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(w.workout.name, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatDuration(w.workout.totalSec), color = p.muted, style = NumberStyle, fontSize = 14.sp)
                    Box(
                        Modifier.size(52.dp).pointerInput(w.id) {
                            detectDragGestures(
                                onDragStart = { dragging = w.id; dragY = 0f },
                                onDragEnd = { dragging = null; dragY = 0f },
                                onDragCancel = { dragging = null; dragY = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val i = items.indexOfFirst { it.id == w.id }
                                if (dragY > rowPx / 2 && i < items.lastIndex) { items.add(i + 1, items.removeAt(i)); dragY -= rowPx }
                                else if (dragY < -rowPx / 2 && i > 0) { items.add(i - 1, items.removeAt(i)); dragY += rowPx }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { DragHandle(p.faint) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DragHandle(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.size(4.dp).clip(CircleShape).background(color)) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, tint = p.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search workouts", color = p.muted, fontSize = 15.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TagChip(label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (on) p.accent else Color.Transparent)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) p.onAccent else p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkoutCard(
    saved: SavedWorkout,
    last: Session?,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
) {
    val p = LocalPalette.current
    val w = saved.workout
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) p.tint else p.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.line, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(start = if (selecting) 4.dp else 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onOpen() }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(w.totalSec), color = p.text, style = NumberStyle, fontSize = 17.sp)
            }
            RoundBar(w, 6)
            Text(meta(w), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (last != null) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (last.completed) p.pink else p.yellow))
                    Spacer(Modifier.width(6.dp))
                }
                Text(lastLine(last), color = p.soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!selecting) {
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(p.tint).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Edit, "Edit ${w.name}", tint = p.accentText, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** One round as colored pieces sized by time. */
@Composable
fun RoundBar(w: Workout, heightDp: Int) {
    val pieces = w.roundBar()
    Row(Modifier.fillMaxWidth().height(heightDp.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        pieces.forEach { (sec, color) ->
            Box(Modifier.weight(sec.toFloat().coerceAtLeast(0.5f)).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color(color)))
        }
    }
}

/** "Last: Tue · 4/4 · hang · Load L 10 · R 8 kg", or "Never done yet". */
private fun lastLine(s: Session?): String {
    if (s == null) return "Never done yet"
    val zone = ZoneId.systemDefault()
    val d = s.day(zone)
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(d, today)
    val whenText = when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 7 -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        else -> "${d.dayOfMonth} " + d.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    }
    val rounds = if (s.completed) "${s.roundsTotal}/${s.roundsTotal}" else "stopped in round ${(s.roundsDone + 1).coerceAtMost(s.roundsTotal)}/${s.roundsTotal}"
    return listOfNotNull("Last: $whenText", rounds, s.valuesLine()).joinToString(" · ")
}

private fun meta(w: Workout): String = buildList {
    if (w.tags.isNotEmpty()) add(w.tags.sorted().joinToString(", "))
    add(if (w.rounds == 1) "1 round" else "${w.rounds} rounds")
    when {
        w.rounds > 1 && w.restBetweenRoundsSec > 0 -> add("${formatDuration(w.restBetweenRoundsSec)} between")
        w.blocks.any { b -> b is Block.Single && b.series.perSide != null } -> add("per side")
        w.warmupSec > 0 -> add("warm-up ${formatDuration(w.warmupSec)}")
        else -> add("${w.seriesCount} series")
    }
}.joinToString(" · ")

@Composable
private fun EmptyState(onImport: () -> Unit, onNew: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No workouts yet", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Import your Hybrid Interval Timer backup (.csv), or make a new one.", color = p.muted, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        BigButton("Import from Hybrid", p.accent, p.onAccent, onImport)
        Spacer(Modifier.height(10.dp))
        BigButton("New workout", p.tint, p.accentText, onNew)
    }
}

@Composable
fun BigButton(label: String, bg: Color, fg: Color, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

