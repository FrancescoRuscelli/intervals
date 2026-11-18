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

/** Add an exercise to a day: search, recent first, then all A–Z; or a new one (mockup G9, without groups for now). */
@Composable
fun GymPickScreen(vm: AppViewModel, day: String) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf<String?>(null) }
    val q = query.trim()
    val found = exercises.filter { q.isNotEmpty() && it.name.contains(q, ignoreCase = true) }
    val recent = exercises.filter { it.id in lastUsed }.sortedByDescending { lastUsed[it.id] }.take(8)
    val groups by vm.groups.collectAsStateWithLifecycle()
    val names = exercises.associate { it.id to it.name }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close", tint = p.text) }
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search or name a new exercise") }, singleLine = true,
                modifier = Modifier.weight(1f))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (q.isNotEmpty()) {
                items(found, key = { "f" + it.id }) { ExerciseRow(it, lastUsed[it.id]) { vm.pickExercise(it.id, day) } }
                if (found.none { it.name.equals(q, ignoreCase = true) }) item {
                    Text("+ New exercise “$q”", color = p.accentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = q }.padding(vertical = 14.dp, horizontal = 4.dp))
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Start a group", Modifier.weight(1f))
                        Text("+ New group", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { vm.editGroup(null) }.padding(6.dp))
                    }
                }
                if (groups.isEmpty()) item {
                    Text("Groups are exercises you do together, e.g. Legs: Squat · Deadlift. Make one with + New group.",
                        color = p.faint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(groups, key = { "g" + it.id }) { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.group)
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
                item {
                    Text("+ New exercise", color = p.accentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = "" }.padding(vertical = 14.dp, horizontal = 4.dp))
                }
                if (recent.isNotEmpty()) {
                    item { SectionTitle("Recent exercises", Modifier.padding(top = 6.dp, bottom = 4.dp)) }
                    items(recent, key = { "r" + it.id }) { ExerciseRow(it, lastUsed[it.id]) { vm.pickExercise(it.id, day) } }
                }
                item { SectionTitle("All, A–Z · ${exercises.size}", Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                items(exercises, key = { "a" + it.id }) { ExerciseRow(it, lastUsed[it.id]) { vm.pickExercise(it.id, day) } }
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

@Composable
private fun ExerciseRow(ex: Exercise, lastDay: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)) {
        Text(ex.name, color = p.text, fontSize = 16.sp)
        Text(
            buildList {
                add(ex.fields.joinToString(" · ") { it.name.lowercase() })
                if (ex.perSide) add("L · R")
                if (ex.assist) add("assist")
                lastDay?.let { add("last " + shortDay(it)) }
            }.joinToString(" · "),
            color = p.muted, fontSize = 12.sp,
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
    var assist by remember { mutableStateOf(false) }
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
                if (GymField.WEIGHT in fields) Toggle("Assist (−)", "The weight is taken off, e.g. a deloaded pull-up", assist) { assist = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && fields.isNotEmpty() && !clash, onClick = {
                onCreate(
                    Exercise(
                        name = name.trim(),
                        fields = GymField.entries.filter { it in fields },
                        perSide = perSide,
                        assist = assist && GymField.WEIGHT in fields,
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
