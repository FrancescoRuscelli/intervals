package app.intervals.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.GymMetric
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.gymDaysPerWeek
import app.intervals.core.recentRecords
import app.intervals.core.setsPerExercise
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.LocalDate

/** Stats, Gym tab (mockup G13): days / sets / exercises, gym days per week, recent records, most trained, real rest vs target. */
@Composable
fun GymStatsSection(vm: AppViewModel, start: LocalDate, today: LocalDate) {
    val p = LocalPalette.current
    val since = remember(today) { today.minusWeeks(12).toString() }
    val from = minOf(since, start.toString())
    val flow = remember(from) { vm.gymSetsSince(from) }
    val all by flow.collectAsState(initial = emptyList())
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val byId = exercises.associateBy { it.id }
    val inPeriod = all.filter { it.day >= start.toString() }

    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("Gym days", "${inPeriod.map { it.day }.distinct().size}", Modifier.weight(1f))
        Tile("Sets", "${inPeriod.size}", Modifier.weight(1f))
        Tile("Exercises", "${inPeriod.map { it.exerciseId }.distinct().size}", Modifier.weight(1f))
    }

    // gym days per week
    val weeks = gymDaysPerWeek(all.map { it.day }.distinct(), 12, today)
    SCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Gym days per week", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val avg = weeks.dropLast(1).map { it.second }.average().takeIf { !it.isNaN() } ?: 0.0
            Text("last 12 weeks · avg ${"%.1f".format(avg)}", color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        val max = (weeks.maxOfOrNull { it.second } ?: 0).coerceAtLeast(3)
        Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = weeks.joinToString { "${it.first}: ${it.second}" } }) {
            val n = weeks.size
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            weeks.forEachIndexed { i, (_, d) ->
                if (d == 0) return@forEachIndexed
                val h = (size.height - 2.dp.toPx()) * d / max
                drawRoundRect(if (i == n - 1) p.accent else Color(0xFF4C3A80), Offset(i * (w + gap), size.height - h), Size(w, h),
                    CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            weeks.forEachIndexed { i, (wk, _) ->
                Text(if (i % 3 == 0) "${wk.dayOfMonth}/${wk.monthValue}" else "", color = p.muted, fontSize = 10.sp,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }

    // recent records
    val records = recentRecords(exercises, all, today.minusDays(30).toString()).take(6)
    SCard {
        Text("Recent records", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("last 30 days", color = p.muted, fontSize = 12.sp)
        if (records.isEmpty()) Text("None in the last 30 days.", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        records.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(p.yellow), contentAlignment = Alignment.Center) {
                    Text("PR", color = Color(0xFF1A1200), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.exercise.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.metric.title.lowercase() + " · " + app.intervals.ui.gym.shortDay(r.day), color = p.muted, fontSize = 12.sp)
                }
                Text(
                    when (r.metric) {
                        GymMetric.MAX_TIME -> formatTime(r.value.toInt())
                        GymMetric.MAX_REPS, GymMetric.TOTAL_REPS -> "${r.value.toInt()}"
                        GymMetric.LEAST_ASSIST -> "−" + formatKg(Math.round(r.value * 10) / 10.0) + " kg"
                        else -> formatKg(Math.round(r.value * 10) / 10.0) + " kg"
                    },
                    color = p.text, style = NumberStyle, fontSize = 16.sp,
                )
            }
        }
    }

    // most trained in the period
    val top = setsPerExercise(inPeriod).take(6)
    SCard {
        Text("Most trained", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("sets in this period", color = p.muted, fontSize = 12.sp)
        if (top.isEmpty()) Text("No sets in this period.", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        val max = top.maxOfOrNull { it.second } ?: 1
        top.forEach { (id, n) ->
            Column(Modifier.padding(top = 10.dp)) {
                Row {
                    Text(byId[id]?.name ?: "?", color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$n", color = p.muted, fontSize = 13.sp)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.line)) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.pink))
                }
            }
        }
    }

    // real rest vs target (only exercises with rests logged in the app)
    val rests = inPeriod.filter { it.restSec != null }.groupBy { it.exerciseId }
        .mapNotNull { (id, s) -> byId[id]?.let { it to s.mapNotNull { x -> x.restSec }.average() } }
        .sortedByDescending { it.second }.take(5)
    if (rests.isNotEmpty()) SCard {
        Text("Rest between sets", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("your real average · white mark = your target", color = p.muted, fontSize = 12.sp)
        val scale = (rests.maxOf { maxOf(it.second, it.first.restSec.toDouble()) } * 1.15).coerceAtLeast(60.0)
        rests.forEach { (ex, avg) ->
            Column(Modifier.padding(top = 10.dp)) {
                Row {
                    Text(ex.name, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatTime(avg.toInt()) + " · target " + formatTime(ex.restSec), color = p.muted, fontSize = 12.sp)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(14.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.line))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth((avg / scale).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.teal))
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
                        Box(Modifier.offset(x = maxWidth * (ex.restSec / scale).toFloat()).width(2.dp).height(14.dp).background(p.text))
                    }
                }
            }
        }
    }
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
private fun SCard(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) { content() }
}
