package com.ryzix.player.utils

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

class PlayerGestureListener(
    private val context: Context,
    private val window: android.view.Window,
    private val screenWidth: Int,
    private val onSeekForward: () -> Unit,
    private val onSeekBackward: () -> Unit,
    private val onSingleTap: () -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onVolumeChange: (Float) -> Unit
) : GestureDetector.SimpleOnGestureListener() {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var startY = 0f
    private var startVolume = 0
    private var startBrightness = 0f
    private var isScrolling = false
    private var scrollSide = Side.NONE

    enum class Side { NONE, LEFT, RIGHT }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        onSingleTap()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (e.x < screenWidth / 2f) {
            onSeekBackward()
        } else {
            onSeekForward()
        }
        return true
    }

    fun onTouchBegin(event: MotionEvent) {
        startY = event.y
        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        startBrightness = window.attributes.screenBrightness
            .takeIf { it >= 0f } ?: 0.5f
        isScrolling = false
        scrollSide = Side.NONE
    }

    fun onTouchMove(event: MotionEvent, dx: Float, dy: Float) {
        if (!isScrolling && abs(dy) > abs(dx) && abs(dy) > 20) {
            isScrolling = true
            scrollSide = if (event.x < screenWidth / 2f) Side.LEFT else Side.RIGHT
        }

        if (!isScrolling) return

        val deltaY = startY - event.y
        val fraction = deltaY / (context.resources.displayMetrics.heightPixels * 0.8f)

        when (scrollSide) {
            Side.LEFT -> {
                val newBrightness = (startBrightness + fraction).coerceIn(0.01f, 1f)
                val layoutParams = window.attributes
                layoutParams.screenBrightness = newBrightness
                window.attributes = layoutParams
                onBrightnessChange(newBrightness)
            }
            Side.RIGHT -> {
                val newVolume = (startVolume + (fraction * maxVolume)).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                onVolumeChange(newVolume.toFloat() / maxVolume)
            }
            Side.NONE -> {}
        }
    }
}
