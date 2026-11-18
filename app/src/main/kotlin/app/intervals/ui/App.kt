package app.intervals.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        LaunchedEffect(vm.message) {
            vm.message?.let { snack.showSnackbar(it); vm.message = null }
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

        Box(Modifier.fillMaxSize().background(p.bg)) {
            Scaffold(
                containerColor = p.bg,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = { if (vm.overlays.isEmpty()) BottomBar(vm.tab) { vm.tab = it } },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    when (vm.tab) {
                        Tab.INTERVALS -> IntervalsListScreen(vm)
                        Tab.GYM -> app.intervals.ui.gym.GymScreen(vm)
                        Tab.JOURNAL -> app.intervals.ui.journal.JournalScreen(vm)
                        Tab.STATS -> app.intervals.ui.stats.StatsScreen(vm)
                        Tab.SETTINGS -> app.intervals.ui.settings.SettingsScreen(vm)
                    }
                }
            }
            // screens on top of the tabs
            when (val top = vm.overlays.lastOrNull()) {
                Overlay.EditWorkout -> EditWorkoutScreen(vm)
                is Overlay.EditSeries -> EditSeriesScreen(vm, top)
                Overlay.Player -> vm.player?.let { app.intervals.player.PlayerScreen(it, settings.keepScreenOn) { it.stop() } }
                is Overlay.GymLog -> app.intervals.ui.gym.GymLogScreen(vm, top.exerciseId, top.day)
                is Overlay.GymPick -> app.intervals.ui.gym.GymPickScreen(vm, top.day)
                is Overlay.EditExercise -> app.intervals.ui.gym.EditExerciseScreen(vm, top.exerciseId)
                is Overlay.EditGroup -> app.intervals.ui.gym.EditGroupScreen(vm, top.groupId)
                Overlay.GymImport -> vm.gymImport?.let { app.intervals.ui.gym.GymImportScreen(it, { vm.cancelGymImport() }, { vm.confirmGymImport() }) }
                Overlay.Finished -> vm.finished?.let { app.intervals.player.FinishedScreen(it, onDiscard = { vm.discardSession() }) { note, values, repeat -> vm.saveSession(note, values, repeat) } }
                null -> Unit
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    NavigationBar(containerColor = p.sheet, tonalElevation = 0.dp) {
        val items: List<Triple<Tab, String, ImageVector>> = listOf(
            Triple(Tab.INTERVALS, "Intervals", AppIcons.Intervals),
            Triple(Tab.GYM, "Gym", AppIcons.Gym),
            Triple(Tab.JOURNAL, "Journal", AppIcons.Journal),
            Triple(Tab.STATS, "Stats", AppIcons.Stats),
            Triple(Tab.SETTINGS, "Settings", AppIcons.Settings),
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
