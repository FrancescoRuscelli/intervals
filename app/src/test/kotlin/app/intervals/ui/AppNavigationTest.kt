package app.intervals.ui

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.intervals.IntervalsApp
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(application = IntervalsApp::class)
class AppNavigationTest {
    @Test
    fun journalIsHomeAndGymDayReturnsToItsOrigin() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<IntervalsApp>())
        val day = "2026-11-19"
        try {
            assertEquals(Tab.JOURNAL, vm.tab)
            vm.openGymDay(day)
            assertEquals(Tab.GYM, vm.tab)
            assertEquals(day, vm.gymDayRoute?.day)
            vm.closeGymDay()
            assertEquals(Tab.JOURNAL, vm.tab)
            assertEquals(day, vm.journalFocusDay)
            assertNull(vm.gymDayRoute)

            vm.selectTab(Tab.GYM)
            vm.openGymDay(day)
            vm.closeGymDay()
            assertEquals(Tab.GYM, vm.tab)
            assertNull(vm.gymDayRoute)
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}
