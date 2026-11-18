package app.intervals.ui.today

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.intervals.core.Session
import app.intervals.core.colorAt
import app.intervals.core.day
import app.intervals.core.dayStrip
import app.intervals.core.formatDuration
import app.intervals.core.totalSec
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.Tab
import app.intervals.ui.journal.GymDayCard
import app.intervals.ui.journal.SessionCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home (bar option D): the last 7 days, start Intervals or Gym, quick start of what you do most, what you did today.
 */
@Composable
fun TodayScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val first = today.minusDays(6)
    // the last 7 days: interval sessions and gym sets
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val sessions by remember(from) { vm.sessions(from, to) }.collectAsState(initial = emptyList())
    val gymSets by remember(first) { vm.gymSetsBetween(first.toString(), today.plusDays(1).toString()) }.collectAsState(initial = emptyList())
    val groups by vm.groups.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val lastRuns by vm.lastByWorkout.collectAsStateWithLifecycle()
    val lastUsed by vm.lastUsed.collectAsStateWithLifecycle()
    val sessionsByDay = sessions.groupBy { it.day(zone) }
    val gymByDay = gymSets.groupBy { LocalDate.parse(it.day) }

    // quick start: the 3 most recent interval workouts and groups together, newest first
    data class Quick(val name: String, val sub: String, val lastDay: String, val color: Color, val gym: Boolean, val start: () -> Unit)
    val quick = buildList {
        workouts.orEmpty().mapNotNull { w -> lastRuns[w.id]?.let { w to it } }.forEach { (w, s) ->
            add(Quick(w.workout.name, "intervals · ${formatDuration(w.workout.totalSec)} · last ${ago(s.day(zone), today)}",
                s.day(zone).toString(), p.pink, false) { vm.player(w.id) })
        }
        groups.forEachIndexed { i, g ->
            val last = g.items.mapNotNull { lastUsed[it.exerciseId] }.maxOrNull() ?: return@forEachIndexed
            val names = g.items.mapNotNull { item -> exercises.firstOrNull { it.id == item.exerciseId }?.name }.joinToString(", ")
            add(Quick(g.name, "gym · last ${ago(LocalDate.parse(last), today)} · $names", last, Color(g.colorAt(i)), true) {
                vm.startGroup(g.id, today.toString()); vm.tab = Tab.GYM
            })
        }
    }.sortedByDescending { it.lastDay }.take(4)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)), color = p.muted, fontSize = 13.sp)
                }
                IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
            }
        }
        // the last 7 days, today on the right; tap = that day in the Journal
        item {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).map { first.plusDays(it.toLong()) }.forEach { d ->
                    val isToday = d == today
                    val strip = gymByDay[d]?.let { dayStrip(it, groups) }.orEmpty()
                    val ss: List<Session> = sessionsByDay[d].orEmpty()
                    Column(
                        Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (isToday) p.tint else p.surface).clickable { vm.openJournalDay(d.toString()) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (isToday) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                            color = if (isToday) p.accentText else p.muted, fontSize = 10.sp, maxLines = 1)
                        Text("${d.dayOfMonth}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (strip.isNotEmpty()) Row(Modifier.padding(top = 2.dp).width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            strip.forEach { Box(Modifier.weight(it.sets.toFloat()).height(4.dp).background(Color(it.color))) }
                        }
                        if (ss.isNotEmpty()) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            ss.take(3).forEach { s -> Box(Modifier.size(4.dp).clip(CircleShape).background(if (s.completed) p.pink else p.yellow)) }
                        }
                    }
                }
            }
        }
        // start
        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StartButton("Intervals", AppIcons.Intervals, p.accent, Color.White, Modifier.weight(1f)) { vm.tab = Tab.INTERVALS }
                StartButton("Gym", AppIcons.Gym, p.pink, Color(0xFF14080C), Modifier.weight(1f)) { vm.gymFocusDay = today.toString(); vm.tab = Tab.GYM }
            }
        }
        if (quick.isNotEmpty()) {
            item { Section("Quick start") }
            quick.forEach { q ->
                item {
                    Row(
                        Modifier.padding(bottom = 6.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(p.surface)
                            .clickable(onClickLabel = "Start ${q.name}", onClick = q.start).padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(6.dp).height(30.dp).clip(RoundedCornerShape(3.dp)).background(q.color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(q.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(q.sub, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (q.gym) p.pink else p.accent), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Play, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // done today
        val todaySessions = sessionsByDay[today].orEmpty().sortedBy { it.startedAt }
        val todayGym = gymByDay[today].orEmpty()
        item { Section("Done today") }
        if (todaySessions.isEmpty() && todayGym.isEmpty()) item {
            Text("Nothing yet. Start above, or pick something from Quick start.", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
        if (todayGym.isNotEmpty()) item {
            GymDayCard(todayGym, exercises.associateBy { it.id }, groups, records = 0) { vm.gymFocusDay = today.toString(); vm.tab = Tab.GYM }
            Spacer(Modifier.height(8.dp))
        }
        todaySessions.forEach { s ->
            item(key = "s" + s.id) {
                SessionCard(s, zone, onNote = { vm.openJournalDay(today.toString()) }, onLongPress = { vm.openJournalDay(today.toString()) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun ago(d: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 7 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        else -> "${d.dayOfMonth} " + d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }
}

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Text(title.uppercase(), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun StartButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(92.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClickLabel = "Open $label", onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(26.dp))
        Text("$label ▶", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
