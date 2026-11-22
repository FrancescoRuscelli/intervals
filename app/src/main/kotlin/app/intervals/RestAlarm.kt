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
    private const val CHANNEL = "rest"
    private const val ID = 2
    const val ACTION_BUZZ = "app.intervals.REST_BUZZ"
    const val ACTION_STOP = "app.intervals.REST_STOP"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_START = "start"
    private const val EXTRA_TARGET = "target"

    /** Stop tapped in the notification: the app hides its clock too. */
    val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A rest started at [startedAt] (elapsedRealtime): shows it and arms the buzz at [targetSec] (none when 0). */
    fun start(context: Context, title: String, startedAt: Long, targetSec: Int) {
        val wallStart = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAt)
        notify(context, title, wallStart, targetSec, ready = false)
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(buzzIntent(context, title, wallStart, targetSec))
        if (targetSec <= 0) return
        val at = startedAt + targetSec * 1000L
        val pi = buzzIntent(context, title, wallStart, targetSec)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(buzzIntent(context, "", 0, 0))
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    internal fun buzz(context: Context, intent: Intent) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        notify(context, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getLongExtra(EXTRA_START, 0), intent.getIntExtra(EXTRA_TARGET, 0), ready = true)
    }

    private fun buzzIntent(context: Context, title: String, wallStart: Long, targetSec: Int) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, RestReceiver::class.java).setAction(ACTION_BUZZ)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_START, wallStart).putExtra(EXTRA_TARGET, targetSec),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, title: String, wallStart: Long, targetSec: Int, ready: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Gym rest", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The rest clock between gym sets"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, 2, Intent(context, RestReceiver::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val target = formatTime(targetSec)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle((if (ready) "Ready" else "Resting") + if (title.isEmpty()) "" else " · $title")
            .setContentText(if (targetSec <= 0) "Rest clock" else if (ready) "$target passed" else "Buzz at $target")
            .setWhen(wallStart).setShowWhen(true).setUsesChronometer(true)
            .setColor(0xFF00BFA5.toInt())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // a forgotten rest goes away by itself
            .setTimeoutAfter(maxOf(0L, wallStart + targetSec * 1000L - System.currentTimeMillis()) + 15 * 60_000L)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .addAction(Notification.Action.Builder(null, "Open", open).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestAlarm.ACTION_BUZZ -> RestAlarm.buzz(context, intent)
            RestAlarm.ACTION_STOP -> {
                RestAlarm.cancel(context)
                RestAlarm.stops.tryEmit(Unit)
            }
        }
    }
}
