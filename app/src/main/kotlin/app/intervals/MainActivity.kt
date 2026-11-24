package app.intervals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.intervals.ui.App
import app.intervals.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
        openFrom(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** The rest notification opens the exercise the rest is for (mockup intervals-ideas-from-other-apps.html, 4). */
    private fun openFrom(intent: android.content.Intent?) {
        val id = intent?.getLongExtra(RestAlarm.EXTRA_EXERCISE, 0L) ?: 0L
        val day = intent?.getStringExtra(RestAlarm.EXTRA_DAY).orEmpty()
        if (id <= 0L || day.isEmpty()) return
        intent?.removeExtra(RestAlarm.EXTRA_EXERCISE)
        vm.openRestExercise(id, day)
    }
}
