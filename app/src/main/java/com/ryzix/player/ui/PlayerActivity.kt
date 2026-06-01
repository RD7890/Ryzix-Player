package com.ryzix.player.ui

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityPlayerBinding
import com.ryzix.player.utils.GestureUtils
import com.ryzix.player.utils.MediaUtils
import com.ryzix.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_DURATION = "extra_duration"

        private val ASPECT_MODES = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null

    private lateinit var gestureDetector: GestureDetector
    private var gestureListener: GestureUtils.PlayerGestureListener? = null

    private var isSeekbarTracking = false
    private var currentUri: String = ""
    private var currentTitle: String = ""
    private var currentPath: String = ""
    private var totalDuration: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        currentUri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        currentPath = intent.getStringExtra(EXTRA_PATH) ?: ""
        totalDuration = intent.getLongExtra(EXTRA_DURATION, 0L)

        viewModel.currentVideoPath = currentPath
        viewModel.currentVideoTitle = currentTitle
        viewModel.currentDuration = totalDuration

        viewModel.init()

        binding.tvTitle.text = currentTitle.substringBeforeLast(".")

        // Auto-detect subtitle file
        val subPath = MediaUtils.findSubtitleFile(currentPath)
        if (subPath != null) {
            Toast.makeText(this, getString(R.string.subtitle_loaded), Toast.LENGTH_SHORT).show()
        }

        setupPlayer()
        setupControls()
        setupGestures()
        setupObservers()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            binding.playerView.useController = false

            lifecycleScope.launch {
                val resumePos = viewModel.getResumePosition()
                val mediaItem = MediaItem.fromUri(Uri.parse(currentUri))
                exo.setMediaItem(mediaItem, resumePos)
                exo.prepare()
                exo.playWhenReady = true
            }

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    updatePlayPauseButton(exo.isPlaying)
                    if (state == Player.STATE_READY) {
                        totalDuration = exo.duration
                        viewModel.currentDuration = totalDuration
                        binding.seekBar.max = totalDuration.toInt().coerceAtLeast(1)
                        binding.tvDuration.text = formatDuration(totalDuration)
                        viewModel.startPositionTracking(exo)
                    }
                    if (state == Player.STATE_ENDED) {
                        viewModel.savePosition(0L)
                        viewModel.showControls()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton(isPlaying)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > videoSize.height) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "${getString(R.string.playback_error)}: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })

            viewModel.playbackSpeed.observe(this) { speed ->
                exo.setPlaybackSpeed(speed)
            }
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
                viewModel.showControls()
            }
        }

        binding.btnRewind.setOnClickListener {
            player?.seekTo((player!!.currentPosition - 10_000L).coerceAtLeast(0L))
            viewModel.showControls()
            showSeekFeedback(-10)
        }

        binding.btnForward.setOnClickListener {
            player?.seekTo((player!!.currentPosition + 10_000L).coerceAtMost(totalDuration))
            viewModel.showControls()
            showSeekFeedback(10)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvPosition.text = formatDuration(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) { isSeekbarTracking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = false
                player?.seekTo(sb.progress.toLong())
            }
        })

        binding.btnLock.setOnClickListener { viewModel.toggleLock() }

        binding.btnAspect.setOnClickListener {
            val next = viewModel.nextAspectRatio()
            binding.playerView.resizeMode = ASPECT_MODES[next % ASPECT_MODES.size]
            val labels = resources.getStringArray(R.array.aspect_ratio_labels)
            Toast.makeText(this, labels[next % labels.size], Toast.LENGTH_SHORT).show()
        }

        binding.btnSpeed.setOnClickListener { showSpeedDialog() }

        binding.btnPip.setOnClickListener { enterPiP() }

        binding.btnRotate.setOnClickListener {
            requestedOrientation =
                if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupGestures() {
        val displayMetrics = resources.displayMetrics
        gestureListener = GestureUtils.PlayerGestureListener(
            context = this,
            window = window,
            screenWidth = displayMetrics.widthPixels,
            onSeekForward = {
                player?.seekTo((player!!.currentPosition + 10_000L).coerceAtMost(totalDuration))
                showSeekFeedback(10)
            },
            onSeekBackward = {
                player?.seekTo((player!!.currentPosition - 10_000L).coerceAtLeast(0L))
                showSeekFeedback(-10)
            },
            onSingleTap = { viewModel.toggleControls() },
            onBrightnessChange = { pct ->
                binding.tvBrightnessIndicator.text = "${(pct * 100).toInt()}%"
                binding.layoutBrightnessIndicator.visibility = View.VISIBLE
                binding.layoutBrightnessIndicator.postDelayed(
                    { binding.layoutBrightnessIndicator.visibility = View.GONE }, 1500
                )
            },
            onVolumeChange = { pct ->
                binding.tvVolumeIndicator.text = "${(pct * 100).toInt()}%"
                binding.layoutVolumeIndicator.visibility = View.VISIBLE
                binding.layoutVolumeIndicator.postDelayed(
                    { binding.layoutVolumeIndicator.visibility = View.GONE }, 1500
                )
            }
        )

        gestureDetector = GestureDetector(this, gestureListener!!)

        binding.playerView.setOnTouchListener { _, event ->
            if (viewModel.isLocked.value == true) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> gestureListener?.onTouchBegin(event)
                MotionEvent.ACTION_MOVE -> {
                    val prevX = if (event.historySize > 0) event.getHistoricalX(0) else event.x
                    val prevY = if (event.historySize > 0) event.getHistoricalY(0) else event.y
                    gestureListener?.onTouchMove(event, event.x - prevX, event.y - prevY)
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupObservers() {
        viewModel.currentPosition.observe(this) { pos ->
            if (!isSeekbarTracking) {
                binding.seekBar.progress = pos.toInt()
                binding.tvPosition.text = formatDuration(pos)
            }
        }

        viewModel.showControls.observe(this) { show ->
            val vis = if (show) View.VISIBLE else View.GONE
            binding.controlsTop.visibility = vis
            binding.controlsBottom.visibility = vis
            binding.controlsCenter.visibility = vis
        }

        viewModel.isLocked.observe(this) { locked ->
            binding.btnLock.setImageResource(
                if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open
            )
            val vis = if (locked) View.GONE else View.VISIBLE
            binding.controlsTop.visibility = vis
            binding.controlsBottom.visibility = vis
            binding.controlsCenter.visibility = vis
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x", "4.0x")
        val values = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
        val current = viewModel.playbackSpeed.value ?: 1.0f
        val currentIndex = values.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 3

        AlertDialog.Builder(this, R.style.Theme_RyzixPlayer_Dialog)
            .setTitle(getString(R.string.playback_speed))
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                viewModel.setPlaybackSpeed(values[which], player)
                binding.btnSpeed.text = speeds[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun updatePlayPauseButton(playing: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun showSeekFeedback(seconds: Int) {
        val label = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        binding.tvSeekFeedback.text = label
        binding.tvSeekFeedback.visibility = View.VISIBLE
        binding.tvSeekFeedback.postDelayed({ binding.tvSeekFeedback.visibility = View.GONE }, 800)
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val mins = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val secs = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, mins, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let { viewModel.savePosition(it.currentPosition) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            player?.pause()
        }
        viewModel.stopPositionTracking()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        player?.let { viewModel.savePosition(it.currentPosition) }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val vis = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.controlsTop.visibility = vis
        binding.controlsBottom.visibility = vis
    }
}
