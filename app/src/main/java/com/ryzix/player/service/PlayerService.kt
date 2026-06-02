package com.ryzix.player.service

  import android.content.Intent
  import androidx.annotation.OptIn
  import androidx.media3.common.AudioAttributes
  import androidx.media3.common.C
  import androidx.media3.common.Player
  import androidx.media3.common.util.UnstableApi
  import androidx.media3.exoplayer.ExoPlayer
  import androidx.media3.session.MediaSession
  import androidx.media3.session.MediaSessionService

  @OptIn(UnstableApi::class)
  class PlayerService : MediaSessionService() {

      private var mediaSession: MediaSession? = null
      private var player: ExoPlayer? = null

      override fun onCreate() {
          super.onCreate()
          val audioAttributes = AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
              .build()
          player = ExoPlayer.Builder(this)
              .setAudioAttributes(audioAttributes, true)
              .setHandleAudioBecomingNoisy(true)
              .build()
          mediaSession = MediaSession.Builder(this, player!!).build()
      }

      override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

      override fun onTaskRemoved(rootIntent: Intent?) {
          val player = mediaSession?.player
          if (player?.playWhenReady != true || player.mediaItemCount == 0 ||
              player.playbackState == Player.STATE_ENDED) stopSelf()
      }

      override fun onDestroy() {
          mediaSession?.run { player.release(); release() }
          mediaSession = null; player = null
          super.onDestroy()
      }
  }