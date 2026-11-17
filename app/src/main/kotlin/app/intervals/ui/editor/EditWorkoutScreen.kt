@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.intervals.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Block
import app.intervals.core.EditorRow
import app.intervals.core.Series
import app.intervals.core.SeriesIcon
import app.intervals.core.SeriesPos
import app.intervals.core.canGroup
import app.intervals.core.deleteSeries
import app.intervals.core.duplicateSeries
import app.intervals.core.editorRows
import app.intervals.core.formatDuration
import app.intervals.core.groupSeries
import app.intervals.core.moveBlock
import app.intervals.core.pos
import app.intervals.core.roundSec
import app.intervals.core.setRepeatTimes
import app.intervals.core.setSkipLast
import app.intervals.core.totalSec
import app.intervals.core.ungroup
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.REST_SERIES
import app.intervals.ui.WORK_SERIES
import app.intervals.ui.list.RoundBar

private enum class Ask { NONE, WARMUP, REST, TAG, REPEAT, DISCARD, DELETE }

/**
 * Edit workout, laid out in play order: warm-up, then the box "Each round ×N" with its series
 * (repeat groups inside), then the rest between rounds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWorkoutScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val draft = vm.draft ?: return
    val w = draft.workout
    var selected by remember { mutableStateOf(setOf<SeriesPos>()) }
    var ask by remember { mutableStateOf(Ask.NONE) }
    var menu by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    fun close() { if (draft.changed) ask = Ask.DISCARD else vm.back() }
    BackHandler { if (selecting) selected = emptySet() else close() }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close) { Icon(AppIcons.Close, "Close", tint = p.text) }
            Text(if (draft.id == null) "New workout" else "Edit workout", color = p.text, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (draft.id != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(AppIcons.More, "More options", tint = p.text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete workout", color = p.dangerText) }, onClick = { menu = false; ask = Ask.DELETE })
                    }
                }
            }
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(p.accent).clickable { vm.saveDraft() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        BasicTextField(
                            w.name, { n -> vm.updateDraft { it.copy(name = n) } }, singleLine = true,
                            textStyle = TextStyle(color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(p.accent),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        w.tags.forEach { t ->
                            Row(
                                Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(p.tint)
                                    .clickable(onClickLabel = "Remove tag $t") { vm.updateDraft { it.copy(tags = it.tags - t) } }
                                    .padding(start = 12.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t, color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Icon(AppIcons.Close, null, tint = p.accentText, modifier = Modifier.size(14.dp))
                            }
                        }
                        Box(
                            Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).border(1.dp, p.faint, RoundedCornerShape(15.dp))
                                .clickable { ask = Ask.TAG }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("+ Tag", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatDuration(w.totalSec), color = p.text, style = NumberStyle, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "total · " + (if (w.rounds == 1) "1 round" else "${w.rounds} rounds") + " of ${formatDuration(w.roundSec)}",
                            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // 1 · warm-up
            item {
                OuterRow(dot = Color(0xFFFF9800), label = "Warm-up", sub = "once, at the start", value = formatDuration(w.warmupSec)) { ask = Ask.WARMUP }
                Connector()
            }

            // 2 · the round box: header with the rounds
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(p.groupLine)
                        .padding(start = 2.dp, end = 2.dp, top = 2.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(p.group)
                        .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (selecting) "${selected.size} selected" else "Each round", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (selecting) "Tap more series, or use the buttons below"
                            else "${formatDuration(w.roundSec)} · played " + if (w.rounds == 1) "once" else "${w.rounds} times",
                            color = p.muted, fontSize = 12.sp,
                        )
                    }
                    if (selecting) {
                        TextButton(onClick = { selected = emptySet() }) { Text("Cancel", color = p.accentText) }
                    } else {
                        StepButton(AppIcons.Minus, "Fewer rounds", p.tint, p.accentText, 40) { if (w.rounds > 1) vm.updateDraft { it.copy(rounds = it.rounds - 1) } }
                        Text("×${w.rounds}", color = p.accentText, style = NumberStyle, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                        StepButton(AppIcons.Plus, "More rounds", p.tint, p.accentText, 40) { if (w.rounds < 99) vm.updateDraft { it.copy(rounds = it.rounds + 1) } }
                    }
                }
            }

            // the round box: series and repeat groups
            w.editorRows().forEach { row ->
                item {
                    BoxBody {
                        when (row) {
                            is EditorRow.RepeatHeader -> RepeatHeader(
                                row,
                                onTimes = { t -> vm.updateDraft { it.setRepeatTimes(row.block, t) } },
                                onUngroup = { vm.updateDraft { it.ungroup(row.block) } },
                                onSkipLast = { on -> vm.updateDraft { it.setSkipLast(row.block, on) } },
                            )
                            is EditorRow.SingleRow, is EditorRow.InRepeat -> {
                                val pos = row.pos!!
                                val s = if (row is EditorRow.SingleRow) row.series else (row as EditorRow.InRepeat).series
                                val group = w.blocks[pos.block] as? Block.Repeat
                                val skippedLast = group != null && group.skipLast && pos.inner == group.series.lastIndex
                                SeriesRow(
                                    s, inGroup = row is EditorRow.InRepeat, skippedLast = skippedLast,
                                    selecting = selecting, selected = pos in selected,
                                    onClick = {
                                        if (selecting) selected = if (pos in selected) selected - pos else selected + pos
                                        else vm.openSeries(pos, s)
                                    },
                                    onLongClick = { selected = selected + pos },
                                )
                            }
                        }
                    }
                }
            }

            // the round box: add buttons, or the actions on the selection
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)).background(p.groupLine)
                        .padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(p.group)
                        .padding(10.dp),
                ) {
                    if (w.blocks.isEmpty()) Text("No series yet: add one", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selecting) {
                            val canGroup = w.canGroup(selected)
                            val oneBlock = selected.singleOrNull()?.takeIf { it.inner == -1 }
                            Chip("Repeat ×…", if (canGroup) p.accent else p.tint, if (canGroup) p.onAccent else p.faint, Modifier.weight(1.3f)) {
                                if (canGroup) ask = Ask.REPEAT else vm.message = "Select single series next to each other"
                            }
                            Chip("Duplicate", p.tint, p.accentText, Modifier.weight(1f)) {
                                vm.updateDraft { it.duplicateSeries(selected) }; selected = emptySet()
                            }
                            if (oneBlock != null) {
                                StepButton(AppIcons.Up, "Move up", p.tint, p.accentText, 40) {
                                    vm.updateDraft { it.moveBlock(oneBlock.block, -1) }
                                    selected = setOf(SeriesPos((oneBlock.block - 1).coerceAtLeast(0)))
                                }
                                StepButton(AppIcons.Down, "Move down", p.tint, p.accentText, 40) {
                                    vm.updateDraft { it.moveBlock(oneBlock.block, 1) }
                                    selected = setOf(SeriesPos((oneBlock.block + 1).coerceAtMost(w.blocks.lastIndex)))
                                }
                            }
                            StepButton(AppIcons.Delete, "Delete selected", p.tint, p.dangerText, 40) {
                                vm.updateDraft { it.deleteSeries(selected) }; selected = emptySet()
                            }
                        } else {
                            Chip("+ Work", if (p.dark) Color(0xFF3A1428) else Color(0xFFFCE4EC), if (p.dark) Color(0xFFFF8AB3) else Color(0xFFAD1457), Modifier.weight(1f)) {
                                vm.updateDraft { it.copy(blocks = it.blocks + Block.Single(WORK_SERIES)) }
                            }
                            Chip("+ Rest", if (p.dark) Color(0xFF3A2E0A) else Color(0xFFFFF8E1), if (p.dark) Color(0xFFFFD54F) else Color(0xFF8D6E00), Modifier.weight(1f)) {
                                vm.updateDraft { it.copy(blocks = it.blocks + Block.Single(REST_SERIES)) }
                            }
                            Chip("+ Other", p.tint, p.accentText, Modifier.weight(1f)) { vm.openSeries(null, WORK_SERIES.copy(name = "")) }
                        }
                    }
                }
            }

            // 3 · rest between rounds
            item {
                Connector()
                val between = w.rounds - 1
                OuterRow(
                    dot = p.teal, bar = true, label = "Rest between rounds",
                    sub = if (w.rounds == 1) "only one round: not played"
                    else (if (between == 1) "once" else "$between times") + " · never after the last round",
                    value = formatDuration(w.restBetweenRoundsSec),
                ) { ask = Ask.REST }
            }

            if (w.blocks.isNotEmpty()) item {
                Column(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("One round in the player", color = p.muted, fontSize = 12.sp)
                    RoundBar(w, 8)
                }
                Text("Tap a line to edit it · long-press to select (repeat, duplicate, move, delete)",
                    color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
        }
    }

    when (ask) {
        Ask.WARMUP -> DurationDialog("Warm-up", w.warmupSec, 0, { ask = Ask.NONE }) { s -> vm.updateDraft { it.copy(warmupSec = s) }; ask = Ask.NONE }
        Ask.REST -> DurationDialog("Rest between rounds", w.restBetweenRoundsSec, 0, { ask = Ask.NONE }) { s -> vm.updateDraft { it.copy(restBetweenRoundsSec = s) }; ask = Ask.NONE }
        Ask.TAG -> TextDialog("New tag", "", "e.g. Hangboard", { ask = Ask.NONE }) { t -> vm.updateDraft { it.copy(tags = it.tags + t) }; ask = Ask.NONE }
        Ask.REPEAT -> NumberDialog("Repeat the selected series", 3, 1, 50, { ask = Ask.NONE }) { n ->
            vm.updateDraft { it.groupSeries(selected, n) }; selected = emptySet(); ask = Ask.NONE
        }
        Ask.DISCARD -> AlertDialog(
            onDismissRequest = { ask = Ask.NONE },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { ask = Ask.NONE; vm.back() }) { Text("Discard", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { ask = Ask.NONE }) { Text("Keep editing") } },
        )
        Ask.DELETE -> AlertDialog(
            onDismissRequest = { ask = Ask.NONE },
            title = { Text("Delete “${w.name}”?") },
            text = { Text("Its sessions stay in the journal.") },
            confirmButton = { TextButton(onClick = { ask = Ask.NONE; vm.deleteDraft() }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { ask = Ask.NONE }) { Text("Cancel") } },
        )
        Ask.NONE -> Unit
    }
}

/** Thin vertical line between the steps (warm-up → round → rest between rounds). */
@Composable
private fun Connector() {
    val p = LocalPalette.current
    Box(Modifier.padding(start = 27.dp).width(2.dp).height(14.dp).background(p.line))
}

