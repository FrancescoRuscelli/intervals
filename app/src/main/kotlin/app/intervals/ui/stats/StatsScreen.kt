package app.intervals.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.day
import app.intervals.core.minutesPerDay
import app.intervals.core.minutesPerWeek
import app.intervals.core.summary
import app.intervals.core.topWorkouts
import app.intervals.core.weekStart
import app.intervals.core.colorAt
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import app.intervals.ui.NumberStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** The period around [anchor]: first day and the day after the last. */
internal fun Period.range(anchor: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
    Period.WEEK -> weekStart(anchor).let { it to it.plusWeeks(1) }
    Period.MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1) }
    Period.YEAR -> anchor.withDayOfYear(1).let { it to it.plusYears(1) }
}

private fun Period.shift(anchor: LocalDate, by: Long): LocalDate = when (this) {
    Period.WEEK -> anchor.plusWeeks(by)
    Period.MONTH -> anchor.plusMonths(by)
    Period.YEAR -> anchor.plusYears(by)
}

private fun Period.title(anchor: LocalDate): String = when (this) {
    Period.WEEK -> weekStart(anchor).let { s ->
        val e = s.plusDays(6)
        if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${e.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))}"
        else "${s.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${e.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"
    }
    Period.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    Period.YEAR -> "${anchor.year}"
}

/**
 * Stats on its own screen (mockup intervals-stats-screen.html), opened from the Journal's month line on that month.
 * Intervals / Gym / Both, Week / Month / Year with ‹ › to go back in time. Every tile, bar and row opens what it counts:
 * a list (sessions, gym days, exercises, records), the Journal on a day, or an exercise's History / Records.
 */
