package app.intervals.player

import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.PlayState
import app.intervals.core.Segment
import app.intervals.core.SegmentKind
import app.intervals.core.Side
import app.intervals.core.formatDuration
import app.intervals.ui.AppIcons
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle

private val DoneGrey = Color(0xFF3A3E46)

@Composable
fun PlayerScreen(c: PlayerController, keepScreenOn: Boolean, spinLevel: Int = 0, showCounters: Boolean = true, onClose: () -> Unit) {
    val p = LocalPalette.current
    var askStop by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, c) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) c.uiVisible = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) c.uiVisible = false
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler { askStop = true }

    @Suppress("UNUSED_VARIABLE") val t = c.tick // redraw on every tick
    // during the coin flip the clock is at the very end: keep showing the last moment
    val st = c.plan.stateAt(minOf(c.elapsedMs, c.plan.totalSec * 1000L - 1))
    val intervalCountdown = st != null && st.secLeft <= 3 && st.segment.durationSec > 3 &&
        !c.starting && !c.paused && !c.celebrating

    val restEdit = st != null && st.segment.kind == SegmentKind.ROUND_REST && c.keys.isNotEmpty() && !c.celebrating
    BoxWithConstraints(
        Modifier.fillMaxSize().background(p.bg)
            // Taps on blank space must stay on the player overlay.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val title: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { askStop = true }) { Icon(AppIcons.Close, "Stop", tint = p.text) }
                Text(
                    c.saved.workout.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(48.dp))
            }
        }
        // the whole workout's time left, big, like the old app
        val timeLeft: @Composable (androidx.compose.ui.unit.TextUnit) -> Unit = { size ->
            if (st != null) Text(
                mmss(if (c.celebrating) 0 else st.totalLeftSec), color = p.soft, style = NumberStyle, fontSize = size, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        val disc: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m, contentAlignment = Alignment.Center) {
                val diameter = (minOf(maxWidth, maxHeight) - 32.dp).coerceAtLeast(0.dp)
                if (st != null) {
                    val color = st.segment.playerColor(p.teal)
                    val ink = if (color == p.teal || color.luminance() > 0.35f) Color(0xFF14080C) else Color.White
                    Disc(c, st, spinLevel, diameter, color, ink, intervalCountdown, showCounters)
                    if (intervalCountdown) {
                        val fontSize = with(LocalDensity.current) { (diameter * 0.38f).toSp() }
                        CountdownDigit(st.secLeft, outside = color, inside = ink, fontSize = fontSize, discSize = diameter - 108.dp)
                    }
                }
            }
        }
        // the rest between rounds: the next round's values, the clock keeps running (mockup ideas, 12)
        val restValues: @Composable () -> Unit = {
            if (restEdit && st != null) RestValues(c, st.round + 1, st.rounds)
        }
        if (landscape) {
            // the phone on its side (mockup ideas, 11): the disc as big as the height allows, the rest on the right
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                disc(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                    title()
                    timeLeft(40.sp)
                    if (restEdit) Box(Modifier.weight(1f, fill = false)) { restValues() } else Spacer(Modifier.height(8.dp))
                    if (st != null) NextSegment(st.next)
                    if (c.celebrating) Spacer(Modifier.height(92.dp)) else Controls(c, st)
                }
            }
        } else Column(Modifier.fillMaxSize()) {
            title()
            timeLeft(52.sp)
            disc(Modifier.weight(1f).fillMaxWidth())
            restValues()
            if (st != null) NextSegment(st.next)
            // Keep the disc in place when the navigation buttons disappear for the coin flip.
            if (c.celebrating) Spacer(Modifier.height(92.dp)) else Controls(c, st)
        }
    }

    if (askStop) {
        AlertDialog(
            onDismissRequest = { askStop = false },
            title = { Text("Stop the workout?") },
            text = { Text("Next you can save it to the journal (as stopped) or discard it.") },
            confirmButton = { TextButton(onClick = { askStop = false; c.stop() }) { Text("Stop", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { askStop = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Disc(
    c: PlayerController,
    st: PlayState,
    spinLevel: Int,
    discDp: Dp,
    color: Color,
    ink: Color,
    intervalCountdown: Boolean,
    showCounters: Boolean,
) {
    val p = LocalPalette.current
    val seg = st.segment
    val density = LocalDensity.current.density
    val showPause = c.paused && !c.celebrating
    val ringExit by animateFloatAsState(
        targetValue = if (showPause) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "Pause rings",
    )
    // Opening: the disc grows from nothing (slight overshoot), then the rings spiral out of it.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1000, easing = LinearEasing)) } // = openingMs in AppViewModel
    val discIn = BackOut.transform((intro.value / 0.5f).coerceIn(0f, 1f))
    val ringsIn = FastOutSlowInEasing.transform(((intro.value - 0.3f) / 0.7f).coerceIn(0f, 1f))
    val ringsHidden = maxOf(ringExit, 1f - ringsIn)

    // the disc inflates from the center at every new segment (fast, tiny overshoot)
    val inflate = remember { Animatable(1f) }
    LaunchedEffect(st.index) {
        inflate.snapTo(0.55f)
        inflate.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    // spin at each new segment, by level: 1 a quarter turn, 2 one turn (rings sway), 3 crazy: 1.5 turns, rings swing
    val spin = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }
    LaunchedEffect(st.index) {
        if (spinLevel !in 1..3 || st.index == 0) return@LaunchedEffect
        val (from, ms, sw) = when (spinLevel) { 1 -> Triple(-90f, 450, 0f); 2 -> Triple(-360f, 600, 0.4f); else -> Triple(-540f, 700, 1f) }
        if (sw > 0f) launch { swing.snapTo(sw); swing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
        spin.snapTo(from)
        spin.animateTo(0f, tween(ms, easing = FastOutSlowInEasing))
    }
    // 4 = always: the disc (with its numbers) and the rings turn slowly all the time, the rings the other way
    val always = if (spinLevel == 4 && !c.paused) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(8000, easing = androidx.compose.animation.core.LinearEasing)), label = "disc").value
    } else 0f
    // the end of a completed workout: a coin flip around the vertical axis, landing on its back ("done ✓")
    val flip = remember { Animatable(0f) }
    LaunchedEffect(c.celebrating) {
        if (c.celebrating) flip.animateTo(1260f, tween(1400, easing = FastOutSlowInEasing))
    }
    val flipK = (flip.value / 1260f).coerceIn(0f, 1f)
    val lift = 1f + 0.14f * kotlin.math.sin(Math.PI * flipK).toFloat()
    val backSide = (flip.value % 360f).let { it > 90f && it < 270f }

    val roundSegs = c.plan.roundSegments(st.round)
    // The rings follow the clock on every frame, read only in the draw phase: no recomposition per frame.
    val frameMs = remember { mutableLongStateOf(c.elapsedMs) }
    LaunchedEffect(c, c.paused, c.starting, c.celebrating, c.tick) {
        frameMs.longValue = c.elapsedMs
        if (!c.paused && !c.starting && !c.celebrating) {
            while (true) withFrameMillis { frameMs.longValue = c.elapsedMs }
        }
    }
    val work = c.plan.workCount(st)
    val chipText = rememberTextMeasurer()
    // the rings' extra turns (Crazy spin swing, Always spin); the chips follow them
    fun seriesTurn() = -50f * swing.value - always * 0.5f
    fun roundsTurn() = 50f * swing.value + always * 0.75f
    Box(
        Modifier.size(discDp).semantics {
            val round = "Round ${st.round} of ${st.rounds}"
            stateDescription = work?.let { (k, n) -> "$round, series $k of $n" } ?: round
        }.clickable(
            interactionSource = null,
            indication = androidx.compose.material3.ripple(bounded = false, radius = discDp / 2),
            enabled = !c.starting && !c.celebrating,
            onClickLabel = if (showPause) "Resume" else "Pause",
            role = Role.Button,
        ) { c.togglePause() },
        contentAlignment = Alignment.Center,
    ) {
        // Pause: the rings spiral into the disc (turn while shrinking behind it); resume: they spiral out.
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            val progress = ringsHidden
            rotationZ = 540f * progress
            scaleX = 1f - 0.9f * progress
            scaleY = scaleX
            alpha = 1f - progress * progress
        }) {
            val ms = minOf(frameMs.longValue, c.plan.totalSec * 1000L)
            val elapsedSec = ms / 1000.0
            val outer = size.minDimension / 2f - RingInset.toPx()
            rotate(seriesTurn()) { drawSeriesRing(roundSegs, st, elapsedSec, outer, p.teal) }
            rotate(roundsTurn()) { drawRoundsRing(c, st, elapsedSec, outer - RingGap.toPx(), p.accent) }
        }
        // the disc: its own layer so it can spin (Crazy spin) and flip (end of the workout)
        Box(
            Modifier.size(discDp - 108.dp)
                .graphicsLayer {
                    rotationZ = if (showPause) 0f else spin.value + always
                    rotationY = flip.value
                    cameraDistance = 14f * density
                    scaleX = inflate.value * lift * discIn
                    scaleY = inflate.value * lift * discIn
                }
                .clip(CircleShape)
                .background(if (backSide) p.accent else color),
            contentAlignment = Alignment.Center,
        ) {
            if (backSide) {
                // seen from behind: mirror it back so "done" reads right
                Column(Modifier.graphicsLayer { rotationY = 180f }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("done", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("✓", color = Color.White, fontSize = (discDp.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (showPause) {
                        Icon(AppIcons.Play, null, tint = ink, modifier = Modifier.size(28.dp))
                        Text("Paused", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("+${mmss(c.pauseSeconds)}", color = ink, style = NumberStyle, fontSize = (discDp.value * 0.14f).sp, fontWeight = FontWeight.Bold)
                        Text("${label(st)} · ${mmss(st.secLeft)} left", color = ink, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    } else if (!intervalCountdown) {
                        Icon(AppIcons.series(seg.icon), null, tint = ink, modifier = Modifier.size(28.dp))
                        Text(label(st), color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(mmss(st.secLeft), color = ink, style = NumberStyle, fontSize = (discDp.value * 0.17f).sp, fontWeight = FontWeight.Bold)
                        // what was set before the start for this series, e.g. "20 mm · 5 kg"
                        c.detailFor(seg)?.let { Text(it, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp)) }
                    }
                }
            }
        }
        // where you are: "3/6" (work series of this round) by the dot, "2/4" (rounds) on the inner ring
        if (showCounters) Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - 3f * ringsHidden).coerceIn(0f, 1f) }) {
            val t = minOf(frameMs.longValue, c.plan.totalSec * 1000L) / 1000.0
            val outer = size.minDimension / 2f - RingInset.toPx()
            if (work != null) seriesDotAngle(roundSegs, t)?.let { a ->
                drawChip(chipText, "${work.first}/${work.second}", a + seriesTurn(), outer + 18.dp.toPx(), p.text, p.bg)
            }
            if (st.rounds > 1) drawChip(chipText, "${st.round}/${st.rounds}", roundsAngle(c, st, t) + roundsTurn(),
                outer - RingGap.toPx(), p.accent, Color.White)
        }
    }
}

private fun Segment.playerColor(teal: Color): Color = when (kind) {
    SegmentKind.ROUND_REST, SegmentKind.CHANGE_SIDE -> teal
    else -> Color(color)
}

private fun label(st: PlayState): String {
    val s = st.segment
    return when (s.kind) {
        SegmentKind.WARMUP -> "Warm-up"
        SegmentKind.CHANGE_SIDE -> "Change side"
        SegmentKind.ROUND_REST -> "Rest between rounds"
        SegmentKind.SERIES -> s.name + when (s.side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }
    }
}

/** Outer ring: the segments of this round, sized by time, rounded ends, done grey, current thicker, a dot at "now". */
private fun DrawScope.drawSeriesRing(segs: List<Segment>, st: PlayState, t: Double, r: Float, teal: Color) {
    if (segs.isEmpty()) return
    val total = segs.sumOf { it.durationSec }.toFloat()
    val gap = 3.5f
    val usable = 360f - gap * segs.size
    var a = -90f
    val thin = 10.dp.toPx()
    val thick = 16.dp.toPx()
    val tl = Offset(center.x - r, center.y - r)
    val sz = Size(r * 2, r * 2)
    var dotAngle: Float? = null
    for (s in segs) {
        val sweep = usable * s.durationSec / total
        val done = s.endSec <= t
        val current = s.startSec <= t && t < s.endSec
        val col = if (done) DoneGrey else s.playerColor(teal)
        val w = when {
            s.kind == SegmentKind.ROUND_REST -> 5.dp.toPx()
            current -> thick
            else -> thin
        }
        // rounded caps eat into the gap: shorten each arc by the cap size
        val cap = (w / 2f) / r * (180f / Math.PI.toFloat())
        val draw = (sweep - 2 * cap).coerceAtLeast(0.1f)
        drawArc(col, a + cap, draw, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        if (current) dotAngle = a + sweep * ((t - s.startSec) / s.durationSec).toFloat()
        a += sweep + gap
    }
    dotAngle?.let {
        val rad = Math.toRadians(it.toDouble())
        drawCircle(Color.White, 5.dp.toPx(), Offset(center.x + r * kotlin.math.cos(rad).toFloat(), center.y + r * kotlin.math.sin(rad).toFloat()))
    }
}

/** Outer ring radius = half the disc box minus this (room for the chips outside it); inner ring is [RingGap] further in. */
private val RingInset = 24.dp
private val RingGap = 20.dp

/** Angle (degrees, 0 = 3 o'clock) of the dot on the series ring at [t] seconds; same layout as [drawSeriesRing]. */
private fun seriesDotAngle(segs: List<Segment>, t: Double): Float? {
    if (segs.isEmpty()) return null
    val total = segs.sumOf { it.durationSec }.toFloat()
    val gap = 3.5f
    val usable = 360f - gap * segs.size
    var a = -90f
    for (s in segs) {
        val sweep = usable * s.durationSec / total
        if (s.startSec <= t && t < s.endSec) return a + sweep * ((t - s.startSec) / s.durationSec).toFloat()
        a += sweep + gap
    }
    return null
}

/** Angle where the current round's piece of the rounds ring goes from done (grey) to left (purple). */
private fun roundsAngle(c: PlayerController, st: PlayState, t: Double): Float {
    val n = st.rounds
    val gap = if (n > 1) 6f else 0f
    val sweep = (360f - gap * n) / n
    val segs = c.plan.roundSegments(st.round)
    val start = segs.firstOrNull()?.startSec ?: 0
    val end = segs.lastOrNull()?.endSec ?: 1
    val frac = ((t - start) / (end - start)).toFloat().coerceIn(0f, 1f)
    return -90f + (st.round - 1) * (sweep + gap) + sweep * frac
}

/** A small pill with [text], centered at [r] from the center at [angle] degrees; the text stays upright. */
private fun DrawScope.drawChip(tm: TextMeasurer, text: String, angle: Float, r: Float, bg: Color, fg: Color) {
    val layout = tm.measure(text, TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    val w = layout.size.width + 12.dp.toPx()
    val h = layout.size.height + 2.dp.toPx()
    val rad = Math.toRadians(angle.toDouble())
    val cx = center.x + r * kotlin.math.cos(rad).toFloat()
    val cy = center.y + r * kotlin.math.sin(rad).toFloat()
    drawRoundRect(bg, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

/** Inner thin ring: one piece per round; done grey, left purple, the current one empties as it runs. */
private fun DrawScope.drawRoundsRing(c: PlayerController, st: PlayState, t: Double, r: Float, accent: Color) {
    val n = st.rounds
    val gap = if (n > 1) 6f else 0f
    val sweep = (360f - gap * n) / n
    val w = 5.dp.toPx()
    val cap = (w / 2f) / r * (180f / Math.PI.toFloat())
    val tl = Offset(center.x - r, center.y - r)
    val sz = Size(r * 2, r * 2)
    val segs = c.plan.roundSegments(st.round)
    val roundStart = segs.firstOrNull()?.startSec ?: 0
    val roundEnd = segs.lastOrNull()?.endSec ?: 1
    val frac = ((t - roundStart) / (roundEnd - roundStart)).toFloat().coerceIn(0f, 1f)
    for (i in 1..n) {
        val start = -90f + (i - 1) * (sweep + gap) + cap
        val full = (sweep - 2 * cap).coerceAtLeast(0.1f)
        when {
            i < st.round -> drawArc(DoneGrey, start, full, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            i > st.round -> drawArc(accent, start, full, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            else -> {
                drawArc(DoneGrey, start, full, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                val left = full * (1f - frac)
                if (left > 0.5f) drawArc(accent, start + full - left, left, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
    }
}

/** Ease-out with a small overshoot past 1 before settling. */
private val BackOut = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

/**
 * The last-3-seconds digit: starts 4x bigger and faint, shrinks into the center and ends solid.
 * Drawn twice and cut at the disc's edge: in the disc's own color where it is outside the disc,
 * in [inside] (dark or white, for contrast) where it is over the disc.
 */
@Composable
private fun CountdownDigit(n: Int, outside: Color, inside: Color, fontSize: TextUnit, discSize: Dp) {
    val shrink = remember(n) { Animatable(0f) }
    LaunchedEffect(shrink) {
        shrink.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    @Composable
    fun digit(color: Color) = Text(
        "$n", color = color, style = NumberStyle, fontSize = fontSize, fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer {
            val remaining = 1f - shrink.value
            scaleX = 1f + 3f * remaining
            scaleY = scaleX
            alpha = 1f - 0.9f * remaining
        },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        digit(outside)
        Box(Modifier.size(discSize).clip(CircleShape), contentAlignment = Alignment.Center) { digit(inside) }
    }
}

@Composable
private fun NextSegment(next: Segment?) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(p.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Next", color = p.muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
        if (next != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(next.playerColor(p.teal)))
            Spacer(Modifier.width(10.dp))
            Text(
                when (next.kind) {
                    SegmentKind.CHANGE_SIDE -> "Change side"
                    SegmentKind.ROUND_REST -> "Rest between rounds"
                    else -> next.name + when (next.side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }
                },
                color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f),
            )
            Text(formatDuration(next.durationSec), color = p.soft, style = NumberStyle, fontSize = 16.sp)
        } else {
            Text("The end", color = p.text, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Controls(c: PlayerController, st: PlayState?) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.surface).clickable(onClickLabel = "Previous") { c.previous() }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Back, "Previous", tint = p.text, modifier = Modifier.size(24.dp))
        }
        // during a work series: mark it as let go of, the timer keeps running (mockup ideas, 2); tap again to take it back
        val work = st != null && !c.starting && c.plan.isWork(st.segment)
        val marked = st != null && st.index in c.letGo
        Box(
            Modifier.width(112.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(if (marked) p.dangerText.copy(alpha = 0.18f) else Color.Transparent)
                .border(if (work || marked) 2.dp else 0.dp, if (work || marked) p.dangerText else Color.Transparent, RoundedCornerShape(24.dp))
                .then(if (work) Modifier.clickable(onClickLabel = if (marked) "Take back let go" else "Let go") { c.toggleLetGo() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (work) Text(if (marked) "Let go ✓" else "Let go", color = p.dangerText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.surface).clickable(onClickLabel = "Next") { c.next() }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Back, "Next", tint = p.text, modifier = Modifier.size(24.dp).scale(scaleX = -1f, scaleY = 1f))
        }
    }
}

/** The next round's values during the rest between rounds (mockup ideas, 12): changed here, they count from that round. */
@Composable
private fun RestValues(c: PlayerController, nextRound: Int, rounds: Int) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(p.surface)
            .heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Round $nextRound of $rounds with", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val many = c.keys.map { it.series }.distinct().size > 1
        c.keys.forEach { k -> ValueRow(k, c.values[k] ?: 0.0, many) { c.values[k] = it } }
    }
}

/** 00:04, 05:25, 1:02:03: minutes always with two digits, like the old app. */
private fun mmss(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val x = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%02d:%02d".format(m, x)
}
