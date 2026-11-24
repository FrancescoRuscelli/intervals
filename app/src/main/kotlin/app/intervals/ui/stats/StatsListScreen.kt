package app.intervals.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.day
import app.intervals.core.dayGroups
import app.intervals.core.dayStrip
import app.intervals.core.exerciseTraining
import app.intervals.core.recentRecords
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.Overlay
import app.intervals.ui.gym.shortDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a Stats tile or row counts, as a list (mockup intervals-stats-screen.html, 3): the interval sessions, the gym days,
 * the exercises by sets, or the records of the period. A row opens the Journal on its day, or the exercise.
 */
@Composable
fun StatsListScreen(vm: AppViewModel, list: Overlay.StatsList) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val from = LocalDate.parse(list.from)
    val to = LocalDate.parse(list.to)
    val range = "${from.format(fmt)} – ${to.minusDays(1).format(fmt)}"
    val title = when (list.kind) {
        "sessions" -> list.name ?: "Interval sessions"
        "gymDays" -> "Gym days"
        "exercises" -> "Exercises"
        else -> "Records"
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Column {
                Text(title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(range, color = p.muted, fontSize = 12.sp)
            }
        }
        when (list.kind) {
            "sessions" -> {
                val all by remember { vm.sessions(0, Long.MAX_VALUE) }.collectAsState(initial = emptyList())
                val rows = all.filter { val d = it.day(zone); !d.isBefore(from) && d.isBefore(to) && (list.name == null || it.workoutName == list.name) }
                    .sortedByDescending { it.startedAt }
                Rows(rows, "No sessions in this period.") { s ->
                    val d = s.day(zone)
                    Row2(
                        title = s.workoutName,
                        sub = shortDay(d.toString()) + " · " + Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) +
                            " · " + hm(s.durationSec) + if (s.completed) "" else " · stopped",
                        lead = { Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.completed) p.pink else p.yellow)) },
                    ) { vm.openJournalDay(d.toString()) }
                }
            }
            "gymDays", "exercises", "records" -> {
                val all by remember { vm.gymSetsSince("0000-01-01") }.collectAsState(initial = emptyList())
                val exercises by vm.exercises.collectAsStateWithLifecycle()
                val groups by vm.groups.collectAsStateWithLifecycle()
                val byId = exercises.associateBy { it.id }
                val inPeriod = all.filter { it.day >= list.from && it.day < list.to }
                when (list.kind) {
                    "gymDays" -> {
                        val days = inPeriod.groupBy { it.day }.toSortedMap(compareByDescending { it }).toList()
                        Rows(days, "No gym days in this period.") { (day, sets) ->
                            val names = dayGroups(sets, groups).joinToString(" · ") { it.name }
                            Row2(
                                title = shortDay(day),
                                sub = (if (names.isEmpty()) "" else "$names · ") + "${sets.size} set${if (sets.size == 1) "" else "s"}",
                                trail = {
                                    Row(Modifier.width(64.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayStrip(sets, groups).forEach { Box(Modifier.weight(it.sets.toFloat()).height(6.dp).background(Color(it.color))) }
                                    }
                                },
                            ) { vm.openJournalDay(day) }
                        }
                    }
                    "exercises" -> {
                        val rows = exerciseTraining(inPeriod).sortedByDescending { it.sets }
                        Rows(rows, "No sets in this period.") { t ->
                            Row2(
                                title = byId[t.exerciseId]?.name ?: "?",
                                sub = "${t.sets} set${if (t.sets == 1) "" else "s"} · ${t.days} day${if (t.days == 1) "" else "s"}",
                            ) { vm.openGymLog(t.exerciseId, LocalDate.now().toString(), tab = 1) }
                        }
                    }
                    else -> {
                        val rows = recentRecords(exercises, all, list.from).filter { it.day < list.to }
                        Rows(rows, "No records in this period.") { r ->
                            Box(Modifier.padding(horizontal = 16.dp)) { RecordRow(r) { vm.openGymLog(r.exercise.id, r.day, tab = 3) } }
                        }
                    }
                }
            }
        }
    }
}

private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
private fun <T> Rows(rows: List<T>, empty: String, row: @Composable (T) -> Unit) {
    val p = LocalPalette.current
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if (rows.isEmpty()) item { Text(empty, color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
        items(rows) { r ->
            row(r)
            HorizontalDivider(color = p.line, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun Row2(title: String, sub: String, lead: (@Composable () -> Unit)? = null, trail: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead?.let { it(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trail?.let { Spacer(Modifier.width(10.dp)); it() }
        Text("  ›", color = p.accentText, fontSize = 16.sp)
    }
}
