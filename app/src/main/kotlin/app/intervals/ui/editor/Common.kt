package app.intervals.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.ui.LocalPalette

/** Small square − / + button. */
@Composable
fun StepButton(icon: ImageVector, label: String, bg: Color, fg: Color, size: Int = 44, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size(18.dp)) }
}

/** Common times, one tap each. */
private val PRESETS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300)

/**
 * Asks a time without typing: preset chips for common times, and two wheels (minutes | seconds) for the exact one.
 * [minSec] is the shortest time allowed (e.g. 1 for a series, 0 for a rest).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationDialog(title: String, sec: Int, minSec: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val p = LocalPalette.current
    var m by remember { mutableStateOf((sec / 60).coerceIn(0, 59)) }
    var s by remember { mutableStateOf(sec % 60) }
    // bumped when a preset is tapped, so the wheels scroll to it
    var jump by remember { mutableStateOf(0) }
    val total = m * 60 + s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.filter { it >= minSec }.forEach { t ->
                        val on = t == total
                        Text(
                            "%d:%02d".format(t / 60, t % 60), color = if (on) p.onAccent else p.soft, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.accent else p.surface)
                                .clickable { m = t / 60; s = t % 60; jump++ }.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Wheel(60, m, jump, "min") { m = it }
                    Text(":", color = p.text, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    Wheel(60, s, jump, "sec") { s = it }
                }
            }
        },
        confirmButton = { TextButton(enabled = total >= minSec, onClick = { onDone(total) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val ROW = 44.dp

/** A scroll wheel 0 until [count]: 5 rows visible, snaps to the middle one, tap a row to pick it. */
@Composable
private fun Wheel(count: Int, value: Int, jump: Int, label: String, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val scope = rememberCoroutineScope()
    // the row in the middle is the value (2 empty rows of padding above it)
    val centered by remember {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val half = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(2) ?: 0
            (if (state.firstVisibleItemScrollOffset > half) first + 1 else first).coerceIn(0, count - 1)
        }
    }
    LaunchedEffect(centered) { if (centered != value) onChange(centered) }
    LaunchedEffect(jump) { if (jump > 0) state.animateScrollToItem(value) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(76.dp).height(ROW * 5), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(ROW).clip(RoundedCornerShape(10.dp)).background(p.tint))
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(vertical = ROW * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count) { i ->
                    Box(
                        Modifier.height(ROW).fillMaxWidth().clickable(onClickLabel = "$i $label") { scope.launch { state.animateScrollToItem(i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("%02d".format(i), color = if (i == centered) p.text else p.faint,
                            fontSize = if (i == centered) 26.sp else 20.sp, style = app.intervals.ui.NumberStyle)
                    }
                }
            }
        }
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

/** Asks one line of text. */
@Composable
fun TextDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = v.isNotBlank(), onClick = { onDone(v.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks a whole number with − / +. */
@Composable
fun NumberDialog(title: String, initial: Int, min: Int, max: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val p = LocalPalette.current
    var n by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                StepButton(app.intervals.ui.AppIcons.Minus, "Less", p.tint, p.accentText) { if (n > min) n-- }
                Text("×$n", fontSize = 28.sp, modifier = Modifier.padding(horizontal = 20.dp))
                StepButton(app.intervals.ui.AppIcons.Plus, "More", p.tint, p.accentText) { if (n < max) n++ }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(n) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(text.uppercase(), color = p.muted, fontSize = 13.sp, letterSpacing = 0.6.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Labeled(label: String, sub: String?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.text, fontSize = 15.sp)
        if (sub != null) Text(sub, color = p.muted, fontSize = 12.sp)
    }
}
