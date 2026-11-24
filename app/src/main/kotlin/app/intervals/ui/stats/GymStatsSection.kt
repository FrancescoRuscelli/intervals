package app.intervals.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import app.intervals.core.colorAt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
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

/** Gym days / sets / exercises; each opens its list. */
@Composable
internal fun GymTiles(vm: AppViewModel, inPeriod: List<GymSet>, start: LocalDate, end: LocalDate) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Gym days", "${inPeriod.map { it.day }.distinct().size}", Modifier.weight(1f)) { vm.openStatsList("gymDays", start, end) }
        StatTile("Sets", "${inPeriod.size}", Modifier.weight(1f)) { vm.openStatsList("exercises", start, end) }
        StatTile("Exercises", "${inPeriod.map { it.exerciseId }.distinct().size}", Modifier.weight(1f)) { vm.openStatsList("exercises", start, end) }
    }
}

/** Gym days per week, the 12 weeks up to the period's; a bar opens the Journal on that week's first gym day. */
@Composable
internal fun GymDaysPerWeekCard(vm: AppViewModel, all: List<GymSet>, end: LocalDate, anchor: LocalDate, today: LocalDate) {
    val p = LocalPalette.current
    val weeks = gymDaysPerWeek(all.map { it.day }.filter { it < end.toString() }.distinct(), 12, minOf(anchor, today))
    StatCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Gym days per week", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val avg = weeks.map { it.second }.average().takeIf { !it.isNaN() } ?: 0.0
            Text("12 weeks · avg ${"%.1f".format(avg)}", color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        TapBars(weeks.map { (w, d) -> "${w.dayOfMonth}/${w.monthValue}" to d }, current = weeks.lastIndex, unit = "days") { i ->
            val wk = weeks[i].first
            val first = all.map { it.day }.filter { it >= wk.toString() && it < wk.plusWeeks(1).toString() }.minOrNull() ?: wk.toString()
            vm.openJournalDay(first)
        }
    }
}

/** Records set in the period (against the whole history); with none, only its title and a quiet "none". */
@Composable
internal fun RecordsCard(vm: AppViewModel, exercises: List<Exercise>, all: List<GymSet>, start: LocalDate, end: LocalDate) {
    val p = LocalPalette.current
    val records = remember(exercises, all, start, end) { recentRecords(exercises, all, start.toString()).filter { it.day < end.toString() } }
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Records", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (records.isEmpty()) "none in this period" else "${records.size} in this period", color = p.muted, fontSize = 12.sp)
        }
        records.take(5).forEach { r -> RecordRow(r) { vm.openGymLog(r.exercise.id, r.day, tab = 3) } }
        if (records.size > 5) Text("All ${records.size} records ›", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp).clickable { vm.openStatsList("records", start, end) }.padding(vertical = 6.dp))
    }
}

/** One record: the cup, the exercise, what and when, the value; tap opens the exercise's Records. */
@Composable
internal fun RecordRow(r: app.intervals.core.GymRecord, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Records of ${r.exercise.name}", onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(p.recordTint), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(app.intervals.ui.AppIcons.Cup, null, tint = p.yellow, modifier = Modifier.size(16.dp))
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
        Text("  ›", color = p.accentText, fontSize = 14.sp)
    }
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
internal fun MostTrainedCard(sets: List<GymSet>, exercises: List<Exercise>, tagColor: Map<Long, Color>, onAll: () -> Unit, onOpen: (Long) -> Unit) {
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
            .map { (label, rows) -> label to rows.sortedByDescending(metric::amount).take(5) }
    }

    StatCard {
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
        val shown = groups.sumOf { it.second.size }
        groups.forEach { (label, rows) ->
            if (label != null) {
                Text(label, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            }
            val max = metric.amount(rows.first()).coerceAtLeast(1L)
            rows.forEach { training ->
                // one line: the name, the chosen number (and one more); a thin bar in the exercise's tag color
                Column(Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "History") { onOpen(training.exerciseId) }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(byId[training.exerciseId]?.name ?: "?", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = p.text, fontWeight = FontWeight.Bold)) { append(metric.display(training)) }
                                append(" · ")
                                append(if (metric == TrainingMetric.DAYS) TrainingMetric.SETS.display(training) else TrainingMetric.DAYS.display(training))
                                append("  ›")
                            },
                            color = p.muted, fontSize = 12.sp, style = NumberStyle, maxLines = 1, modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
                        Box(Modifier.fillMaxWidth(metric.amount(training).toFloat() / max).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(tagColor[training.exerciseId] ?: p.muted))
                    }
                }
            }
        }
        if (totals.size > shown) Text("All ${totals.size} exercises ›", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onAll).padding(vertical = 6.dp))
    }
}

