package app.intervals.ui.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.ui.AppIcons
import app.intervals.ui.GymImportPreview
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import app.intervals.ui.editor.SectionTitle
import app.intervals.ui.list.BigButton

/** What a FitNotes backup would bring in (mockup "Import from FitNotes"); nothing changes until Import. */
@Composable
fun GymImportScreen(preview: GymImportPreview, onCancel: () -> Unit, onImport: () -> Unit) {
    val p = LocalPalette.current
    val r = preview.result
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(AppIcons.Close, "Cancel", tint = p.text) }
            Text("Import from FitNotes", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Text(preview.fileName, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("The backup file keeps your comments", color = p.muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("%,d".format(r.days), "days", Modifier.weight(1f))
                Stat("%,d".format(r.sets.size), "sets", Modifier.weight(1f))
                Stat("%,d".format(r.exercises.size), "exercises", Modifier.weight(1f))
            }

            SectionTitle("Your comments become data")
            Card {
                Line("L · R", "side of the set", r.sideComments)
                Line("Band colors", "bands", r.bandComments)
                Line("Anything else", "note, kept as written", r.noteComments)
            }

            if (r.pairs.isNotEmpty() || r.oneSided.isNotEmpty()) {
                SectionTitle("Left + right become one exercise · ${r.pairs.size} pairs")
                Card {
                    r.pairs.sortedByDescending { it.rightSets + it.leftSets }.forEach {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(it.name, color = p.text, fontSize = 15.sp)
                            Text("(Right) ${it.rightSets} + (Left) ${it.leftSets} sets", color = p.muted, fontSize = 12.sp)
                        }
                    }
                    if (r.oneSided.isNotEmpty()) {
                        Text("One side only, made per-side too: ${r.oneSided.joinToString(", ")}", color = p.muted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Text(
                buildString {
                    append("Skipped: ${r.skippedExercises} exercises never used, the categories, routines and body weight. ")
                    append("Nothing changes until you press Import.")
                },
                color = p.muted, fontSize = 13.sp,
            )
            if (preview.existingSets > 0) {
                Text(
                    "The ${"%,d".format(preview.existingSets)} sets already in Gym are replaced by this backup.",
                    color = p.dangerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        BigButton("Import ${"%,d".format(r.days)} days", p.accent, p.onAccent, onImport,
            Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(14.dp)) { content() }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.text, fontSize = 24.sp, style = NumberStyle)
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Line(from: String, to: String, n: Int) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(from, color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("→ $to", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1.4f))
        Text("$n", color = p.text, fontSize = 15.sp, style = NumberStyle)
    }
}
