package app.intervals.player

import androidx.compose.foundation.background
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

/** "20 mm · 5 kg" for each series from the values set before the start. */
fun setupDetails(setup: Map<FieldKey, Double>): Map<String, String> =
    setup.entries.filter { it.key.side == null }.groupBy { it.key.series }
        .mapValues { (_, e) -> e.joinToString(" · ") { (k, v) -> number(v) + if (k.unit.isEmpty()) "" else " ${k.unit}" } }

private fun number(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

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
    LaunchedEffect(workoutId) {
        val last = vm.lastValues(workoutId)
        keys.forEach { k ->
            values[k] = last.filter { it.series == k.series && it.field == k.field && it.side == k.side }.maxByOrNull { it.round }?.value ?: 0.0
        }
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close", tint = p.text) }
            Text(saved.workout.name, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Set them before you start; they are prefilled from last time.", color = p.muted, fontSize = 13.sp)
            keys.forEach { k ->
                val step = when (k.unit.lowercase()) { "kg" -> 0.5; else -> 1.0 }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(k.field + when (k.side) { Side.LEFT -> " · L"; Side.RIGHT -> " · R"; null -> "" }, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (keys.map { it.series }.distinct().size > 1) Text(k.series, color = p.muted, fontSize = 12.sp)
                    }
                    Round("−") { values[k] = (values[k] ?: 0.0) - step }
                    Text(number(values[k] ?: 0.0) + if (k.unit.isEmpty()) "" else " ${k.unit}", color = p.text, style = NumberStyle, fontSize = 20.sp,
                        modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Round("+") { values[k] = (values[k] ?: 0.0) + step }
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
