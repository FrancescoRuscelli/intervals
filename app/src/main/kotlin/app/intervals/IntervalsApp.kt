package app.intervals

import android.app.Application
import app.intervals.data.IntervalsDatabase
import app.intervals.data.IntervalsRepository

/** Holds the one database and repository of the app; opened on first use, not at startup. */
class IntervalsApp : Application() {
    val repository: IntervalsRepository by lazy { IntervalsRepository(IntervalsDatabase.create(this)) }
}
