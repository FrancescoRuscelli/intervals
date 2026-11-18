package app.intervals.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.Exercise
import app.intervals.core.GymSet
import app.intervals.core.dayRows
import app.intervals.core.formatDuration
import app.intervals.core.recordSets
import app.intervals.core.summary
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.Overlay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val recapDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val recapTime = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun GymFinishScreen(vm: AppViewModel, finish: Overlay.GymFinish) {
    val p = LocalPalette.current
    val day = finish.day
    val storedNote by remember(day) { vm.gymDayNote(day) }.collectAsStateWithLifecycle(null)
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val recap by produceState<Map<Long, List<GymSet>>?>(null, finish) {
        val daySets = vm.gymDay(day).first().groupBy { it.exerciseId }
        value = daySets.mapValues { (id, sets) ->
            vm.exerciseAll(id).first().filter { it.day < day } + sets
        }
    }
    val byExercise = recap.orEmpty()
    val exercisesById = remember(exercises) { exercises.associateBy { it.id } }
    val exerciseIds = remember(finish.group, byExercise) {
        (finish.group.items.map { it.exerciseId } + byExercise.keys).distinct().filter { it in byExercise }
    }
    val records = remember(exercisesById, byExercise, day) {
        byExercise.mapValues { (id, history) -> exercisesById[id]?.let { recordSets(it, day, history) }.orEmpty() }
    }
    val recordCount = records.values.sumOf { it.size }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        IconButton(onClick = { vm.back() }, enabled = !vm.gymFinishSaving, modifier = Modifier.padding(start = 4.dp)) {
            Icon(AppIcons.Back, "Back to group", tint = p.text)
        }
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Column(
                        Modifier.size(134.dp).background(p.tint, CircleShape).padding(8.dp)
                            .background(p.accent, CircleShape),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("done", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Icon(AppIcons.Check, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(44.dp))
                    }
                    Text(finish.group.name, color = p.text, style = NumberStyle, fontSize = 30.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "${LocalDate.parse(day).format(recapDate)} · ${finish.start.format(recapTime)}–${finish.end.format(recapTime)}",
                        color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinishStat(formatDuration(finish.elapsedSec), "time", Modifier.weight(1f))
                    val count = recap?.values?.sumOf { history -> history.count { it.day == day } }
                    FinishStat(count?.toString() ?: "—", "sets", Modifier.weight(1f))
                    FinishStat(if (recap == null) "—" else "$recordCount", "records", Modifier.weight(1f), highlight = recordCount > 0)
                }
            }
            items(exerciseIds, key = { it }) { id ->
                val ex = exercisesById[id] ?: return@items
                val history = byExercise.getValue(id)
                val before = history.lastOrNull { it.day < day }?.day
                val compared = before?.let { prior ->
                    val label = runCatching { LocalDate.parse(prior).format(recapDate) }.getOrDefault(prior)
                    "vs $label · ${setLine(ex, history.filter { it.day == prior })}"
                }
                Column(
                    Modifier.fillMaxWidth().background(p.surface, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val prs = records[id].orEmpty()
                        if (prs.isNotEmpty()) {
                            Text(
                                "PR " + prs.joinToString(" · ") { setLine(ex, listOf(it)) },
                                color = Color(0xFF1A1200), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp).background(p.yellow, RoundedCornerShape(11.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(setLine(ex, history.filter { it.day == day }), color = p.soft, style = NumberStyle, fontSize = 14.sp)
                    Text(compared ?: "First logged day", color = p.muted, fontSize = 12.sp)
                }
            }
            item {
                OutlinedTextField(
                    value = draft ?: storedNote.orEmpty(), onValueChange = { draft = it },
                    enabled = storedNote != null && !vm.gymFinishSaving,
                    label = { Text("Note for the day") }, placeholder = { Text("How did it go?") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = p.surface, unfocusedContainerColor = p.surface,
                        focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedLabelColor = p.accentText,
                    ),
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vm.gymFinishError) Text("Couldn’t save the day note. Try again.", color = p.text, fontSize = 13.sp)
            Button(
                onClick = { vm.saveGymFinish(finish, draft ?: storedNote.orEmpty()) },
                enabled = storedNote != null && recap != null && !vm.gymFinishSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) {
                Text(if (vm.gymFinishSaving) "Saving…" else "Save to journal", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun setLine(exercise: Exercise, sets: List<GymSet>): String = dayRows(sets).joinToString(" · ") { row ->
    val side = if (row.set.side == null) "" else "${row.label} "
    side + row.set.summary(exercise) + if (row.count > 1) " ×${row.count}" else ""
}

@Composable
private fun FinishStat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    val p = LocalPalette.current
    val fg = if (highlight) Color(0xFF1A1200) else p.text
    Column(modifier.background(if (highlight) p.yellow else p.surface, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = fg, style = NumberStyle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = if (highlight) fg else p.muted, fontSize = 12.sp)
    }
}
