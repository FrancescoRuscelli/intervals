@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.intervals.ui.journal

import app.intervals.core.letGoText
import app.intervals.ui.gym.withoutColors
import androidx.activity.compose.BackHandler
import app.intervals.core.isWarmup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.intervals.core.Exercise
import app.intervals.core.GymGroup
import app.intervals.core.GymSet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.IntrinsicSize
import app.intervals.core.Session
import app.intervals.core.day
import app.intervals.core.formatDuration
import app.intervals.core.summary
import app.intervals.core.valuesLine
import app.intervals.ui.AppIcons
import app.intervals.ui.AppViewModel
import app.intervals.ui.LocalPalette
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar (Monday first) with a dot per session (pink = completed, yellow = stopped) and the gym strip,
 * month totals, and below it the selected day, which swipes to the day before or after.
 */
@Composable
fun JournalScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Journal", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
        }
        // Stats is its own screen now, opened from the month line (mockup intervals-stats-screen.html)
        JournalCalendar(vm)
    }
}

/**
 * The month on top, always shown; below it the selected day's workouts. Swiping the day sideways goes to the day before or
 * after, and the calendar follows and marks it. Swiping the month selects that month's last trained day.
 */
@Composable
private fun JournalCalendar(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    // one calendar page per month and one day page per day, from 2000 to today
    val firstMonth = remember { YearMonth.of(2000, 1) }
    val firstDay = remember { firstMonth.atDay(1) }
    val monthCount = remember(today) { ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(today)).toInt() + 1 }
    val dayCount = remember(today) { ChronoUnit.DAYS.between(firstDay, today).toInt() + 1 }
    val monthPage = { d: LocalDate -> ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(d)).toInt().coerceIn(0, monthCount - 1) }
    val dayPage = { d: LocalDate -> ChronoUnit.DAYS.between(firstDay, d).toInt().coerceIn(0, dayCount - 1) }
    val pager = rememberPagerState(initialPage = monthCount - 1) { monthCount }
    val days = rememberPagerState(initialPage = dayCount - 1) { dayCount }
    val scope = rememberCoroutineScope()
    val month = firstMonth.plusMonths(pager.currentPage.toLong())
    val selectedDay = firstDay.plusDays(days.currentPage.toLong())

    // the whole history, like the records already read it
    val allSessions by remember { vm.sessions(0, Long.MAX_VALUE) }.collectAsState(initial = null)
    val allSets by remember { vm.gymSetsBetween("0000-01-01", "9999-12-31") }.collectAsState(initial = null)
    val notes by remember { vm.gymDayNotes() }.collectAsState(initial = emptyMap())
    val exercises by vm.exercises.collectAsState()
    val allGroups by vm.groups.collectAsState()
    val byDay = remember(allSessions, zone) { allSessions.orEmpty().groupBy { it.day(zone) } }
    val setsByDay = remember(allSets) { allSets.orEmpty().groupBy { LocalDate.parse(it.day) } }
    val strips = remember(setsByDay, allGroups) { setsByDay.mapValues { (_, s) -> app.intervals.core.dayStrip(s, allGroups) } }

    var filter by rememberSaveable { mutableStateOf("") }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val query = filter.trim()
    val matching: Set<LocalDate>? = remember(query, allSets, allSessions, exercises, allGroups, zone) {
        if (query.isEmpty()) null else {
            val ids = exercises.filter { it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet() +
                allGroups.filter { it.isTag && it.name.contains(query, ignoreCase = true) }.flatMap { it.items }.map { it.exerciseId }
            buildSet<LocalDate> {
                allSets.orEmpty().filter { it.exerciseId in ids }.forEach { add(LocalDate.parse(it.day)) }
                allSessions.orEmpty().filter { it.workoutName.contains(query, ignoreCase = true) }.forEach { add(it.day(zone)) }
            }
        }
    }
    val totals = remember(byDay, setsByDay) {
        (byDay.keys + setsByDay.keys).groupBy { YearMonth.from(it) }.mapValues { (_, d) ->
            Triple(d.sumOf { byDay[it].orEmpty().size + if (it in setsByDay) 1 else 0 }, d.size, d.sumOf { setsByDay[it].orEmpty().size })
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val closeFilter: () -> Unit = {
        keyboard?.hide()
        filterOpen = false
    }
    BackHandler(enabled = filterOpen && vm.overlays.isEmpty(), onBack = closeFilter)

    // swiped to a day of another month: the calendar shows that month
    LaunchedEffect(days.settledPage) {
        val m = monthPage(firstDay.plusDays(days.settledPage.toLong()))
        if (m != pager.currentPage) pager.animateScrollToPage(m)
    }
    // swiped to another month: select its last trained day (or today / its last day)
    LaunchedEffect(pager.settledPage) {
        val m = firstMonth.plusMonths(pager.settledPage.toLong())
        if (YearMonth.from(firstDay.plusDays(days.currentPage.toLong())) == m) return@LaunchedEffect
        val trained = (byDay.keys + setsByDay.keys).filter { YearMonth.from(it) == m }
        days.scrollToPage(dayPage(trained.maxOrNull() ?: if (m == YearMonth.from(today)) today else m.atEndOfMonth()))
    }
    // Opened for a specific date.
    LaunchedEffect(vm.journalFocusDay) {
        val d = vm.journalFocusDay?.let(LocalDate::parse) ?: return@LaunchedEffect
        days.scrollToPage(dayPage(d))
        pager.scrollToPage(monthPage(d))
        vm.journalFocusDay = null
    }
    val pick = { d: LocalDate -> scope.launch { days.scrollToPage(dayPage(d)) }; Unit }
    var editing by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val active = filter.isNotBlank()
                IconButton(onClick = { if (filterOpen) closeFilter() else filterOpen = true }) {
                    Box {
                        Icon(
                            AppIcons.Search,
                            if (active) "Filter calendar, active filter" else "Filter calendar",
                            tint = if (active || filterOpen) p.accentText else p.muted,
                        )
                        if (active) Box(Modifier.align(Alignment.TopEnd).size(5.dp).clip(CircleShape).background(p.accent))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Icon(AppIcons.Back, "Previous month", tint = p.text) }
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year, color = p.text, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(108.dp))
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = month < YearMonth.from(today)) {
                    Icon(AppIcons.Back, "Next month", tint = if (month < YearMonth.from(today)) p.text else p.faint,
                        modifier = Modifier.size(24.dp).scale(scaleX = -1f, scaleY = 1f))
                }
            }
            if (filterOpen) {
                LaunchedEffect(Unit) {
                    if (vm.overlays.isEmpty()) {
                        searchFocus.requestFocus()
                        keyboard?.show()
                    }
                }
                OutlinedTextField(
                    filter, { filter = it }, placeholder = { Text("Filter calendar by exercise or tag") }, singleLine = true,
                    trailingIcon = if (filter.isNotEmpty()) ({
                        IconButton(onClick = { filter = "" }) { Icon(AppIcons.Close, "Clear calendar filter") }
                    }) else null,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                )
            }
            MonthTotals(totals[month]) { vm.openStats(month) }
            HorizontalPager(pager, key = { it }, verticalAlignment = Alignment.Top,
                flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1))) { page ->
                Calendar(firstMonth.plusMonths(page.toLong()), today, selectedDay, byDay, strips, matching.orEmpty(), matching != null, pick)
            }
        }
        // the selected day; swipe for the day before or after
        HorizontalPager(days, Modifier.weight(1f), key = { it }, verticalAlignment = Alignment.Top,
            flingBehavior = PagerDefaults.flingBehavior(days, pagerSnapDistance = PagerSnapDistance.atMost(1))) { page ->
            val d = firstDay.plusDays(page.toLong())
            val sets = setsByDay[d].orEmpty()
            val records = remember(d, allSets, exercises) {
                val ids = sets.map { it.exerciseId }.toSet()
                if (ids.isEmpty()) emptyMap() else app.intervals.core.recordMarksOn(d.toString(), allSets.orEmpty().filter { it.exerciseId in ids }, exercises.filter { it.id in ids })
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                DayEntry(
                    vm, d, today, sets, byDay[d].orEmpty().sortedBy { it.startedAt }, notes[d.toString()].orEmpty(),
                    exercises, allGroups, records, zone, closeFilter, onNote = { editing = it }, onDelete = { deleting = it },
                    previous = { s -> allSessions.orEmpty().filter { it.workoutName == s.workoutName && it.startedAt < s.startedAt && it.values.isNotEmpty() }.maxByOrNull { it.startedAt } },
                )
            }
        }
    }

    editing?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Note") },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("How did it go?") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { vm.setNote(s.id, text); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this session?") },
            text = { Text("${s.workoutName}, with its logged values and note.") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); deleting = null }) { Text("Delete", color = p.dangerText) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** The selected day: its date with the day menu, the gym card, the interval sessions. */
@Composable
private fun DayEntry(
    vm: AppViewModel, d: LocalDate, today: LocalDate, sets: List<GymSet>, sessions: List<Session>, note: String,
    exercises: List<Exercise>, groups: List<GymGroup>, records: Map<Long, String?>, zone: ZoneId, closeFilter: () -> Unit,
    onNote: (Session) -> Unit, onDelete: (Session) -> Unit, previous: (Session) -> Session? = { null },
) {
    val p = LocalPalette.current
    val day = d.toString()
    var dayMenu by remember(day) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (d == today) "Today" else d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", Locale.ENGLISH)),
                color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            val running = day in vm.activeGroups
            Box {
                IconButton(onClick = { dayMenu = true }) {
                    Icon(AppIcons.More, "Day actions", tint = p.accentText, modifier = Modifier.rotate(90f))
                }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (running) "Resume workout" else "Add exercise") },
                        onClick = {
                            dayMenu = false
                            closeFilter()
                            if (running) vm.openGymDay(day) else vm.openGymPick(day)
                        },
                    )
                }
            }
        }
        if (sets.isNotEmpty() || note.isNotBlank()) {
            GymDayCard(sets, exercises.associateBy { it.id }, groups, records, note = note) { vm.openGymDay(day) }
            Spacer(Modifier.height(6.dp))
        }
        if (sessions.isEmpty() && sets.isEmpty() && note.isBlank()) {
            Text(
                if (d == today) "No sessions today." else "No sessions this day.",
                color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        sessions.forEach { s ->
            SessionCard(s, zone, previous(s), onNote = { onNote(s) }, onLongPress = { onDelete(s) },
                onProgress = s.workoutId?.let { id -> { closeFilter(); vm.openWorkoutProgress(id) } })
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MonthTotals(t: Triple<Int, Int, Int>?, onStats: () -> Unit) {
    val p = LocalPalette.current
    val (sessions, days, sets) = t ?: Triple(0, 0, 0)
    // the month line is the way into Stats: tap anywhere on it
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(12.dp)).background(p.surface)
            .clickable(onClickLabel = "Open Stats", onClick = onStats).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Text(
        buildAnnotatedString {
            val totals = listOf(
                sessions to if (sessions == 1) "session" else "sessions",
                days to if (days == 1) "day trained" else "days trained",
                sets to if (sets == 1) "gym set" else "gym sets",
            )
            totals.forEachIndexed { index, (count, label) ->
                if (index > 0) append("  ·  ")
                withStyle(SpanStyle(color = p.text, fontWeight = FontWeight.Bold)) { append(count.toString()) }
                append(" $label")
            }
        },
        color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
    )
    Text("Stats ›", color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The month, Monday first. */
@Composable
private fun Calendar(
    month: YearMonth, today: LocalDate, selected: LocalDate?, byDay: Map<LocalDate, List<Session>>,
    gym: Map<LocalDate, List<app.intervals.core.StripPiece>>, highlighted: Set<LocalDate>, filtering: Boolean,
    onPick: (LocalDate) -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
        val weeks = (List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }).chunked(7)
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d -> DayCell(d, today, selected, byDay[d].orEmpty(), gym[d].orEmpty(), d in highlighted, filtering, onPick, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate?, today: LocalDate, selected: LocalDate?, sessions: List<Session>, strip: List<app.intervals.core.StripPiece>, highlighted: Boolean, filtering: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    val gym = strip.isNotEmpty()
    val p = LocalPalette.current
    Box(modifier.height(44.dp), contentAlignment = Alignment.Center) {
        if (d == null) return@Box
        val sel = d == selected
        val future = d > today
        Column(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (sel || highlighted) (if (p.dark) Color(0xFF3A2F5C) else p.tint) else Color.Transparent)
                .border(2.dp, if (highlighted || d == today && !sel) p.accent else Color.Transparent, CircleShape)
                .clickable(enabled = !future) { onPick(d) },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("${d.dayOfMonth}", color = if (future || filtering && !highlighted) p.faint else p.text, fontSize = 14.sp,
                fontWeight = if (sessions.isNotEmpty() || gym || sel) FontWeight.Bold else FontWeight.Normal)
            // gym: the day strip (split by exercise, sized by sets, colored by group)
            if (gym) Row(Modifier.padding(top = 2.dp).width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                strip.forEach { Box(Modifier.weight(it.sets.toFloat()).height(4.dp).background(Color(it.color))) }
            }
            // intervals: a dot per session (pink completed, yellow stopped)
            if (sessions.isNotEmpty()) Row(Modifier.height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                sessions.take(3).forEach { s ->
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (s.completed) p.pink else p.yellow))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: Session, zone: ZoneId, before: Session?, onNote: () -> Unit, onLongPress: () -> Unit, onProgress: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val time = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
            .combinedClickable(onClick = onNote, onLongClick = onLongPress).padding(10.dp),
    ) {
        Box(Modifier.width(5.dp).height(56.dp).clip(RoundedCornerShape(3.dp)).background(if (s.completed) p.pink else p.yellow))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.workoutName, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$time · ${formatDuration(s.durationSec)}", color = p.muted, fontSize = 12.sp)
            }
            Text(
                if (s.completed) "Completed · ${s.roundsTotal} rounds" else "Stopped in round ${(s.roundsDone + 1).coerceAtMost(s.roundsTotal)} of ${s.roundsTotal}",
                color = if (s.completed) p.soft else p.yellow, fontSize = 13.sp,
            )
            s.valuesLine()?.let { Text(it, color = p.soft, fontSize = 13.sp) }
            // the hangs let go of early (mockup intervals-ideas-from-other-apps.html, 2)
            s.letGoText()?.let { Text(it, color = p.dangerText, fontSize = 13.sp) }
            // against the previous session of this workout (mockup intervals-next-features.html, 3C)
            if (s.values.isNotEmpty()) app.intervals.core.valuesChange(s, before)?.let { (text, dir) ->
                val day = before?.let { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }
                Text(
                    (when (dir) { 1 -> "▲ "; -1 -> "▼ "; else -> "" }) + text + (day?.let { " ($it)" } ?: ""),
                    color = when (dir) { 1 -> Color(0xFF4ADE80); -1 -> p.workText; else -> p.muted }, fontSize = 12.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.note.isBlank()) "+ note" else "“${s.note}”",
                    color = if (s.note.isBlank()) p.accentText else p.muted, fontSize = 13.sp,
                    fontStyle = if (s.note.isBlank()) FontStyle.Normal else FontStyle.Italic, modifier = Modifier.weight(1f),
                )
                // the workout's logged values over time (mockup intervals-ideas-from-other-apps.html, 3)
                if (onProgress != null && s.values.any { it.text == null }) Text(
                    "Progress ›", color = p.accentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onProgress).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The gym sets of a day as one card: each exercise as bars, then the notes. Tap = open in Gym. */
@Composable
private fun GymDayCard(
    sets: List<app.intervals.core.GymSet>, exercises: Map<Long, app.intervals.core.Exercise>,
    groups: List<app.intervals.core.GymGroup>, records: Map<Long, String?>, note: String = "", onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val byExercise = sets.sortedBy { it.order }.groupBy { it.exerciseId }
    val strip = app.intervals.core.dayStrip(sets, groups)
    val dayGroups = app.intervals.core.dayGroups(sets, groups)
    val bar = strip.map { Color(it.color) }.distinct().ifEmpty { listOf(p.accent) }
    val prs = sets.filter { it.id in records }.map { it.exerciseId }.distinct().size
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable(onClick = onOpen).padding(10.dp),
    ) {
        // left bar in the colors of the groups trained
        Column(Modifier.width(5.dp).height(64.dp).clip(RoundedCornerShape(3.dp))) {
            bar.forEach { Box(Modifier.fillMaxWidth().weight(1f).background(it)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gym" + if (dayGroups.isEmpty()) "" else " · " + dayGroups.joinToString(" · ") { it.name },
                    color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${sets.size} set${if (sets.size == 1) "" else "s"}" + if (prs > 0) " · $prs PR" else "", color = p.muted, fontSize = 12.sp)
            }
            // the day strip, labeled: one piece per exercise, sized by sets
            if (strip.isNotEmpty()) Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                strip.forEach { piece ->
                    Box(Modifier.weight(piece.sets.toFloat()).height(22.dp).background(Color(piece.color)).padding(horizontal = 5.dp), contentAlignment = Alignment.CenterStart) {
                        Text(short(exercises[piece.exerciseId]?.name ?: "?") + " " + piece.sets,
                            color = if (Color(piece.color).luminance() > 0.35f) Color(0xFF0B0C0E) else Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
            byExercise.forEach { (id, exSets) ->
                val ex = exercises[id] ?: return@forEach
                ExerciseOfDay(ex, exSets, records)
            }
            // the day note; a set's note shows when its bar is tapped
            if (note.isNotBlank()) Text("“$note”", color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
        }
    }
}

/** One exercise of the day (mockup intervals-journal-day-ideas.html): its name and sets, then the bars (C2). */
@Composable
private fun ExerciseOfDay(ex: Exercise, sets: List<GymSet>, records: Map<Long, String?>) {
    val p = LocalPalette.current
    // tapping a bar picks that set: the line above says where it sits in the day
    var picked by remember(sets) { mutableStateOf<Int?>(null) }
    val pick = { n: Int -> picked = if (picked == n) null else n }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ex.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val n = picked
            val line = if (n != null) setLine(sets, n, ex, records) else {
                val real = sets.filterNot { it.isWarmup }
                // "top" only with a real weight (at 0 kg it says nothing), and the bands are the bars' colors, not words
                val top = app.intervals.core.topSet(real, ex)?.takeIf { app.intervals.core.GymField.WEIGHT in ex.fields && (it.weightKg ?: 0.0) != 0.0 }
                val warm = sets.size - real.size
                "${real.size} set${if (real.size == 1) "" else "s"}" + (if (warm > 0) " + $warm warm-up" else "") +
                    (top?.let { " · top " + it.withoutColors(ex).summary(ex) } ?: "")
            }
            Text(line, color = if (n != null) p.accentText else p.muted, fontSize = 11.sp, maxLines = 1)
        }
        DayBars(app.intervals.core.dayBars(sets, ex, records.keys, records.filterValues { it != null }.keys), picked, pick)
    }
}

/** The line for a tapped set [n] (1-based): "#3 of 5 · “elbow a bit sore”" when it has a note, else its place; " · PR" on a record. */
private fun setLine(sets: List<GymSet>, n: Int, ex: Exercise, records: Map<Long, String?>): String {
    val s = sets[n - 1]
    val text = if (s.note.isNotBlank()) "#$n of ${sets.size} · ${s.summary(ex)} · “${s.note.trim()}”" else setPlace(sets, n, ex)
    if (s.id !in records) return text
    // a record still the best, or one a later day beat (mockup intervals-record-marks.html, A)
    return text + (records[s.id]?.let { " · was a PR until " + java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) } ?: " · PR, best now")
}

/** "#4 of 11 · after 30 × 5 · before 37.5 × 5": where set [n] (1-based, day order) sits in the day. */
private fun setPlace(sets: List<GymSet>, n: Int, ex: Exercise): String =
    "#$n of ${sets.size}" + (sets.getOrNull(n - 2)?.let { " · after " + it.summary(ex) } ?: "") + (sets.getOrNull(n)?.let { " · before " + it.summary(ex) } ?: "")

/**
 * A bar per set in the order done, always as high as its reps (mockup intervals-journal-bar-marks.html, 5 + N2 + P4).
 * Plain bars are purple; a per-side set has L or R written at the top inside it. With bands the bar is the band colors,
 * striped like elastic, with a light outline (black drawn dark grey, so it shows on the dark card). The kg sit inside at the foot, only on the
 * first bar of each weight. A set with a note has its top-right corner folded; a record has a gold cup over its reps.
 */
@Composable
internal fun DayBars(bars: List<app.intervals.core.DayBar>, picked: Int?, onPick: ((Int) -> Unit)?, rests: List<Int?>? = null) {
    val p = LocalPalette.current
    val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: return
    val barWidth = if (bars.any { it.under.isNotEmpty() }) 30.dp else 24.dp
    val ink = { c: Color -> if (c.luminance() > 0.35f) Color(0xFF0B0C0E) else Color.White }
    val shown = { c: Long -> Color(c).let { if (it.luminance() < 0.03f) Color(0xFF3A3D44) else it } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, b ->
            val on = picked == i + 1
            // a warm-up is only a dashed outline (mockup intervals-ideas-from-other-apps.html, 5)
            val fills = if (b.warmup) listOf(Color.Transparent) else b.colors.map(shown).ifEmpty { listOf(p.accent) }
            val side = when (b.side) { app.intervals.core.Side.LEFT -> "L"; app.intervals.core.Side.RIGHT -> "R"; null -> null }
            val kg = b.under.takeIf { it.isNotEmpty() && it != bars.getOrNull(i - 1)?.under }
            Column(Modifier.weight(1f, fill = false).width(barWidth).then(if (onPick != null) Modifier.clickable { onPick(i + 1) } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                // gold for the best now, grey for a record beaten since
                if (b.record) Icon(AppIcons.Cup, if (b.beaten) "Past record" else "Record", tint = if (b.beaten) p.faint else p.yellow, modifier = Modifier.padding(bottom = 1.dp).size(11.dp))
                Text(b.top, color = if (b.warmup) p.muted else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                // short bars still fit the L / R letter at the top and the kg at the foot, one above the other (in sp, so they grow with the font size)
                val room = with(density) { (if (side != null && kg != null) 27.sp else if (side != null) 14.sp else if (kg != null) 15.sp else 0.sp).toDp().value }
                val height = maxOf(6 + 40 * b.value / max, room.toDouble())
                val border = when {
                    on -> 2.dp to p.text
                    b.colors.isNotEmpty() -> 1.5.dp to p.muted
                    else -> 1.dp to p.line
                }
                val noteMark = p.text
                val dashColor = if (on) p.text else p.muted
                Box(
                    Modifier.fillMaxWidth().height(height.dp).clip(shape)
                        .then(if (b.warmup) Modifier else Modifier.border(border.first, border.second, shape))
                        .drawWithContent {
                            drawContent()
                            if (b.warmup) {
                                val w = (if (on) 2.dp else 1.dp).toPx()
                                drawRect(dashColor, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                            }
                            if (b.note) {
                                val c = 7.dp.toPx()
                                drawPath(Path().apply { moveTo(size.width - c, 0f); lineTo(size.width, 0f); lineTo(size.width, c); close() }, noteMark)
                            }
                        },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        fills.forEach { Box(Modifier.fillMaxWidth().weight(1f).background(it)) }
                    }
                    // a band bar is striped like elastic (mockup section 5, D), so the purple band is not a plain bar
                    if (b.colors.isNotEmpty()) Box(Modifier.matchParentSize().drawBehind {
                        val gap = 6.dp.toPx()
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(Color.White.copy(alpha = 0.32f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                            x += gap
                        }
                    })
                    if (side != null) Text(
                        side, color = if (b.warmup) p.muted else ink(fills.first()), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                    )
                    if (kg != null) Text(
                        kg, color = if (b.warmup) p.muted else ink(fills.last()), fontSize = 9.sp, lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, letterSpacing = (-0.2).sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                    )
                }
                // History: the rest before the set under its bar, yellow over 5 min
                if (rests != null) {
                    val r = rests.getOrNull(i)?.takeIf { it > 0 }
                    Text(r?.let { app.intervals.core.formatTime(it) } ?: " ", color = if ((r ?: 0) > 300) p.recordText else p.muted,
                        fontSize = 8.sp, letterSpacing = (-0.2).sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Short tag of an exercise for the strip: initials of its words ("Barbell Deep Squat" → DS), or the first letters of one word. */
private fun short(name: String): String {
    val skip = setOf("barbell", "dumbbell", "bar", "the", "with", "and", "left", "right", "hand")
    val words = name.split(' ', '(', ')', '-').map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in skip }
    return when {
        words.isEmpty() -> name.take(2).uppercase()
        words.size == 1 -> words[0].take(3).replaceFirstChar { it.uppercase() }
        else -> words.take(3).joinToString("") { it.first().uppercase() }
    }
}
