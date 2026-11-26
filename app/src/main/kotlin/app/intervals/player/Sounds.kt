package app.intervals.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

    // 880 Hz for 0.8 s, quick fade in and a long fade out: the Android tones are short or sound like a phone
    private val endTone = runCatching {
        val rate = 44100
        val n = rate * 8 / 10
        val pcm = ShortArray(n) { i ->
            val env = minOf(1.0, i / (rate * 0.01), (n - i).toDouble() / (n * 0.6))
            (Math.sin(2 * Math.PI * 880 * i / rate) * env * volume * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
            .also { it.write(pcm, 0, n) }
    }.getOrNull()

    // the phone sound picked in Settings, on the media volume like the voice
    private val phoneSound = endSound?.let { u ->
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

    /** A segment is over (each hang, rest, round rest): a long, clean tone, unlike the countdown beeps. */
    fun segmentDone() {
        duckFor(1500)
        phoneSound?.let { r ->
            runCatching { r.stop(); r.play() }
            // a long ringtone is cut: only its start marks the end of the set
            handler.removeCallbacks(stopPhoneSound); handler.postDelayed(stopPhoneSound, 2500)
            return
        }
        val t = endTone ?: return
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
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
        endTone?.release()
        runCatching { phoneSound?.stop() }
    }
}
