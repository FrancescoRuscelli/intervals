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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
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

/** Edit exercise (mockup G16): name, fields, step, rest, per side, specifications; merge another into it; delete. Assisted = negative weight. */
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
    var askStep by remember { mutableStateOf(false) }
    var askRest by remember { mutableStateOf(false) }
    // the spec being added (null name = new) or changed
    var specEdit by remember { mutableStateOf<Pair<Int?, app.intervals.core.Spec?>?>(null) }
    // variants (mockup G19b): renames carry the sets along when saved; the variant being renamed / added
    var renames by remember(exerciseId) { mutableStateOf(emptyMap<String, String>()) }
    var variantEdit by remember { mutableStateOf<Pair<Int?, String>?>(null) }
    val variantCounts by remember(exerciseId) { vm.variantCounts(exerciseId) }.collectAsStateWithLifecycle(emptyMap())
    var mergeAs by remember { mutableStateOf<String?>(null) }
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
                    .clickable(enabled = ok) { vm.saveExercise(e.copy(name = e.name.trim(), notes = e.notes.trim()), renames, original.variants.firstOrNull()) }.padding(horizontal = 18.dp),
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
            SectionTitle("What you write down")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // bands are a specification now ("+ Add specification")
                GymField.entries.filter { it != GymField.BANDS }.forEach { f ->
                    val on = f in e.fields
                    Pill(f.name.lowercase().replaceFirstChar { it.uppercase() }, on) {
                        val fields = if (on) e.fields - f else (e.fields + f).sortedBy { it.ordinal }
                        if (fields.isNotEmpty()) e = e.copy(fields = fields)
                    }
                }
            }
            if (GymField.WEIGHT in e.fields) {
                SlotLine("Step for − / + on the weight", null, formatKg(e.weightStep) + " kg") { askStep = true }
            }
            Column {
                Text("Your specifications", color = p.text, fontSize = 15.sp)
                Text("e.g. pinch width (mm) or grip (open, half crimp); each set keeps its own, starting from the last", color = p.muted, fontSize = 12.sp)
                e.specs.forEachIndexed { i, sp ->
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface)
                            .clickable { specEdit = i to sp }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (sp.isChoice) sp.choices.joinToString(" · ") + if (sp.multiple) " · several" else "" else "number" + (if (sp.unit.isNotBlank()) " in ${sp.unit}" else "") + " · step ${formatKg(sp.step)}",
                                color = p.muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { e = e.copy(specs = e.specs - sp) }) { Icon(AppIcons.Close, "Remove ${sp.name}", tint = p.muted) }
                    }
                }
                Pill("+ Add specification", false, Modifier.padding(top = 8.dp)) { specEdit = null to null }
            }
            SlotLine("Rest: buzz me after", "the clock counts up and keeps going", formatTime(e.restSec)) { askRest = true }
            Column {
                Text("Variants", color = p.text, fontSize = 15.sp)
                Text("Different ways of doing it, e.g. deep / half / normal. Each set keeps its variant; the log starts from the last one you used.",
                    color = p.muted, fontSize = 12.sp)
                e.variants.forEachIndexed { i, name ->
                    // sets of this variant: by its saved name (before a rename), plus the ones without a variant for the first
                    val savedName = renames.entries.firstOrNull { it.value == name }?.key ?: name
                    val n = (variantCounts[savedName] ?: 0) + if (savedName == original.variants.firstOrNull()) variantCounts[null] ?: 0 else 0
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (n == 0) "none yet" else "$n sets", color = p.muted, fontSize = 12.sp)
                        IconButton(enabled = i > 0, onClick = {
                            e = e.copy(variants = e.variants.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) })
                        }) { Icon(AppIcons.Up, "Move $name up", tint = if (i > 0) p.muted else p.faint) }
                        IconButton(onClick = { variantEdit = i to name }) { Icon(AppIcons.Edit, "Rename $name", tint = p.muted) }
                        // a variant with sets can't go: its sets would lose what they were
                        if (n == 0) IconButton(onClick = { e = e.copy(variants = e.variants - name) }) { Icon(AppIcons.Close, "Remove $name", tint = p.muted) }
                    }
                }
                Pill("+ Add variant", false, Modifier.padding(top = 8.dp)) { variantEdit = null to "" }
            }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp)) {
                SwitchLine("Left and right apart", "each set is L or R; turning it off keeps old sets with their side", e.perSide) { e = e.copy(perSide = it) }
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
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Its $n sets move to “${original.name}”, with their sides and notes, and “${from.name}” is deleted. This can't be undone (a backup from Settings can).")
                    // e.g. Barbell Squat merged into Squat as its "normal" variant
                    if (original.variants.isNotEmpty()) {
                        Text("Its sets become the variant:", color = p.muted, fontSize = 13.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            original.variants.forEach { v -> Pill(v, mergeAs == v) { mergeAs = v } }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = original.variants.isEmpty() || mergeAs != null, onClick = {
                    vm.mergeExercise(from, original, mergeAs); mergeFrom = null; mergeAs = null
                }) { Text("Merge $n sets") }
            },
            dismissButton = { TextButton(onClick = { mergeFrom = null }) { Text("Cancel") } },
        )
    }
    specEdit?.let { (index, current) ->
        SpecDialog(current, taken = e.specs.filterIndexed { i, _ -> i != index }.map { it.name.lowercase() }.toSet(),
            onDismiss = { specEdit = null }) { sp ->
            e = e.copy(specs = if (index == null) e.specs + sp else e.specs.toMutableList().also { it[index] = sp })
            specEdit = null
        }
    }
    variantEdit?.let { (index, old) ->
        VariantDialog(old, taken = e.variants.filterIndexed { i, _ -> i != index }.map { it.lowercase() }.toSet(), onDismiss = { variantEdit = null }) { name ->
            if (index == null) e = e.copy(variants = e.variants + name)
            else {
                e = e.copy(variants = e.variants.toMutableList().also { it[index] = name })
                // remember the rename from the saved name, so its sets follow on Save
                val saved = renames.entries.firstOrNull { it.value == old }?.key ?: old
                if (saved in original.variants) renames = renames + (saved to name)
            }
            variantEdit = null
        }
    }
    if (askStep) StepDialog(e.weightStep, onDismiss = { askStep = false }) { e = e.copy(weightStep = it); askStep = false }
    if (askRest) app.intervals.ui.editor.DurationDialog("Rest: buzz me after", e.restSec, 0, { askRest = false }) {
        e = e.copy(restSec = it); askRest = false
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

/**
 * Add or change a specification: a name, then a number (unit, step) or a choice (its options, comma separated).
 * The name of an existing one is fixed: the sets keep their values under it.
 */
@Composable
private fun SpecDialog(current: app.intervals.core.Spec?, taken: Set<String>, onDismiss: () -> Unit, onDone: (app.intervals.core.Spec) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(current?.name ?: "") }
    var choice by remember { mutableStateOf(current?.isChoice ?: false) }
    var unit by remember { mutableStateOf(current?.unit ?: "") }
    var step by remember { mutableStateOf(current?.step?.let(::formatKg) ?: "1") }
    var options by remember { mutableStateOf(current?.choices?.joinToString(", ") ?: "") }
    var multiple by remember { mutableStateOf(current?.multiple ?: false) }
    var colors by remember { mutableStateOf(current?.colors ?: emptyMap()) }
    val choices = options.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    val stepN = step.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val clash = name.trim().lowercase() in taken
    val ok = name.isNotBlank() && !clash && if (choice) choices.size >= 2 else stepN != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "New specification" else current.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current == null) OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Width") }, singleLine = true, isError = clash,
                    supportingText = if (clash) ({ Text("Already used on this exercise") }) else null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Number", !choice, Modifier.weight(1f)) { choice = false }
                    Pill("Choice", choice, Modifier.weight(1f)) { choice = true }
                }
                if (choice) {
                    OutlinedTextField(options, { options = it }, label = { Text("Options, comma separated") },
                        placeholder = { Text("small, medium, large") })
                    Text("At least two.", color = p.muted, fontSize = 12.sp)
                    SwitchLine("Can pick several", "e.g. bands: tap = \u00D71, again \u00D72, again none", multiple) { multiple = it }
                    if (choices.isNotEmpty()) Text("Color (optional)", color = p.muted, fontSize = 12.sp)
                    choices.forEach { c ->
                        Column {
                            Text(c, color = p.text, fontSize = 14.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (listOf<Long?>(null) + SPEC_COLORS).forEach { col ->
                                    val on = colors[c] == col
                                    Box(
                                        Modifier.size(30.dp).clip(CircleShape).background(col?.let { Color(it) } ?: p.surface)
                                            .border(if (on) 3.dp else 1.dp, if (on) p.text else p.line, CircleShape)
                                            .clickable(onClickLabel = "$c " + (col?.let { "color" } ?: "no color")) {
                                                colors = if (col == null) colors - c else colors + (c to col)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) { if (col == null) Text("\u2013", color = p.muted, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, placeholder = { Text("mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(step, { step = it }, label = { Text("Step − / +") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onDone(
                    if (choice) app.intervals.core.Spec(name.trim(), choices = choices, multiple = multiple, colors = colors.filterKeys { it in choices })
                    else app.intervals.core.Spec(name.trim(), unit = unit.trim(), step = stepN ?: 1.0)
                )
            }) { Text(if (current == null) "Add" else "OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A setting as one line: label (and a hint), the value on the right, tap to change it. */
@Composable
private fun SlotLine(label: String, sub: String?, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable(onClickLabel = "Change $label", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = p.muted, fontSize = 12.sp)
        }
        Text(value, color = p.accentText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("  \u203A", color = p.muted, fontSize = 20.sp)
    }
}

/** The weight step: common ones in one tap, or any number typed (0.25, 1.25, 200…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDialog(current: Double, onDismiss: () -> Unit, onDone: (Double) -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(formatKg(current)) }
    val n = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Step for − / +") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    STEPS.forEach { st ->
                        val on = n == st
                        Box(
                            Modifier.height(40.dp).widthIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.accent else p.surface)
                                .clickable { text = formatKg(st) }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(formatKg(st) + " kg", color = if (on) p.onAccent else p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Or any step, kg") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(enabled = n != null, onClick = { n?.let(onDone) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Colors a choice can have: the old band colors first, then the app's Material set. */
private val SPEC_COLORS = listOf(
    0xFF43A047, 0xFF8E44D8, 0xFF16181C, 0xFFE53935, 0xFFFFC107,
    0xFFE91E63, 0xFF3F51B5, 0xFF03A9F4, 0xFF00BCD4, 0xFFCDDC39, 0xFFFF9800, 0xFF795548, 0xFF9E9E9E, 0xFFFFFFFF,
)

/** Name a new variant, or rename one (its sets follow when you save). */
@Composable
private fun VariantDialog(current: String, taken: Set<String>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val clash = name.trim().lowercase() in taken
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current.isEmpty()) "New variant" else "Rename \u201C$current\u201D") },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("e.g. deep, half, upper half") }, singleLine = true, isError = clash,
                supportingText = if (clash) ({ Text("This exercise already has it") }) else null)
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !clash, onClick = { onDone(name.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
