package app.intervals.ui.settings

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.SPIN_LABELS
import app.intervals.ui.LocalPalette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val START = 135f // degrees, the arc runs clockwise from bottom-left…
private const val SWEEP = 270f // …to bottom-right
private val SUB = listOf("only the inflate", "a quick quarter turn", "one turn, the rings sway", "1.5 turns, the rings swing", "turns all the time")

/** A circular slider for the disc spin (0 none … 4 always): drag the knob or tap a stop; the middle shows it spinning. */
@Composable
fun SpinDial(level: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    var current by remember(level) { mutableIntStateOf(level) }
    fun stopAngle(i: Int) = START + SWEEP * i / 4f
    fun nearest(o: Offset, w: Float, h: Float): Int {
        var a = Math.toDegrees(atan2((o.y - h / 2).toDouble(), (o.x - w / 2).toDouble())).toFloat()
        if (a < 0) a += 360f
        // the gap at the bottom: snap to the closer end
        var rel = (a - START + 360f) % 360f
        if (rel > SWEEP) rel = if (rel > SWEEP + (360f - SWEEP) / 2) 0f else SWEEP
        return Math.round(rel / SWEEP * 4).coerceIn(0, 4)
    }

    // the preview disc in the middle: shows the chosen spin
    val demo = remember { Animatable(0f) }
    LaunchedEffect(current) {
        if (current in 1..3) while (true) {
            val from = when (current) { 1 -> -90f; 2 -> -360f; else -> -540f }
            demo.snapTo(from)
            demo.animateTo(0f, tween(when (current) { 1 -> 450; 2 -> 600; else -> 700 }, easing = FastOutSlowInEasing))
            kotlinx.coroutines.delay(900)
        } else demo.snapTo(0f)
    }
    val forever = rememberInfiniteTransition(label = "dial")
    val always by forever.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "always")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(210.dp)
                .semantics {
                    contentDescription = "Disc spin: ${SPIN_LABELS[current]}"
                    setProgress { v -> val i = Math.round(v * 4).coerceIn(0, 4); current = i; onChange(i); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> current = nearest(o, size.width.toFloat(), size.height.toFloat()); onChange(current) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onChange(current) }) { change, _ ->
                        change.consume()
                        current = nearest(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val r = size.minDimension / 2f - 18.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                val sz = Size(r * 2, r * 2)
                drawArc(p.line, START, SWEEP, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                if (current > 0) drawArc(p.accent, START, SWEEP * current / 4f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                for (i in 0..4) {
                    val a = Math.toRadians(stopAngle(i).toDouble())
                    val pt = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                    drawCircle(if (i <= current) p.onAccent else p.faint, 3.dp.toPx(), pt)
                }
                // the knob
                val a = Math.toRadians(stopAngle(current).toDouble())
                val knob = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                drawCircle(p.bg, 15.dp.toPx(), knob)
                drawCircle(p.accent, 12.dp.toPx(), knob)
                // the preview disc, with a mark so the turn is visible
                val dr = r - 34.dp.toPx()
                rotate(demo.value + if (current == 4) always else 0f) {
                    drawCircle(p.pink, dr)
                    drawLine(Color(0xFF14080C), Offset(center.x, center.y - dr * 0.75f), Offset(center.x, center.y - dr * 0.3f), 5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SPIN_LABELS[current], color = Color(0xFF14080C), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(SUB[current], color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
