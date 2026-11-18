package app.intervals.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.editor.SectionTitle

private val STEPS = listOf(0.5, 1.0, 1.25, 2.5, 5.0)
private val RESTS = listOf(60, 90, 120, 150, 180, 240, 300)

/** Edit exercise (mockup G16): name, notes, fields, steps, rest, per side, assist; merge another into it; delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExerciseScreen(vm: AppViewModel, exerciseId: Long) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val counts by vm.exerciseCounts.collectAsStateWithLifecycle()
    val original = exercises.firstOrNull { it.id == exerciseId } ?: return
    var e by remember(exerciseId) { mutableStateOf(original) }
    var merging by remember { mutableStateOf(false) }
    var mergeFrom by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val (nSets, first) = counts[exerciseId] ?: (0 to null)
    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close without saving", tint = p.text) }
            Text("Edit exercise", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val ok = e.name.isNotBlank() && e.fields.isNotEmpty() &&
                exercises.none { it.id != e.id && it.name.equals(e.name.trim(), ignoreCase = true) }
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (ok) p.accent else p.tint)
                    .clickable(enabled = ok) { vm.saveExercise(e.copy(name = e.name.trim(), notes = e.notes.trim())) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = if (ok) p.onAccent else p.faint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Name · renaming keeps all your history", color = p.muted, fontSize = 12.sp)
                BasicTextField(
                    e.name, { e = e.copy(name = it) }, singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
            }
            Column {
                Text("Notes (how you do it)", color = p.muted, fontSize = 12.sp)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).padding(12.dp)) {
                    if (e.notes.isEmpty()) Text("e.g. 20 mm edge, open hand", color = p.faint, fontSize = 15.sp)
                    BasicTextField(e.notes, { e = e.copy(notes = it) }, textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
                }
            }

            SectionTitle("What you write down")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GymField.entries.forEach { f ->
                    val on = f in e.fields
                    Pill(f.name.lowercase().replaceFirstChar { it.uppercase() }, on) {
                        val fields = if (on) e.fields - f else (e.fields + f).sortedBy { it.ordinal }
                        if (fields.isNotEmpty()) e = e.copy(fields = fields, assist = e.assist && GymField.WEIGHT in fields)
                    }
                }
            }
            if (GymField.WEIGHT in e.fields) {
                Column {
                    Text("Step for − / + on the weight", color = p.text, fontSize = 15.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STEPS.forEach { st -> Pill(formatKg(st) + " kg", e.weightStep == st, Modifier.weight(1f)) { e = e.copy(weightStep = st) } }
                    }
                }
            }
            Column {
                Text("Rest: buzz me after", color = p.text, fontSize = 15.sp)
                Text("the clock counts up and keeps going", color = p.muted, fontSize = 12.sp)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RESTS.forEach { r -> Pill(formatTime(r), e.restSec == r) { e = e.copy(restSec = r) } }
                }
            }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp)) {
                SwitchLine("Left and right apart", "each set is L or R; turning it off keeps old sets with their side", e.perSide) { e = e.copy(perSide = it) }
                if (GymField.WEIGHT in e.fields) SwitchLine("Assisted (weight taken off)", "shown as −20 kg; less is better", e.assist) { e = e.copy(assist = it) }
            }

            Text(
                if (nSets == 0) "Not logged yet." else "In your log: $nSets sets since ${first?.let(::shortDay)}",
                color = p.muted, fontSize = 12.sp,
            )
            ActionLine("Merge another exercise into this", "its sets move here, then it's gone", p.accentText) { merging = true }
            ActionLine("Delete exercise" + if (nSets > 0) " and its $nSets sets…" else "…", null, p.dangerText) { deleting = true }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (merging) {
        val others = exercises.filter { it.id != exerciseId }.sortedByDescending { similarity(it.name, original.name) }
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Merge into “${original.name}”") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Only if it is really the same exercise. Similar names first.", color = p.muted, fontSize = 13.sp)
                    others.forEach { o ->
                        val n = counts[o.id]?.first ?: 0
                        Row(Modifier.fillMaxWidth().clickable { mergeFrom = o; merging = false }.padding(vertical = 10.dp)) {
                            Text(o.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("$n sets", color = p.muted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }
    mergeFrom?.let { from ->
        val n = counts[from.id]?.first ?: 0
        AlertDialog(
            onDismissRequest = { mergeFrom = null },
            title = { Text("Merge “${from.name}”?") },
            text = { Text("Its $n sets move to “${original.name}”, with their sides and notes, and “${from.name}” is deleted. This can't be undone (a backup from Settings can).") },
            confirmButton = { TextButton(onClick = { vm.mergeExercise(from, original); mergeFrom = null }) { Text("Merge $n sets") } },
            dismissButton = { TextButton(onClick = { mergeFrom = null }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${original.name}”?") },
            text = { Text(if (nSets > 0) "Its $nSets sets are deleted too." else "It has no sets.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteExercise(original) }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/** Words in common, to put "Block Pulls Squeezed" near "Block Pulls". */
private fun similarity(a: String, b: String): Int {
    val wa = a.lowercase().split(' ', '(', ')').filter { it.length > 1 }.toSet()
    val wb = b.lowercase().split(' ', '(', ')').filter { it.length > 1 }.toSet()
    return (wa intersect wb).size
}

@Composable
private fun Pill(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.tint else p.surface)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SwitchLine(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, color = p.text, fontSize = 15.sp)
            Text(sub, color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun ActionLine(label: String, sub: String?, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable(onClick = onClick).padding(16.dp)) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = p.muted, fontSize = 12.sp)
    }
}
