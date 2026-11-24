package app.intervals.ui.gym

import app.intervals.core.colorAt
import androidx.compose.ui.text.withStyle

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

import androidx.activity.compose.BackHandler

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import app.intervals.core.pickSections
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
 * Add an exercise to a day (mockup intervals-add-exercise-ideas.html, A with B's workouts): search and + New on top,
 * the workouts, then chips (Recent, All, each tag in its color) to filter the exercises; each exercise has its tags
 * written under its name. Tap to log. Hold to select several (numbered in the order picked), then Workout saves them as a new
 * workout. Tags are made and edited in the Gym exercise list, not here.
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
    var filter by remember { mutableStateOf("recent") } // "recent", "all" or "g<tag id>"
    var selection by remember { mutableStateOf(listOf<Long>()) }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { selection = emptyList() }
    val q = query.trim()
    val names = exercises.associate { it.id to it.name }
    val workoutGroups = groups.filterNot { it.isTag }
    val tags = groups.filter { it.isTag }
    // each exercise's tags, written under its name in your tag order (mockup intervals-tag-dots.html, 3)
    val tagsOf = remember(groups) { exerciseTags(groups) }
    val since = remember(day) { java.time.LocalDate.parse(day).minusDays(14).toString() }
    val byRecent = compareByDescending<Exercise> { if (it.id in doneCount) "~" else lastUsed[it.id] ?: "" }.thenBy { it.name.lowercase() }
    val shown = when {
        q.isNotEmpty() -> {
            val tagged = tags.filter { it.name.contains(q, ignoreCase = true) }.flatMap { it.items }.map { it.exerciseId }.toSet()
            exercises.filter { it.name.contains(q, ignoreCase = true) || it.id in tagged }.sortedBy { it.name.lowercase() }
        }
        filter == "all" -> exercises.sortedBy { it.name.lowercase() }
        filter.startsWith("g") -> {
            val ids = tags.firstOrNull { "g${it.id}" == filter }?.items?.map { it.exerciseId }.orEmpty().toSet()
            exercises.filter { it.id in ids }.sortedWith(byRecent)
        }
        else -> exercises.filter { it.id in doneCount || (lastUsed[it.id] ?: "") in since..day }.sortedWith(byRecent)
    }
    fun tap(ex: Exercise) {
        if (selecting) selection = if (ex.id in selection) selection - ex.id else selection + ex.id
        else vm.pickExercise(ex.id, day)
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.workTint).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selection = emptyList() }) { Icon(AppIcons.Close, "Cancel selection", tint = p.workText) }
                Text("${selection.size} selected", color = p.workText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val ids = selection; selection = emptyList(); vm.editGroup(null, isTag = false, items = ids) }) {
                    Text("Workout", color = p.workText, fontWeight = FontWeight.Bold)
                }
            }
        } else Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (q.isEmpty() && !selecting && day !in vm.activeGroups) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Workouts", Modifier.weight(1f))
                        Text("+ New", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { vm.editGroup(null, isTag = false) }.padding(6.dp))
                    }
                }
                if (workoutGroups.isEmpty()) item {
                    Text("None yet: hold exercises to select them and tap Workout, or + New.", color = p.muted, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
                items(workoutGroups, key = { "g" + it.id }) { g ->
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
            if (q.isEmpty()) item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Exercises", Modifier)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PickChip("Recent", null, null, filter == "recent") { filter = "recent" }
                        PickChip("All", exercises.size, null, filter == "all") { filter = "all" }
                        tags.forEachIndexed { i, t -> PickChip(t.name, t.items.size, Color(t.colorAt(i)), filter == "g${t.id}") { filter = "g${t.id}" } }
                    }
                }
            }
            if (shown.isEmpty() && q.isEmpty()) item {
                Text(if (exercises.isEmpty()) "No exercises here yet: + New makes one." else "Nothing here: try All.", color = p.muted, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            items(shown, key = { "x" + it.id }) { ex ->
                val place = selection.indexOf(ex.id)
                ExerciseRow(
                    ex, doneCount[ex.id], lastUsed[ex.id], selected = place >= 0,
                    lead = if (selecting) ({ SelectBox(place) }) else null, tags = tagsOf[ex.id].orEmpty(),
                    onLongClick = { if (ex.id !in selection) selection = selection + ex.id },
                ) { tap(ex) }
            }
            if (q.isNotEmpty() && exercises.none { it.name.equals(q, ignoreCase = true) }) item {
                Text("+ New exercise \u201C$q\u201D", color = p.accentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable { creating = q }.padding(vertical = 14.dp, horizontal = 4.dp))
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

/** A filter chip of the picker: label, count, and a tag's color dot. */
@Composable
private fun PickChip(label: String, count: Int?, dot: Color?, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.surface)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (on) p.accentText else p.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        count?.let { Text(" $it", color = p.muted, fontSize = 12.sp) }
    }
}

/** The tick box while selecting: the place in the order picked, empty when not picked. */
@Composable
private fun SelectBox(place: Int) {
    val p = LocalPalette.current
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (place >= 0) p.accent else Color.Transparent)
            .border(1.5.dp, if (place >= 0) p.accent else p.muted, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { if (place >= 0) Text("${place + 1}", color = p.onAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
}

/** One line: name, an L·R badge for per-side ones, and on the right "done 5 sets" (today) or the last day. */
@Composable
internal fun ExerciseRow(
    ex: Exercise, doneToday: Int?, lastDay: String?, selected: Boolean = false,
    lead: (@Composable () -> Unit)? = null, tags: List<Pair<String, Long>> = emptyList(), onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) p.tint else Color.Transparent)
            .semantics { if (onLongClick != null) this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select exercise")
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead?.let { Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) { it() }; Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.name, color = p.text, fontSize = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (ex.perSide) Text("L\u00B7R", color = p.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(p.tint).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            // the exercise's tags: a dot in each tag's color and its name in grey (mockup intervals-gym-list-tags.html, B), cut with … when they don't fit
            if (tags.isNotEmpty()) Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    tags.forEachIndexed { i, (name, color) ->
                        if (i > 0) append("   ")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(color))) { append("\u25CF ") }
                        append(name)
                    }
                },
                color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
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
                Text("Each set records", color = p.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField.entries.filter { it != GymField.BANDS }.forEach { f ->
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

/** Each exercise's tags as (name, color), in your tag order; the color as in the Journal ([colorAt]). */
internal fun exerciseTags(groups: List<app.intervals.core.GymGroup>): Map<Long, List<Pair<String, Long>>> =
    groups.filter { it.isTag }.withIndex()
        .flatMap { (i, t) -> t.items.map { it.exerciseId to (t.name to t.colorAt(i)) } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.distinctBy { it.first.lowercase() } }
