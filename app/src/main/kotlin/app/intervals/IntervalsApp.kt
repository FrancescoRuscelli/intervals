package app.intervals

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.intervals.data.IntervalsDatabase
import app.intervals.data.IntervalsRepository
import app.intervals.player.FinishedInfo
import app.intervals.player.PlayerController
import kotlinx.coroutines.MainScope

/** Holds the one database and repository of the app; opened on first use, not at startup. */
class IntervalsApp : Application() {
    val repository: IntervalsRepository by lazy { IntervalsRepository(IntervalsDatabase.create(this)) }

    /** Lives as long as the app process: a running workout must not stop when the screen goes away. */
    val appScope = MainScope()

    /** The workout running now (null = none). Kept here, not in a screen, so it survives the activity. */
    var player by mutableStateOf<PlayerController?>(null)
    /** A finished run waiting to be saved. */
    var finished by mutableStateOf<FinishedInfo?>(null)
}
