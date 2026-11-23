@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.intervals.ui.journal

import androidx.activity.compose.BackHandler
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
import app.intervals.core.JournalSets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
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
    var stats by rememberSaveable { mutableStateOf(false) }
    val calendarState = rememberSaveableStateHolder()
    LaunchedEffect(vm.journalFocusDay) {
        if (vm.journalFocusDay != null) stats = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Journal", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.openSettings() }) { Icon(AppIcons.Settings, "Settings", tint = p.text) }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(p.surface).selectableGroup().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Calendar", "Stats").forEachIndexed { i, label ->
                val on = (i == 1) == stats
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.accent else Color.Transparent)
                        .selectable(on, role = Role.Tab) { stats = i == 1 },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (stats) app.intervals.ui.stats.StatsScreen(vm, showTitle = false)
        else calendarState.SaveableStateProvider("calendar") { JournalCalendar(vm) }
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
            MonthTotals(totals[month])
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
                if (ids.isEmpty()) 0 else app.intervals.core.recordsOn(d.toString(), allSets.orEmpty().filter { it.exerciseId in ids }, exercises.filter { it.id in ids })
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                DayEntry(
                    vm, d, today, sets, byDay[d].orEmpty().sortedBy { it.startedAt }, notes[d.toString()].orEmpty(),
                    exercises, allGroups, records, zone, closeFilter, onNote = { editing = it }, onDelete = { deleting = it },
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
    exercises: List<Exercise>, groups: List<GymGroup>, records: Int, zone: ZoneId, closeFilter: () -> Unit,
    onNote: (Session) -> Unit, onDelete: (Session) -> Unit,
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
            val mode = vm.settings.collectAsState().value.journalSets
            GymDayCard(sets, exercises.associateBy { it.id }, groups, records, note = note, mode = mode) { vm.openGymDay(day) }
            Spacer(Modifier.height(6.dp))
        }
        if (sessions.isEmpty() && sets.isEmpty() && note.isBlank()) {
            Text(
                if (d == today) "No sessions today." else "No sessions this day.",
                color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        sessions.forEach { s ->
            SessionCard(s, zone, onNote = { onNote(s) }, onLongPress = { onDelete(s) })
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MonthTotals(t: Triple<Int, Int, Int>?) {
    val p = LocalPalette.current
    val (sessions, days, sets) = t ?: Triple(0, 0, 0)
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
        color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
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
private fun SessionCard(s: Session, zone: ZoneId, onNote: () -> Unit, onLongPress: () -> Unit) {
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
            Text(
                if (s.note.isBlank()) "+ note" else "“${s.note}”",
                color = if (s.note.isBlank()) p.accentText else p.muted, fontSize = 13.sp,
                fontStyle = if (s.note.isBlank()) FontStyle.Normal else FontStyle.Italic,
            )
        }
    }
}

/** The gym sets of a day as one card: each exercise as bars and / or its table by load ([mode]), then the notes. Tap = open in Gym. */
@Composable
private fun GymDayCard(
    sets: List<app.intervals.core.GymSet>, exercises: Map<Long, app.intervals.core.Exercise>,
    groups: List<app.intervals.core.GymGroup>, records: Int, note: String = "", mode: JournalSets = JournalSets.BOTH, onOpen: () -> Unit,
) {
    val p = LocalPalette.current
    val byExercise = sets.sortedBy { it.order }.groupBy { it.exerciseId }
    val strip = app.intervals.core.dayStrip(sets, groups)
    val dayGroups = app.intervals.core.dayGroups(sets, groups)
    val bar = strip.map { Color(it.color) }.distinct().ifEmpty { listOf(p.accent) }
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
                Text("${sets.size} set${if (sets.size == 1) "" else "s"}" + if (records > 0) " · $records PR" else "", color = p.muted, fontSize = 12.sp)
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
                ExerciseOfDay(ex, exSets, mode)
            }
            (listOf(note) + sets.map { it.note }).filter { it.isNotBlank() }.distinct().forEach {
                Text("“$it”", color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
            }
        }
    }
}

/** One exercise of the day (mockup intervals-journal-day-ideas.html): its name and sets, then the bars (C2) and / or the table by load (A2). */
@Composable
private fun ExerciseOfDay(ex: Exercise, sets: List<GymSet>, mode: JournalSets) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ex.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val top = app.intervals.core.topSet(sets, ex)?.takeIf { app.intervals.core.GymField.WEIGHT in ex.fields }
            Text("${sets.size} set${if (sets.size == 1) "" else "s"}" + (top?.let { " · top " + it.summary(ex) } ?: ""), color = p.muted, fontSize = 11.sp, maxLines = 1)
        }
        if (mode != JournalSets.TABLE) DayBars(app.intervals.core.dayBars(sets, ex))
        if (mode != JournalSets.BARS) LoadTable(app.intervals.core.loadRows(sets, ex))
    }
}

/** A bar per set in the order done, always as high as its reps; the band colors fill it, the side is a cap (L pink, R green), the kg are under it. */
@Composable
private fun DayBars(bars: List<app.intervals.core.DayBar>) {
    val p = LocalPalette.current
    val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: return
    Row(Modifier.fillMaxWidth().height(78.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            Column(Modifier.weight(1f, fill = false).width(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(b.top, color = if (b.note) p.yellow else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                Column(Modifier.fillMaxWidth().height((6 + 40 * b.value / max).dp).clip(shape).border(1.dp, p.line, shape)) {
                    b.side?.let { Box(Modifier.fillMaxWidth().height(3.dp).background(if (it == app.intervals.core.Side.LEFT) p.pink else p.teal)) }
                    if (b.colors.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f).background(p.accent))
                    b.colors.forEach { Box(Modifier.fillMaxWidth().weight(1f).background(Color(it))) }
                }
                Text(b.under, color = p.muted, fontSize = 9.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The sets by load, lightest first: the load (or its band colors) on the left, the reps in boxes with their number in the day's order. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LoadTable(rows: List<app.intervals.core.LoadRow>) {
    val p = LocalPalette.current
    rows.forEach { r ->
        Row(verticalAlignment = Alignment.Top) {
            Row(Modifier.width(70.dp).padding(top = 9.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                r.colors.forEach { Box(Modifier.size(11.dp).clip(CircleShape).background(Color(it)).border(1.dp, Color(0x55FFFFFF), CircleShape)) }
                if (r.label.isNotEmpty() || r.colors.isEmpty()) {
                    Text(r.label.ifEmpty { "–" }, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.End)
                }
            }
            androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                r.sets.forEach { (n, s) ->
                    val tint = when (s.side) {
                        app.intervals.core.Side.LEFT -> p.pink.copy(alpha = 0.25f)
                        app.intervals.core.Side.RIGHT -> p.teal.copy(alpha = 0.25f)
                        null -> p.bg
                    }
                    Box(Modifier.padding(top = 4.dp, start = 4.dp)) {
                        Box(Modifier.height(24.dp).widthIn(min = 24.dp).clip(RoundedCornerShape(6.dp)).background(tint).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                            Text(s.reps?.toString() ?: s.timeSec?.let { app.intervals.core.formatTime(it) } ?: "✓", color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        // the set's number in the day; yellow when it has a note
                        Text("$n", color = if (s.note.isNotBlank()) p.yellow else p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp).clip(RoundedCornerShape(4.dp)).background(p.surface).padding(horizontal = 2.dp))
                    }
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
