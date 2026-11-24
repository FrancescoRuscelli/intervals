package app.intervals.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Block
import app.intervals.core.PRESETS
import app.intervals.core.Workout
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette

/** + New (mockup intervals-next-features.html, 3A): the presets by section (Fingers…), each with its shape, or start empty. */
@Composable
fun NewWorkoutScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Close, "Close", tint = p.text) }
            Text("New workout", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.tint).clickable { vm.newWorkoutFrom(null) },
                contentAlignment = Alignment.Center,
            ) { Text("Start empty", color = p.accentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            PRESETS.groupBy { it.section }.forEach { (section, presets) ->
                Text(section.uppercase(), color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                presets.forEach { pr ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
                            .clickable(onClickLabel = "Start from ${pr.name}") { vm.newWorkoutFrom(pr.workout) }.padding(14.dp),
                    ) {
                        Text(pr.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(pr.summary, color = p.muted, fontSize = 13.sp)
                        Shape(pr.workout, Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** One round of the workout as colored pieces, sized by time, then its rest between rounds in grey. */
@Composable
private fun Shape(w: Workout, modifier: Modifier) {
    val pieces = w.blocks.flatMap { b -> when (b) { is Block.Single -> listOf(b.series); is Block.Repeat -> b.unrolled() } }
        .map { Color(it.color) to it.durationSec } + listOfNotNull(w.restBetweenRoundsSec.takeIf { it > 0 }?.let { Color(0xFF3A3D44) to it })
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        pieces.forEach { (c, sec) -> Box(Modifier.weight(sec.toFloat()).fillMaxSize().background(c)) }
    }
}