@Composable
fun StatsScreen(vm: AppViewModel, month: String) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var anchorText by rememberSaveable(month) {
        val ym = YearMonth.parse(month)
        mutableStateOf((if (ym == YearMonth.from(today)) today else ym.atEndOfMonth()).toString())
    }
    val anchor = LocalDate.parse(anchorText)
    var periodIdx by rememberSaveable { mutableIntStateOf(Period.MONTH.ordinal) }
    var kind by rememberSaveable { mutableIntStateOf(2) }
    val period = Period.entries[periodIdx]
    val (start, end) = period.range(anchor)
    val all by remember { vm.sessions(0, Long.MAX_VALUE) }.collectAsState(initial = emptyList())
    val inPeriod = all.filter { val d = it.day(zone); !d.isBefore(start) && d.isBefore(end) }
    val sum = summary(inPeriod, zone)
    // the whole gym history: records are judged against it
    val gymAll by remember { vm.gymSetsSince("0000-01-01") }.collectAsState(initial = emptyList())
    val gymIn = remember(gymAll, start, end) { gymAll.filter { it.day >= start.toString() && it.day < end.toString() } }
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val tagColor = remember(groups) {
        groups.filter { it.isTag }.withIndex().flatMap { (i, t) -> t.items.map { it.exerciseId to Color(t.colorAt(i)) } }.distinctBy { it.first }.toMap()
    }
    val gymDaySet = remember(gymAll) { gymAll.map { LocalDate.parse(it.day) }.toSet() }
    val ivDaySet = remember(all) { all.map { it.day(zone) }.toSet() }
    val sessions = { name: String? -> vm.openStatsList("sessions", start, end, name) }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Back", tint = p.text) }
            Text("Stats", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segmented(listOf("Intervals", "Gym", "Both"), kind) { kind = it }
            Spacer(Modifier.height(8.dp))
            Segmented(Period.entries.map { it.label }, periodIdx) { periodIdx = it }
            // ‹ the period › : back in time, not past today
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchorText = period.shift(anchor, -1).toString() }) { Text("‹", color = p.accentText, fontSize = 24.sp) }
                Text(period.title(anchor), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                val canNext = !end.isAfter(today)
                IconButton(enabled = canNext, onClick = { anchorText = minOf(period.shift(anchor, 1), today).toString() }) {
                    Text("›", color = if (canNext) p.accentText else p.faint, fontSize = 24.sp)
                }
            }

            val toMonth = { d: LocalDate -> anchorText = YearMonth.from(d).atEndOfMonth().coerceAtMost(today).toString(); periodIdx = Period.MONTH.ordinal }
            val mostTrained: @Composable () -> Unit = {
                MostTrainedCard(gymIn, exercises, tagColor, onAll = { vm.openStatsList("exercises", start, end) }) { id -> vm.openGymLog(id, today.toString(), tab = 1) }
            }
            when (kind) {
                0 -> {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Time", hm(sum.totalSec), Modifier.weight(1f)) { sessions(null) }
                        StatTile("Sessions", "${sum.sessions}", Modifier.weight(1f)) { sessions(null) }
                        StatTile("Completed", if (sum.sessions == 0) "–" else "${sum.completed * 100 / sum.sessions}%", Modifier.weight(1f)) { sessions(null) }
                    }
                    StatCard {
                        // the bars of the period; a tap opens the Journal on that day or week, or a year's month in Stats
                        val (title, bars) = when (period) {
                            Period.WEEK -> "Minutes per week · 12 weeks" to minutesPerWeek(all, 12, anchor, zone).map { (w, m) -> Triple("${w.dayOfMonth}/${w.monthValue}", m, w) }
                            Period.MONTH -> "Minutes per day" to minutesPerDay(all, YearMonth.from(anchor), zone).mapIndexed { i, m -> Triple("${i + 1}", m, start.plusDays(i.toLong())) }
                            Period.YEAR -> "Minutes per month" to (1..12).map { mo ->
                                val ym = YearMonth.of(anchor.year, mo)
                                Triple(ym.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), minutesPerDay(all, ym, zone).sum(), ym.atDay(1))
                            }
                        }
                        Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        TapBars(bars.map { it.first to it.second }, current = bars.indexOfFirst { it.third == period.barOf(anchor) }, unit = "min") { i ->
                            val d = bars[i].third
                            if (period == Period.YEAR) toMonth(d) else vm.openJournalDay(d.toString())
                        }
                    }
                    IntervalsCard(inPeriod, sum.sessions, sum.totalSec, showEmpty = true, onOpen = sessions)
                }
                1 -> {
                    GymTiles(vm, gymIn, start, end)
                    GymDaysPerWeekCard(vm, gymAll, end, anchor, today)
                    SetsPerTagCard(vm, gymAll, groups, anchor, today)
                    mostTrained()
                    RecordsCard(vm, exercises, gymAll, start, end)
                }
                else -> {
                    // Both (mockup intervals-stats-tidy.html): one row of tiles, one chart, then the lists
                    val trained = (gymDaySet + ivDaySet).count { !it.isBefore(start) && it.isBefore(end) }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Days trained", "$trained", Modifier.weight(1f)) { vm.openStatsList("gymDays", start, end) }
                        StatTile("Gym sets", "${gymIn.size}", Modifier.weight(1f)) { vm.openStatsList("exercises", start, end) }
                        StatTile("Interval time", hm(sum.totalSec), Modifier.weight(1f), dim = sum.sessions == 0) { sessions(null) }
                    }
                    TrainingCard(period, start, end, anchor, today, gymDaySet, ivDaySet, onDay = { vm.openJournalDay(it.toString()) }, onMonth = toMonth)
                    SetsPerTagCard(vm, gymAll, groups, anchor, today)
                    mostTrained()
                    if (inPeriod.isNotEmpty()) IntervalsCard(inPeriod, sum.sessions, sum.totalSec, showEmpty = false, onOpen = sessions)
                    RecordsCard(vm, exercises, gymAll, start, end)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Under "Both" (mockup intervals-stats-both-chart.html, 2): per day a block for the gym and one for intervals, so
 * every training day shows whatever it was; a year shows per month the gym days and the interval days. The hint on the
 * right is the days trained per week in the period (up to today).
 */
