package app.intervals.ui.editor

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import app.intervals.core.LogField
import app.intervals.core.PerSide
import app.intervals.core.SeriesIcon
import app.intervals.core.deleteSeries
import app.intervals.core.formatDuration
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.Overlay

/** The 12 series colors: the 4 app colors first, then the other Material colors. */
val SERIES_COLORS = listOf(
    0xFFE91E63, 0xFFFFC107, 0xFF00BFA5, 0xFF7C3AED, 0xFFF44336, 0xFFFF5722,
    0xFFFF9800, 0xFFCDDC39, 0xFF8BC34A, 0xFF4CAF50, 0xFF03A9F4, 0xFF3F51B5,
)

private enum class SeriesAsk { NONE, TIME, CHANGE }

@Composable
fun EditSeriesScreen(vm: AppViewModel, target: Overlay.EditSeries) {
    val p = LocalPalette.current
    var s by remember(target) { mutableStateOf(target.template) }
    var ask by remember { mutableStateOf(SeriesAsk.NONE) }
    val ink = Color(0xFF1A0A10)
    fun done() = vm.applySeries(target.pos, s.copy(name = s.name.trim().ifEmpty { "series" }))
    BackHandler { done() }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // colored header
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Color(s.color))
                    .statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::done) { Icon(AppIcons.Back, "Back", tint = ink) }
                    Text(if (target.pos == null) "New series" else "Edit series", color = ink, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (target.pos != null) {
                        IconButton(onClick = {
                            vm.updateDraft { it.deleteSeries(listOf(target.pos)) }
                            vm.back()
                        }) { Icon(AppIcons.Delete, "Delete series", tint = ink) }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Name", color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    BasicTextField(
                        s.name, { s = s.copy(name = it) }, singleLine = true,
                        textStyle = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(ink), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(2.dp).background(ink.copy(alpha = 0.45f)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    RoundStep(AppIcons.Minus, "5 seconds less", ink) { s = s.copy(durationSec = (s.durationSec - 5).coerceAtLeast(1)) }
                    Text(
                        formatDuration(s.durationSec), color = ink, style = NumberStyle, fontSize = 72.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 18.dp).clickable(onClickLabel = "Type the time") { ask = SeriesAsk.TIME },
                    )
                    RoundStep(AppIcons.Plus, "5 seconds more", ink) { s = s.copy(durationSec = s.durationSec + 5) }
                }
                Text("Tap the time to type it · min : sec", color = ink, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // color
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Color")
                    SERIES_COLORS.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(c))
                                        .border(3.dp, if (c == s.color) p.text else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable(onClickLabel = "Color") { s = s.copy(color = c) },
                                )
                            }
                        }
                    }
                }
                // icon
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Icon")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeriesIcon.entries.forEach { ic ->
                            val on = ic == s.icon
                            Column(
                                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.tint else p.surface)
                                    .border(2.dp, if (on) p.accent else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { s = s.copy(icon = ic) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(AppIcons.series(ic), null, tint = p.text, modifier = Modifier.size(22.dp))
                                Text(AppIcons.label(ic), color = p.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                // sides
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Sides")
                    Card {
                        SwitchRow("Do it per side", "Left, then change, then right", s.perSide != null) { on ->
                            s = s.copy(perSide = if (on) PerSide(5) else null, logFields = if (on) s.logFields else s.logFields.map { it.copy(leftRightApart = false) })
                        }
                        s.perSide?.let { ps ->
                            Row(Modifier.fillMaxWidth().height(44.dp).clickable { ask = SeriesAsk.CHANGE }, verticalAlignment = Alignment.CenterVertically) {
                                Text("Time to change side", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(formatDuration(ps.changeSec), color = p.text, style = NumberStyle, fontSize = 17.sp)
                            }
                            Text("Right side color", color = p.text, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                            Text("Left uses the color above", color = p.muted, fontSize = 12.sp)
                            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val same = ps.rightColor == null
                                Box(
                                    Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(s.color))
                                        .border(3.dp, if (same) p.text else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable { s = s.copy(perSide = ps.copy(rightColor = null)) }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Same", color = Color(0xFF14080C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                                SERIES_COLORS.filter { it != s.color }.take(7).forEach { c ->
                                    Box(
                                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(c))
                                            .border(3.dp, if (ps.rightColor == c) p.text else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable(onClickLabel = "Right side color") { s = s.copy(perSide = ps.copy(rightColor = c)) },
                                    )
                                }
                            }
                        }
                    }
                }
                // log
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Log")
                    Card {
                        Text("Values you write down for this series (e.g. Load kg, Edge mm). Asked in the rest between rounds and at the end; start from last time.",
                            color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                        s.logFields.forEachIndexed { i, f ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                            FieldEditor(f, perSide = s.perSide != null,
                                onChange = { nf -> s = s.copy(logFields = s.logFields.mapIndexed { j, x -> if (j == i) nf else x }) },
                                onRemove = { s = s.copy(logFields = s.logFields.filterIndexed { j, _ -> j != i }) })
                        }
                        Box(
                            Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                                .border(2.dp, p.line, RoundedCornerShape(12.dp))
                                .clickable { s = s.copy(logFields = s.logFields + LogField(if (s.logFields.isEmpty()) "Load" else "Field ${s.logFields.size + 1}", if (s.logFields.isEmpty()) "kg" else "")) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+ Add field", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                // sound
                Column {
                    SectionTitle("Sound", Modifier.padding(bottom = 6.dp))
                    SwitchRow("Say the name when it starts", null, s.sayName) { s = s.copy(sayName = it) }
                    SwitchRow("Beep the last 3 seconds", null, s.beepLast3) { s = s.copy(beepLast3 = it) }
                }
            }
        }
        Box(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(14.dp)).background(p.accent).clickable(onClick = ::done),
            contentAlignment = Alignment.Center,
        ) { Text("Done", color = p.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }

    when (ask) {
        SeriesAsk.TIME -> DurationDialog("Time", s.durationSec, 1, { ask = SeriesAsk.NONE }) { s = s.copy(durationSec = it); ask = SeriesAsk.NONE }
        SeriesAsk.CHANGE -> DurationDialog("Time to change side", s.perSide?.changeSec ?: 5, 0, { ask = SeriesAsk.NONE }) {
            s = s.copy(perSide = (s.perSide ?: PerSide(5)).copy(changeSec = it)); ask = SeriesAsk.NONE
        }
        SeriesAsk.NONE -> Unit
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).border(2.dp, ink.copy(alpha = 0.5f), CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun SwitchRow(label: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(if (sub == null) 48.dp else 56.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Labeled(label, sub, Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun FieldEditor(f: LogField, perSide: Boolean, onChange: (LogField) -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Field", color = p.muted, fontSize = 12.sp)
                UnderlinedField(f.name) { if (it.isNotBlank()) onChange(f.copy(name = it)) }
            }
            if (!f.isChoice) {
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(64.dp)) {
                    Text("Unit", color = p.muted, fontSize = 12.sp)
                    UnderlinedField(f.unit) { onChange(f.copy(unit = it.trim())) }
                }
            }
            IconButton(onClick = onRemove) { Icon(AppIcons.Close, "Remove field ${f.name}", tint = p.muted) }
        }
        // a choice instead of a number (mockup intervals-ideas-from-other-apps.html, 9), e.g. Grip
        SwitchRow("A choice", if (f.isChoice) "Picked from a list, shown on the disc" else "A number, e.g. 10 kg", f.isChoice) { on ->
            onChange(if (on) f.copy(choices = listOf("open", "half crimp", "full crimp"), unit = "") else f.copy(choices = emptyList()))
        }
        if (f.isChoice) Column(Modifier.padding(bottom = 4.dp)) {
            Text("Choices, separated by /", color = p.muted, fontSize = 12.sp)
            // its own text while typing, so a "/" just typed is not lost when the list is read back
            var text by remember { mutableStateOf(f.choices.joinToString(" / ")) }
            BasicTextField(
                text, { t ->
                    text = t
                    val list = t.split('/', ',').map(String::trim).filter(String::isNotEmpty).distinct()
                    if (list.isNotEmpty()) onChange(f.copy(choices = list))
                }, singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(p.line))
        }
        if (perSide) {
            SwitchRow("Left and right apart", if (f.leftRightApart) "One value per side" else "Same value for both sides", f.leftRightApart) {
                onChange(f.copy(leftRightApart = it))
            }
        }
    }
}

@Composable
private fun UnderlinedField(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    var text by remember(value) { mutableStateOf(value) }
    Column {
        BasicTextField(
            text, { text = it; onChange(it) }, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.line))
    }
}

