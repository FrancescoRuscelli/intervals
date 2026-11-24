package app.intervals.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.GROUP_COLORS
import app.intervals.core.GroupItem
import app.intervals.core.GymGroup
import app.intervals.core.colorAt
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import kotlin.math.roundToInt

/**
 * A workout (or a tag): name, color, its exercises in order (mockup intervals-new-workout-ideas.html, B). The exercises
 * to add are listed below to tick: ticking adds to the end, unticking removes; the number on the box is the place in the
 * workout. No set plans: what to do is decided while training. Tags are made in the Gym exercise list, so there is no
 * Tag / Workout switch here.
 */
@Composable
fun EditGroupScreen(vm: AppViewModel, groupId: Long?, newIsTag: Boolean = false, newItems: List<Long> = emptyList()) {
    val p = LocalPalette.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
    val byId = exercises.associateBy { it.id }
    val original = groupId?.let { id -> groups.firstOrNull { it.id == id } }
    if (groupId != null && original == null) return
    var g by remember(groupId, newIsTag) { mutableStateOf(original ?: GymGroup(name = if (newIsTag) "New tag" else "New workout", items = newItems.map { GroupItem(it) }, isTag = newIsTag)) }
    var deleting by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Long?>(null) } // a tag's id; null = recent first
    BackHandler { vm.back() }
    val tags = groups.filter { it.isTag && it.id != g.id }
    val inGroup = g.items.map { it.exerciseId }
    fun toggle(id: Long) {
        g = g.copy(items = if (id in inGroup) g.items.filter { it.exerciseId != id } else g.items + GroupItem(id))
    }

    // dragging an exercise by its handle: which one and how far it moved since its last swap
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 62.dp.toPx() }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close without saving", tint = p.text) }
            Text(
                when { g.isTag -> "Tag"; original == null -> "New workout"; else -> "Edit workout" },
                color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            val ok = g.name.isNotBlank() && g.items.isNotEmpty()
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (ok) p.accent else p.tint)
                    .clickable(enabled = ok) { vm.saveGroup(g.copy(name = g.name.trim())) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = if (ok) p.onAccent else p.faint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    BasicTextField(g.name, { g = g.copy(name = it) }, singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
                    val sameKind = groups.filter { it.isTag == g.isTag }
                    val index = sameKind.indexOfFirst { it.id == g.id }.let { if (it < 0) sameKind.size else it }
                    val current = g.colorAt(index)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GROUP_COLORS.forEach { c ->
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                                    .border(3.dp, if (c == current) p.text else Color.Transparent, CircleShape)
                                    .clickable(onClickLabel = "Color") { g = g.copy(color = c) },
                            )
                        }
                    }
                    Text(
                        "${g.items.size} exercise${if (g.items.size == 1) "" else "s"}" + if (g.items.size > 1) " · drag ⋮⋮ to reorder" else "",
                        color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.items.forEachIndexed { i, item ->
                        val moving = dragging == i
                        Row(
                            Modifier.fillMaxWidth().height(56.dp)
                                .offset { IntOffset(0, if (moving) dragY.roundToInt() else 0) }
                                .clip(RoundedCornerShape(14.dp)).background(if (moving) p.tint else p.surface)
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(36.dp).height(56.dp).pointerInput(g.items.size) {
                                    detectDragGestures(
                                        onDragStart = { dragging = g.items.indexOf(item); dragY = 0f },
                                        onDragEnd = { dragging = null; dragY = 0f },
                                        onDragCancel = { dragging = null; dragY = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        val from = dragging ?: return@detectDragGestures
                                        dragY += amount.y
                                        val to = when {
                                            dragY > rowPx / 2 && from < g.items.lastIndex -> from + 1
                                            dragY < -rowPx / 2 && from > 0 -> from - 1
                                            else -> null
                                        }
                                        if (to != null) {
                                            g = g.copy(items = g.items.toMutableList().apply { add(to, removeAt(from)) })
                                            dragging = to
                                            dragY -= if (to > from) rowPx else -rowPx
                                        }
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("⋮⋮", color = p.muted, fontSize = 16.sp, letterSpacing = (-2).sp) }
                            Box(Modifier.size(28.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(byId[item.exerciseId]?.name ?: "(deleted exercise)", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, modifier = Modifier.weight(1f))
                            IconButton(onClick = { toggle(item.exerciseId) }) { Icon(AppIcons.Close, "Remove", tint = p.muted, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            // the exercises to tick
            item {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add exercises", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip("Recent", null, filter == null) { filter = null }
                        tags.forEachIndexed { i, t -> FilterChip(t.name, Color(t.colorAt(i)), filter == t.id) { filter = t.id } }
                    }
                }
            }
            val q = query.trim()
            val shown = exercises
                .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                .filter { ex -> q.isNotEmpty() || filter == null || tags.firstOrNull { it.id == filter }?.items?.any { it.exerciseId == ex.id } == true }
                .sortedWith(compareByDescending<app.intervals.core.Exercise> { lastUsed[it.id] ?: "" }.thenBy { it.name.lowercase() })
            items(shown, key = { "x" + it.id }) { ex ->
                val place = inGroup.indexOf(ex.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (place >= 0) p.tint else Color.Transparent)
                        .clickable(onClickLabel = if (place >= 0) "Remove ${ex.name}" else "Add ${ex.name}") { toggle(ex.id) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (place >= 0) p.accent else Color.Transparent)
                            .border(1.5.dp, if (place >= 0) p.accent else p.muted, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (place >= 0) Text("${place + 1}", color = p.onAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(12.dp))
                    Text(ex.name, color = p.text, fontSize = 15.sp, maxLines = 2, modifier = Modifier.weight(1f))
                    Text(lastUsed[ex.id]?.let(::shortDay) ?: "never", color = p.muted, fontSize = 12.sp)
                }
            }
            if (original != null) item {
                Text("Delete " + if (g.isTag) "tag" else "workout", color = p.dangerText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp).clickable { deleting = true }.padding(8.dp))
            }
        }
    }

    if (deleting && original != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${original.name}”?") },
            text = { Text("Only the ${if (original.isTag) "tag" else "workout"}: the exercises and their sets stay.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteGroup(original) }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FilterChip(label: String, dot: Color?, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.surface)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (on) p.accentText else p.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
