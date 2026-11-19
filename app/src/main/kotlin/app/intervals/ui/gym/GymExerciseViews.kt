package app.intervals.ui.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.GymMetric
import app.intervals.core.GymSet
import app.intervals.core.MetricPoint
import app.intervals.core.Side
import app.intervals.core.dayRows
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.historyDays
import app.intervals.core.metricSeries
import app.intervals.core.metricsFor
import app.intervals.core.repMaxes
import app.intervals.core.summary
import app.intervals.core.topSet
import app.intervals.core.trend
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- History ----------

/** Every training day of the exercise, newest first; tap a day to open it in the log. */
@Composable
fun ExerciseHistory(ex: Exercise, all: List<GymSet>, onOpenDay: (String) -> Unit) {
    val p = LocalPalette.current
    val days = historyDays(all)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                if (days.isEmpty()) "Nothing logged yet."
                else "${days.size} days · ${all.size} sets · since ${shortDay(days.last().first)}",
                color = p.muted, fontSize = 13.sp,
            )
        }
        items(days, key = { it.first }) { (day, sets) ->
            val top = topSet(sets, ex)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable { onOpenDay(day) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(longDay(day), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "${sets.size} set${if (sets.size == 1) "" else "s"}" + (top?.let { " · top " + it.summary(ex) } ?: ""),
                        color = p.muted, fontSize = 12.sp,
                    )
                }
                dayRows(sets).forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.label, color = p.muted, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                        Text(row.set.summary(ex), color = p.text, style = NumberStyle, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (row.count > 1) {
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.workTint).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("×${row.count}", color = p.workText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        // the real rest before these sets, on the side of their row
                        row.restText?.let { Text(it, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(76.dp)) }
                    }
                    if (row.set.note.isNotBlank()) Text(row.set.note, color = p.muted, fontSize = 12.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 44.dp))
                }
            }
        }
    }
}

// ---------- Graph ----------

private enum class Range(val label: String, val months: Long?) { M3("3 M", 3), M6("6 M", 6), Y1("1 Y", 12), ALL("All", null) }

/** One graph at a time, chosen with chips; per-side exercises draw left and right. */
@Composable
fun ExerciseGraph(ex: Exercise, all: List<GymSet>) {
    val p = LocalPalette.current
    val metrics = metricsFor(ex).filter { m -> m != GymMetric.REST || all.any { it.restSec != null } }
    var metricName by rememberSaveable(ex.id) { mutableStateOf(metrics.first().name) }
    val metric = GymMetric.valueOf(metricName).takeIf { it in metrics } ?: metrics.first()
    var rangeName by rememberSaveable { mutableStateOf(Range.M6.name) }
    val range = Range.valueOf(rangeName)
    val from = range.months?.let { LocalDate.now().minusMonths(it).toString() }
    val sets = if (from == null) all else all.filter { it.day >= from }
    val lines: List<Pair<String, List<MetricPoint>>> =
        if (ex.perSide) listOf("Left" to metricSeries(metric, sets, Side.LEFT), "Right" to metricSeries(metric, sets, Side.RIGHT))
        else listOf("" to metricSeries(metric, sets))
    val fmt: (Double) -> String = when (metric) {
        GymMetric.MAX_TIME, GymMetric.REST -> { v -> formatTime(v.toInt()) }
        GymMetric.TOTAL_REPS, GymMetric.MAX_REPS -> { v -> v.toInt().toString() }
        GymMetric.LEAST_ASSIST -> { v -> "−" + formatKg(round1(v)) + " kg" }
        else -> { v -> formatKg(round1(v)) + " kg" }
    }
    var tapped by rememberSaveable(metricName, rangeName) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.forEach { m -> Chip(m.title, m == metric) { metricName = m.name } }
        }
        val pts = lines.flatMap { it.second }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val best = if (metric.lowerIsBetterUi()) pts.minByOrNull { it.value } else pts.maxByOrNull { it.value }
                if (best != null && metric != GymMetric.REST) Text("best " + fmt(best.value), color = p.text, style = NumberStyle, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (pts.size < 2) {
                Text(if (pts.isEmpty()) "Nothing in this period." else "One day only: the graph needs at least two.", color = p.muted, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 40.dp))
            } else {
                LineChart(lines.map { it.second }, if (ex.perSide) listOf(p.pink, p.teal) else listOf(p.pink), fmt) { tapped = it }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (ex.perSide) { Legend(p.pink, "Left"); Legend(p.teal, "Right") } else Legend(p.pink, "per day")
                    if (metric != GymMetric.REST) Legend(p.recordText, "record")
                    if (!ex.perSide) Legend(p.muted, "trend")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Range.entries.forEach { r -> Chip(r.label, r == range) { rangeName = r.name } }
        }
        tapped?.let { day ->
            val daySets = all.filter { it.day == day }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, p.accent, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(longDay(day), color = p.accentText, fontSize = 12.sp)
                Text(daySets.joinToString(" · ") { (it.side?.let { s -> (if (s == Side.LEFT) "L " else "R ") } ?: "") + it.summary(ex) },
                    color = p.text, style = NumberStyle, fontSize = 14.sp)
            }
        }
        Text("Tap the graph to see a day.", color = p.muted, fontSize = 12.sp)
    }
}

private fun GymMetric.lowerIsBetterUi() = this == GymMetric.LEAST_ASSIST
private fun round1(v: Double) = Math.round(v * 10) / 10.0

