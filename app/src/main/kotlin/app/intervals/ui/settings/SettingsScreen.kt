package app.intervals.ui.settings

import app.intervals.core.spinLevel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.core.AppSettings
import app.intervals.core.DiscAnimation
import app.intervals.core.ThemeMode
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.editor.Labeled
import app.intervals.ui.editor.SectionTitle
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val s by vm.settings.collectAsStateWithLifecycle()
    fun set(change: (AppSettings) -> AppSettings) = vm.saveSettings(change(s))
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup { text ->
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } }.isSuccess
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (pendingRestore == null) vm.message = "Could not read the file"
        }
    }
    val pickFitNotes = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewFitNotes) }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) vm.message = "Could not read the file" else vm.importHybrid(text)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("Settings", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

        Group("Look") {
            Text("Theme", color = p.text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            Segmented(listOf("System", "Light", "Dark"), s.theme.ordinal) { i -> set { it.copy(theme = ThemeMode.entries[i]) } }
            Text("Disc spin", color = p.text, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
            Text("What the disc does at each new series · drag the knob", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            SpinDial(s.spinLevel) { lvl -> set { it.copy(spin = lvl, discAnimation = if (lvl >= 3) DiscAnimation.CRAZY_SPIN else DiscAnimation.CALM) } }
            Spacer(Modifier.height(10.dp))
        }

        Group("Sound") {
            SwitchRow("Say the series names", "Voice, in English", s.sayNames) { v -> set { it.copy(sayNames = v) } }
            SwitchRow("Old master voice", "Slower, a little higher: «Hang, now you must»", s.masterVoice) { v -> set { it.copy(masterVoice = v) } }
            SwitchRow("Beep the last 3 seconds", null, s.beepLast3) { v -> set { it.copy(beepLast3 = v) } }
            // the phone's own sound picker, as Hybrid Interval Timer does
            val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                @Suppress("DEPRECATION") val uri = r.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (r.resultCode == android.app.Activity.RESULT_OK && uri != null) set { it.copy(endSound = uri.toString()) }
            }
            val soundName = remember(s.endSound) { s.endSound?.let { u -> runCatching { android.media.RingtoneManager.getRingtone(context, android.net.Uri.parse(u))?.getTitle(context) }.getOrNull() } }
            ActionRow("End-of-set sound", soundName ?: "The app's own tone · tap to pick a phone sound") {
                pickSound.launch(android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "End-of-set sound")
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.endSound?.let(android.net.Uri::parse)))
            }
            if (s.endSound != null) ActionRow("Back to the app's own tone", "Instead of ${soundName ?: "the phone sound"}") { set { it.copy(endSound = null) } }
            SwitchRow("Lower music during cues", "Other apps get quieter for a moment", s.duckMusic) { v -> set { it.copy(duckMusic = v) } }
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", color = p.text, fontSize = 15.sp, modifier = Modifier.width(80.dp))
                var vol by remember(s.volume) { mutableStateOf(s.volume) }
                Slider(
                    vol, { vol = it }, onValueChangeFinished = { set { it.copy(volume = vol) } },
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Group("Workout") {
            SwitchRow("Series and round numbers", "Small 3/6 and 2/4 on the rings of the player", s.showCounters) { v -> set { it.copy(showCounters = v) } }
            SwitchRow("Keep the screen on", "Only while a workout runs", s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
            SwitchRow("Lock the controls", "Avoid taps by mistake; hold to unlock", s.lockControls) { v -> set { it.copy(lockControls = v) } }
        }

        Group("Back up and restore") {
            ActionRow("Back up to a file", "Save all Intervals data as a .json file") {
                saveBackup.launch("intervals-backup-${LocalDate.now()}.json")
            }
            HorizontalDivider(color = p.line)
            ActionRow("Restore from a file", "Replaces all Intervals data") {
                pickRestore.launch(arrayOf("application/json", "*/*"))
            }
        }

        Group("Import from another app") {
            ActionRow("FitNotes", "Import a .fitnotes file. Replaces the gym log.") {
                pickFitNotes.launch(arrayOf("*/*"))
            }
            HorizontalDivider(color = p.line)
            ActionRow("Hybrid Interval Timer", "Import workouts from a .csv file") {
                pickCsv.launch(arrayOf("text/*", "*/*"))
            }
        }

        Text("Intervals 0.1", color = p.faint, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces your workouts, journal, gym log and settings with the data in this backup. If the file can't be read, nothing changes.") },
            confirmButton = { TextButton(onClick = { pendingRestore = null; vm.restoreBackup(text) }) { Text("Restore", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    SectionTitle(title, Modifier.padding(top = 12.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun SwitchRow(label: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(if (sub == null) 52.dp else 60.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Labeled(label, sub, Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun ActionRow(label: String, sub: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Labeled(label, sub, Modifier.weight(1f))
        Text("›", color = p.accentText, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bg).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.accent else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (on) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
