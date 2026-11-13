package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
import com.example.player.MusicPlayerManager

/**
 * バックグラウンド再生およびMediaSession管理を行う常駐サービス。
 * AndroidのForeground Service (mediaPlayback) として動作し、OSのLow Memory Killerから保護されます。
 * ロック画面通知およびメディアコントロールを完全サポート。
 */
class MusicPlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "revera_music_playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val provider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel_name)
                .build()
            setMediaNotificationProvider(provider)
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "Failed to set custom notification provider", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.playback_notification_channel_name)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ReveraMusicPod lock screen and media controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return MusicPlayerManager.activeSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = MusicPlayerManager.activeSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

