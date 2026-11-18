package app.intervals.ui.gym

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Band
import app.intervals.core.Exercise
import app.intervals.core.GymField
import app.intervals.core.GymSet
import app.intervals.core.QUICK_TAGS
import app.intervals.core.Side
import app.intervals.core.SideChoice
import app.intervals.core.afterSet
import app.intervals.core.formatKg
import app.intervals.core.formatTime
import app.intervals.core.letter
import app.intervals.core.sides
import app.intervals.core.startValues
import app.intervals.core.summary
import app.intervals.core.tap
import app.intervals.core.toggleTag
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.list.BigButton
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Values being entered; only the exercise's fields are used. */
private data class Values(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val timeSec: Int = 0,
    val distance: Double = 0.0,
    val bands: List<Band> = emptyList(),
)

private fun GymSet.toValues() = Values(weightKg ?: 0.0, reps ?: 0, timeSec ?: 0, distance ?: 0.0, bands)

private fun Values.into(s: GymSet, ex: Exercise) = s.copy(
    weightKg = weightKg.takeIf { GymField.WEIGHT in ex.fields },
    reps = reps.takeIf { GymField.REPS in ex.fields },
    timeSec = timeSec.takeIf { GymField.TIME in ex.fields },
    distance = distance.takeIf { GymField.DISTANCE in ex.fields },
    bands = if (GymField.BANDS in ex.fields) bands else emptyList(),
)

