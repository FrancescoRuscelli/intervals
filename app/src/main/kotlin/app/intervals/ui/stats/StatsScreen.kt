package app.intervals.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.day
import app.intervals.core.minutesPerDay
import app.intervals.core.minutesPerWeek
import app.intervals.core.summary
import app.intervals.core.topWorkouts
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** Week / Month / Year: time, sessions, completed; minutes chart; most done workouts. */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIdx]
    // one query for the last ~13 months covers every view
    val from = remember { today.minusMonths(13).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val to = remember { today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val flow = remember { vm.sessions(from, to) }
    val all by flow.collectAsState(initial = emptyList())

    val start = when (period) {
        Period.WEEK -> app.intervals.core.weekStart(today)
        Period.MONTH -> today.withDayOfMonth(1)
        Period.YEAR -> today.withDayOfYear(1)
    }
    val inPeriod = all.filter { !it.day(zone).isBefore(start) }
    val sum = summary(inPeriod, zone)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Stats", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Period.entries.forEachIndexed { i, per ->
                val on = i == periodIdx
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.accent else Color.Transparent).clickable { periodIdx = i },
                    contentAlignment = Alignment.Center,
                ) { Text(per.label, color = if (on) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Time", hm(sum.totalSec), Modifier.weight(1f))
            Tile("Sessions", "${sum.sessions}", Modifier.weight(1f))
            Tile("Completed", if (sum.sessions == 0) "–" else "${sum.completed * 100 / sum.sessions}%", Modifier.weight(1f))
        }

        Card {
            val (title, bars) = when (period) {
                Period.WEEK -> "Minutes per week · last 12 weeks" to minutesPerWeek(all, 12, today, zone).map { (w, m) -> "${w.dayOfMonth}/${w.monthValue}" to m }
                Period.MONTH -> "Minutes per day · " + today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) to
                    minutesPerDay(all, YearMonth.from(today), zone).mapIndexed { i, m -> "${i + 1}" to m }
                Period.YEAR -> "Minutes per month · ${today.year}" to (1..12).map { mo ->
                    val ym = YearMonth.of(today.year, mo)
                    ym.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) to minutesPerDay(all, ym, zone).sum()
                }
            }
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Bars(bars, currentIndex = when (period) {
                Period.WEEK -> bars.lastIndex
                Period.MONTH -> today.dayOfMonth - 1
                Period.YEAR -> today.monthValue - 1
            })
        }

        Card {
            Text("Most done", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val top = topWorkouts(inPeriod, 6)
            if (top.isEmpty()) Text("No sessions in this period yet.", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            val max = top.maxOfOrNull { it.times } ?: 1
            top.forEach { t ->
                Column(Modifier.padding(top = 10.dp)) {
                    Row {
                        Text(t.name, color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${t.times}× · ${hm(t.totalSec)}", color = p.muted, fontSize = 13.sp)
                    }
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.line)) {
                        Box(Modifier.fillMaxWidth(t.times.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.pink))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) { content() }
}

/** Simple bar chart: the current period in purple, the others pink; a few labels under it. */
@Composable
private fun Bars(bars: List<Pair<String, Int>>, currentIndex: Int) {
    val p = LocalPalette.current
    val max = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = bars.joinToString { "${it.first}: ${it.second} min" }
        },
    ) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        bars.forEachIndexed { i, (_, m) ->
            if (m <= 0) return@forEachIndexed
            val h = (size.height - 4.dp.toPx()) * m / max
            drawRoundRect(
                if (i == currentIndex) p.accent else p.pink,
                topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h),
                cornerRadius = CornerRadius((w / 2).coerceAtMost(4.dp.toPx())),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val step = when { bars.size > 20 -> 7; bars.size > 12 -> 3; else -> 1 }
        bars.forEachIndexed { i, (label, _) ->
            Text(if (i % step == 0) label else "", color = p.muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
    Text("max ${max} min", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
}

