package com.ryzix.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ryzix.player.db.AppDatabase
import com.ryzix.player.db.WatchHistory
import com.ryzix.player.utils.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.watchHistoryDao()
    val prefs = PreferenceUtils(application)

    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _isLocked = MutableLiveData(false)
    val isLocked: LiveData<Boolean> = _isLocked

    private val _playbackSpeed = MutableLiveData(1.0f)
    val playbackSpeed: LiveData<Float> = _playbackSpeed

    private val _aspectRatioIndex = MutableLiveData(0)
    val aspectRatioIndex: LiveData<Int> = _aspectRatioIndex

    private val _showControls = MutableLiveData(true)
    val showControls: LiveData<Boolean> = _showControls

    private var positionTrackJob: Job? = null
    private var hideControlsJob: Job? = null

    var currentVideoPath: String = ""
    var currentVideoTitle: String = ""
    var currentDuration: Long = 0L

    fun init() {
        viewModelScope.launch {
            _playbackSpeed.value = prefs.playbackSpeed.first()
            _aspectRatioIndex.value = prefs.aspectRatio.first()
        }
    }

    fun startPositionTracking(player: Player) {
        positionTrackJob?.cancel()
        positionTrackJob = viewModelScope.launch {
            while (isActive) {
                _currentPosition.postValue(player.currentPosition)
                delay(500)
            }
        }
    }

    fun stopPositionTracking() {
        positionTrackJob?.cancel()
    }

    fun toggleLock() {
        _isLocked.value = !(_isLocked.value ?: false)
    }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun toggleControls() {
        val showing = _showControls.value ?: true
        _showControls.value = !showing
        if (!showing) scheduleHideControls()
    }

    fun showControls() {
        _showControls.value = true
        scheduleHideControls()
    }

    fun hideControls() {
        _showControls.value = false
        hideControlsJob?.cancel()
    }

    private fun scheduleHideControls(delayMs: Long = 3500) {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(delayMs)
            _showControls.postValue(false)
        }
    }

    fun setPlaybackSpeed(speed: Float, player: Player?) {
        _playbackSpeed.value = speed
        player?.setPlaybackSpeed(speed)
        viewModelScope.launch { prefs.setPlaybackSpeed(speed) }
    }

    fun nextAspectRatio(): Int {
        val next = ((_aspectRatioIndex.value ?: 0) + 1) % 5
        _aspectRatioIndex.value = next
        viewModelScope.launch { prefs.setAspectRatio(next) }
        return next
    }

    fun savePosition(position: Long) {
        if (currentVideoPath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertOrUpdate(
                WatchHistory(
                    videoPath = currentVideoPath,
                    title = currentVideoTitle,
                    duration = currentDuration,
                    lastPosition = position,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getResumePosition(): Long {
        if (currentVideoPath.isBlank()) return 0L
        return db.watchHistoryDao().getHistoryForVideo(currentVideoPath)?.lastPosition ?: 0L
    }

    override fun onCleared() {
        super.onCleared()
        positionTrackJob?.cancel()
        hideControlsJob?.cancel()
    }
}
