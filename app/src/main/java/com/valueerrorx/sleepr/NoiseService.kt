package com.valueerrorx.sleepr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/** Vordergrunddienst: hält die Wiedergabe bei gesperrtem Bildschirm am Laufen */
class NoiseService : Service() {

    companion object {
        const val ACTION_PLAY = "sleepr.PLAY"
        const val ACTION_STOP = "sleepr.STOP"
        const val ACTION_UPDATE = "sleepr.UPDATE"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 1

        @Volatile var engine: NoiseEngine? = null; private set
        val playing get() = engine?.running == true

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, NoiseService::class.java).setAction(action)
            if (action == ACTION_PLAY) ctx.startForegroundService(i) else if (playing) ctx.startService(i)
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Wiedergabe", NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSession(this, "sleepr").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = stopPlayback()
                override fun onStop() = stopPlayback()
                override fun onPlay() = startPlayback()
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback()
            ACTION_STOP -> stopPlayback()
            ACTION_UPDATE -> if (playing) showNotification()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        showNotification()
        if (playing) return
        Mixer.load(this)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopPlayback() }
            .build().also { audio.requestAudioFocus(it) }
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleepr:play").apply { acquire(12 * 60 * 60 * 1000L) }
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "sleepr")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, Mixer.summary()).build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE)
            .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        session.isActive = true
        engine = NoiseEngine { main.post { finish() } }.also { it.start() }
    }

    private fun stopPlayback() {
        val e = engine
        if (e == null || !e.running) finish() else e.stop() // finish() folgt nach dem Ausblenden
    }

    private fun finish() {
        if (engine?.running == true) return
        engine = null
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
        session.setPlaybackState(PlaybackState.Builder()
            .setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, NoiseService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("sleepr läuft")
            .setContentText(Mixer.summary() + if (Mixer.timer > 0) " · Timer ${Mixer.timer} min" else "")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stop), "Stopp", stop).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        engine?.stop()
        session.release()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