/** Log sets of one exercise on one day (mockup G2): tiles with − / +, Log set, rest clock counting up, the sets done. */
@Composable
fun GymLogScreen(vm: AppViewModel, exerciseId: Long, day: String) {
    val p = LocalPalette.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val ex = exercises.firstOrNull { it.id == exerciseId } ?: return
    val sets by remember(exerciseId, day) { vm.exerciseDay(exerciseId, day) }.collectAsStateWithLifecycle(null)
    val lastTime by produceState<List<GymSet>?>(null, exerciseId, day) { value = vm.lastSets(exerciseId, day) }

    var values by remember(exerciseId) { mutableStateOf<Values?>(null) }
    var side by remember(exerciseId) { mutableStateOf<SideChoice?>(null) }
    var editing by remember { mutableStateOf<GymSet?>(null) }
    var noteFor by remember { mutableStateOf<GymSet?>(null) }
    var typing by remember { mutableStateOf<GymField?>(null) }

    // start from today's last set, else from last time; the side after the last one done
    LaunchedEffect(sets != null, lastTime != null) {
        val today = sets ?: return@LaunchedEffect
        val last = lastTime ?: return@LaunchedEffect
        if (values == null) values = startValues(today, last)?.toValues() ?: Values()
        if (side == null && ex.perSide) side = when (today.lastOrNull()?.side) {
            Side.LEFT -> SideChoice.RIGHT
            Side.RIGHT -> SideChoice.LEFT
            null -> SideChoice.RIGHT
        }
    }
    val v = values ?: Values()
    fun change(f: (Values) -> Values) { values = f(v) }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Column(Modifier.weight(1f)) {
                Text(ex.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                val l = lastTime
                Text(
                    when {
                        l == null -> ""
                        l.isEmpty() -> "First time"
                        else -> "Last · ${shortDay(l.first().day)} · " + l.take(4).joinToString(", ") { s ->
                            (s.side?.let { it.letter + " " } ?: "") + s.summary(ex)
                        } + if (l.size > 4) " …" else ""
                    },
                    color = p.muted, fontSize = 13.sp, maxLines = 2,
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ex.perSide && editing == null) item {
                Segmented(listOf("L", "R", "Both"), side?.ordinal ?: 0) { side = SideChoice.entries[it] }
            }
            if (GymField.BANDS in ex.fields) item { BandsTile(v.bands) { b -> change { it.copy(bands = it.bands.tap(b)) } } }
            item {
                val tiles = listOf(GymField.WEIGHT, GymField.REPS, GymField.TIME, GymField.DISTANCE).filter { it in ex.fields }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.forEach { f ->
                        val (label, text, unit) = when (f) {
                            GymField.WEIGHT -> Triple(if (ex.assist) "Assist" else "Weight", (if (ex.assist && v.weightKg != 0.0) "−" else "") + formatKg(v.weightKg), "kg")
                            GymField.REPS -> Triple("Reps", "${v.reps}", "")
                            GymField.TIME -> Triple("Time", formatTime(v.timeSec), "")
                            else -> Triple("Distance", formatKg(v.distance), "")
                        }
                        StepTile(label, text, unit, Modifier.weight(1f),
                            onTap = { typing = f },
                            onMinus = { change { it.step(f, ex, -1) } },
                            onPlus = { change { it.step(f, ex, +1) } })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val e = editing
                    if (e == null) {
                        BigButton(
                            when (side) { SideChoice.LEFT -> "Log set · L"; SideChoice.RIGHT -> "Log set · R"; SideChoice.BOTH -> "Log L + R"; null -> "Log set" },
                            p.accent, p.onAccent,
                            {
                                vm.logSets(side.sides().map { s -> v.into(GymSet(exerciseId = ex.id, day = day, order = 0, side = s), ex) })
                                side = side?.afterSet()
                            },
                            Modifier.weight(1f),
                        )
                    } else {
                        BigButton("Cancel", p.tint, p.accentText, { editing = null; values = (sets?.lastOrNull() ?: e).toValues() }, Modifier.weight(1f))
                        BigButton("Update set", p.accent, p.onAccent, { vm.updateGymSet(v.into(e, ex)); editing = null }, Modifier.weight(1.4f))
                    }
                }
            }
            if (day == LocalDate.now().toString() && editing == null) item { RestClock(vm, ex) }

            val list = sets.orEmpty()
            if (list.isNotEmpty()) item {
                Text("SETS", color = p.muted, fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SetLine(i + 1, s, ex, s.id == editing?.id) { noteFor = s }
            }
            if (list.isNotEmpty()) item { Text("Tap a set to edit it or add a note", color = p.faint, fontSize = 12.sp) }
        }
    }

    typing?.let { f ->
        val current = when (f) {
            GymField.WEIGHT -> formatKg(v.weightKg); GymField.REPS -> "${v.reps}"; GymField.TIME -> "${v.timeSec}"; else -> formatKg(v.distance)
        }
        NumberDialog(
            when (f) { GymField.WEIGHT -> "Weight (kg)"; GymField.REPS -> "Reps"; GymField.TIME -> "Time (seconds)"; else -> "Distance" },
            current, decimal = f == GymField.WEIGHT || f == GymField.DISTANCE, onDismiss = { typing = null },
        ) { n ->
            change {
                when (f) {
                    GymField.WEIGHT -> it.copy(weightKg = n)
                    GymField.REPS -> it.copy(reps = n.toInt())
                    GymField.TIME -> it.copy(timeSec = n.toInt())
                    else -> it.copy(distance = n)
                }
            }
            typing = null
        }
    }
    noteFor?.let { s ->
        SetDialog(s, ex, onDismiss = { noteFor = null },
            onSave = { vm.updateGymSet(it); noteFor = null },
            onEditValues = { editing = s; values = s.toValues(); noteFor = null },
            onDelete = { vm.deleteGymSet(s.id); noteFor = null })
    }
}

/** − / + by the exercise's step (weight), 1 (reps), 5 s (time); never below 0. */
private fun Values.step(f: GymField, ex: Exercise, dir: Int): Values = when (f) {
    GymField.WEIGHT -> copy(weightKg = (weightKg + dir * ex.weightStep).coerceAtLeast(0.0))
    GymField.REPS -> copy(reps = (reps + dir).coerceAtLeast(0))
    GymField.TIME -> copy(timeSec = (timeSec + dir * 5).coerceAtLeast(0))
    GymField.DISTANCE -> copy(distance = (distance + dir).coerceAtLeast(0.0))
    GymField.BANDS -> this
}

internal fun shortDay(day: String): String {
    val d = LocalDate.parse(day)
    val pattern = if (d.year == LocalDate.now().year) "EEE d MMM" else "d MMM yyyy"
    return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
}

@Composable
private fun StepTile(label: String, value: String, unit: String, modifier: Modifier, onTap: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = p.muted, fontSize = 13.sp)
        Row(Modifier.clickable(onClickLabel = "Type $label", onClick = onTap).padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.text, fontSize = 40.sp, style = NumberStyle)
            if (unit.isNotEmpty()) Text(" $unit", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            app.intervals.ui.editor.StepButton(AppIcons.Minus, "$label less", p.tint, p.accentText, 52, onMinus)
            app.intervals.ui.editor.StepButton(AppIcons.Plus, "$label more", p.tint, p.accentText, 52, onPlus)
        }
    }
}

