package com.ryzix.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ryzix.player.R

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    companion object {
        const val CHANNEL_ID       = "ryzix_music_channel"
        const val NOTIFICATION_ID  = 1001

        val COMMAND_TOGGLE_SHUFFLE = SessionCommand("ryzix.TOGGLE_SHUFFLE", Bundle.EMPTY)
        val COMMAND_TOGGLE_REPEAT  = SessionCommand("ryzix.TOGGLE_REPEAT",  Bundle.EMPTY)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttrs, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Custom notification actions: Shuffle + Repeat shown in expanded view
        val shuffleBtn = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setSessionCommand(COMMAND_TOGGLE_SHUFFLE)
            .setIconResId(R.drawable.ic_shuffle)
            .setEnabled(true)
            .build()

        val repeatBtn = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setSessionCommand(COMMAND_TOGGLE_REPEAT)
            .setIconResId(R.drawable.ic_repeat)
            .setEnabled(true)
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setCustomLayout(ImmutableList.of(shuffleBtn, repeatBtn))
            .setCallback(SessionCallbackHandler())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player ?: return
        if (!p.playWhenReady
            || p.mediaItemCount == 0
            || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    // ── Session callback — handles custom commands from notification ───────

    private inner class SessionCallbackHandler : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(COMMAND_TOGGLE_SHUFFLE)
                .add(COMMAND_TOGGLE_REPEAT)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_TOGGLE_SHUFFLE.customAction -> {
                    player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                }
                COMMAND_TOGGLE_REPEAT.customAction -> {
                    player?.let {
                        it.repeatMode = when (it.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else                   -> Player.REPEAT_MODE_OFF
                        }
                    }
                }
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // ── Notification channel ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ryzix music playback controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
