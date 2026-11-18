package app.intervals.player

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.intervals.core.AppSettings
import app.intervals.core.Block
import app.intervals.core.SavedWorkout
import app.intervals.core.Series
import app.intervals.core.Workout
import java.time.Duration
import kotlinx.coroutines.MainScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerControllerTest {
    @Test
    fun pauseCountsUpWhileWorkoutTimeIsFrozen() {
        val workout = Workout("Intervals", blocks = listOf(Block.Single(Series("Work", 60, 0xFF663399))))
        val settings = AppSettings(sayNames = false, beepLast3 = false, duckMusic = false)
        val sounds = Sounds(ApplicationProvider.getApplicationContext(), settings.volume, settings.duckMusic, settings.voiceLanguage)
        val player = PlayerController(SavedWorkout(1, 0, workout, 0, 0), settings, sounds, MainScope(),
            onEnd = { _, _, _, _ -> error("Workout finished early") })
        val main = shadowOf(Looper.getMainLooper())

        try {
            player.start()
            main.idleFor(Duration.ofSeconds(2))
            assertEquals(2_000L, player.elapsedMs)

            player.togglePause()
            assertTrue(player.paused)
            assertEquals(0, player.pauseSeconds)
            main.idleFor(Duration.ofMillis(3_200))
            assertEquals(3, player.pauseSeconds)
            assertEquals(2_000L, player.elapsedMs)

            player.togglePause()
            assertFalse(player.paused)
            main.idleFor(Duration.ofSeconds(1))
            assertEquals(3_000L, player.elapsedMs)
            player.togglePause()
            assertEquals(0, player.pauseSeconds)
        } finally {
            player.close()
        }
    }
}
