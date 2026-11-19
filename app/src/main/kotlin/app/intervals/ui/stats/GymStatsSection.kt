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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.ExerciseTraining
import app.intervals.core.GymMetric
import app.intervals.core.GymSet
import app.intervals.core.exerciseTraining
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.gymDaysPerWeek
import app.intervals.core.recentRecords
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.LocalDate

/** Stats, Gym tab (mockup G13): days / sets / exercises, gym days per week, recent records, most trained. */
@Composable
fun GymStatsSection(vm: AppViewModel, start: LocalDate, today: LocalDate) {
    val p = LocalPalette.current
    val since = remember(today) { today.minusWeeks(12).toString() }
    val from = minOf(since, start.toString())
    val flow = remember(from) { vm.gymSetsSince(from) }
    val all by flow.collectAsState(initial = emptyList())
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val inPeriod = remember(all, start) {
        val firstDay = start.toString()
        all.filter { it.day >= firstDay }
    }

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
                drawRoundRect(if (i == n - 1) p.accent else if (p.dark) Color(0xFF4C3A80) else p.accent.copy(alpha = 0.55f), Offset(i * (w + gap), size.height - h), Size(w, h),
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
        if (records.isEmpty()) Text("None in the last 30 days.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
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
                        else -> formatKg(Math.round(r.value * 10) / 10.0) + " kg"
                    },
                    color = p.text, style = NumberStyle, fontSize = 16.sp,
                )
            }
        }
    }

    MostTrainedCard(inPeriod, exercises)
}

private enum class TrainingMetric(val label: String) {
    SETS("Sets"), DAYS("Days"), REPS("Reps");

    fun amount(training: ExerciseTraining): Long = when (this) {
        SETS -> training.sets.toLong()
        DAYS -> training.days.toLong()
        REPS -> training.work ?: 0L
    }

    fun display(training: ExerciseTraining): String {
        val amount = amount(training)
        return when (this) {
            SETS -> "%,d %s".format(amount, if (amount == 1L) "set" else "sets")
            DAYS -> "%,d %s".format(amount, if (amount == 1L) "day" else "days")
            REPS -> when {
                training.work == null -> "—"
                training.timed -> "%,d min %d s".format(amount / 60, amount % 60)
                else -> "%,d %s".format(amount, if (amount == 1L) "rep" else "reps")
            }
        }
    }
}

@Composable
private fun MostTrainedCard(sets: List<GymSet>, exercises: List<Exercise>) {
    val p = LocalPalette.current
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    var selected by rememberSaveable { mutableIntStateOf(TrainingMetric.SETS.ordinal) }
    val metric = TrainingMetric.entries[selected]
    val totals = remember(sets) { exerciseTraining(sets) }
    val groups = remember(totals, metric) {
        val sections = if (metric == TrainingMetric.REPS) {
            val (timed, counted) = totals.filter { it.work != null }.partition { it.timed }
            listOf("Reps" to counted, "Time" to timed)
        } else {
            listOf(null to totals)
        }
        sections.filter { (_, rows) -> rows.isNotEmpty() }
            .map { (label, rows) -> label to rows.sortedByDescending(metric::amount).take(6) }
    }

    SCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Most trained", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(
                Modifier.selectableGroup().border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TrainingMetric.entries.forEach { option ->
                    val on = option == metric
                    Box(
                        Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (on) p.accent else Color.Transparent)
                            .selectable(on, role = Role.RadioButton) { selected = option.ordinal }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option.label, color = if (on) p.onAccent else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            val message = if (totals.isEmpty()) "No sets in this period." else "No reps or time logged in this period."
            Text(message, color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
        groups.forEach { (label, rows) ->
            if (label != null) {
                Text(label, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            }
            val max = metric.amount(rows.first()).coerceAtLeast(1L)
            rows.forEach { training ->
                Column(Modifier.padding(top = 10.dp)) {
                    Text(byId[training.exerciseId]?.name ?: "?", color = p.text, fontSize = 14.sp)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) {
                                append(metric.display(training))
                            }
                            TrainingMetric.entries.filter { it != metric }.forEach {
                                append(" · ")
                                append(it.display(training))
                            }
                        },
                        color = p.muted, fontSize = 12.sp, style = NumberStyle,
                    )
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.line)) {
                        Box(Modifier.fillMaxWidth(metric.amount(training).toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.pink))
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
