package app.intervals.ui.gym

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import app.intervals.core.pickSections
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.editor.SectionTitle

/**
 * Add an exercise to a day (mockup G9, reorganised 2026-11-19): search + New, then folding sections
 * with each exercise once: done today, recent, one per group, everything else A–Z. Start a group stays on top.
 */
@Composable
fun GymPickScreen(vm: AppViewModel, day: String) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val daySets by remember(day) { vm.gymDay(day) }.collectAsStateWithLifecycle(emptyList())
    val doneCount = remember(daySets) { daySets.groupingBy { it.exerciseId }.eachCount() }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf<String?>(null) }
    // open sections; groups and the long A–Z list start closed
    val open = remember { mutableStateMapOf("today" to true, "recent" to true) }
    val q = query.trim()
    val names = exercises.associate { it.id to it.name }
    val sections = remember(exercises, doneCount, lastUsed, groups, day) {
        pickSections(exercises, doneCount.keys, lastUsed, groups, day)
    }
    fun pick(ex: Exercise) = vm.pickExercise(ex.id, day)

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close", tint = p.text) }
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(p.tint).clickable(onClickLabel = "New exercise") { creating = q }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+ New", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (q.isNotEmpty()) {
                val found = sections.flatMap { it.exercises }.distinctBy { it.id }.filter { it.name.contains(q, ignoreCase = true) }
                    .sortedBy { it.name.lowercase() }
                items(found, key = { "f" + it.id }) { ExerciseRow(it, doneCount[it.id], lastUsed[it.id]) { pick(it) } }
                if (exercises.none { it.name.equals(q, ignoreCase = true) }) item {
                    Text("+ New exercise \u201C$q\u201D", color = p.accentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = q }.padding(vertical = 14.dp, horizontal = 4.dp))
                }
            } else {
                if (day !in vm.activeGroups) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Start a group", Modifier.weight(1f))
                            Text("+ New group", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { vm.editGroup(null) }.padding(6.dp))
                        }
                    }
                    items(groups, key = { "g" + it.id }) { g ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(p.group)
                                .clickable(onClickLabel = "Start ${g.name}") { vm.startGroup(g.id, day) }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Play, null, tint = p.onAccent, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(g.items.mapNotNull { names[it.exerciseId] }.joinToString(" · "), color = p.muted, fontSize = 12.sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.editGroup(g.id) }) { Icon(AppIcons.Edit, "Edit ${g.name}", tint = p.muted, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                if (sections.isEmpty()) item {
                    Text("No exercises here yet: + New makes one.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                sections.forEach { sec ->
                    val isOpen = open[sec.key] == true
                    item(key = "h" + sec.key) {
                        Row(
                            Modifier.fillMaxWidth().clickable { open[sec.key] = !isOpen }.padding(top = 14.dp, bottom = 6.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (isOpen) "\u25BE" else "\u25B8", color = p.muted, fontSize = 14.sp, modifier = Modifier.width(18.dp))
                            SectionTitle("${sec.title} \u00B7 ${sec.exercises.size}")
                        }
                    }
                    if (isOpen) items(sec.exercises, key = { sec.key + it.id }) { ExerciseRow(it, doneCount[it.id], lastUsed[it.id]) { pick(it) } }
                }
            }
        }
    }

    creating?.let { name ->
        NewExerciseDialog(name, exercises.map { it.name.lowercase() }.toSet(), onDismiss = { creating = null }) {
            creating = null
            vm.createExercise(it, day)
        }
    }
}

/** One line: name, an L·R badge for per-side ones, and on the right "done 5 sets" (today) or the last day. */
@Composable
private fun ExerciseRow(ex: Exercise, doneToday: Int?, lastDay: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ex.name, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        if (ex.perSide) Text("L\u00B7R", color = p.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(p.tint).padding(horizontal = 6.dp, vertical = 2.dp))
        Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
        Text(
            if (doneToday != null) "\u2713 $doneToday set${if (doneToday == 1) "" else "s"}" else lastDay?.let(::shortDay) ?: "never",
            color = if (doneToday != null) p.pink else p.muted, fontSize = 13.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewExerciseDialog(initial: String, taken: Set<String>, onDismiss: () -> Unit, onCreate: (Exercise) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(initial) }
    var fields by remember { mutableStateOf(setOf(GymField.WEIGHT, GymField.REPS)) }
    var perSide by remember { mutableStateOf(false) }
    val clash = name.trim().lowercase() in taken
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, isError = clash,
                    supportingText = if (clash) ({ Text("You already have one with this name") }) else null)
                Text("What do you write down?", color = p.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField.entries.forEach { f ->
                        val on = f in fields
                        Text(f.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (on) p.accentText else p.soft, fontSize = 14.sp,
                            modifier = Modifier.background(if (on) p.tint else p.surface, RoundedCornerShape(16.dp))
                                .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
                                .clickable { fields = if (on) fields - f else fields + f }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                Toggle("Left and right apart", "Each set is L or R (or both at once)", perSide) { perSide = it }
                if (GymField.WEIGHT in fields) Text("Assisted? Log a negative weight, e.g. −20 kg.", color = p.muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && fields.isNotEmpty() && !clash, onClick = {
                onCreate(
                    Exercise(
                        name = name.trim(),
                        fields = GymField.entries.filter { it in fields },
                        perSide = perSide,
                    )
                )
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Toggle(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 15.sp)
            Text(sub, color = p.muted, fontSize = 12.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}
