package app.intervals.ui.editor

import androidx.compose.foundation.background
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

/** Asks a time as minutes and seconds. */
@Composable
fun DurationDialog(title: String, sec: Int, minSec: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var m by remember { mutableStateOf((sec / 60).toString()) }
    var s by remember { mutableStateOf((sec % 60).toString()) }
    val total = (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(m, { m = it.filter(Char::isDigit).take(3) }, label = { Text("min") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
                Text(":", fontSize = 24.sp)
                OutlinedTextField(s, { s = it.filter(Char::isDigit).take(2) }, label = { Text("sec") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
            }
        },
        confirmButton = { TextButton(enabled = total >= minSec, onClick = { onDone(total) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
