package app.intervals.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.intervals.IntervalsApp
import app.intervals.MainActivity
import app.intervals.R
import app.intervals.core.PlayState
import app.intervals.core.SegmentKind
import app.intervals.core.Side

/**
 * Keeps a running workout alive with the screen off: foreground service + partial wake lock,
 * only while a workout runs. Its notification shows the current series with a live countdown
 * (drawn by the system, no updates every second) and Pause / Next buttons, also on the lock screen.
 */
class PlayerService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as IntervalsApp
        when (intent?.action) {
            ACTION_PAUSE -> app.player?.togglePause()
            ACTION_NEXT -> app.player?.next()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        val n = build(this, app.player)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "intervals:player")
                .apply { acquire(4 * 60 * 60 * 1000L) } // safety limit: 4 h
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "player"
        private const val ID = 1
        const val ACTION_PAUSE = "pause"
        const val ACTION_NEXT = "next"
        const val ACTION_STOP = "stop"

        fun start(context: Context) {
            ensureChannel(context)
            context.startForegroundService(Intent(context, PlayerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlayerService::class.java))
        }

        /** Refresh the notification (new segment, pause / resume). */
        fun update(context: Context, c: PlayerController) {
            val nm = context.getSystemService(NotificationManager::class.java)
            runCatching { nm.notify(ID, build(context, c)) }
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Workout running", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "The current series and its countdown while a workout runs"
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                )
            }
        }

        private fun action(context: Context, action: String, code: Int) = PendingIntent.getService(
            context, code, Intent(context, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun label(st: PlayState): String = when (st.segment.kind) {
            SegmentKind.WARMUP -> "Warm-up"
            SegmentKind.CHANGE_SIDE -> "Change side"
            SegmentKind.ROUND_REST -> "Rest between rounds"
            SegmentKind.SERIES -> st.segment.name + when (st.segment.side) { Side.LEFT -> " · left"; Side.RIGHT -> " · right"; null -> "" }
        }

        private fun build(context: Context, c: PlayerController?): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val b = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            val st = c?.plan?.stateAt(c.elapsedMs)
            if (c == null || st == null) return b.setContentTitle("Intervals").build()
            b.setContentTitle(label(st))
                .setSubText(c.saved.workout.name)
                .setContentText("Round ${st.round} of ${st.rounds}" + if (c.paused) " · paused" else "")
            if (!c.paused) {
                val endMs = st.segment.endSec * 1000L - c.elapsedMs
                b.setWhen(System.currentTimeMillis() + endMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            } else {
                b.setShowWhen(false)
            }
            b.addAction(Notification.Action.Builder(null, if (c.paused) "Resume" else "Pause", action(context, ACTION_PAUSE, 1)).build())
            b.addAction(Notification.Action.Builder(null, "Next", action(context, ACTION_NEXT, 2)).build())
            return b.build()
        }
    }
}
