package com.ryzix.player.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
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
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import coil.transform.BlurTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityMusicPlayerBinding

@OptIn(UnstableApi::class)
class MusicPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_URIS     = "music_playlist_uris"
        const val EXTRA_PLAYLIST_TITLES   = "music_playlist_titles"
        const val EXTRA_PLAYLIST_ARTISTS  = "music_playlist_artists"
        const val EXTRA_PLAYLIST_ALBUM_IDS = "music_playlist_album_ids"
        const val EXTRA_PLAYLIST_INDEX    = "music_playlist_index"
    }

    private lateinit var binding: ActivityMusicPlayerBinding

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var playlistUris:    List<String> = emptyList()
    private var playlistTitles:  List<String> = emptyList()
    private var playlistArtists: List<String> = emptyList()
    private var playlistAlbumIds: List<String> = emptyList()
    private var currentIndex = 0

    private var isShuffle = false
    private var isRepeat  = false
    private var isSeekbarTracking = false

    private val positionRunnable = object : Runnable {
        override fun run() {
            val exo = player ?: return
            if (!isSeekbarTracking) {
                val pos = exo.currentPosition
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

        playlistUris     = intent.getStringArrayListExtra(EXTRA_PLAYLIST_URIS)    ?: emptyList()
        playlistTitles   = intent.getStringArrayListExtra(EXTRA_PLAYLIST_TITLES)  ?: emptyList()
        playlistArtists  = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ARTISTS) ?: emptyList()
        playlistAlbumIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ALBUM_IDS) ?: emptyList()
        currentIndex     = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0)

        if (playlistUris.isEmpty()) { finish(); return }

        setupPlayer()
        setupControls()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->

            val items = playlistUris.mapIndexed { i, uriStr ->
                val title   = playlistTitles.getOrElse(i)  { "" }
                val artist  = playlistArtists.getOrElse(i) { "" }
                val albumIdStr = playlistAlbumIds.getOrElse(i) { "" }
                val artUri  = if (albumIdStr.isNotEmpty())
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

            exo.setMediaItems(items, currentIndex, 0L)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        binding.seekBar.max = exo.duration.toInt().coerceAtLeast(1)
                        binding.tvDuration.text = formatMs(exo.duration)
                        handler.post(positionRunnable)
                    }
                    updatePlayPauseIcon()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentIndex = exo.currentMediaItemIndex
                    updateTrackInfo()
                }
            })
        }
        updateTrackInfo()
    }

    private fun updateTrackInfo() {
        val title   = playlistTitles.getOrElse(currentIndex)  { "Unknown" }
        val artist  = playlistArtists.getOrElse(currentIndex) { "Unknown Artist" }
        val albumIdStr = playlistAlbumIds.getOrElse(currentIndex) { "" }

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
            binding.imgBgArt.load(artUri) {
                crossfade(true)
                transformations(BlurTransformation(this@MusicPlayerActivity, 25f))
            }
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
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        binding.btnNext.setOnClickListener {
            player?.let {
                if (it.hasNextMediaItem()) it.seekToNextMediaItem()
            }
        }

        binding.btnPrevious.setOnClickListener {
            player?.let {
                if ((it.currentPosition < 3_000L) && it.hasPreviousMediaItem()) {
                    it.seekToPreviousMediaItem()
                } else {
                    it.seekTo(0L)
                }
            }
        }

        binding.btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            player?.shuffleModeEnabled = isShuffle
            binding.btnShuffle.alpha = if (isShuffle) 1.0f else 0.4f
        }

        binding.btnRepeat.setOnClickListener {
            isRepeat = !isRepeat
            player?.repeatMode = if (isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
            val title = playlistTitles.getOrElse(currentIndex) { "Track" }
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
                player?.seekTo(sb.progress.toLong())
            }
        })
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
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
        super.onDestroy()
        handler.removeCallbacks(positionRunnable)
        player?.release()
        player = null
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { finish() }
}
