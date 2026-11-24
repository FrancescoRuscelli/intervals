package app.intervals.player

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Side
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle

/**
 * Before a workout that logs values (mockup intervals-next-features.html, 3B): edge, load… set with − / +, prefilled
 * from last time, then Start. They are shown on the disc and are this session's values in the journal.
 */
@Composable
fun SetupScreen(vm: AppViewModel, workoutId: Long) {
    val p = LocalPalette.current
    val workouts by vm.workouts.collectAsState()
    val saved = workouts?.firstOrNull { it.id == workoutId } ?: return
    val keys = remember(saved) { fieldKeys(saved) }
    val values = remember(saved) { mutableStateMapOf<FieldKey, Double>() }
    var loaded by remember { mutableStateOf(false) }
    // a load to try (mockup intervals-ideas-from-other-apps.html, 1): the same kg on the last two clean sessions
    val sessions by remember(workoutId) { vm.sessionsOf(workoutId) }.collectAsState(emptyList())
    val hints = remember(sessions) {
        app.intervals.core.intervalLoadHints(sessions).mapNotNull { h -> keys.firstOrNull { it.matches(h) }?.let { it to h.value } }.toMap()
    }
    LaunchedEffect(workoutId) {
        val last = vm.lastValues(workoutId)
        keys.forEach { k -> values[k] = last.valueFor(k) ?: 0.0 }
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close", tint = p.text) }
            Text(saved.workout.name, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set them before you start; they are prefilled from last time. You can change them in the rests between rounds.", color = p.muted, fontSize = 13.sp)
            keys.forEach { k ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    ValueRow(k, values[k] ?: 0.0, showSeries = keys.map { it.series }.distinct().size > 1) { values[k] = it }
                    val hint = hints[k]
                    if (hint != null && (values[k] ?: 0.0) < hint) Text(
                        "↑ Try ${k.text(hint)}: all rounds done twice at ${k.text(hint - app.intervals.core.INTERVAL_LOAD_STEP)}. Tap to use.",
                        color = p.restText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(p.restTint)
                            .clickable { values[k] = hint }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(16.dp).height(56.dp).clip(RoundedCornerShape(18.dp)).background(p.accent)
                .clickable(enabled = loaded) { vm.startWithSetup(workoutId, values.toMap()) },
            contentAlignment = Alignment.Center,
        ) { Text("Start", color = p.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Round(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(44.dp).clip(CircleShape).background(p.tint).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = p.accentText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * One logged value with its controls: − / + for a number (0.5 kg, else 1), a row of chips for a choice (mockup ideas, 9).
 * Used before the start and in the rests between rounds.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ValueRow(k: FieldKey, v: Double, showSeries: Boolean, onChange: (Double) -> Unit) {
    val p = LocalPalette.current
    val title = k.field + when (k.side) { Side.LEFT -> " · L"; Side.RIGHT -> " · R"; null -> "" }
    if (k.choices.isNotEmpty()) {
        Column {
            Text(title + if (showSeries) " · ${k.series}" else "", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                k.choices.forEachIndexed { i, c ->
                    val on = v.toInt() == i
                    Text(c, color = if (on) p.accentText else p.soft, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.bg)
                            .choiceBorder(on, p).clickable(onClickLabel = c) { onChange(i.toDouble()) }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        return
    }
    val step = when (k.unit.lowercase()) { "kg" -> 0.5; else -> 1.0 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (showSeries) Text(k.series, color = p.muted, fontSize = 12.sp)
        }
        Round("−") { onChange(v - step) }
        Text(k.text(v), color = p.text, style = NumberStyle, fontSize = 20.sp,
            modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round("+") { onChange(v + step) }
    }
}

private fun Modifier.choiceBorder(on: Boolean, p: app.intervals.ui.Palette): Modifier =
    border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
