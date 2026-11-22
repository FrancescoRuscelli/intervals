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
 * Month calendar (Monday first) with a dot per session (pink = completed, yellow = stopped),
 * month totals, and the sessions of the selected day with their logged values and note.
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

@Composable
private fun JournalCalendar(vm: AppViewModel) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    // one calendar page per month, swipe or arrows; the last page is this month
    val firstMonth = remember { YearMonth.of(2000, 1) }
    val monthCount = remember(today) { ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(today)).toInt() + 1 }
    val pager = rememberPagerState(initialPage = monthCount - 1) { monthCount }
    val scope = rememberCoroutineScope()
    val month = firstMonth.plusMonths(pager.currentPage.toLong())
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDay = LocalDate.parse(dayText)
    val (loadedSessions, loadedGymSets) = monthEntries(vm, month, zone)
    val byDay = remember(loadedSessions, zone) { loadedSessions.orEmpty().groupBy { it.day(zone) } }
    val gymDates = remember(loadedGymSets) { loadedGymSets.orEmpty().map { LocalDate.parse(it.day) }.toSet() }
    val trainedDays = byDay.keys + gymDates
    var filter by rememberSaveable { mutableStateOf("") }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val closeFilter: () -> Unit = {
        keyboard?.hide()
        filterOpen = false
    }
    BackHandler(enabled = filterOpen && vm.overlays.isEmpty(), onBack = closeFilter)
    val gymOfDay = key(dayText) {
        remember { vm.gymDay(dayText) }.collectAsState(initial = emptyList()).value
    }
    val gymNote = key(dayText) {
        remember { vm.gymDayNote(dayText) }.collectAsState(initial = "").value
    }
    val showGymCard = gymOfDay.isNotEmpty() || gymNote.isNotBlank()
    val exercises by vm.exercises.collectAsState()
    val allGroups by vm.groups.collectAsState()
    // records of the selected day need each exercise's history up to that day
    val dayIds = gymOfDay.map { it.exerciseId }.toSet()
    val history by remember(dayText, dayIds) {
        if (dayIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else vm.gymSetsBetween("0000-01-01", LocalDate.parse(dayText).plusDays(1).toString())
    }.collectAsState(initial = emptyList())
    val records = remember(history, dayText, exercises, dayIds) {
        app.intervals.core.recordsOn(dayText, history.filter { it.exerciseId in dayIds }, exercises.filter { it.id in dayIds })
    }
    // swiped to another month: select its last trained day (or today / its last day), once its data is loaded
    LaunchedEffect(month, loadedSessions, loadedGymSets) {
        if (vm.journalFocusDay != null || YearMonth.from(selectedDay) == month || loadedSessions == null || loadedGymSets == null) {
            return@LaunchedEffect
        }
        dayText = (trainedDays.maxOrNull() ?: if (month == YearMonth.from(today)) today else month.atEndOfMonth()).toString()
    }
    // Opened for a specific date.
    LaunchedEffect(vm.journalFocusDay) {
        val d = vm.journalFocusDay?.let(LocalDate::parse) ?: return@LaunchedEffect
        pager.scrollToPage(ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(d)).toInt().coerceIn(0, monthCount - 1))
        dayText = d.toString()
        vm.journalFocusDay = null
    }
    var editing by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }
    var dayMenu by remember(dayText) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        item {
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
        }
        if (filterOpen) item {
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
        item {
            MonthTotals(loadedSessions.orEmpty().size + gymDates.size, trainedDays.size, loadedGymSets.orEmpty().size)
        }
        item {
            HorizontalPager(pager, key = { it }, verticalAlignment = Alignment.Top,
                flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1))) { page ->
                val pageMonth = firstMonth.plusMonths(page.toLong())
                val entries = if (pageMonth == month) loadedSessions to loadedGymSets else monthEntries(vm, pageMonth, zone)
                MonthCalendar(pageMonth, today, selectedDay, zone, entries.first.orEmpty(), entries.second.orEmpty(), exercises, allGroups, filter) {
                    dayText = it.toString()
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedDay.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)),
                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                if (selectedDay <= today) {
                    val running = dayText in vm.activeGroups
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
                                    if (running) vm.openGymDay(dayText) else vm.openGymPick(dayText)
                                },
                            )
                        }
                    }
                }
            }
        }
        val ofDay = byDay[selectedDay].orEmpty().sortedBy { it.startedAt }
        if (showGymCard) item(key = "gym") {
            GymDayCard(gymOfDay, exercises.associateBy { it.id }, allGroups, records, note = gymNote) { vm.openGymDay(dayText) }
            Spacer(Modifier.height(6.dp))
        }
        if (ofDay.isEmpty() && !showGymCard) item {
            Text(
                if (selectedDay == today) "No sessions today." else "No sessions this day.",
                color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp),
            )
        }
        items(ofDay, key = { it.id }) { s ->
            SessionCard(s, zone, onNote = { editing = s }, onLongPress = { deleting = s })
            Spacer(Modifier.height(6.dp))
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

@Composable
private fun MonthTotals(sessions: Int, days: Int, sets: Int) {
    val p = LocalPalette.current
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

@Composable
private fun monthEntries(vm: AppViewModel, month: YearMonth, zone: ZoneId): Pair<List<Session>?, List<GymSet>?> = key(month, zone) {
    val start = month.atDay(1)
    val end = month.plusMonths(1).atDay(1)
    val sessions by remember {
        vm.sessions(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
    }.collectAsState(initial = null)
    val gymSets by remember { vm.gymSetsBetween(start.toString(), end.toString()) }.collectAsState(initial = null)
    sessions to gymSets
}

@Composable
private fun MonthCalendar(
    month: YearMonth, today: LocalDate, selected: LocalDate, zone: ZoneId,
    sessions: List<Session>, gymSets: List<GymSet>, exercises: List<Exercise>, groups: List<GymGroup>,
    filter: String, onPick: (LocalDate) -> Unit,
) {
    val query = filter.trim()
    val matchingExercises = remember(exercises, groups, query) {
        if (query.isEmpty()) emptySet() else exercises.filter { it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet() +
            groups.filter { it.isTag && it.name.contains(query, ignoreCase = true) }.flatMap { it.items }.map { it.exerciseId }
    }
    val highlighted = remember(sessions, gymSets, matchingExercises, query, zone) {
        if (query.isEmpty()) emptySet() else buildSet {
            addAll(gymSets.filter { it.exerciseId in matchingExercises }.map { LocalDate.parse(it.day) })
            addAll(sessions.filter { it.workoutName.contains(query, ignoreCase = true) }.map { it.day(zone) })
        }
    }
    val strips = remember(gymSets, groups) {
        gymSets.groupBy { LocalDate.parse(it.day) }.mapValues { (_, s) -> app.intervals.core.dayStrip(s, groups) }
    }
    Calendar(month, today, selected, sessions.groupBy { it.day(zone) }, strips, highlighted, query.isNotEmpty(), onPick)
}

@Composable
private fun Calendar(month: YearMonth, today: LocalDate, selected: LocalDate, byDay: Map<LocalDate, List<Session>>, gym: Map<LocalDate, List<app.intervals.core.StripPiece>>, highlighted: Set<LocalDate>, filtering: Boolean, onPick: (LocalDate) -> Unit) {
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
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
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
