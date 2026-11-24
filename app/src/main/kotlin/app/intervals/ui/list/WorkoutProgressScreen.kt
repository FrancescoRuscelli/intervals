package app.intervals.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Side
import app.intervals.core.ValueKey
import app.intervals.core.valueKeys
import app.intervals.core.valueRecords
import app.intervals.core.valueSeries
import app.intervals.player.fmtValue
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.gym.Chip
import app.intervals.ui.gym.Legend
import app.intervals.ui.gym.LineChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Period(val label: String, val months: Long?) { M3("3 M", 3), M6("6 M", 6), Y1("1 Y", 12), ALL("All", null) }

/**
 * Progress of an interval workout's logged values (mockup intervals-ideas-from-other-apps.html, 3): a chip per value
 * (Load, Edge…), left pink and right teal, records gold while still the best and grey once beaten. Tap a point = that day
 * in the Journal.
 */
@Composable
fun WorkoutProgressScreen(vm: AppViewModel, workoutId: Long) {
    val p = LocalPalette.current
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val name = workouts?.firstOrNull { it.id == workoutId }?.workout?.name
    val sessions by remember(workoutId) { vm.sessionsOf(workoutId) }.collectAsStateWithLifecycle(emptyList())
    val zone = ZoneId.systemDefault()
    val dayOf = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString() }
    // one chip per value, both sides of a value under one chip
    val keys = remember(sessions) { valueKeys(sessions) }
    val fields = keys.map { it.series to it.field }.distinct()
    var picked by rememberSaveable(workoutId) { mutableStateOf(0) }
    val field = fields.getOrNull(picked) ?: fields.firstOrNull()
    var periodName by rememberSaveable(workoutId) { mutableStateOf(Period.M6.name) }
    val period = Period.valueOf(periodName)
    val from = period.months?.let { LocalDate.now().minusMonths(it).toString() }
    val fmtDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Column(Modifier.weight(1f)) {
                Text(name ?: sessions.firstOrNull()?.workoutName.orEmpty(), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Progress · ${sessions.size} session${if (sessions.size == 1) "" else "s"}", color = p.muted, fontSize = 12.sp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (field == null) {
                Text("Nothing logged yet. Add a value to log (Load, Edge…) to a series in Edit workout, and it shows here after a session.",
                    color = p.muted, fontSize = 14.sp)
                return@Column
            }
            if (fields.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val many = fields.map { it.first }.distinct().size > 1
                fields.forEachIndexed { i, (series, f) ->
                    val unit = keys.first { it.series == series && it.field == f }.unit
                    Chip((if (many) "$series · " else "") + f + if (unit.isEmpty()) "" else " $unit", i == picked) { picked = i }
                }
            }
            val sideKeys = keys.filter { it.series == field.first && it.field == field.second }
            val shown = sessions.filter { from == null || dayOf(it.startedAt) >= from }
            val lines = sideKeys.map { k -> k to valueSeries(shown, k, dayOf) }
            val unit = sideKeys.first().unit
            val fmt = { v: Double -> fmtValue(v) + if (unit.isEmpty()) "" else " $unit" }
            val color = { k: ValueKey -> if (k.side == Side.RIGHT) p.teal else p.pink }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) {
                Text(field.second, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val pts = lines.flatMap { it.second }
                if (pts.map { it.day }.distinct().size < 2) {
                    Text(if (pts.isEmpty()) "Nothing in this period." else "One day only: the graph needs at least two.", color = p.muted, fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 40.dp))
                } else {
                    LineChart(lines.map { it.second }, lines.map { color(it.first) }, fmt) { day -> vm.openJournalDay(day) }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        sideKeys.forEach { k -> Legend(color(k), when (k.side) { Side.LEFT -> "Left"; Side.RIGHT -> "Right"; null -> "per day" }) }
                        Legend(p.recordText, "record")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { r -> Chip(r.label, r == period) { periodName = r.name } }
            }
            // records over the whole history: gold while still the best, grey once beaten (as in the gym)
            Text("RECORDS", color = p.muted, fontSize = 12.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(horizontal = 12.dp, vertical = 4.dp)) {
                sideKeys.forEach { k ->
                    valueRecords(valueSeries(sessions, k, dayOf)).take(4).forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when (k.side) { Side.LEFT -> "Left"; Side.RIGHT -> "Right"; null -> "" },
                                color = color(k), fontSize = 12.sp, modifier = Modifier.width(44.dp))
                            Icon(AppIcons.Cup, null, tint = if (r.beatenOn == null) p.yellow else p.faint, modifier = Modifier.padding(end = 8.dp).width(14.dp))
                            Text(fmt(r.value), color = if (r.beatenOn == null) p.text else p.muted, style = NumberStyle, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            val day = LocalDate.parse(r.day).format(fmtDay)
                            Text(if (r.beatenOn == null) "best now · $day" else "$day · until ${LocalDate.parse(r.beatenOn).format(fmtDay)}",
                                color = if (r.beatenOn == null) p.recordText else p.faint, fontSize = 12.sp,
                                modifier = Modifier.clickable { vm.openJournalDay(r.day) })
                        }
                    }
                }
            }
        }
    }
}
