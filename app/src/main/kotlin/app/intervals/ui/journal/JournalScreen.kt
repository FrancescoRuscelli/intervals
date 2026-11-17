@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.intervals.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Session
import app.intervals.core.day
import app.intervals.core.formatDuration
import app.intervals.core.summary
import app.intervals.core.valuesLine
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar (Monday first) with a dot per session (pink = completed, yellow = stopped),
 * month totals, and the sessions of the selected day with their logged values and note.
 */
@Composable
fun JournalScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDay = LocalDate.parse(dayText)
    val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val flow = remember(from, to) { vm.sessions(from, to) }
    val sessions by flow.collectAsState(initial = emptyList())
    val byDay = sessions.groupBy { it.day(zone) }
    val sum = summary(sessions, zone)
    var editing by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Journal", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(AppIcons.Back, "Previous month", tint = p.text) }
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year, color = p.text, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(118.dp))
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(today)) {
                    Icon(AppIcons.Back, "Next month", tint = if (month < YearMonth.from(today)) p.text else p.faint,
                        modifier = Modifier.size(24.dp).scale(scaleX = -1f, scaleY = 1f))
                }
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Sessions", "${sum.sessions}", Modifier.weight(1f))
                Tile("Days trained", "${sum.days}", Modifier.weight(1f))
                Tile("Time", hm(sum.totalSec), Modifier.weight(1f))
            }
        }
        item { Calendar(month, today, selectedDay, byDay) { dayText = it.toString() } }
        item {
            Text(
                selectedDay.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)),
                color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        val ofDay = byDay[selectedDay].orEmpty().sortedBy { it.startedAt }
        if (ofDay.isEmpty()) item {
            Text(
                if (selectedDay == today) "Nothing yet today. Sessions appear here when you finish or stop a workout."
                else "No sessions this day.",
                color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp),
            )
        }
        items(ofDay, key = { it.id }) { s ->
            SessionCard(s, zone, onNote = { editing = s }, onLongPress = { deleting = s })
            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Note") },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("How did it go?") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { vm.setNote(s.id, text); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this session?") },
            text = { Text("${s.workoutName}, with its logged values and note.") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); deleting = null }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun hm(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, color = p.text, style = NumberStyle, fontSize = 22.sp)
    }
}

@Composable
private fun Calendar(month: YearMonth, today: LocalDate, selected: LocalDate, byDay: Map<LocalDate, List<Session>>, onPick: (LocalDate) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(18.dp)).padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d -> DayCell(d, today, selected, byDay[d].orEmpty(), onPick, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate?, today: LocalDate, selected: LocalDate, sessions: List<Session>, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Box(modifier.height(46.dp), contentAlignment = Alignment.Center) {
        if (d == null) return@Box
        val sel = d == selected
        val future = d > today
        Column(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (sel) p.accent else Color.Transparent)
                .border(2.dp, if (d == today && !sel) p.accent else Color.Transparent, CircleShape)
                .clickable(enabled = !future) { onPick(d) },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("${d.dayOfMonth}", color = if (sel) p.onAccent else if (future) p.faint else p.text, fontSize = 14.sp,
                fontWeight = if (sessions.isNotEmpty() || sel) FontWeight.Bold else FontWeight.Normal)
            Row(Modifier.height(6.dp).padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                sessions.take(3).forEach { s ->
                    Box(Modifier.size(5.dp).clip(CircleShape).background(
                        if (sel) p.onAccent else if (s.completed) p.pink else p.yellow,
                    ))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: Session, zone: ZoneId, onNote: () -> Unit, onLongPress: () -> Unit) {
    val p = LocalPalette.current
    val time = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
            .combinedClickable(onClick = onNote, onLongClick = onLongPress).padding(14.dp),
    ) {
        Box(Modifier.width(5.dp).height(56.dp).clip(RoundedCornerShape(3.dp)).background(if (s.completed) p.pink else p.yellow))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.workoutName, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$time · ${formatDuration(s.durationSec)}", color = p.muted, fontSize = 12.sp)
            }
            Text(
                if (s.completed) "Completed · ${s.roundsTotal} rounds" else "Stopped in round ${(s.roundsDone + 1).coerceAtMost(s.roundsTotal)} of ${s.roundsTotal}",
                color = if (s.completed) p.soft else p.yellow, fontSize = 13.sp,
            )
            s.valuesLine()?.let { Text(it, color = p.soft, fontSize = 13.sp) }
            Text(
                if (s.note.isBlank()) "+ note" else "“${s.note}”",
                color = if (s.note.isBlank()) p.accentText else p.muted, fontSize = 13.sp,
                fontStyle = if (s.note.isBlank()) FontStyle.Normal else FontStyle.Italic,
            )
        }
    }
}
