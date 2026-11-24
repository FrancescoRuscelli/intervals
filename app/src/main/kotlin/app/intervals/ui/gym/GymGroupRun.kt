package app.intervals.ui.gym

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.GroupItem
import app.intervals.core.GymGroup
import app.intervals.core.GymMetric
import app.intervals.core.GymSet
import app.intervals.core.dayRows
import app.intervals.core.formatTime
import app.intervals.core.metricsFor
import app.intervals.core.nextGroupItem
import app.intervals.core.planLabel
import app.intervals.core.recordSets
import app.intervals.core.summary
import app.intervals.ui.ActiveGymGroup
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.GymRest
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.list.BigButton
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun GymGroupRun(
    vm: AppViewModel,
    day: String,
    group: GymGroup,
    run: ActiveGymGroup,
    byId: Map<Long, Exercise>,
    setsByExercise: Map<Long, List<GymSet>>,
) {
    val p = LocalPalette.current
    val items = group.items.filter { it.exerciseId in byId }.distinctBy { it.exerciseId }
    val next = nextGroupItem(items, setsByExercise)?.exerciseId?.let(byId::get)
    val extra = setsByExercise.keys.filter { id -> id in byId && items.none { it.exerciseId == id } }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Group · started ${run.startTime.format(startTimeFormat)}", color = p.muted, fontSize = 12.sp)
                Text(group.name, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RunElapsed(run.startedAt)
            IconButton(onClick = { vm.endGroup(day) }) { Icon(AppIcons.Close, "Discard group; logged sets stay", tint = p.muted) }
            IconButton(onClick = vm::openSettings) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
        }
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.exerciseId }) { i, item ->
                val sets = setsByExercise[item.exerciseId].orEmpty()
                val state = when {
                    item.exerciseId == next?.id -> "now"
                    sets.size >= (item.planSets ?: 1) -> "done"
                    else -> "next"
                }
                GroupExerciseCard(vm, day, byId.getValue(item.exerciseId), item, sets, i + 1, state)
            }
            itemsIndexed(extra, key = { _, id -> id }) { i, id ->
                GroupExerciseCard(vm, day, byId.getValue(id), GroupItem(id), setsByExercise.getValue(id), items.size + i + 1, "done")
            }
            item {
                val label = if (day == LocalDate.now().toString()) "+ Add an exercise just for today" else "+ Add an exercise to this day"
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).border(1.dp, p.groupLine, shape)
                        .clickable(role = Role.Button) { vm.openGymPick(day) }.padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val rest = vm.gymRest
            if (rest != null && rest.visible && rest.day == day && day == LocalDate.now().toString() && rest.startedAt >= run.startedAt) {
                val nextLabel = next?.let { "${it.name} set ${setsByExercise[it.id].orEmpty().size + 1}" }
                RestClock(rest, vm::dismissGymRest, nextLabel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                next?.let {
                    Button(
                        onClick = { vm.openGymLog(it.id, day) }, modifier = Modifier.weight(2f).heightIn(min = 54.dp),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("Go to ${it.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                BigButton("Finish", p.surface, p.text, { vm.finishGymGroup(day, group) }, Modifier.weight(1f))
            }
        }
    }
}

private val startTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun RunElapsed(startedAt: Long) {
    Text(formatTime(elapsedSeconds(startedAt)), color = LocalPalette.current.soft, fontSize = 22.sp, style = NumberStyle)
}

@Composable
internal fun RestClock(rest: GymRest, onDismiss: () -> Unit, next: String? = null) {
    val p = LocalPalette.current
    val elapsed = elapsedSeconds(rest.startedAt)
    val ready = rest.targetSec in 1..elapsed
    val fg = if (ready) p.onRestReady else p.restText
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (ready) p.restReady else p.restTint)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Intervals, null, tint = fg, modifier = Modifier.padding(end = 4.dp).size(22.dp))
            Column(Modifier.weight(1f)) {
                val status = if (ready) "Ready" else "Resting"
                Text(if (next == null) status else "$status · next: $next", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (rest.targetSec > 0) {
                    val target = formatTime(rest.targetSec)
                    Text(if (ready) "$target target passed" else "Target $target", color = fg, fontSize = 12.sp)
                }
            }
            Text(formatTime(elapsed), color = fg, fontSize = 28.sp, style = NumberStyle)
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, "Stop the rest clock", tint = fg) }
        }
        if (rest.targetSec > 0) {
            LinearProgressIndicator(
                progress = { (elapsed.toFloat() / rest.targetSec).coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth(), color = fg, trackColor = fg.copy(alpha = 0.2f),
            )
        }
    }
}

