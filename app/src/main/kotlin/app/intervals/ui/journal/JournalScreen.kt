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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
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
 * month totals, and below it the diary of every day trained, newest first.
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
 * The month on top and the diary below (mockup intervals-journal-month-diary.html, option 2): scrolling the diary shrinks the
 * month to the week being read; pulling down at the top, or tapping the month name, opens it again. Swiping the month moves the diary.
 */
@Composable
private fun JournalCalendar(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    // one calendar page per month, swipe or arrows; the last page is this month
    val firstMonth = remember { YearMonth.of(2000, 1) }
    val monthCount = remember(today) { ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(today)).toInt() + 1 }
    val pageOf = { d: LocalDate -> ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(d)).toInt().coerceIn(0, monthCount - 1) }
    val pager = rememberPagerState(initialPage = monthCount - 1) { monthCount }
    val feed = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val month = firstMonth.plusMonths(pager.currentPage.toLong())
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDay = LocalDate.parse(dayText)
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<LocalDate?>(null) }

    // the whole history, like the records already read it
    val allSessions by remember { vm.sessions(0, Long.MAX_VALUE) }.collectAsState(initial = null)
    val allSets by remember { vm.gymSetsBetween("0000-01-01", "9999-12-31") }.collectAsState(initial = null)
    val notes by remember { vm.gymDayNotes() }.collectAsState(initial = emptyMap())
    val exercises by vm.exercises.collectAsState()
    val allGroups by vm.groups.collectAsState()
    val byDay = remember(allSessions, zone) { allSessions.orEmpty().groupBy { it.day(zone) } }
    val setsByDay = remember(allSets) { allSets.orEmpty().groupBy { LocalDate.parse(it.day) } }
    val strips = remember(setsByDay, allGroups) { setsByDay.mapValues { (_, s) -> app.intervals.core.dayStrip(s, allGroups) } }
    val noteOf = { d: LocalDate -> notes[d.toString()].orEmpty() }

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
    val rows = remember(byDay, setsByDay, notes, today, selectedDay, matching) {
        app.intervals.core.diaryRows(byDay.keys + setsByDay.keys + notes.filterValues { it.isNotBlank() }.keys.map(LocalDate::parse), today, selectedDay, matching)
    }
    val totals = remember(byDay, setsByDay) {
        (byDay.keys + setsByDay.keys).groupBy { YearMonth.from(it) }.mapValues { (_, days) ->
            Triple(days.sumOf { byDay[it].orEmpty().size + if (it in setsByDay) 1 else 0 }, days.size, days.sumOf { setsByDay[it].orEmpty().size })
        }
    }
    // the day at the top of the diary: the week shown when the month is shrunk
    val readingDay by remember(rows) { derivedStateOf { rows.getOrNull(feed.firstVisibleItemIndex)?.day } }

    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val closeFilter: () -> Unit = {
        keyboard?.hide()
        filterOpen = false
    }
    BackHandler(enabled = filterOpen && vm.overlays.isEmpty(), onBack = closeFilter)

    // reading another month in the diary: the calendar follows, so opening it shows that month
    LaunchedEffect(feed, rows) {
        snapshotFlow { readingDay }.collect { d -> if (d != null && feed.isScrollInProgress && pageOf(d) != pager.currentPage) pager.scrollToPage(pageOf(d)) }
    }
    // swiped or stepped to another month: the diary goes to its newest day (an empty month leaves it where it is)
    LaunchedEffect(pager.settledPage) {
        val m = firstMonth.plusMonths(pager.settledPage.toLong())
        if (readingDay?.let(YearMonth::from) == m) return@LaunchedEffect
        val i = rows.indexOfFirst { YearMonth.from(it.day) == m }
        if (i >= 0) feed.scrollToItem(i)
    }
    // a picked day: the diary scrolls to it once it is in the rows
    LaunchedEffect(jumpTo, rows) {
        val d = jumpTo ?: return@LaunchedEffect
        val i = rows.indexOfFirst { it is app.intervals.core.DiaryDay && it.day == d }
        if (i >= 0) {
            feed.animateScrollToItem(i)
            jumpTo = null
        }
    }
    // Opened for a specific date.
    LaunchedEffect(vm.journalFocusDay) {
        val d = vm.journalFocusDay?.let(LocalDate::parse) ?: return@LaunchedEffect
        pager.scrollToPage(pageOf(d))
        dayText = d.toString()
        jumpTo = d
        vm.journalFocusDay = null
    }
    val pick = { d: LocalDate ->
        dayText = d.toString()
        jumpTo = d
    }
    // scrolling the diary down shrinks the month; pulling down once it is at the top opens it again
    val shrink = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) collapsed = true
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 2f) collapsed = false
                return Offset.Zero
            }
        }
    }
    var editing by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    Column(Modifier.fillMaxSize().nestedScroll(shrink)) {
        Column(Modifier.padding(horizontal = 12.dp).animateContentSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val active = filter.isNotBlank()
                IconButton(onClick = { if (filterOpen) closeFilter() else filterOpen = true }) {
                    Box {
                        Icon(
                            AppIcons.Search,
                            if (active) "Filter journal, active filter" else "Filter journal",
                            tint = if (active || filterOpen) p.accentText else p.muted,
                        )
                        if (active) Box(Modifier.align(Alignment.TopEnd).size(5.dp).clip(CircleShape).background(p.accent))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Icon(AppIcons.Back, "Previous month", tint = p.text) }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year + if (collapsed) " ▾" else " ▴",
                    color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show the month" else "Show only the week") { collapsed = !collapsed }
                        .padding(vertical = 6.dp),
                )
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
                    filter, { filter = it }, placeholder = { Text("Filter by exercise or tag") }, singleLine = true,
                    trailingIcon = if (filter.isNotEmpty()) ({
                        IconButton(onClick = { filter = "" }) { Icon(AppIcons.Close, "Clear filter") }
                    }) else null,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                )
            }
            if (collapsed) {
                Calendar(month, today, selectedDay, byDay, strips, matching.orEmpty(), matching != null, pick, weekOf = readingDay ?: selectedDay)
            } else {
                MonthTotals(totals[month])
                HorizontalPager(pager, key = { it }, verticalAlignment = Alignment.Top,
                    flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1))) { page ->
                    Calendar(firstMonth.plusMonths(page.toLong()), today, selectedDay, byDay, strips, matching.orEmpty(), matching != null, pick)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = feed, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
            items(rows, key = { r -> (if (r is app.intervals.core.DiaryMonth) "m" else "d") + r.day }) { r ->
                if (r is app.intervals.core.DiaryMonth) {
                    Column(Modifier.padding(top = 10.dp, bottom = 6.dp, start = 4.dp)) {
                        Text(r.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase() + " " + r.month.year,
                            color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        MonthTotals(totals[r.month])
                    }
                } else {
                    val d = r.day
                    val sets = setsByDay[d].orEmpty()
                    val records = remember(d, allSets, exercises) {
                        val ids = sets.map { it.exerciseId }.toSet()
                        if (ids.isEmpty()) 0 else app.intervals.core.recordsOn(d.toString(), allSets.orEmpty().filter { it.exerciseId in ids }, exercises.filter { it.id in ids })
                    }
                    DiaryDayEntry(
                        vm, d, today, picked = d == selectedDay, sets, byDay[d].orEmpty().sortedBy { it.startedAt }, noteOf(d),
                        exercises, allGroups, records, zone, closeFilter, onNote = { editing = it }, onDelete = { deleting = it },
                    )
                }
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

/** One day of the diary: its date with the day menu, the gym card, the interval sessions. The picked day has an accent edge. */
@Composable
private fun DiaryDayEntry(
    vm: AppViewModel, d: LocalDate, today: LocalDate, picked: Boolean, sets: List<GymSet>, sessions: List<Session>, note: String,
    exercises: List<Exercise>, groups: List<GymGroup>, records: Int, zone: ZoneId, closeFilter: () -> Unit,
    onNote: (Session) -> Unit, onDelete: (Session) -> Unit,
) {
    val p = LocalPalette.current
    val day = d.toString()
    var dayMenu by remember(day) { mutableStateOf(false) }
    Column(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(2.dp, if (picked) p.accent else Color.Transparent, RoundedCornerShape(18.dp)).padding(if (picked) 4.dp else 0.dp),
    ) {
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

/** The month (Monday first), or only the week of [weekOf] when the month is shrunk. */
@Composable
private fun Calendar(
    month: YearMonth, today: LocalDate, selected: LocalDate, byDay: Map<LocalDate, List<Session>>,
    gym: Map<LocalDate, List<app.intervals.core.StripPiece>>, highlighted: Set<LocalDate>, filtering: Boolean,
    onPick: (LocalDate) -> Unit, weekOf: LocalDate? = null,
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
        val weeks = if (weekOf != null) {
            val monday = weekOf.minusDays(weekOf.dayOfWeek.value - 1L)
            listOf((0L..6L).map { monday.plusDays(it) })
        } else {
            val lead = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
            (List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }).chunked(7)
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d -> DayCell(d, today, selected, byDay[d].orEmpty(), gym[d].orEmpty(), d in highlighted, filtering, onPick, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate?, today: LocalDate, selected: LocalDate, sessions: List<Session>, strip: List<app.intervals.core.StripPiece>, highlighted: Boolean, filtering: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier) {
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

/** The gym sets of a day as one card: each exercise with its rows ("80 × 5 ×3, 85 × 3"), then the notes. Tap = open in Gym. */
@Composable
private fun GymDayCard(
    sets: List<app.intervals.core.GymSet>, exercises: Map<Long, app.intervals.core.Exercise>,
    groups: List<app.intervals.core.GymGroup>, records: Int, note: String = "", onOpen: () -> Unit,
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
                val rows = app.intervals.core.dayRows(exSets).joinToString(", ") { r ->
                    (if (r.set.side != null) r.label + " " else "") + r.set.summary(ex) + if (r.count > 1) " ×${r.count}" else ""
                }
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = p.text, fontWeight = FontWeight.SemiBold)) { append(ex.name) }
                    append("  $rows")
                }, color = p.soft, fontSize = 13.sp)
            }
            (listOf(note) + sets.map { it.note }).filter { it.isNotBlank() }.distinct().forEach {
                Text("“$it”", color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
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
