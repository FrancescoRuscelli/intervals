package app.intervals.player

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.PlayState
import app.intervals.core.SegmentKind
import app.intervals.core.Side
import app.intervals.core.formatDuration
import app.intervals.ui.AppIcons
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle

private val DoneGrey = Color(0xFF3A3E46)

@Composable
fun PlayerScreen(c: PlayerController, keepScreenOn: Boolean, discAnimation: app.intervals.core.DiscAnimation = app.intervals.core.DiscAnimation.CALM, onClose: () -> Unit) {
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

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { askStop = true }) { Icon(AppIcons.Close, "Stop", tint = p.text) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.saved.workout.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (st != null) Text("Round ${st.round} of ${st.rounds}", color = p.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(48.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (st != null) Disc(c, st, discAnimation == app.intervals.core.DiscAnimation.CRAZY_SPIN, Modifier.padding(16.dp).fillMaxWidth().aspectRatio(1f))
            if (c.countdown > 0) GetReady(c.countdown)
        }

        if (st != null) Info(c, st)
        // during the coin flip the buttons step aside (nothing to pause any more)
        if (c.celebrating) Spacer(Modifier.height(120.dp)) else Controls(c)
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
private fun Disc(c: PlayerController, st: PlayState, crazy: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val seg = st.segment
    val color = Color(seg.color)
    val ink = if (color.luminance() > 0.35f) Color(0xFF14080C) else Color.White

    // the disc inflates from the center at every new segment (fast, tiny overshoot)
    val inflate = remember { Animatable(1f) }
    LaunchedEffect(st.index) {
        inflate.snapTo(0.55f)
        inflate.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    // Crazy spin: the disc whirls 1.5 turns and settles upright; the series ring swings back, the rounds ring the other way
    val spin = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }
    LaunchedEffect(st.index) {
        if (!crazy || st.index == 0) return@LaunchedEffect
        launch { swing.snapTo(1f); swing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
        spin.snapTo(-180f)
        spin.animateTo(360f, tween(700, easing = FastOutSlowInEasing))
        spin.snapTo(0f)
    }
    // the end of a completed workout: a coin flip around the vertical axis, landing on its back ("done ✓")
    val flip = remember { Animatable(0f) }
    LaunchedEffect(c.celebrating) {
        if (c.celebrating) flip.animateTo(1260f, tween(1400, easing = FastOutSlowInEasing))
    }
    val flipK = (flip.value / 1260f).coerceIn(0f, 1f)
    val lift = 1f + 0.14f * kotlin.math.sin(Math.PI * flipK).toFloat()
    val backSide = (flip.value % 360f).let { it > 90f && it < 270f }

    // last 3 s: the number starts huge and higher up, drops down shrinking (~0.4 s)
    val drop = remember { Animatable(1f) }
    val bigCount = st.secLeft <= 3 && seg.durationSec > 3
    LaunchedEffect(st.index, st.secLeft) {
        if (bigCount) { drop.snapTo(0f); drop.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    }

    val roundSegs = c.plan.roundSegments(st.round)
    val elapsedSec = c.elapsedMs / 1000.0
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sizePx = constraints.maxWidth.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f - 14.dp.toPx()
            rotate(-50f * swing.value) { drawSeriesRing(roundSegs, st, elapsedSec, outer, p.teal) }
            rotate(50f * swing.value) { drawRoundsRing(c, st, outer - 24.dp.toPx(), p.accent) }
        }
        val discDp = (sizePx / LocalDensityPx()).dp
        val density = LocalDensityPx()
        // the disc: its own layer so it can spin (Crazy spin) and flip (end of the workout)
        Box(
            Modifier.size(discDp - 108.dp)
                .graphicsLayer {
                    rotationZ = spin.value
                    rotationY = flip.value
                    cameraDistance = 14f * density
                    scaleX = inflate.value * lift
                    scaleY = inflate.value * lift
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
            } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!bigCount) {
                Icon(app.intervals.ui.AppIcons.series(seg.icon), null, tint = ink, modifier = Modifier.size(28.dp))
                Text(label(st), color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(formatDuration(st.secLeft), color = ink, style = NumberStyle, fontSize = (discDp.value * 0.2f).sp, fontWeight = FontWeight.Medium)
            } else {
                Text(label(st), color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                val k = drop.value
                Text(
                    "${st.secLeft}", color = ink, style = NumberStyle, fontWeight = FontWeight.Bold,
                    fontSize = (discDp.value * 0.34f).sp,
                    modifier = Modifier.offset(y = (-(1f - k) * 70f).dp).scale(1f + (1f - k) * 1.6f),
                )
            }
        }
        }
    }
}

@Composable
private fun LocalDensityPx(): Float = androidx.compose.ui.platform.LocalDensity.current.density

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
private fun DrawScope.drawSeriesRing(segs: List<app.intervals.core.Segment>, st: PlayState, t: Double, r: Float, teal: Color) {
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
        val col = when {
            done -> DoneGrey
            s.kind == SegmentKind.ROUND_REST || s.kind == SegmentKind.CHANGE_SIDE -> teal
            else -> Color(s.color)
        }
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

/** Inner thin ring: one piece per round; done grey, left purple, the current one empties as it runs. */
private fun DrawScope.drawRoundsRing(c: PlayerController, st: PlayState, r: Float, accent: Color) {
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
    val frac = ((c.elapsedMs / 1000.0 - roundStart) / (roundEnd - roundStart)).toFloat().coerceIn(0f, 1f)
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

@Composable
private fun GetReady(n: Int) {
    val p = LocalPalette.current
    val drop = remember { Animatable(0f) }
    LaunchedEffect(n) { drop.snapTo(0f); drop.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Get ready", color = p.teal, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            val k = drop.value
            Text("$n", color = p.text, style = NumberStyle, fontSize = 120.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (-(1f - k) * 70f).dp).scale(1f + (1f - k) * 1.6f))
        }
    }
}

@Composable
private fun Info(c: PlayerController, st: PlayState) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text(
                if (st.inRound > 0) "Series ${st.inRound} of ${st.ofRound}" else "Warm-up",
                color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Text("${formatDuration(st.totalLeftSec)} left", color = p.muted, fontSize = 13.sp)
        }
        val n = st.next
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Next", color = p.muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
            if (n != null) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(n.color)))
                Spacer(Modifier.width(10.dp))
                Text(
                    when (n.kind) {
                        SegmentKind.CHANGE_SIDE -> "Change side"
                        SegmentKind.ROUND_REST -> "Rest between rounds"
                        else -> n.name + when (n.side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }
                    },
                    color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Text(formatDuration(n.durationSec), color = p.soft, style = NumberStyle, fontSize = 16.sp)
            } else {
                Text("The end", color = p.text, fontSize = 16.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
            Box(Modifier.fillMaxWidth(st.progress.toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
        }
    }
}

@Composable
private fun Controls(c: PlayerController) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.surface).clickable(onClickLabel = "Previous") { c.previous() }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Back, "Previous", tint = p.text, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.size(84.dp).clip(CircleShape).background(p.accent).clickable(onClickLabel = if (c.paused) "Resume" else "Pause") { c.togglePause() }, contentAlignment = Alignment.Center) {
            if (c.paused) Icon(AppIcons.Play, "Resume", tint = p.onAccent, modifier = Modifier.size(32.dp))
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { Box(Modifier.width(8.dp).height(28.dp).clip(RoundedCornerShape(3.dp)).background(p.onAccent)) }
            }
        }
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.surface).clickable(onClickLabel = "Next") { c.next() }, contentAlignment = Alignment.Center) {
            Icon(AppIcons.Back, "Next", tint = p.text, modifier = Modifier.size(24.dp).scale(scaleX = -1f, scaleY = 1f))
        }
    }
}