/** The rest as a small pill: "⏱ 1:12 / 3:00" while resting, filled "Ready 3:14" once the target passed. Tap = stop. */
@Composable
internal fun RestPill(rest: GymRest, onStop: () -> Unit) {
    val p = LocalPalette.current
    val elapsed = elapsedSeconds(rest.startedAt)
    val ready = rest.targetSec in 1..elapsed
    val fg = if (ready) p.onRestReady else p.restText
    Text(
        if (ready) "Ready ${formatTime(elapsed)}" else "⏱ ${formatTime(elapsed)}" + if (rest.targetSec > 0) " / ${formatTime(rest.targetSec)}" else "",
        color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = NumberStyle,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(if (ready) p.restReady else p.restTint)
            .clickable(role = Role.Button, onClickLabel = "Stop the rest clock", onClick = onStop)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun elapsedSeconds(startedAt: Long): Int {
    val seconds by produceState(0, startedAt) {
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            value = (elapsed / 1000).toInt()
            delay(1000 - elapsed % 1000)
        }
    }
    return seconds
}

@Composable
private fun GroupExerciseCard(vm: AppViewModel, day: String, ex: Exercise, item: GroupItem, sets: List<GymSet>, number: Int, state: String) {
    val p = LocalPalette.current
    val last by produceState<List<GymSet>?>(null, ex.id, day) { value = vm.lastSets(ex.id, day) }
    val shape = RoundedCornerShape(20.dp)
    val statusColor = when (state) {
        "done" -> p.workText
        "now" -> p.accentText
        else -> p.muted
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (state == "done") p.group else p.surface)
            .then(if (state == "now") Modifier.border(2.dp, p.accent, shape) else Modifier)
            .clickable(onClickLabel = "Log ${ex.name}") { vm.openGymLog(ex.id, day) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val badgeBg = when (state) { "done" -> p.workTint; "now" -> p.accent; else -> p.tint }
            Box(Modifier.size(32.dp).clip(CircleShape).background(badgeBg), contentAlignment = Alignment.Center) {
                Text(if (state == "done") "✓" else "$number", color = if (state == "now") p.onAccent else statusColor, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(ex.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                val details = buildList {
                    add(item.planLabel())
                    if (sets.isNotEmpty()) add("${sets.size} logged")
                    if (ex.perSide) add("per side")
                    last?.let { previous ->
                        if (previous.isEmpty()) add("First time")
                        else add("Last time · ${previous.size} sets · ${previous.last().summary(ex)}")
                    }
                }
                Text(details.joinToString(" · "), color = p.muted, fontSize = 12.sp)
            }
            Text(state, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        GroupSetGrid(vm, ex, day, sets, item.planSets ?: 0)
    }
}

@Composable
private fun GroupSetGrid(vm: AppViewModel, ex: Exercise, day: String, sets: List<GymSet>, planned: Int) {
    val p = LocalPalette.current
    val remaining = (planned - sets.size).coerceAtLeast(0)
    if (sets.isEmpty() && remaining == 0) return
    val metric = metricsFor(ex).firstOrNull { it != GymMetric.REST }
    val history = if (sets.isNotEmpty() && metric != null) {
        val all by remember(ex.id) { vm.exerciseAll(ex.id) }.collectAsStateWithLifecycle(emptyList())
        all
    } else emptyList()
    val records = remember(ex, day, history) {
        recordSets(ex, day, history).map { it.id }.toSet()
    }
    val rows = remember(sets) { dayRows(sets) }
    var firstSet = 0
    val cells = buildList {
        rows.forEach { row ->
            val pr = (firstSet until firstSet + row.count).any { sets[it].id in records }
            add(SetCell(row.label + if (pr) " · PR" else "", row.set.summary(ex), row.count, pr))
            firstSet += row.count
        }
        // shortcut: cap empty boxes for large plans, show the exact remaining count in the final box
        repeat(remaining.coerceAtMost(12)) { i ->
            add(SetCell("${sets.size + i + 1}", if (i == 11 && remaining > 12) "+${remaining - i} left" else "—"))
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cell ->
                    val fg = when { cell.pr -> p.recordText; cell.count > 0 -> p.workText; else -> p.muted }
                    val bg = when { cell.pr -> p.recordTint; cell.count > 0 -> p.workTint; else -> Color.Transparent }
                    Column(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).background(bg)
                            .then(if (cell.count == 0) Modifier.border(1.dp, p.groupLine, shape) else Modifier)
                            .padding(horizontal = 3.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (cell.count > 1) "${cell.label} · ×${cell.count}" else cell.label, color = fg, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text(cell.detail, color = fg, fontSize = 13.sp, style = NumberStyle, textAlign = TextAlign.Center)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private data class SetCell(val label: String, val detail: String, val count: Int = 0, val pr: Boolean = false)
