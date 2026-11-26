package app.intervals

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.intervals.core.formatTime
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The gym rest outside the app (mockup intervals-rest-timer.html): a notification with the running clock and Stop,
 * and the buzz at the target from an alarm, so it comes even when the app was closed.
 */
object RestAlarm {
    /**
     * Normal importance (the old "rest" channel was low: Android put it under Silent, with no icon and nothing on the lock
     * screen). No sound of its own: the buzz and sound at the target are played in [buzz].
     */
    private const val CHANNEL = "rest_clock"
    private const val OLD_CHANNEL = "rest"
    private const val ID = 2
    const val ACTION_BUZZ = "app.intervals.REST_BUZZ"
    const val ACTION_STOP = "app.intervals.REST_STOP"
    const val ACTION_MORE = "app.intervals.REST_MORE"
    /** On the notification's open intent: the exercise and day to open (mockup intervals-ideas-from-other-apps.html, 4). */
    const val EXTRA_EXERCISE = "exercise"
    const val EXTRA_DAY = "day"
    private const val PREFS = "rest"

    /** Stop tapped in the notification: the app hides its clock too. */
    val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** "+30 s" tapped in the notification: the new target, seconds. */
    val targets = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /**
     * The rest running now, kept on the phone so it outlives the app (a rest across an app restart is still timed and saved).
     * [startedAt] is elapsedRealtime; [next] is the next set, e.g. "set 4 · L 16 × 3".
     */
    data class Saved(val title: String, val startedAt: Long, val targetSec: Int, val next: String, val exerciseId: Long, val day: String)

    fun saved(context: Context): Saved? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = sp.getLong("start", -1)
        // after a reboot elapsedRealtime starts again from 0: an older rest is gone
        if (start < 0 || start > SystemClock.elapsedRealtime()) return null
        return Saved(sp.getString("title", "").orEmpty(), start, sp.getInt("target", 0), sp.getString("next", "").orEmpty(),
            sp.getLong("exercise", 0), sp.getString("day", "").orEmpty())
    }

    private fun save(context: Context, r: Saved?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (r == null) e.clear() else e.putString("title", r.title).putLong("start", r.startedAt).putInt("target", r.targetSec)
            .putString("next", r.next).putLong("exercise", r.exerciseId).putString("day", r.day)
        e.apply()
    }

    /** A rest started at [startedAt] (elapsedRealtime): shows it and arms the buzz at [targetSec] (none when 0). */
    fun start(context: Context, title: String, startedAt: Long, targetSec: Int, next: String = "", exerciseId: Long = 0, day: String = "") {
        val r = Saved(title, startedAt, targetSec, next, exerciseId, day)
        save(context, r)
        val wallStart = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAt)
        val ready = targetSec > 0 && SystemClock.elapsedRealtime() >= startedAt + targetSec * 1000L
        notify(context, r, wallStart, ready)
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(buzzIntent(context))
        if (targetSec <= 0 || ready) return
        val at = startedAt + targetSec * 1000L
        val pi = buzzIntent(context)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
    }

    fun cancel(context: Context) {
        save(context, null)
        context.getSystemService(AlarmManager::class.java).cancel(buzzIntent(context))
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    internal fun buzz(context: Context) {
        val r = saved(context) ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
        // three buzzes and the phone's notification sound (silent when the phone is on silent)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        runCatching { android.media.RingtoneManager.getRingtone(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play() }
        notify(context, r, System.currentTimeMillis() - (SystemClock.elapsedRealtime() - r.startedAt), ready = true)
    }

    /** "+30 s": the target moves on and the buzz with it. */
    internal fun more(context: Context) {
        val r = saved(context) ?: return
        val target = maxOf(r.targetSec, ((SystemClock.elapsedRealtime() - r.startedAt) / 1000).toInt()) + 30
        start(context, r.title, r.startedAt, target, r.next, r.exerciseId, r.day)
        targets.tryEmit(target)
    }

    private fun buzzIntent(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, RestReceiver::class.java).setAction(ACTION_BUZZ),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The bar to the target (Android 16+): a teal segment as long as the target, filled by the time rested; full once ready.
     * Null without a target or on older Android (then the text style).
     */
    private fun progressStyle(r: Saved, ready: Boolean): Notification.Style? {
        if (Build.VERSION.SDK_INT < 36 || r.targetSec <= 0) return null
        val done = if (ready) r.targetSec else ((SystemClock.elapsedRealtime() - r.startedAt) / 1000).toInt().coerceIn(0, r.targetSec)
        return Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(r.targetSec).setColor(0xFF00BFA5.toInt())))
            .setProgress(done)
    }

    /** Redraws the bar (called every few seconds while a rest runs and the app is alive). False once there is nothing to update. */
    fun refresh(context: Context): Boolean {
        val r = saved(context) ?: return false
        if (r.targetSec <= 0 || Build.VERSION.SDK_INT < 36) return false
        val ready = SystemClock.elapsedRealtime() >= r.startedAt + r.targetSec * 1000L
        notify(context, r, System.currentTimeMillis() - (SystemClock.elapsedRealtime() - r.startedAt), ready)
        return !ready
    }

    private fun notify(context: Context, r: Saved, wallStart: Long, ready: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Gym rest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The rest clock between gym sets, also on the lock screen"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
            nm.deleteNotificationChannel(OLD_CHANNEL)
        }
        // a tap opens the exercise the rest is for
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_EXERCISE, r.exerciseId).putExtra(EXTRA_DAY, r.day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, 2, Intent(context, RestReceiver::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val more = PendingIntent.getBroadcast(
            context, 3, Intent(context, RestReceiver::class.java).setAction(ACTION_MORE), PendingIntent.FLAG_IMMUTABLE,
        )
        val target = formatTime(r.targetSec)
        val next = r.next.takeIf { it.isNotEmpty() }?.let { "Next: $it" }
        val line = listOfNotNull(if (r.targetSec <= 0) "Rest clock" else if (ready) "$target passed" else "Target $target", next).joinToString(" · ")
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle((if (ready) "Ready" else "Resting") + if (r.title.isEmpty()) "" else " · ${r.title}")
            .setContentText(line)
            .setStyle(progressStyle(r, ready) ?: Notification.BigTextStyle().bigText(line))
            .setWhen(wallStart).setShowWhen(true).setUsesChronometer(true)
            .setColor(0xFF00BFA5.toInt())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // a forgotten rest goes away by itself
            .setTimeoutAfter(maxOf(0L, wallStart + r.targetSec * 1000L - System.currentTimeMillis()) + 15 * 60_000L)
            .addAction(Notification.Action.Builder(null, "+30 s", more).build())
            // a Live Update (Android 16+, mockup intervals-rest-notification.html, B): the rest clock as a chip in the status bar,
            // pinned first in the shade and on the lock screen; Android keeps such cards uncolored
            .apply { if (Build.VERSION.SDK_INT >= 36) setRequestPromotedOngoing(true) }
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestAlarm.ACTION_BUZZ -> RestAlarm.buzz(context)
            RestAlarm.ACTION_MORE -> RestAlarm.more(context)
            RestAlarm.ACTION_STOP -> {
                RestAlarm.cancel(context)
                RestAlarm.stops.tryEmit(Unit)
            }
        }
    }
}
