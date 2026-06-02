package com.ryzix.player.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        const val EXTRA_URI      = "extra_uri"
        const val EXTRA_TITLE    = "extra_title"
        const val EXTRA_PATH     = "extra_path"
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
        private val SPEED_LABELS  = arrayOf("0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "3×", "4×")

        private const val CONTROLS_HIDE_DELAY   = 3500L
        private const val SEEK_OVERLAY_DELAY    = 800L
        private const val INDICATOR_DELAY       = 1200L

        private const val PIP_ACTION      = "com.ryzix.player.PIP_ACTION"
        private const val PIP_EXTRA       = "pip_action"
        private const val PIP_PLAY_PAUSE  = 1
        private const val PIP_REWIND      = 2
        private const val PIP_FORWARD     = 3
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var player: ExoPlayer? = null

    // Audio effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private lateinit var gestureDetector: GestureDetector
    private var gestureListener: PlayerGestureListener? = null

    private var isSeekbarTracking = false
    private var isLocked = false
    private var aspectModeIndex = 0
    private var speedIndex = 3

    private var currentUri   = ""
    private var currentTitle = ""
    private var currentPath  = ""
    private var totalDuration = 0L

    private val controlsHandler    = Handler(Looper.getMainLooper())
    private val seekOverlayHandler  = Handler(Looper.getMainLooper())
    private val indicatorHandler    = Handler(Looper.getMainLooper())

    private val hideControlsRunnable   = Runnable { hideControls() }
    private val hideSeekOverlayRunnable = Runnable { hideSeekOverlay() }
    private val hideIndicatorRunnable  = Runnable {
        binding.layoutBrightnessIndicator.animate().alpha(0f).setDuration(200)
            .withEndAction { binding.layoutBrightnessIndicator.visibility = View.GONE }.start()
        binding.layoutVolumeIndicator.animate().alpha(0f).setDuration(200)
            .withEndAction { binding.layoutVolumeIndicator.visibility = View.GONE }.start()
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(PIP_EXTRA, 0)) {
                PIP_PLAY_PAUSE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
                PIP_REWIND    -> seek(-10_000L)
                PIP_FORWARD   -> seek(+10_000L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipActions()
        }
    }

    private val positionRunnable = object : Runnable {
        override fun run() {
            player?.let { exo ->
                if (!isSeekbarTracking) {
                    val pos = exo.currentPosition
                    binding.seekBar.progress = pos.toInt()
                    binding.tvPosition.text  = formatDuration(pos)
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

        currentUri   = intent.getStringExtra(EXTRA_URI)   ?: run { finish(); return }
        currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        currentPath  = intent.getStringExtra(EXTRA_PATH)  ?: ""
        totalDuration = intent.getLongExtra(EXTRA_DURATION, 0L)

        viewModel.currentVideoPath  = currentPath
        viewModel.currentVideoTitle = currentTitle
        viewModel.currentDuration   = totalDuration
        viewModel.init()

        binding.tvTitle.text = currentTitle.substringBeforeLast(".")

        if (MediaUtils.findSubtitleFile(currentPath) != null)
            Toast.makeText(this, getString(R.string.subtitle_loaded), Toast.LENGTH_SHORT).show()

        setupPlayer()
        setupControls()
        setupGestures()
        scheduleHideControls()
    }

    // ── PLAYER ────────────────────────────────────────────────────────────────

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
                        initAudioEffects(exo.audioSessionId)
                        restoreSavedEq(exo.audioSessionId)
                        updateCodecLabel()
                    }
                    if (state == Player.STATE_ENDED) {
                        viewModel.savePosition(0L)
                        showControls()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipActions()
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0
                        && videoSize.width > videoSize.height)
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity,
                        "${getString(R.string.playback_error)}: ${error.message}",
                        Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun initAudioEffects(sessionId: Int) {
        if (sessionId == 0) return
        try {
            equalizer = Equalizer(0, sessionId).also { it.enabled = true }
            bassBoost = BassBoost(0, sessionId).also { it.enabled = true }
            loudnessEnhancer = LoudnessEnhancer(sessionId).also { it.enabled = true }
        } catch (_: Exception) {}
    }

    private fun restoreSavedEq(sessionId: Int) {
        val prefs = getSharedPreferences("ryzix_prefs", MODE_PRIVATE)
        try {
            val eq = equalizer ?: return
            for (i in 0 until eq.numberOfBands.toInt().coerceAtMost(5)) {
                val progress = prefs.getInt("eq_band_$i", 50)
                val min = eq.bandLevelRange[0]
                val max = eq.bandLevelRange[1]
                val level = (min + (progress / 100.0) * (max - min)).toInt().toShort()
                eq.setBandLevel(i.toShort(), level)
            }
            val bass = prefs.getInt("bass_boost", 0)
            bassBoost?.setStrength((bass * 10).toShort().coerceIn(0, 1000))
            bassBoost?.enabled = bass > 0
            val amp = prefs.getInt("amplifier", 0)
            loudnessEnhancer?.setTargetGain(amp * 100)
            loudnessEnhancer?.enabled = amp > 0
        } catch (_: Exception) {}
    }

    private fun updateCodecLabel() {
        try {
            val name = player?.videoFormat?.codecs ?: return
            binding.tvCodec.text = when {
                name.contains("avc", true)  -> "H.264"
                name.contains("hevc", true) -> "HEVC"
                name.contains("vp9",  true) -> "VP9"
                name.contains("av1",  true) -> "AV1"
                else -> "HW"
            }
        } catch (_: Exception) {}
    }

    // ── CONTROLS ─────────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            scheduleHideControls()
        }
        binding.btnRewind.setOnClickListener {
            seek(-10_000L); showSeekOverlay(-10); scheduleHideControls()
        }
        binding.btnForward.setOnClickListener {
            seek(+10_000L); showSeekOverlay(+10); scheduleHideControls()
        }
        binding.btnPrevious.setOnClickListener {
            player?.seekTo(0L); scheduleHideControls()
        }
        binding.btnNext.setOnClickListener {
            player?.let { it.seekTo((it.currentPosition + 30_000).coerceAtMost(it.duration)) }
            scheduleHideControls()
        }
        binding.btnBack.setOnClickListener { saveAndFinish() }
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
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            scheduleHideControls()
        }
        binding.btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % SPEED_OPTIONS.size
            player?.setPlaybackSpeed(SPEED_OPTIONS[speedIndex])
            binding.btnSpeed.text = SPEED_LABELS[speedIndex]
            scheduleHideControls()
        }

        // EQ bottom sheet
        binding.btnEqualizer.setOnClickListener {
            val sessionId = player?.audioSessionId ?: 0
            releaseAudioEffects() // sheet will own the effects while open
            val sheet = EqualizerBottomSheet.newInstance(sessionId)
            sheet.setOnDismissListener {
                // Re-attach effects after sheet closes
                player?.audioSessionId?.let { sid ->
                    if (sid != 0) { initAudioEffects(sid); restoreSavedEq(sid) }
                }
            }
            sheet.show(supportFragmentManager, "eq")
            scheduleHideControls()
        }

        // Audio track selector
        binding.btnAudioTrack.setOnClickListener {
            showAudioTrackDialog()
            scheduleHideControls()
        }

        // Subtitle selector
        binding.btnSubtitle.setOnClickListener {
            showSubtitleDialog()
            scheduleHideControls()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvPosition.text = formatDuration(p.toLong())
                    showSeekOverlayAt(p.toLong(), 0)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = true; cancelHideControls()
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeekbarTracking = false
                player?.seekTo(sb.progress.toLong())
                scheduleHideControls(); hideSeekOverlay()
            }
        })
    }

    private fun showAudioTrackDialog() {
        val exo = player ?: return
        val audioGroups = exo.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) {
            Toast.makeText(this, "No alternate audio tracks", Toast.LENGTH_SHORT).show(); return
        }
        val labels = audioGroups.mapIndexed { i, g ->
            val fmt = g.getTrackFormat(0)
            fmt.language?.uppercase() ?: "Track ${i + 1}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.eq_preset).replace("Preset","Audio Track"))
            .setItems(labels) { _, which ->
                val params = exo.trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(audioGroups[which].getTrackFormat(0).language)
                    .build()
                exo.trackSelectionParameters = params
            }.show()
    }

    private fun showSubtitleDialog() {
        val exo = player ?: return
        val subGroups = exo.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
        val labels = mutableListOf("Off")
        subGroups.forEachIndexed { i, g ->
            val lang = g.getTrackFormat(0).language?.uppercase() ?: "Sub ${i + 1}"
            labels.add(lang)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Subtitles")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setIgnoredTextSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .setPreferredTextLanguage(null)
                        .build()
                } else {
                    val lang = subGroups[which - 1].getTrackFormat(0).language
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setPreferredTextLanguage(lang)
                        .build()
                }
            }.show()
    }

    // ── GESTURES ─────────────────────────────────────────────────────────────

    private fun setupGestures() {
        val sw = resources.displayMetrics.widthPixels
        gestureListener = PlayerGestureListener(
            context = this, window = window, screenWidth = sw,
            onSeekForward  = { seek(+10_000L); showSeekOverlay(+10) },
            onSeekBackward = { seek(-10_000L); showSeekOverlay(-10) },
            onSingleTap    = {
                if (!isLocked) {
                    if (binding.controlsContainer.visibility == View.VISIBLE) hideControls()
                    else showControls()
                }
            },
            onBrightnessChange = { lvl -> showBrightnessIndicator((lvl * 100).toInt()) },
            onVolumeChange     = { lvl -> showVolumeIndicator((lvl * 100).toInt()) }
        )
        gestureDetector = GestureDetector(this, gestureListener!!)
        var lastX = 0f; var lastY = 0f
        binding.playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureListener?.onTouchBegin(event); lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    gestureListener?.onTouchMove(event, event.x - lastX, event.y - lastY)
                    lastX = event.x; lastY = event.y
                }
            }
            if (!isLocked) gestureDetector.onTouchEvent(event)
            true
        }
    }

    // ── SEEK OVERLAY ─────────────────────────────────────────────────────────

    private fun showSeekOverlay(deltaSeconds: Int) = showSeekOverlayAt(player?.currentPosition ?: 0L, deltaSeconds)
    private fun showSeekOverlayAt(posMs: Long, deltaSeconds: Int) {
        binding.layoutSeekOverlay.visibility = View.VISIBLE
        binding.layoutSeekOverlay.alpha = 1f
        binding.tvSeekTime.text  = formatDuration(posMs)
        binding.tvSeekDelta.text = when {
            deltaSeconds > 0 -> "+${deltaSeconds}s"
            deltaSeconds < 0 -> "${deltaSeconds}s"
            else -> ""
        }
        seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable)
        seekOverlayHandler.postDelayed(hideSeekOverlayRunnable, SEEK_OVERLAY_DELAY)
    }
    private fun hideSeekOverlay() {
        binding.layoutSeekOverlay.animate().alpha(0f).setDuration(200)
            .withEndAction { binding.layoutSeekOverlay.visibility = View.GONE }.start()
    }

    // ── INDICATORS ───────────────────────────────────────────────────────────

    private fun showVolumeIndicator(level: Int) {
        binding.layoutVolumeIndicator.apply { visibility = View.VISIBLE; alpha = 1f }
        binding.tvVolumeIndicator.text = "$level%"
        indicatorHandler.removeCallbacks(hideIndicatorRunnable)
        indicatorHandler.postDelayed(hideIndicatorRunnable, INDICATOR_DELAY)
    }
    private fun showBrightnessIndicator(level: Int) {
        binding.layoutBrightnessIndicator.apply { visibility = View.VISIBLE; alpha = 1f }
        binding.tvBrightnessIndicator.text = "$level%"
        indicatorHandler.removeCallbacks(hideIndicatorRunnable)
        indicatorHandler.postDelayed(hideIndicatorRunnable, INDICATOR_DELAY)
    }

    // ── CONTROLS SHOW / HIDE ─────────────────────────────────────────────────

    private fun showControls() {
        if (isLocked) return
        if (binding.controlsContainer.visibility != View.VISIBLE) {
            binding.controlsContainer.alpha = 0f
            binding.controlsContainer.visibility = View.VISIBLE
            binding.controlsContainer.animate().alpha(1f).setDuration(200).start()
        }
        scheduleHideControls()
    }
    private fun hideControls() {
        binding.controlsContainer.animate().alpha(0f).setDuration(250)
            .withEndAction { binding.controlsContainer.visibility = View.GONE }.start()
    }
    private fun scheduleHideControls() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }
    private fun cancelHideControls() = controlsHandler.removeCallbacks(hideControlsRunnable)

    // ── LOCK ─────────────────────────────────────────────────────────────────

    private fun toggleLock() {
        isLocked = !isLocked
        if (isLocked) {
            binding.controlsContainer.visibility = View.GONE
            binding.lockOverlay.apply { alpha = 0f; visibility = View.VISIBLE
                animate().alpha(1f).setDuration(200).start() }
            binding.btnLock.setImageResource(R.drawable.ic_lock)
        } else {
            binding.lockOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction { binding.lockOverlay.visibility = View.GONE }.start()
            binding.btnLock.setImageResource(R.drawable.ic_lock_open)
            showControls()
        }
    }

    // ── PIP ──────────────────────────────────────────────────────────────────

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) enterPictureInPictureMode(buildPipParams())
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val playing = player?.isPlaying == true
        fun mkAction(code: Int, iconRes: Int, label: String) = RemoteAction(
            Icon.createWithResource(this, iconRes), label, label,
            PendingIntent.getBroadcast(this, code,
                Intent(PIP_ACTION).putExtra(PIP_EXTRA, code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(
                mkAction(PIP_REWIND,    R.drawable.ic_rewind, "-10s"),
                mkAction(PIP_PLAY_PAUSE, if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (playing) "Pause" else "Play"),
                mkAction(PIP_FORWARD,  R.drawable.ic_forward, "+10s")
            )).build()
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipActions() { if (isInPictureInPictureMode) setPictureInPictureParams(buildPipParams()) }
    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        if (isInPiP) { binding.controlsContainer.visibility = View.GONE
            binding.lockOverlay.visibility = View.GONE; cancelHideControls()
        } else { if (!isLocked) showControls() }
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private fun seek(offsetMs: Long) {
        player?.let { it.seekTo((it.currentPosition + offsetMs).coerceIn(0L, it.duration)) }
    }
    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
    }
    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private fun saveAndFinish() {
        viewModel.savePosition(player?.currentPosition ?: 0L); finish()
    }
    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "00:00"
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
    private fun releaseAudioEffects() {
        try { equalizer?.release(); bassBoost?.release(); loudnessEnhancer?.release() } catch (_: Exception) {}
        equalizer = null; bassBoost = null; loudnessEnhancer = null
    }

    // ── LIFECYCLE ────────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PIP_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(pipReceiver, filter)
    }
    override fun onPause() {
        super.onPause()
        player?.let { viewModel.savePosition(it.currentPosition) }
        if (!isInPictureInPictureMode) player?.pause()
    }
    override fun onStop() {
        super.onStop()
        controlsHandler.removeCallbacksAndMessages(null)
        seekOverlayHandler.removeCallbacksAndMessages(null)
        indicatorHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(pipReceiver) } catch (_: Exception) {}
    }
    override fun onDestroy() {
        super.onDestroy()
        releaseAudioEffects()
        player?.release(); player = null
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() { saveAndFinish() }
}
