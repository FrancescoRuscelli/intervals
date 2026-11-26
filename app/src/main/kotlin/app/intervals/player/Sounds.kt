package app.intervals.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voice and beeps of the player. Text-to-speech starts when the player opens and is released when it closes.
 * Other apps' music is lowered only while a cue plays (if [duck]).
 */
class Sounds(context: Context, private val volume: Float, private val duck: Boolean, language: String, endSound: String? = null) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale.forLanguageTag(language)
    }
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, (volume * 100).toInt().coerceIn(1, 100)) }.getOrNull()
    private val attrs = AudioAttributes.Builder()
        // like a navigator: media volume, still heard on silent (sonification is muted then, so the voice was never heard)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    private val release = Runnable { audio.abandonAudioFocusRequest(focus) }

    // the phone sound picked in Settings, or the app's own: Android's fallback ring (AOSP, Apache 2.0), the one Hybrid Interval Timer plays
    private val phoneSound = (endSound ?: "android.resource://${context.packageName}/${app.intervals.R.raw.end_tone}").let { u ->
        runCatching { android.media.RingtoneManager.getRingtone(context, android.net.Uri.parse(u))?.apply { audioAttributes = attrs; volume = this@Sounds.volume } }.getOrNull()
    }
    private val stopPhoneSound = Runnable { runCatching { phoneSound?.stop() } }

    init {
        tts.setAudioAttributes(attrs)
    }

    private fun duckFor(ms: Long) {
        if (!duck) return
        handler.removeCallbacks(release)
        audio.requestAudioFocus(focus)
        handler.postDelayed(release, ms)
    }

    fun say(text: String) {
        if (!ttsReady) return
        duckFor(1800)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }, text)
    }

    /** Short beep for 3, 2; a longer, higher one for 1. */
    fun beep(n: Int) {
        duckFor(600)
        if (n == 1) tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) else tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /** A segment is over (each hang, rest, round rest): the end sound, unlike the countdown beeps. */
    fun segmentDone() {
        duckFor(1500)
        phoneSound?.let { r ->
            runCatching { r.stop(); r.play() }
            // a long ringtone is cut: only its start marks the end of the set
            handler.removeCallbacks(stopPhoneSound); handler.postDelayed(stopPhoneSound, 2500)
        }
    }

    fun finished() {
        duckFor(2500)
        tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        if (ttsReady) tts.speak("Well done", TextToSpeech.QUEUE_ADD, null, "done")
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        audio.abandonAudioFocusRequest(focus)
        tts.stop()
        tts.shutdown()
        tones?.release()
        runCatching { phoneSound?.stop() }
    }
}