/** Middle part of the round box: purple side borders around the content. */
@Composable
private fun BoxBody(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().background(p.groupLine).padding(horizontal = 2.dp).background(p.group).padding(horizontal = 10.dp)) { content() }
}

@Composable
private fun OuterRow(dot: Color, label: String, sub: String, value: String, bar: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (bar) Box(Modifier.size(width = 26.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
            else Box(Modifier.size(26.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(12.dp))
        Labeled(label, sub, Modifier.weight(1f))
        Text(value, color = p.text, style = NumberStyle, fontSize = 17.sp)
    }
}

@Composable
private fun RepeatHeader(row: EditorRow.RepeatHeader, onTimes: (Int) -> Unit, onUngroup: () -> Unit, onSkipLast: (Boolean) -> Unit) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth().height(48.dp)
            .background(p.accent.copy(alpha = 0.10f)).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Repeat, null, tint = p.accentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).clickable { menu = true }) {
            Text("Repeat ×${row.times} ▾", color = p.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${formatDuration(row.cycleSec)} each · ${formatDuration(row.totalSec)} in all", color = p.muted, fontSize = 11.sp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (row.size >= 2) {
                    DropdownMenuItem(
                        text = { Text(if (row.skipLast) "Play “${row.lastName}” also the last time" else "Skip “${row.lastName}” the last time") },
                        onClick = { menu = false; onSkipLast(!row.skipLast) },
                    )
                }
                DropdownMenuItem(text = { Text("Ungroup (play once)") }, onClick = { menu = false; onUngroup() })
            }
        }
        StepButton(AppIcons.Minus, "Fewer repeats", Color.Transparent, p.accentText, 40) { if (row.times > 1) onTimes(row.times - 1) }
        StepButton(AppIcons.Plus, "More repeats", Color.Transparent, p.accentText, 40) { if (row.times < 99) onTimes(row.times + 1) }
    }
}

