package com.ryzix.player.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityMusicPlayerBinding
import com.ryzix.player.service.PlayerService

@OptIn(UnstableApi::class)
class MusicPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_URIS      = "music_playlist_uris"
        const val EXTRA_PLAYLIST_TITLES    = "music_playlist_titles"
        const val EXTRA_PLAYLIST_ARTISTS   = "music_playlist_artists"
        const val EXTRA_PLAYLIST_ALBUM_IDS = "music_playlist_album_ids"
        const val EXTRA_PLAYLIST_INDEX     = "music_playlist_index"
    }

    private lateinit var binding: ActivityMusicPlayerBinding

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private var playlistUris:     List<String> = emptyList()
    private var playlistTitles:   List<String> = emptyList()
    private var playlistArtists:  List<String> = emptyList()
    private var playlistAlbumIds: List<String> = emptyList()
    private var startIndex = 0

    private var isShuffle = false
    private var isRepeat  = false
    private var isSeekbarTracking = false

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: notification shows anyway on grant */ }

    private val positionRunnable = object : Runnable {
        override fun run() {
            val ctrl = controller ?: return
            if (!isSeekbarTracking) {
                val pos = ctrl.currentPosition
                binding.seekBar.progress = pos.toInt()
                binding.tvPosition.text  = formatMs(pos)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playlistUris     = intent.getStringArrayListExtra(EXTRA_PLAYLIST_URIS)     ?: emptyList()
        playlistTitles   = intent.getStringArrayListExtra(EXTRA_PLAYLIST_TITLES)   ?: emptyList()
        playlistArtists  = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ARTISTS)  ?: emptyList()
        playlistAlbumIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ALBUM_IDS) ?: emptyList()
        startIndex       = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0)

        if (playlistUris.isEmpty()) { finish(); return }

        requestNotificationPermission()
        connectToService()
        setupControls()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlayerService::class.java)
        )
        controllerFuture = MediaController.Builder(this, sessionToken)
            .buildAsync()

        controllerFuture!!.addListener({
            try {
                controller = controllerFuture!!.get()
                onControllerReady()
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onControllerReady() {
        val ctrl = controller ?: return

        val items = playlistUris.mapIndexed { i, uriStr ->
            val title      = playlistTitles.getOrElse(i)  { "" }
            val artist     = playlistArtists.getOrElse(i) { "" }
            val albumIdStr = playlistAlbumIds.getOrElse(i) { "" }
            val artUri     = if (albumIdStr.isNotEmpty())
                Uri.parse("content://media/external/audio/albumart/$albumIdStr") else null
            MediaItem.Builder()
                .setUri(uriStr)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(artUri)
                        .build()
                ).build()
        }

        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.prepare()
        ctrl.playWhenReady = true

        ctrl.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    binding.seekBar.max = ctrl.duration.toInt().coerceAtLeast(1)
                    binding.tvDuration.text = formatMs(ctrl.duration)
                    handler.post(positionRunnable)
                }
                updatePlayPauseIcon()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateTrackInfo()
            }
        })

        updateTrackInfo()
    }

    private fun updateTrackInfo() {
        val ctrl = controller ?: return
        val idx = ctrl.currentMediaItemIndex

        val title      = playlistTitles.getOrElse(idx)   { "Unknown" }
        val artist     = playlistArtists.getOrElse(idx)  { "Unknown Artist" }
        val albumIdStr = playlistAlbumIds.getOrElse(idx) { "" }

        binding.tvSongTitle.text  = title
        binding.tvArtistName.text = artist

        if (albumIdStr.isNotEmpty()) {
            val artUri = Uri.parse("content://media/external/audio/albumart/$albumIdStr")
            binding.imgAlbumArt.load(artUri) {
                crossfade(true)
                error(R.drawable.ic_music_note)
                listener(
                    onSuccess = { _, _ -> binding.imgAlbumArtFallback.visibility = View.GONE },
                    onError   = { _, _ -> binding.imgAlbumArtFallback.visibility = View.VISIBLE }
                )
            }
            binding.imgBgArt.load(artUri) { crossfade(true) }
        } else {
            binding.imgAlbumArtFallback.visibility = View.VISIBLE
        }

        binding.seekBar.progress = 0
        binding.tvPosition.text  = "0:00"
        binding.tvDuration.text  = "0:00"
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        binding.btnNext.setOnClickListener {
            controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
        }

        binding.btnPrevious.setOnClickListener {
            controller?.let {
                if (it.currentPosition < 3_000L && it.hasPreviousMediaItem()) {
                    it.seekToPreviousMediaItem()
                } else {
                    it.seekTo(0L)
                }
            }
        }

        binding.btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            controller?.shuffleModeEnabled = isShuffle
            binding.btnShuffle.alpha = if (isShuffle) 1.0f else 0.4f
        }

        binding.btnRepeat.setOnClickListener {
            isRepeat = !isRepeat
            controller?.repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            binding.btnRepeat.alpha = if (isRepeat) 1.0f else 0.4f
        }

        binding.btnFavorite.setOnClickListener {
            binding.btnFavorite.setImageResource(
                if (binding.btnFavorite.tag == "liked") {
                    binding.btnFavorite.tag = null
                    R.drawable.ic_favorite_border
                } else {
                    binding.btnFavorite.tag = "liked"
                    R.drawable.ic_favorite_border
                }
            )
        }

        binding.btnMoreOptions.setOnClickListener {
            val idx   = controller?.currentMediaItemIndex ?: 0
            val title = playlistTitles.getOrElse(idx) { "Track" }
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(arrayOf("Share", "Info")) { _, _ -> }
                .show()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) binding.tvPosition.text = formatMs(p.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) { isSeekbarTracking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = false
                controller?.seekTo(sb.progress.toLong())
            }
        })
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (controller?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun formatMs(ms: Long): String {
        if (ms < 0) return "0:00"
        val m = ms / 1000 / 60
        val s = ms / 1000 % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(positionRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(positionRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { finish() }
}