@Composable
private fun TrainingCard(
    period: Period, start: LocalDate, end: LocalDate, anchor: LocalDate, today: LocalDate,
    gym: Set<LocalDate>, iv: Set<LocalDate>, onDay: (LocalDate) -> Unit, onMonth: (LocalDate) -> Unit,
) {
    val p = LocalPalette.current
    val last = minOf(end.minusDays(1), today)
    val trained = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.count { it in gym || it in iv }
    val weeks = (java.time.temporal.ChronoUnit.DAYS.between(start, last) + 1).coerceAtLeast(1) / 7.0
    // (label, gym, intervals, the day or month it opens)
    val bars: List<Triple<String, Pair<Int, Int>, LocalDate>> = when (period) {
        Period.YEAR -> (1..12).map { mo ->
            val ym = YearMonth.of(anchor.year, mo)
            val ds = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
            Triple(ym.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), ds.count { it in gym } to ds.count { it in iv }, ym.atDay(1))
        }
        else -> generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }.map { d ->
            Triple(if (period == Period.WEEK) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else "${d.dayOfMonth}",
                (if (d in gym) 1 else 0) to (if (d in iv) 1 else 0), d)
        }.toList()
    }
    StatCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (period == Period.YEAR) "Days trained per month" else "Training per day", color = p.text, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${"%.1f".format(trained / weeks)} days / week", color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        val max = (bars.maxOfOrNull { it.second.first + it.second.second } ?: 0).coerceAtLeast(if (period == Period.YEAR) 1 else 2)
        Row(
            Modifier.fillMaxWidth().height(110.dp).semantics {
                contentDescription = bars.joinToString { "${it.first}: gym ${it.second.first}, intervals ${it.second.second}" }
            },
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { (label, v, d) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = "Open $label") { if (period == Period.YEAR) onMonth(d) else onDay(d) },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // the empty part first, so the blocks stand on the base line: intervals on top of the gym
                    val rest = 1f - (v.first + v.second).toFloat() / max
                    if (rest > 0f) Spacer(Modifier.weight(rest))
                    if (v.second > 0) Box(Modifier.fillMaxWidth().weight(v.second.toFloat() / max).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(p.pink))
                    if (v.first > 0) Box(Modifier.fillMaxWidth().weight(v.first.toFloat() / max).clip(RoundedCornerShape(topStart = if (v.second > 0) 0.dp else 3.dp, topEnd = if (v.second > 0) 0.dp else 3.dp)).background(Color(0xFF5B8DEF)))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = when { bars.size > 20 -> 7; bars.size > 7 -> 3; else -> 1 }
            bars.forEachIndexed { i, b -> Text(if (i % step == 0) b.first else "", color = p.muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(Color(0xFF5B8DEF) to "gym", p.pink to "intervals").forEach { (c, t) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(5.dp))
                    Text(t, color = p.muted, fontSize = 11.sp)
                }
            }
        }
    }
}

/** The interval workouts done most in the period; each opens its sessions. */
@Composable
private fun IntervalsCard(inPeriod: List<app.intervals.core.Session>, count: Int, totalSec: Int, showEmpty: Boolean, onOpen: (String?) -> Unit) {
    val p = LocalPalette.current
    val top = topWorkouts(inPeriod, 5)
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (showEmpty) "Most done" else "Intervals", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (count > 0) Text("$count session${if (count == 1) "" else "s"} · ${hm(totalSec)}", color = p.muted, fontSize = 12.sp)
        }
        if (top.isEmpty()) Text("No sessions in this period.", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        val max = top.maxOfOrNull { it.times } ?: 1
        top.forEach { t ->
            Column(Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Sessions of ${t.name}") { onOpen(t.name) }
                .padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${t.times}× · ${hm(t.totalSec)}  ›", color = p.muted, fontSize = 12.sp)
                }
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
                    Box(Modifier.fillMaxWidth(t.times.toFloat() / max).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.pink))
                }
            }
        }
    }
}

/** The bar of the period the anchor falls in: its week start, its day, or its month's first day. */
private fun Period.barOf(anchor: LocalDate): LocalDate = when (this) {
    Period.WEEK -> weekStart(anchor)
    Period.MONTH -> anchor
    Period.YEAR -> anchor.withDayOfMonth(1)
}

internal fun hm(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.accent else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A number tile that opens what it counts. */
@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier, dim: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Show $label", onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = if (dim) p.faint else p.text, style = NumberStyle, fontSize = 22.sp)
        Row {
            Text(label, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("›", color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun StatCard(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) { content() }
}

/** A bar chart whose bars can be tapped: the current one in purple; a few labels under it. */
@Composable
internal fun TapBars(bars: List<Pair<String, Int>>, current: Int, unit: String, onTap: (Int) -> Unit) {
    val p = LocalPalette.current
    val max = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = bars.joinToString { "${it.first}: ${it.second} $unit" } },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { i, (label, v) ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = "Open $label") { onTap(i) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (v > 0) Box(
                    Modifier.fillMaxWidth().fillMaxHeight(v.toFloat() / max).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (i == current) p.accent else if (p.dark) Color(0xFF4C3A80) else p.accent.copy(alpha = 0.55f)),
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val step = when { bars.size > 20 -> 7; bars.size > 6 -> 3; else -> 1 }
        bars.forEachIndexed { i, (label, _) ->
            Text(if (i % step == 0) label else "", color = p.muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true))
        }
    }
}
