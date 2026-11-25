package app.intervals.core

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What the disc does at every new series (it always inflates; the end of the workout is always a coin flip). */
@Serializable
enum class DiscAnimation { CALM, CRAZY_SPIN }

/** Everything on the Settings screen. Defaults are what a fresh install shows. */
@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 0xAARRGGBB; purple by default. */
    val accent: Long = 0xFF7C3AED,
    val sayNames: Boolean = true,
    /** BCP 47 language tag for the voice. */
    val voiceLanguage: String = "en",
    val beepLast3: Boolean = true,
    /** 0..1 */
    val volume: Float = 0.8f,
    /** Lower other apps' music during cues. */
    val duckMusic: Boolean = true,
    val discAnimation: DiscAnimation = DiscAnimation.CALM,
    /** Disc spin 0..4 (none, a little, more, crazy, always); -1 = not set yet, taken from [discAnimation]. */
    val spin: Int = -1,
    val keepScreenOn: Boolean = true,
    /** Player: small "3/6" (work series) and "2/4" (rounds) chips on the rings. */
    val showCounters: Boolean = true,
    val lockControls: Boolean = false,
) {
    init {
        require(volume in 0f..1f) { "volume must be in 0..1" }
    }
}

/** How much the disc spins, 0 = no spin … 4 = always spinning (older settings: Calm = 0, Crazy spin = 3). */
val AppSettings.spinLevel: Int get() = if (spin in 0..4) spin else if (discAnimation == DiscAnimation.CRAZY_SPIN) 3 else 0

val SPIN_LABELS = listOf("No spin", "A little", "More", "Crazy", "Always")
