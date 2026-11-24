package app.intervals.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.GymSet
import app.intervals.core.boxText
import app.intervals.core.loadText
import app.intervals.ui.AppIcons
import app.intervals.ui.LocalPalette
import app.intervals.core.letter
import app.intervals.core.fieldsOf
import app.intervals.ui.list.BigButton

/**
 * Moves sets logged under the wrong exercise (mockup intervals-move-sets.html, C2): the exercise's sets of the day on top,
 * all ticked (tap one to keep it here), then the exercise to move them to (similar names first) and its variant.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MoveSetsSheet(
    from: Exercise, sets: List<GymSet>, exercises: List<Exercise>, day: String,
    onDismiss: () -> Unit, onMove: (ids: List<Long>, into: Exercise, variant: String?) -> Unit,
) {
    val p = LocalPalette.current
    var moving by remember(sets) { mutableStateOf(sets.map { it.id }.toSet()) }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Exercise?>(null) }
    var variant by remember { mutableStateOf<String?>(null) }
    val others = remember(exercises, from) { exercises.filter { it.id != from.id } }
    val similar = remember(others) { others.map { it to similarity(it.name, from.name) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5).map { it.first } }
    val rest = remember(others, similar) { others.filterNot { it in similar }.sortedBy { it.name.lowercase() } }
    val found = if (query.isBlank()) null else others.filter { it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.sheet) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Move sets of ${from.name} · ${shortDay(day)}", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Label("SETS · TAP TO KEEP ONE HERE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sets.forEach { s ->
                    val on = s.id in moving
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) p.workTint else p.bg).border(1.dp, if (on) p.pink else p.line, RoundedCornerShape(10.dp))
                                .semantics { selected = on }
                                .clickable(onClickLabel = if (on) "Keep here" else "Move") { moving = if (on) moving - s.id else moving + s.id }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((s.side?.let { it.letter + " " } ?: "") + s.boxText(from), color = if (on) p.workText else p.muted, fontSize = 16.sp)
                        }
                        Text(s.loadText(from), color = p.muted, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            Label("TO")
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 320.dp)) {
                val pick = { e: Exercise -> target = e; variant = null }
                if (found != null) {
                    if (found.isEmpty()) item { Text("No exercise called “${query.trim()}”.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp)) }
                    items(found, key = { it.id }) { TargetRow(it, it.id == target?.id, variant, { v -> variant = v }) { pick(it) } }
                } else {
                    if (similar.isNotEmpty()) item { Label("SIMILAR") }
                    items(similar, key = { "s${it.id}" }) { TargetRow(it, it.id == target?.id, variant, { v -> variant = v }) { pick(it) } }
                    item { Label("ALL") }
                    items(rest, key = { it.id }) { TargetRow(it, it.id == target?.id, variant, { v -> variant = v }) { pick(it) } }
                }
            }
            val t = target
            // values the target does not record stay in the sets, but its screens will not show them
            val chosen = sets.filter { it.id in moving }
            // what the chosen variant records (a variant can have its own fields)
            val tf = t?.let { it.fieldsOf(variant) }.orEmpty()
            val lost = if (t == null) emptyList() else listOfNotNull(
                GymField.WEIGHT.takeIf { f -> f !in tf && chosen.any { it.weightKg != null } },
                GymField.REPS.takeIf { f -> f !in tf && chosen.any { it.reps != null } },
                GymField.TIME.takeIf { f -> f !in tf && chosen.any { it.timeSec != null } },
                GymField.DISTANCE.takeIf { f -> f !in tf && chosen.any { it.distance != null } },
            )
            if (t != null && lost.isNotEmpty()) {
                val names = lost.joinToString(", ") { it.name.lowercase() }
                Text("${t.name} has no $names: kept in the sets, but not shown. Add it to ${t.name} to see it.",
                    color = p.recordText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(AppIcons.Close, "Cancel", tint = p.text) }
                val ready = t != null && chosen.isNotEmpty() && (t.variants.isEmpty() || variant != null)
                val n = chosen.size
                BigButton(
                    when {
                        t == null -> "Pick an exercise"
                        n == 0 -> "Tick a set to move"
                        t.variants.isNotEmpty() && variant == null -> "Choose the ${t.name} variant"
                        else -> "Move $n set${if (n == 1) "" else "s"} to ${t.name}" + (variant?.let { " · $it" } ?: "")
                    },
                    if (ready) p.accent else p.surface, if (ready) androidx.compose.ui.graphics.Color.White else p.muted,
                    { if (ready && t != null) onMove(chosen.map { it.id }, t, variant) },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.muted, fontSize = 12.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** An exercise to move to; once picked, an exercise with variants asks inside its own box which of them the sets were. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetRow(ex: Exercise, on: Boolean, variant: String?, onVariant: (String) -> Unit, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) p.tint else p.sheet)
            .then(if (on) Modifier.border(1.dp, p.accent, RoundedCornerShape(10.dp)) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { selected = on }.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val sub = ex.variants.joinToString(" · ").ifEmpty { ex.fields.joinToString(" · ") { it.name.lowercase() } }
                if (sub.isNotEmpty() && !(on && ex.variants.isNotEmpty())) Text(sub, color = p.muted, fontSize = 12.sp)
            }
            if (on) Text("✓", color = p.accentText, fontSize = 16.sp)
        }
        if (on && ex.variants.isNotEmpty()) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
            Text("VARIANT", color = p.muted, fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ex.variants.forEach { v -> Pill(v, v == variant) { onVariant(v) } }
            }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(text, color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.sheet)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp)).semantics { selected = on }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}