@Composable
private fun BandsTile(bands: List<Band>, onTap: (Band) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp)) {
        Text("Bands · tap again for ×2", color = p.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Band.entries.forEach { b ->
                val n = bands.count { it == b }
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(bandColor(b).copy(alpha = if (n > 0) 1f else 0.25f))
                        .then(if (b == Band.BLACK) Modifier.border(1.5.dp, Color(0xFF5A5F68), CircleShape) else Modifier)
                        .clickable(onClickLabel = "${b.name.lowercase()} band") { onTap(b) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (n > 1) Text("×$n", color = if (b == Band.YELLOW) Color(0xFF1A1200) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Counts up from the last set logged today; buzzes once at the exercise's rest target and keeps counting. */
@Composable
private fun RestClock(vm: AppViewModel, ex: Exercise) {
    val p = LocalPalette.current
    val start = vm.lastSetAt ?: return
    val context = LocalContext.current
    val elapsed by produceState(0, start) {
        while (true) {
            value = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
            delay(1000 - (SystemClock.elapsedRealtime() - start) % 1000)
        }
    }
    val over = ex.restSec in 1..elapsed
    LaunchedEffect(start, over) { if (over && elapsed < ex.restSec + 2) buzz(context) }
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (over) p.teal.copy(alpha = 0.25f) else p.surface).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatTime(elapsed), color = p.teal, fontSize = 20.sp, style = NumberStyle)
        Text(if (ex.restSec > 0) "  resting · buzz at ${formatTime(ex.restSec)}" else "  resting", color = p.muted, fontSize = 13.sp)
    }
}

private fun buzz(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
}

@Composable
private fun SetLine(n: Int, s: GymSet, ex: Exercise, editing: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (editing) p.tint else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.side?.letter ?: "$n", color = p.muted, fontSize = 15.sp, style = NumberStyle, modifier = Modifier.width(32.dp))
            if (GymField.BANDS in ex.fields && s.bands.isNotEmpty()) {
                BandDots(s.bands)
                Spacer(Modifier.width(8.dp))
            }
            // the bands are the dots; the text is the rest ("× 5")
            Text(s.copy(bands = emptyList()).summary(ex).ifEmpty { "–" },
                color = p.text, fontSize = 17.sp, style = NumberStyle, modifier = Modifier.weight(1f))
            s.restSec?.let { Text("rested ${formatTime(it)}", color = p.muted, fontSize = 12.sp) }
        }
        if (s.note.isNotEmpty()) Text(s.note, color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetDialog(s: GymSet, ex: Exercise, onDismiss: () -> Unit, onSave: (GymSet) -> Unit, onEditValues: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    var note by remember(s.id) { mutableStateOf(s.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((s.side?.let { it.letter + " · " } ?: "") + s.summary(ex)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_TAGS.forEach { tag ->
                        val on = tag in note.split(" · ")
                        Text(tag, color = if (on) p.accentText else p.soft, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.tint else p.surface)
                                .border(1.dp, if (on) p.accent else p.line, RoundedCornerShape(16.dp))
                                .clickable { note = toggleTag(note, tag) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEditValues) { Text("Edit values") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete set", color = p.dangerText) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s.copy(note = note.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this set?") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = p.dangerText) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Asks a number; [decimal] allows 22.5. */
@Composable
private fun NumberDialog(title: String, initial: String, decimal: Boolean, onDismiss: () -> Unit, onDone: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val n = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
        },
        confirmButton = { TextButton(enabled = n != null, onClick = { n?.let(onDone) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if (i == selected) p.accent else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == selected) p.onAccent else p.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