/**
 * Sets per tag per week (mockup intervals-next-features.html, 2A and 2B): "This week" puts each tag's sets against a
 * tick at its average of the 3 weeks before; "4 weeks" draws each tag's last four weeks. An exercise counts for its first
 * tag; the untagged ones are grouped. Tapping a tag opens the week's exercises.
 */
@Composable
internal fun SetsPerTagCard(vm: AppViewModel, all: List<GymSet>, groups: List<app.intervals.core.GymGroup>, anchor: LocalDate, today: LocalDate) {
    val p = LocalPalette.current
    var fourWeeks by rememberSaveable { mutableStateOf(false) }
    val tags = groups.filter { it.isTag }
    val tagOf = remember(groups) { tags.flatMap { t -> t.items.map { it.exerciseId to t.id } }.distinctBy { it.first }.toMap() }
    val colorOf = remember(groups) { tags.withIndex().associate { (i, t) -> t.id to Color(t.colorAt(i)) } }
    val lastWeek = app.intervals.core.weekStart(minOf(anchor, today))
    val weeks = (3 downTo 0).map { lastWeek.minusWeeks(it.toLong()) }
    // sets[week][tag id or -1 for untagged]
    val counts = remember(all, lastWeek, groups) {
        weeks.map { w ->
            val from = w.toString(); val to = w.plusWeeks(1).toString()
            all.filter { it.day >= from && it.day < to }.groupingBy { tagOf[it.exerciseId] ?: -1L }.eachCount()
        }
    }
    val rows = (tags.map { it.id to it.name } + (-1L to "Untagged")).filter { (id, _) -> counts.any { (it[id] ?: 0) > 0 } }
    val max = (counts.flatMap { it.values }.maxOrNull() ?: 1).coerceAtLeast(1)
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sets per tag", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(Modifier.border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(false to "This week", true to "4 weeks").forEach { (v, label) ->
                    val on = fourWeeks == v
                    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.accent else Color.Transparent)
                        .clickable { fourWeeks = v }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) p.onAccent else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text(
            if (fourWeeks) weeks.joinToString(" · ") { "${it.dayOfMonth}/${it.monthValue}" }
            else "week of ${lastWeek.dayOfMonth}/${lastWeek.monthValue} · tick = 3-week average",
            color = p.muted, fontSize = 12.sp,
        )
        if (rows.isEmpty()) Text("No sets in these weeks.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        rows.forEach { (id, name) ->
            val color = colorOf[id] ?: p.muted
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Exercises of the week") { vm.openStatsList("exercises", lastWeek, lastWeek.plusWeeks(1)) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(72.dp))
                if (fourWeeks) {
                    Row(Modifier.weight(1f).height(34.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        counts.forEachIndexed { i, c ->
                            val v = c[id] ?: 0
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                                if (v > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(v.toFloat() / max).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(color.copy(alpha = if (i == 3) 1f else 0.45f)))
                            }
                        }
                    }
                    Text("${counts[3][id] ?: 0}", color = p.text, fontSize = 13.sp, style = NumberStyle, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                } else {
                    val v = counts[3][id] ?: 0
                    val avg = (0..2).map { counts[it][id] ?: 0 }.average()
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(p.line))
                        Box(Modifier.fillMaxWidth(v.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
                        // the 3-week average as a tick
                        Box(Modifier.fillMaxWidth((avg / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(p.text))
                        }
                    }
                    Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                        Text("$v", color = p.text, fontSize = 13.sp, style = NumberStyle)
                        val d = Math.round(v - avg).toInt()
                        Text((if (d >= 0) "+" else "") + d, color = if (d >= 0) Color(0xFF4ADE80) else p.workText, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