@Composable
private fun LineChart(series: List<List<MetricPoint>>, colors: List<Color>, fmt: (Double) -> String, onTap: (String) -> Unit) {
    val p = LocalPalette.current
    val days = series.flatten().map { it.day }.distinct().sorted()
    val values = series.flatten().map { it.value }
    val lo = values.min()
    val hi = values.max()
    val pad = ((hi - lo) * 0.12).coerceAtLeast(if (hi == lo) (hi * 0.1).coerceAtLeast(1.0) else 0.0)
    val min = lo - pad
    val max = hi + pad
    val desc = series.joinToString(". ") { s -> s.joinToString { "${it.day} ${fmt(it.value)}" } }
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = desc }
            .pointerInput(days) {
                detectTapGestures { o ->
                    val i = Math.round(o.x / size.width * (days.size - 1)).coerceIn(0, days.lastIndex)
                    onTap(days[i])
                }
            },
    ) {
        val left = 0f
        val w = size.width
        val h = size.height - 4.dp.toPx()
        fun x(day: String) = left + w * days.indexOf(day) / (days.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (h - (h * (v - min) / (max - min))).toFloat() + 2.dp.toPx()
        // grid: 3 lines
        for (k in 0..2) {
            val yy = 2.dp.toPx() + h * k / 2
            drawLine(p.line, Offset(0f, yy), Offset(w, yy), 1.dp.toPx())
        }
        if (series.size == 1) {
            trend(series[0].map { it.value })?.let { (a, b) ->
                drawLine(p.muted, Offset(x(series[0].first().day), y(a)), Offset(x(series[0].last().day), y(b)), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        series.forEachIndexed { si, pts ->
            if (pts.isEmpty()) return@forEachIndexed
            val path = Path()
            pts.forEachIndexed { i, pt -> if (i == 0) path.moveTo(x(pt.day), y(pt.value)) else path.lineTo(x(pt.day), y(pt.value)) }
            drawPath(path, colors[si], style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEach { pt ->
                drawCircle(p.surface, (if (pt.record) 6f else 4.5f).dp.toPx(), Offset(x(pt.day), y(pt.value)))
                drawCircle(if (pt.record) p.recordText else colors[si], (if (pt.record) 4.5f else 3f).dp.toPx(), Offset(x(pt.day), y(pt.value)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(shortDay(days.first()), color = p.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(fmt(lo) + " – " + fmt(hi), color = p.muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(shortDay(days.last()), color = p.muted, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) p.tint else p.surface)
            .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) p.accentText else p.soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

// ---------- Records ----------

/** Real records (heaviest per reps, beaten ones faded); holds and reps for the other kinds. No estimates. */
@Composable
fun ExerciseRecords(ex: Exercise, all: List<GymSet>) {
    val p = LocalPalette.current
    val hasW = GymField.WEIGHT in ex.fields
    val hasR = GymField.REPS in ex.fields
    val sides: List<Side?> = if (ex.perSide) listOf(Side.LEFT, Side.RIGHT) else listOf(null)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (all.isEmpty()) item { Text("No records yet: log some sets.", color = p.muted, fontSize = 14.sp) }
        sides.forEach { side ->
            val sets = all.filter { side == null || it.side == side }
            if (sets.isEmpty()) return@forEach
            val title = when (side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }
            if (hasW && hasR) item {
                Card("Real records$title", if (ex.assist) "least assist for each number of reps" else "heaviest for each number of reps") {
                    val rms = repMaxes(sets, ex.assist)
                    rms.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            row.forEach { rm ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (rm.beaten) p.bg else p.recordTint)
                                        .border(1.dp, if (rm.beaten) p.line else p.recordText, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("${rm.reps} rep${if (rm.reps == 1) "" else "s"}", color = if (rm.beaten) p.muted else p.recordText, fontSize = 11.sp)
                                    Text((if (ex.assist) "−" else "") + formatKg(rm.weightKg), color = if (rm.beaten) p.muted else p.recordText,
                                        style = NumberStyle, fontSize = 17.sp)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text("Faded = beaten by more reps with as much (or less assist). Tap the Graph tab for the history.",
                        color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (!(hasW && hasR)) item {
                val bestTime = sets.maxByOrNull { it.timeSec ?: -1 }?.takeIf { it.timeSec != null }
                val bestReps = sets.maxByOrNull { it.reps ?: -1 }?.takeIf { it.reps != null }
                Card("Records$title", null) {
                    bestTime?.let { RecordLine("Longest", formatTime(it.timeSec!!), shortDay(it.day)) }
                    bestReps?.let { RecordLine("Most reps", "${it.reps}" + (it.bands.takeIf { b -> b.isNotEmpty() }?.let { _ -> " · " + it.summary(ex) } ?: ""), shortDay(it.day)) }
                    if (hasW) sets.filter { it.weightKg != null }.let { ws ->
                        val b = if (ex.assist) ws.minByOrNull { it.weightKg!! } else ws.maxByOrNull { it.weightKg!! }
                        b?.let { RecordLine(if (ex.assist) "Least assist" else "Heaviest", it.summary(ex), shortDay(it.day)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordLine(label: String, value: String, day: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(p.yellow), contentAlignment = Alignment.Center) {
            Text("PR", color = Color(0xFF1A1200), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(day, color = p.muted, fontSize = 12.sp)
        }
        Text(value, color = p.text, style = NumberStyle, fontSize = 16.sp)
    }
}

@Composable
private fun Card(title: String, sub: String?, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) {
        Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = p.muted, fontSize = 12.sp)
        content()
    }
}

private fun longDay(day: String): String =
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern(
        if (LocalDate.parse(day).year == LocalDate.now().year) "EEEE d MMMM" else "EEEE d MMMM yyyy", Locale.ENGLISH))
