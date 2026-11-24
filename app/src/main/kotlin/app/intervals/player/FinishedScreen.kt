package app.intervals.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Block
import app.intervals.core.LoggedValue
import app.intervals.core.SavedWorkout
import app.intervals.core.Side
import app.intervals.core.formatDuration
import app.intervals.core.seriesCount
import app.intervals.ui.AppIcons
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle

/** The result of a run, shown on the Finished screen. */
data class FinishedInfo(
    val saved: SavedWorkout,
    val completed: Boolean,
    val elapsedMs: Long,
    val roundsDone: Int,
    val startedAt: Long,
    /** The previous session's values, to compare with. */
    val last: List<LoggedValue>,
    /** The values at the end of the run (set before the start, changed in the rests between rounds). */
    val now: Map<FieldKey, Double> = emptyMap(),
    /** The values each round was done with (mockup intervals-ideas-from-other-apps.html, 12). */
    val perRound: Map<Int, Map<FieldKey, Double>> = emptyMap(),
    val letGo: List<app.intervals.core.LetGo> = emptyList(),
    val workDone: Int = 0,
    /** Values to try next time, from this run and the one before (mockup ideas, 1). */
    val hints: Map<FieldKey, Double> = emptyMap(),
    /** Where the let-gos are among the work series played (0 first), for the dots. */
    val letGoAt: Set<Int> = emptySet(),
)

/**
 * One value to confirm: series · field · side (null = both sides). A choice field has [choices]; its value is then the
 * place of the choice in the list.
 */
data class FieldKey(val series: String, val field: String, val unit: String, val side: Side?, val choices: List<String> = emptyList()) {
    /** "20 mm", "half crimp", "+10 kg" as shown on the disc. */
    fun text(v: Double): String = if (choices.isNotEmpty()) choices.getOrElse(v.toInt()) { "" } else fmtValue(v) + if (unit.isEmpty()) "" else " $unit"

    fun matches(l: LoggedValue) = l.series == series && l.field == field && l.side == side

    /** The value of [l] for this key: a choice's place found by its text (so a renamed list still works), else the number. */
    fun valueOf(l: LoggedValue): Double = if (choices.isNotEmpty()) l.text?.let { t -> choices.indexOf(t).takeIf { it >= 0 }?.toDouble() } ?: 0.0 else l.value

    fun logged(round: Int, v: Double) = LoggedValue(series, field, unit, round, side, v, choices.getOrNull(v.toInt()).takeIf { choices.isNotEmpty() })
}

