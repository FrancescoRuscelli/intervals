package app.intervals.ui.gym

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.GroupItem
import app.intervals.core.GymGroup
import app.intervals.core.planLabel
import app.intervals.core.GROUP_COLORS
import app.intervals.core.colorAt
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.editor.StepButton

/** A category tag or an exact workout group: name, exercises, optional workout plans, Save, Delete. */
@Composable
fun EditGroupScreen(vm: AppViewModel, groupId: Long?, newIsTag: Boolean = false) {
    val p = LocalPalette.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val byId = exercises.associateBy { it.id }
    val original = groupId?.let { id -> groups.firstOrNull { it.id == id } }
    if (groupId != null && original == null) return
    var g by remember(groupId, newIsTag) { mutableStateOf(original ?: GymGroup(name = if (newIsTag) "New tag" else "New workout", items = emptyList(), isTag = newIsTag)) }
    var adding by remember { mutableStateOf(false) }
    var planFor by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf(false) }
    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close without saving", tint = p.text) }
            Text(if (g.isTag) "Exercise tag" else "Workout group", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val ok = g.name.isNotBlank() && g.items.isNotEmpty()
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (ok) p.accent else p.tint)
                    .clickable(enabled = ok) { vm.saveGroup(g.copy(name = g.name.trim())) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = if (ok) p.onAccent else p.faint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        listOf(true to "Tag", false to "Workout").forEach { (tag, label) ->
                            val on = g.isTag == tag
                            Box(
                                Modifier.height(38.dp).clip(RoundedCornerShape(14.dp)).background(if (on) p.tint else p.surface)
                                    .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(14.dp))
                                    .clickable { g = g.copy(isTag = tag) }.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Text("Name", color = p.muted, fontSize = 12.sp)
                    BasicTextField(g.name, { g = g.copy(name = it) }, singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
                    Text("${g.items.size} exercise${if (g.items.size == 1) "" else "s"}" + if (g.isTag) " · category only" else " · a plan is optional",
                        color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(if (g.isTag) "Color in the Journal" else "Color in Quick Start", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    val sameKind = groups.filter { it.isTag == g.isTag }
                    val index = sameKind.indexOfFirst { it.id == g.id }.let { if (it < 0) sameKind.size else it }
                    val current = g.colorAt(index)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GROUP_COLORS.forEach { c ->
                            Box(
                                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color(c))
                                    .border(3.dp, if (c == current) p.text else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable(onClickLabel = "Group color") { g = g.copy(color = c) },
                            )
                        }
                    }
                }
            }
            itemsIndexed(g.items) { i, item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable(enabled = !g.isTag) { planFor = i }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(byId[item.exerciseId]?.name ?: "(deleted exercise)", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (g.isTag) "Tagged" else item.planLabel() + " · tap to change", color = p.muted, fontSize = 12.sp)
                    }
                    StepButton(AppIcons.Up, "Move up", androidx.compose.ui.graphics.Color.Transparent, p.muted, 36) {
                        if (i > 0) g = g.copy(items = g.items.toMutableList().apply { add(i - 1, removeAt(i)) })
                    }
                    StepButton(AppIcons.Down, "Move down", androidx.compose.ui.graphics.Color.Transparent, p.muted, 36) {
                        if (i < g.items.lastIndex) g = g.copy(items = g.items.toMutableList().apply { add(i + 1, removeAt(i)) })
                    }
                    StepButton(AppIcons.Close, "Remove", androidx.compose.ui.graphics.Color.Transparent, p.muted, 36) {
                        g = g.copy(items = g.items.filterIndexed { j, _ -> j != i })
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, p.line, RoundedCornerShape(16.dp)).clickable { adding = true },
                    contentAlignment = Alignment.Center,
                ) { Text("+ Add exercise", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (original != null) item {
                Text("Delete " + if (g.isTag) "tag" else "workout group", color = p.dangerText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp).clickable { deleting = true }.padding(8.dp))
            }
        }
    }

    if (adding) {
        val taken = g.items.map { it.exerciseId }.toSet()
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add exercise") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("Search") }, singleLine = true)
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        exercises.filter { it.id !in taken && (q.isBlank() || it.name.contains(q.trim(), true)) }.forEach { ex ->
                            Text(ex.name, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                                g = g.copy(items = g.items + GroupItem(ex.id)); adding = false
                            }.padding(vertical = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    planFor?.let { i ->
        val item = g.items.getOrNull(i) ?: run { planFor = null; return@let }
        var sets by remember(i) { mutableStateOf(item.planSets) }
        var note by remember(i) { mutableStateOf(item.planNote) }
        AlertDialog(
            onDismissRequest = { planFor = null },
            title = { Text("Plan for ${byId[item.exerciseId]?.name ?: "this exercise"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sets", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        StepButton(AppIcons.Minus, "Fewer", p.tint, p.accentText) { sets = sets?.let { if (it <= 1) null else it - 1 } }
                        Text(sets?.toString() ?: "no plan", fontSize = 18.sp, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        StepButton(AppIcons.Plus, "More", p.tint, p.accentText) { sets = (sets ?: 0) + 1 }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, placeholder = { Text("e.g. 3 × 8 @ 40 kg") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    g = g.copy(items = g.items.mapIndexed { j, x -> if (j == i) x.copy(planSets = sets, planNote = note.trim()) else x })
                    planFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { planFor = null }) { Text("Cancel") } },
        )
    }
    if (deleting && original != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${original.name}”?") },
            text = { Text("Only the ${if (original.isTag) "tag" else "workout group"}: the exercises and their sets stay.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteGroup(original) }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
