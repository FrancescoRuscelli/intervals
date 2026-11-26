package app.intervals.ui

import app.intervals.core.spinLevel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intervals.ui.editor.EditWorkoutScreen
import app.intervals.ui.editor.EditSeriesScreen
import app.intervals.ui.list.IntervalsListScreen

@Composable
fun App(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    IntervalsTheme(settings.theme) {
        val p = LocalPalette.current
        val snack = remember { SnackbarHostState() }
        val tabState = rememberSaveableStateHolder()
        LaunchedEffect(vm.message) {
            vm.message?.let { m ->
                val undo = vm.undo
                vm.undo = null
                val r = snack.showSnackbar(m, actionLabel = if (undo != null) "Undo" else null, duration = androidx.compose.material3.SnackbarDuration.Short)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo?.invoke()
                vm.message = null
            }
        }
        // ask once for notifications (Android 13+): the lock-screen countdown and Pause / Next buttons
        val askNotif = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(vm.overlays.lastOrNull()) {
            if (vm.overlays.lastOrNull() == Overlay.Player && android.os.Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        BackHandler(enabled = vm.overlays.isNotEmpty() && vm.overlays.last() != Overlay.Player && vm.overlays.last() != Overlay.Finished) { vm.back() }
        // Back on Intervals or Gym goes to the Journal; only Back on the Journal leaves the app
        BackHandler(enabled = vm.overlays.isEmpty() && vm.tab != Tab.JOURNAL) { vm.selectTab(Tab.JOURNAL) }
        // a new screen or tab never keeps the keyboard of the one before (e.g. the Gym search under an exercise's log)
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(vm.overlays.size, vm.tab) { focus.clearFocus(force = true); keyboard?.hide() }

        Box(Modifier.fillMaxSize().background(p.bg)) {
            Scaffold(
                containerColor = p.bg,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = { if (vm.overlays.isEmpty()) BottomBar(vm.tab, vm::selectTab) },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    val selected = vm.tab
                    tabState.SaveableStateProvider(selected.name) {
                        when (selected) {
                            Tab.JOURNAL -> app.intervals.ui.journal.JournalScreen(vm)
                            Tab.INTERVALS -> IntervalsListScreen(vm)
                            Tab.GYM -> app.intervals.ui.gym.GymScreen(vm)
                        }
                    }
                }
            }
            // screens on top of the tabs: they take every touch, so a tap on their empty space never reaches the tab
            // underneath (missing the log screen's pencil opened the Gym tab's Settings, in the same corner)
            if (vm.overlays.isNotEmpty()) Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            })
            when (val top = vm.overlays.lastOrNull()) {
                Overlay.EditWorkout -> EditWorkoutScreen(vm)
                is Overlay.EditSeries -> EditSeriesScreen(vm, top)
                Overlay.Player -> vm.player?.let { app.intervals.player.PlayerScreen(it, settings.keepScreenOn, settings.spinLevel, settings.showCounters, settings.swipeDisc) { it.stop() } }
                is Overlay.GymLog -> app.intervals.ui.gym.GymLogScreen(vm, top.exerciseId, top.day, top.tab)
                is Overlay.Setup -> app.intervals.player.SetupScreen(vm, top.workoutId)
                Overlay.NewWorkout -> app.intervals.ui.list.NewWorkoutScreen(vm)
                is Overlay.Stats -> app.intervals.ui.stats.StatsScreen(vm, top.month)
                is Overlay.StatsList -> app.intervals.ui.stats.StatsListScreen(vm, top)
                is Overlay.WorkoutProgress -> app.intervals.ui.list.WorkoutProgressScreen(vm, top.workoutId)
                Overlay.Settings -> androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding(),
                ) {
                    androidx.compose.material3.IconButton(onClick = { vm.back() }, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                        androidx.compose.material3.Icon(AppIcons.Back, "Back", tint = p.text)
                    }
                    app.intervals.ui.settings.SettingsScreen(vm)
                }
                is Overlay.GymPick -> app.intervals.ui.gym.GymPickScreen(vm, top.day)
                is Overlay.EditExercise -> app.intervals.ui.gym.EditExerciseScreen(vm, top.exerciseId)
                is Overlay.EditGroup -> app.intervals.ui.gym.EditGroupScreen(vm, top.groupId, top.isTag, top.items)
                is Overlay.GymFinish -> app.intervals.ui.gym.GymFinishScreen(vm, top)
                Overlay.GymImport -> vm.gymImport?.let { app.intervals.ui.gym.GymImportScreen(it, { vm.cancelGymImport() }, { vm.confirmGymImport() }) }
                Overlay.Finished -> vm.finished?.let { app.intervals.player.FinishedScreen(it, onDiscard = { vm.discardSession() }) { note, values, repeat -> vm.saveSession(note, values, repeat) } }
                null -> Unit
            }
            // Back with the keyboard up only closes the keyboard. Keyed on it, so this handler is added last and comes
            // before every screen's own Back while the keyboard shows.
            val imeUp = imeVisible()
            androidx.compose.runtime.key(imeUp) {
                BackHandler(enabled = imeUp) { focus.clearFocus(force = true); keyboard?.hide() }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    NavigationBar(containerColor = p.sheet, tonalElevation = 0.dp) {
        val items: List<Triple<Tab, String, ImageVector>> = listOf(
            Triple(Tab.JOURNAL, "Journal", AppIcons.Journal),
            Triple(Tab.INTERVALS, "Intervals", AppIcons.Intervals),
            Triple(Tab.GYM, "Gym", AppIcons.Gym),
        )
        items.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(label, fontSize = 12.sp, fontWeight = if (tab == current) FontWeight.Bold else FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = p.accentText, selectedTextColor = p.accentText, indicatorColor = p.tint,
                    unselectedIconColor = p.muted, unselectedTextColor = p.muted,
                ),
            )
        }
    }
}

/** True while the keyboard is on screen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun imeVisible(): Boolean = androidx.compose.foundation.layout.WindowInsets.isImeVisible