fun fmtValue(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

/** The value of [k] in [values] (the last round logged), or null. */
fun List<LoggedValue>.valueFor(k: FieldKey): Double? = filter { k.matches(it) }.maxByOrNull { it.round }?.let { k.valueOf(it) }

fun fieldKeys(saved: SavedWorkout): List<FieldKey> {
    val series = saved.workout.blocks.flatMap { b -> when (b) { is Block.Single -> listOf(b.series); is Block.Repeat -> b.series } }
        .distinctBy { it.name }
    return series.flatMap { s ->
        s.logFields.flatMap { f ->
            if (f.leftRightApart && s.perSide != null) listOf(Side.LEFT, Side.RIGHT).map { FieldKey(s.name, f.name, f.unit, it, f.choices) }
            else listOf(FieldKey(s.name, f.name, f.unit, null, f.choices))
        }
    }
}

private fun fmt(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

@Composable
fun FinishedScreen(info: FinishedInfo, onDiscard: () -> Unit, onSave: (note: String, values: Map<FieldKey, Double>, repeat: Boolean) -> Unit) {
    val p = LocalPalette.current
    val keys = remember(info) { fieldKeys(info.saved) }
    // start from the values the run ended with; compare with the session before
    val values = remember(info) {
        mutableStateMapOf<FieldKey, Double>().apply { keys.forEach { k -> put(k, info.now[k] ?: info.last.valueFor(k) ?: 0.0) } }
    }
    val lastOf = remember(info) { keys.associateWith { k -> info.last.valueFor(k) } }
    var note by remember { mutableStateOf("") }
    var askDiscard by remember { mutableStateOf(false) }
    if (askDiscard) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askDiscard = false },
        title = { Text("Discard this session?") },
        text = { Text("It won't be saved in the journal.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askDiscard = false; onDiscard() }) { Text("Discard", color = p.dangerText) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askDiscard = false }) { Text("Cancel") } },
    )
    BackHandler { onSave(note, values.toMap(), false) }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { askDiscard = true }) { Text("Discard", color = p.dangerText, fontSize = 15.sp) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onSave(note, values.toMap(), false) }) { Icon(AppIcons.Close, "Save and close", tint = p.text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DoneDisc(info.completed)
            Spacer(Modifier.height(12.dp))
            Text(if (info.completed) "Well done" else "Stopped", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            val roundsTotal = info.saved.workout.rounds
            Text(
                info.saved.workout.name + if (info.completed) " · completed" else " · stopped in round ${(info.roundsDone + 1).coerceAtMost(roundsTotal)}",
                color = p.muted, fontSize = 16.sp, textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Time", formatDuration((info.elapsedMs / 1000).toInt()), Modifier.weight(1f))
                Stat("Rounds", "${info.roundsDone}/$roundsTotal", Modifier.weight(1f))
                Stat("Series", "${info.saved.workout.seriesCount * roundsTotal}", Modifier.weight(1f))
            }

            if (info.letGo.isNotEmpty()) LetGoCard(info)

            if (keys.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface)
                        .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    // rounds done with different values (changed between rounds) say so under the value
                    val rounds = info.perRound.toSortedMap().values.toList()
                    val varied = keys.filter { k -> rounds.mapNotNull { it[k] }.distinct().size > 1 }.toSet()
                    Text(if (varied.isEmpty()) "Logged values · same in all rounds" else "Logged values · a change here counts for all rounds",
                        color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    keys.groupBy { it.series }.forEach { (series, ks) ->
                        Text(series, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        ks.forEach { k ->
                            if (k.choices.isNotEmpty()) {
                                Box(Modifier.padding(vertical = 6.dp)) { ValueRow(k, values[k] ?: 0.0, showSeries = false) { values[k] = it } }
                                return@forEach
                            }
                            val v = values[k] ?: 0.0
                            val step = if (k.unit.equals("kg", true)) 0.5 else 1.0
                            val prev = lastOf[k]
                            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(k.field + when (k.side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }, color = p.text, fontSize = 15.sp)
                                    val d = if (prev == null) null else v - prev
                                    Text(
                                        when {
                                            prev == null -> "first time"
                                            d == 0.0 -> "same as last time"
                                            d!! > 0 -> "last ${fmt(prev)} · ▲ ${fmt(d)}"
                                            else -> "last ${fmt(prev)} · ▼ ${fmt(-d)}"
                                        },
                                        color = if (d != null && d > 0) Color(0xFFFF8AB3) else p.muted, fontSize = 12.sp,
                                    )
                                }
                                StepCircle(AppIcons.Minus, "${k.field} less") { values[k] = v - step }
                                Text("${fmt(v)} ${k.unit}".trim(), color = p.text, style = NumberStyle, fontSize = 20.sp,
                                    textAlign = TextAlign.Center, modifier = Modifier.width(96.dp))
                                StepCircle(AppIcons.Plus, "${k.field} more") { values[k] = v + step }
                            }
                            if (k in varied && values[k] == info.now[k]) Text(
                                "by round: " + rounds.mapNotNull { it[k] }.joinToString(" · ") { fmt(it) },
                                color = p.muted, fontSize = 12.sp,
                            )
                            info.hints[k]?.takeIf { it > v }?.let { h ->
                                Text("Next time: try ${k.text(h)}", color = p.restText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text("Note for the journal", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface)
                    .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (note.isEmpty()) Text("How did it go?", color = p.muted, fontSize = 15.sp)
                    BasicTextField(note, { note = it }, textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).background(p.tint).clickable { onSave(note, values.toMap(), true) },
                contentAlignment = Alignment.Center) { Text("Repeat", color = p.accentText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Box(Modifier.weight(1.4f).height(54.dp).clip(RoundedCornerShape(14.dp)).background(p.accent).clickable { onSave(note, values.toMap(), false) },
                contentAlignment = Alignment.Center) { Text("Save to journal", color = p.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "hang 11 / 12" with a dot per work series, an empty one for each let go, and where it happened (mockup ideas, 2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetGoCard(info: FinishedInfo) {
    val p = LocalPalette.current
    val name = info.letGo.map { it.series }.distinct().singleOrNull() ?: "Work"
    val total = info.workDone.coerceAtLeast(info.letGo.size)
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name.replaceFirstChar { it.uppercase() }, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${total - info.letGo.size} / $total", color = p.text, style = NumberStyle, fontSize = 18.sp)
        }
        if (total <= 60) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                val miss = i in info.letGoAt
                Box(Modifier.size(11.dp).clip(CircleShape).background(if (miss) Color.Transparent else p.pink)
                    .border(if (miss) 2.dp else 0.dp, if (miss) p.dangerText else Color.Transparent, CircleShape))
            }
        }
        info.letGo.forEach { g ->
            Text("Round ${g.round}, ${g.series} ${g.rep}" + when (g.side) { Side.LEFT -> " left"; Side.RIGHT -> " right"; null -> "" } +
                ": let go at ${formatDuration(g.heldSec)}", color = p.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DoneDisc(completed: Boolean) {
    val p = LocalPalette.current
    val ring = listOf(p.accent, p.pink, p.yellow, p.teal)
    Canvas(Modifier.size(150.dp)) {
        val r = size.minDimension / 2f - 6.dp.toPx()
        for (i in 0 until 4) {
            drawArc(ring[i], i * 90f - 90f + 8f, 74f, false,
                Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(if (completed) p.accent else Color(0xFFFFC107), radius = r - 14.dp.toPx())
        val path = Path().apply {
            moveTo(center.x - 22.dp.toPx(), center.y + 1.dp.toPx())
            lineTo(center.x - 6.dp.toPx(), center.y + 17.dp.toPx())
            lineTo(center.x + 24.dp.toPx(), center.y - 15.dp.toPx())
        }
        drawPath(path, if (completed) Color.White else Color(0xFF1A1200), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, color = p.text, style = NumberStyle, fontSize = 22.sp)
    }
}

@Composable
private fun StepCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(44.dp).clip(CircleShape).background(p.tint).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = p.accentText, modifier = Modifier.size(18.dp))
    }
}
