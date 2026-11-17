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
        BackHandler(enabled = vm.overlays.isNotEmpty()) { vm.back() }

        Box(Modifier.fillMaxSize().background(p.bg)) {
            Scaffold(
                containerColor = p.bg,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = { if (vm.overlays.isEmpty()) BottomBar(vm.tab) { vm.tab = it } },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    when (vm.tab) {
                        Tab.INTERVALS -> IntervalsListScreen(vm)
                        Tab.GYM -> ComingSoon("Gym", "The strength log comes after the interval screens.")
                        Tab.JOURNAL -> ComingSoon("Journal", "Sessions appear here once the player is ready.")
                        Tab.STATS -> ComingSoon("Stats", "Coming with the journal.")
                        Tab.SETTINGS -> ComingSoon("Settings", "Coming in this step.")
                    }
                }
            }
            // screens on top of the tabs
            when (val top = vm.overlays.lastOrNull()) {
                Overlay.EditWorkout -> EditWorkoutScreen(vm)
                is Overlay.EditSeries -> EditSeriesScreen(vm, top)
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

@Composable
private fun ComingSoon(title: String, text: String) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text, color = p.muted, fontSize = 15.sp)
    }
}
