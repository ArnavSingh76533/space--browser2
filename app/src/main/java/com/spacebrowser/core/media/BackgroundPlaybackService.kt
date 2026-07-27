package com.spacebrowser.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.spacebrowser.MainActivity
import com.spacebrowser.R
import com.spacebrowser.SpaceApp
import com.spacebrowser.core.browser.MediaCommand

/**
 * Keeps an explicitly playing WebView alive after the activity leaves the
 * foreground and exposes standard notification/headset media controls.
 */
class BackgroundPlaybackService : Service() {

    private lateinit var mediaSession: MediaSession
    private var title: String = "SPACE media"
    private var playing: Boolean = true

    private val tabManager get() = (application as SpaceApp).container.tabManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "SPACE Web Media").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = run(MediaCommand.Play)
                    override fun onPause() = run(MediaCommand.Pause)
                    override fun onSkipToNext() = run(MediaCommand.Next)
                    override fun onSkipToPrevious() = run(MediaCommand.Previous)
                    override fun onSeekTo(pos: Long) =
                        run(MediaCommand.SeekTo(pos.coerceAtLeast(0L) / 1000.0))
                },
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
        playing = intent?.getBooleanExtra(EXTRA_PLAYING, playing) ?: playing
        when (intent?.action) {
            ACTION_PLAY -> run(MediaCommand.Play)
            ACTION_PAUSE -> run(MediaCommand.Pause)
            ACTION_NEXT -> run(MediaCommand.Next)
            ACTION_PREVIOUS -> run(MediaCommand.Previous)
            ACTION_STOP -> {
                run(MediaCommand.Pause)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, notification())
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun run(command: MediaCommand) {
        tabManager.runMediaCommand(command) { success ->
            if (success) {
                playing = command !is MediaCommand.Pause
                updatePlaybackState()
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification())
            }
        }
    }

    private fun updatePlaybackState() {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build(),
        )
    }

    private fun notification(): Notification {
        updatePlaybackState()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val previous = action("Previous", android.R.drawable.ic_media_previous, ACTION_PREVIOUS)
        val toggle = if (playing) {
            action("Pause", android.R.drawable.ic_media_pause, ACTION_PAUSE)
        } else {
            action("Play", android.R.drawable.ic_media_play, ACTION_PLAY)
        }
        val next = action("Next", android.R.drawable.ic_media_next, ACTION_NEXT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Playing from SPACE Browser")
            .setContentIntent(openApp)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(previous)
            .addAction(toggle)
            .addAction(next)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(label: String, iconRes: Int, action: String): Notification.Action {
        val pending = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, BackgroundPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            label,
            pending,
        ).build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background media",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Controls media playing in SPACE Browser"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "space_background_media"
        private const val NOTIFICATION_ID = 2204
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PLAYING = "playing"
        private const val ACTION_PLAY = "com.spacebrowser.media.PLAY"
        private const val ACTION_PAUSE = "com.spacebrowser.media.PAUSE"
        private const val ACTION_NEXT = "com.spacebrowser.media.NEXT"
        private const val ACTION_PREVIOUS = "com.spacebrowser.media.PREVIOUS"
        private const val ACTION_STOP = "com.spacebrowser.media.STOP"

        fun start(context: Context, title: String) {
            val intent = Intent(context, BackgroundPlaybackService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PLAYING, true)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundPlaybackService::class.java))
        }
    }
}
