@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.intervals.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Block
import app.intervals.core.SavedWorkout
import app.intervals.core.Workout
import app.intervals.core.formatDuration
import app.intervals.core.roundBar
import app.intervals.core.seriesCount
import app.intervals.core.totalSec
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle

@Composable
fun IntervalsListScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val all by vm.workouts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text == null) vm.message = "Could not read the file" else vm.importHybrid(text)
        }
    }

    val list = all ?: return
    val tags = list.flatMap { it.workout.tags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    val shown = list.filter {
        (tag == null || tag in it.workout.tags) && (query.isBlank() || it.workout.name.contains(query.trim(), ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Intervals", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${list.size} workouts", color = p.muted, fontSize = 13.sp)
                }
                IconButton(onClick = { pickCsv.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) {
                    Icon(AppIcons.Import, contentDescription = "Import from Hybrid Interval Timer", tint = p.text)
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { w ->
                    WorkoutCard(
                        w,
                        canMove = tag == null && query.isBlank(),
                        onOpen = { vm.edit(w) },
                        onPlay = { vm.player(w.id) },
                        onDuplicate = { vm.duplicate(w.id) },
                        onDelete = { vm.delete(w.id, w.workout.name) },
                        onMove = { by -> val ids = list.map { it.id }; val i = ids.indexOf(w.id); vm.move(ids, i, i + by) },
                    )
                }
            }
        }
        if (list.isNotEmpty()) {
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
            .combinedClickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) p.onAccent else p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    saved: SavedWorkout,
    canMove: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val w = saved.workout
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(w.totalSec), color = p.text, style = NumberStyle, fontSize = 17.sp)
            }
            RoundBar(w, 6)
            Text(meta(w), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(p.accent).combinedClickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Play, "Start ${w.name}", tint = p.onAccent, modifier = Modifier.size(20.dp)) }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
            if (canMove) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
            }
            DropdownMenuItem(text = { Text("Delete", color = p.dangerText) }, onClick = { menu = false; confirm = true })
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete “${w.name}”?") },
            text = { Text("Its sessions stay in the journal.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
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

private fun meta(w: Workout): String = buildList {
    w.tags.firstOrNull()?.let { add(it) }
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
        modifier.height(54.dp).clip(RoundedCornerShape(14.dp)).background(bg).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
