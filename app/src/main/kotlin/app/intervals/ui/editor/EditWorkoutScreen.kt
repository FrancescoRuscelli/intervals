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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.EditorRow
import app.intervals.core.Series
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
        // top bar
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

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // name
                    Column {
                        Text("Workout name", color = p.muted, fontSize = 12.sp)
                        BasicTextField(
                            w.name, { n -> vm.updateDraft { it.copy(name = n) } }, singleLine = true,
                            textStyle = TextStyle(color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(p.accent),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(p.accent))
                    }
                    // tags
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
                    SettingRow(AppIcons.Flame, "Warm-up", null, formatDuration(w.warmupSec)) { ask = Ask.WARMUP }
                    // rounds
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)
                            .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Rounds", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        StepButton(AppIcons.Minus, "Fewer rounds", p.tint, p.accentText) { if (w.rounds > 1) vm.updateDraft { it.copy(rounds = it.rounds - 1) } }
                        Text("${w.rounds}", color = p.text, style = NumberStyle, fontSize = 22.sp, modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        StepButton(AppIcons.Plus, "More rounds", p.tint, p.accentText) { if (w.rounds < 99) vm.updateDraft { it.copy(rounds = it.rounds + 1) } }
                    }
                    SettingRow(AppIcons.Hourglass, "Rest between rounds", "Never after the last round", formatDuration(w.restBetweenRoundsSec)) { ask = Ask.REST }
                    // summary
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text("One round · ${formatDuration(w.roundSec)}", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("Total ", color = p.muted, fontSize = 13.sp)
                            Text(formatDuration(w.totalSec), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (w.blocks.isNotEmpty()) RoundBar(w, 8)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            // series list
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(p.sheet)
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        Text("${selected.size} SELECTED", color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selected = emptySet() }) { Text("Cancel", color = p.accentText) }
                    } else {
                        SectionTitle("Series in each round", Modifier.weight(1f))
                        Text("Long-press to select", color = p.muted, fontSize = 12.sp)
                    }
                }
            }
            val rows = w.editorRows()
            itemsIndexed(rows) { _, row ->
                Box(Modifier.fillMaxWidth().background(p.sheet)) {
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
                            SeriesRow(
                                s, inGroup = row is EditorRow.InRepeat, selecting = selecting, selected = pos in selected,
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
            item { Box(Modifier.fillMaxWidth().height(24.dp).background(p.sheet)) }
        }

        // bottom actions
        Row(
            Modifier.fillMaxWidth().background(p.sheet).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selecting) {
                val canGroup = w.canGroup(selected)
                val oneBlock = selected.singleOrNull()?.takeIf { it.inner == -1 }
                ActionButton("Repeat ×…", if (canGroup) p.accent else p.tint, if (canGroup) p.onAccent else p.faint, Modifier.weight(1.4f)) {
                    if (canGroup) ask = Ask.REPEAT else vm.message = "Select single series next to each other"
                }
                ActionButton("Duplicate", p.tint, p.accentText, Modifier.weight(1f)) {
                    vm.updateDraft { it.duplicateSeries(selected) }; selected = emptySet()
                }
                if (oneBlock != null) {
                    StepButton(AppIcons.Up, "Move up", p.tint, p.accentText, 52) { vm.updateDraft { it.moveBlock(oneBlock.block, -1) }; selected = setOf(SeriesPos((oneBlock.block - 1).coerceAtLeast(0))) }
                    StepButton(AppIcons.Down, "Move down", p.tint, p.accentText, 52) {
                        vm.updateDraft { it.moveBlock(oneBlock.block, 1) }; selected = setOf(SeriesPos((oneBlock.block + 1).coerceAtMost(w.blocks.lastIndex)))
                    }
                }
                StepButton(AppIcons.Delete, "Delete selected", p.tint, p.dangerText, 52) {
                    vm.updateDraft { it.deleteSeries(selected) }; selected = emptySet()
                }
            } else {
                ActionButton("+ Work", p.pink, Color(0xFF14080C), Modifier.weight(1f)) { vm.updateDraft { it.copy(blocks = it.blocks + app.intervals.core.Block.Single(WORK_SERIES)) } }
                ActionButton("+ Rest", p.yellow, Color(0xFF14080C), Modifier.weight(1f)) { vm.updateDraft { it.copy(blocks = it.blocks + app.intervals.core.Block.Single(REST_SERIES)) } }
                StepButton(AppIcons.Edit, "Add a custom series", p.tint, p.accentText, 50) { vm.openSeries(null, WORK_SERIES.copy(name = "")) }
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

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String?, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.teal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Labeled(label, sub, Modifier.weight(1f))
        Text(value, color = p.text, style = NumberStyle, fontSize = 18.sp)
    }
}

@Composable
private fun RepeatHeader(row: EditorRow.RepeatHeader, onTimes: (Int) -> Unit, onUngroup: () -> Unit, onSkipLast: (Boolean) -> Unit) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(p.group)
            .padding(start = 12.dp, end = 4.dp).height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Repeat, null, tint = p.accentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { menu = true }) {
            Text("Repeat ▾", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (row.skipLast) Text("no “${row.lastName}” after the last", color = p.muted, fontSize = 11.sp)
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
        Text("×${row.times}", color = p.accentText, style = NumberStyle, fontSize = 20.sp)
        StepButton(AppIcons.Plus, "More repeats", Color.Transparent, p.accentText, 40) { if (row.times < 99) onTimes(row.times + 1) }
    }
}

@Composable
private fun SeriesRow(s: Series, inGroup: Boolean, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(horizontal = if (inGroup) 12.dp else 0.dp).fillMaxWidth()
            .background(if (selected) p.tint else if (inGroup) p.group else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (inGroup) 14.dp else 20.dp, end = 16.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(s.color)), contentAlignment = Alignment.Center) {
            Icon(AppIcons.series(s.icon), null, tint = Color(0xFF111111), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name.ifBlank { "series" }, color = p.text, fontSize = 16.sp)
            val extras = buildList {
                s.perSide?.let { add("per side · change ${formatDuration(it.changeSec)}") }
                if (s.logFields.isNotEmpty()) add("logs " + s.logFields.joinToString { it.name })
            }
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = p.muted, fontSize = 12.sp)
        }
        Text(formatDuration(s.durationSec), color = p.soft, style = NumberStyle, fontSize = 16.sp)
    }
}

@Composable
private fun ActionButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}
