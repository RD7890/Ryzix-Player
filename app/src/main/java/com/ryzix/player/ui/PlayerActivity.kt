package com.ryzix.player.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
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
import com.ryzix.player.utils.MediaUtils
import com.ryzix.player.utils.PlayerGestureListener
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
        private val ASPECT_LABELS = arrayOf("Fit", "Fill", "Zoom", "16:9", "4:3")

        private val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
        private val SPEED_LABELS = arrayOf("0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "3×", "4×")

        private const val CONTROLS_HIDE_DELAY_MS = 3500L
        private const val SEEK_OVERLAY_HIDE_DELAY_MS = 800L
        private const val INDICATOR_HIDE_DELAY_MS = 1200L
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null

    private lateinit var gestureDetector: GestureDetector
    private var gestureListener: PlayerGestureListener? = null

    private var isSeekbarTracking = false
    private var isLocked = false
    private var aspectModeIndex = 0
    private var speedIndex = 3

    private var currentUri: String = ""
    private var currentTitle: String = ""
    private var currentPath: String = ""
    private var totalDuration: Long = 0L

    private val controlsHandler = Handler(Looper.getMainLooper())
    private val seekOverlayHandler = Handler(Looper.getMainLooper())
    private val indicatorHandler = Handler(Looper.getMainLooper())

    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideSeekOverlayRunnable = Runnable { hideSeekOverlay() }
    private val hideIndicatorRunnable = Runnable {
        binding.layoutBrightnessIndicator.animate().alpha(0f).setDuration(200)
            .withEndAction { binding.layoutBrightnessIndicator.visibility = View.GONE }.start()
        binding.layoutVolumeIndicator.animate().alpha(0f).setDuration(200)
            .withEndAction { binding.layoutVolumeIndicator.visibility = View.GONE }.start()
    }

    // Seekbar position update
    private val positionRunnable = object : Runnable {
        override fun run() {
            player?.let { exo ->
                if (!isSeekbarTracking) {
                    val pos = exo.currentPosition
                    binding.seekBar.progress = pos.toInt()
                    binding.tvPosition.text = formatDuration(pos)
                }
            }
            controlsHandler.postDelayed(this, 500)
        }
    }

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

        if (MediaUtils.findSubtitleFile(currentPath) != null) {
            Toast.makeText(this, getString(R.string.subtitle_loaded), Toast.LENGTH_SHORT).show()
        }

        setupPlayer()
        setupControls()
        setupGestures()

        scheduleHideControls()
    }

    // ─── PLAYER ──────────────────────────────────────────────────────────────

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            binding.playerView.useController = false

            lifecycleScope.launch {
                val resumePos = viewModel.getResumePosition()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(currentUri)), resumePos)
                exo.prepare()
                exo.playWhenReady = true
            }

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    updatePlayPauseIcon()
                    if (state == Player.STATE_READY) {
                        totalDuration = exo.duration
                        viewModel.currentDuration = totalDuration
                        binding.seekBar.max = totalDuration.toInt().coerceAtLeast(1)
                        binding.tvDuration.text = formatDuration(totalDuration)
                        controlsHandler.post(positionRunnable)
                    }
                    if (state == Player.STATE_ENDED) {
                        viewModel.savePosition(0L)
                        showControls()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0
                        && videoSize.width > videoSize.height) {
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
        }
    }

    // ─── CONTROLS ────────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            scheduleHideControls()
        }

        binding.btnRewind.setOnClickListener {
            seek(-10_000L)
            showSeekOverlay(-10)
            scheduleHideControls()
        }

        binding.btnForward.setOnClickListener {
            seek(10_000L)
            showSeekOverlay(+10)
            scheduleHideControls()
        }

        binding.btnPrevious.setOnClickListener {
            player?.seekTo(0L)
            scheduleHideControls()
        }

        binding.btnNext.setOnClickListener {
            player?.let {
                if (it.currentPosition + 30_000 < it.duration) it.seekTo(it.currentPosition + 30_000)
                else it.seekTo(it.duration)
            }
            scheduleHideControls()
        }

        binding.btnBack.setOnClickListener {
            saveAndFinish()
        }

        binding.btnLock.setOnClickListener { toggleLock() }
        binding.btnUnlock.setOnClickListener { toggleLock() }

        binding.btnAspect.setOnClickListener {
            aspectModeIndex = (aspectModeIndex + 1) % ASPECT_MODES.size
            binding.playerView.resizeMode = ASPECT_MODES[aspectModeIndex]
            Toast.makeText(this, ASPECT_LABELS[aspectModeIndex], Toast.LENGTH_SHORT).show()
            scheduleHideControls()
        }

        binding.btnPip.setOnClickListener { enterPip() }

        binding.btnRotate.setOnClickListener {
            requestedOrientation = when (requestedOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ->
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ->
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            scheduleHideControls()
        }

        binding.btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % SPEED_OPTIONS.size
            val speed = SPEED_OPTIONS[speedIndex]
            player?.setPlaybackSpeed(speed)
            binding.btnSpeed.text = SPEED_LABELS[speedIndex]
            scheduleHideControls()
        }

        // Seekbar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvPosition.text = formatDuration(progress.toLong())
                    showSeekOverlayAt(progress.toLong(), 0)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = true
                cancelHideControls()
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = false
                player?.seekTo(sb.progress.toLong())
                scheduleHideControls()
                hideSeekOverlay()
            }
        })

        // Tap on player surface toggles controls
        binding.playerView.setOnClickListener {
            if (isLocked) return@setOnClickListener
            if (binding.controlsContainer.visibility == View.VISIBLE) hideControls()
            else showControls()
        }
    }

    // ─── GESTURES ────────────────────────────────────────────────────────────

    private fun setupGestures() {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        gestureListener = PlayerGestureListener(
            context = this,
            audioManager = audioManager,
            onDoubleTapLeft = { seek(-10_000L); showSeekOverlay(-10) },
            onDoubleTapRight = { seek(10_000L); showSeekOverlay(+10) },
            onBrightnessChange = { level ->
                showBrightnessIndicator(level)
                val lp = window.attributes
                lp.screenBrightness = level / 100f
                window.attributes = lp
            },
            onVolumeChange = { level -> showVolumeIndicator(level) },
            onSingleTap = {
                if (!isLocked) {
                    if (binding.controlsContainer.visibility == View.VISIBLE) hideControls()
                    else showControls()
                }
            }
        )
        gestureDetector = GestureDetector(this, gestureListener!!)
        binding.playerView.setOnTouchListener { v, event ->
            if (!isLocked) gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }
    }

    // ─── SEEK OVERLAY (MX-style center display) ───────────────────────────

    private fun showSeekOverlay(deltaSeconds: Int) {
        val pos = player?.currentPosition ?: 0L
        showSeekOverlayAt(pos, deltaSeconds)
    }

    private fun showSeekOverlayAt(posMs: Long, deltaSeconds: Int) {
        binding.layoutSeekOverlay.visibility = View.VISIBLE
        binding.layoutSeekOverlay.alpha = 1f
        binding.tvSeekTime.text = formatDuration(posMs)
        binding.tvSeekDelta.text = if (deltaSeconds == 0) "" else
            if (deltaSeconds > 0) "[+${deltaSeconds}s]" else "[${deltaSeconds}s]"
        seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable)
        seekOverlayHandler.postDelayed(hideSeekOverlayRunnable, SEEK_OVERLAY_HIDE_DELAY_MS)
    }

    private fun hideSeekOverlay() {
        binding.layoutSeekOverlay.animate()
            .alpha(0f).setDuration(200)
            .withEndAction { binding.layoutSeekOverlay.visibility = View.GONE }
            .start()
    }

    // ─── VOLUME / BRIGHTNESS INDICATORS ─────────────────────────────────────

    private fun showVolumeIndicator(level: Int) {
        binding.layoutVolumeIndicator.apply {
            visibility = View.VISIBLE; alpha = 1f
        }
        binding.tvVolumeIndicator.text = "$level%"
        indicatorHandler.removeCallbacks(hideIndicatorRunnable)
        indicatorHandler.postDelayed(hideIndicatorRunnable, INDICATOR_HIDE_DELAY_MS)
    }

    private fun showBrightnessIndicator(level: Int) {
        binding.layoutBrightnessIndicator.apply {
            visibility = View.VISIBLE; alpha = 1f
        }
        binding.tvBrightnessIndicator.text = "$level%"
        indicatorHandler.removeCallbacks(hideIndicatorRunnable)
        indicatorHandler.postDelayed(hideIndicatorRunnable, INDICATOR_HIDE_DELAY_MS)
    }

    // ─── CONTROLS SHOW / HIDE (smooth fade) ──────────────────────────────────

    private fun showControls() {
        if (isLocked) return
        binding.controlsContainer.apply {
            if (visibility == View.VISIBLE) return
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(200).start()
        }
        scheduleHideControls()
    }

    private fun hideControls() {
        binding.controlsContainer.animate()
            .alpha(0f).setDuration(250)
            .withEndAction { binding.controlsContainer.visibility = View.GONE }
            .start()
    }

    private fun scheduleHideControls() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun cancelHideControls() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
    }

    // ─── LOCK ────────────────────────────────────────────────────────────────

    private fun toggleLock() {
        isLocked = !isLocked
        if (isLocked) {
            binding.controlsContainer.visibility = View.GONE
            binding.lockOverlay.apply {
                alpha = 0f; visibility = View.VISIBLE
                animate().alpha(1f).setDuration(200).start()
            }
            binding.btnLock.setImageResource(R.drawable.ic_lock)
        } else {
            binding.lockOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction { binding.lockOverlay.visibility = View.GONE }.start()
            binding.btnLock.setImageResource(R.drawable.ic_lock_open)
            showControls()
        }
    }

    // ─── PIP ─────────────────────────────────────────────────────────────────

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private fun seek(offsetMs: Long) {
        player?.let {
            val newPos = (it.currentPosition + offsetMs).coerceIn(0L, it.duration)
            it.seekTo(newPos)
        }
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = player?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun saveAndFinish() {
        val pos = player?.currentPosition ?: 0L
        viewModel.savePosition(pos)
        finish()
    }

    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "00:00"
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    // ─── LIFECYCLE ───────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        player?.let { viewModel.savePosition(it.currentPosition) }
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        controlsHandler.removeCallbacksAndMessages(null)
        seekOverlayHandler.removeCallbacksAndMessages(null)
        indicatorHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    override fun onBackPressed() {
        saveAndFinish()
    }
}