@Composable
private fun SeriesRow(
    s: Series, inGroup: Boolean, skippedLast: Boolean, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val p = LocalPalette.current
    val ps = s.perSide
    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) p.tint else if (inGroup) p.accent.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (inGroup) 12.dp else 4.dp, end = 6.dp),
    ) {
        val logs = if (s.logFields.isNotEmpty()) "logs " + s.logFields.joinToString { it.name } else null
        val skipNote = if (skippedLast) "not the last time" else null
        val name = s.name.ifBlank { "series" }
        if (ps == null) {
            Line(s.color, s.icon, name, listOfNotNull(skipNote, logs).joinToString(" · ").ifEmpty { null }, s.durationSec, selecting, selected, onClick, big = true)
        } else {
            // one per-side series, shown as its three steps: left, change side, right
            Line(s.color, s.icon, "$name · left", logs, s.durationSec, selecting, selected, onClick, big = true)
            Line(0, null, "change side", null, ps.changeSec, selecting, selected, onClick, big = false)
            Line(ps.rightColor ?: s.color, s.icon, "$name · right", skipNote, s.durationSec, selecting, selected, onClick, big = true)
        }
    }
}

@Composable
private fun Line(
    color: Long, icon: SeriesIcon?, name: String, sub: String?, sec: Int,
    selecting: Boolean, selected: Boolean, onClick: () -> Unit, big: Boolean,
) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(if (big) 52.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selecting) {
            if (big) Checkbox(checked = selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
            else Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.series(icon), null, tint = Color(0xFF111111), modifier = Modifier.size(16.dp))
                }
            } else {
                Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(p.teal))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (big) p.text else p.teal, fontSize = if (big) 16.sp else 13.sp)
            if (sub != null) Text(sub, color = p.muted, fontSize = 11.sp)
        }
        Text(formatDuration(sec), color = if (big) p.soft else p.teal, style = NumberStyle, fontSize = if (big) 16.sp else 13.sp)
    }
}

@Composable
private fun Chip(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
