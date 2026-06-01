package com.ryzix.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ryzix.player.db.AppDatabase
import com.ryzix.player.db.WatchHistory
import com.ryzix.player.model.Folder
import com.ryzix.player.model.VideoItem
import com.ryzix.player.utils.MediaUtils
import com.ryzix.player.utils.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.watchHistoryDao()
    private val prefs = PreferenceUtils(application)

    private val _allVideos = MutableLiveData<List<VideoItem>>(emptyList())
    val allVideos: LiveData<List<VideoItem>> = _allVideos

    private val _folders = MutableLiveData<List<Folder>>(emptyList())
    val folders: LiveData<List<Folder>> = _folders

    private val _filteredVideos = MutableLiveData<List<VideoItem>>(emptyList())
    val filteredVideos: LiveData<List<VideoItem>> = _filteredVideos

    private val _searchResults = MutableLiveData<List<VideoItem>>(emptyList())
    val searchResults: LiveData<List<VideoItem>> = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val recentHistory = dao.getRecentHistory(20)

    private var currentFolderId: Long = -1L
    private var currentSearchQuery: String = ""

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            val videos = withContext(Dispatchers.IO) {
                MediaUtils.getAllVideos(getApplication())
            }
            val sortOrder = prefs.sortOrder.first()
            val sorted = sortVideos(videos, sortOrder)
            _allVideos.value = sorted
            _folders.value = MediaUtils.groupByFolder(sorted)
            _filteredVideos.value = sorted
            _isLoading.value = false
        }
    }

    fun openFolder(folderId: Long) {
        currentFolderId = folderId
        val all = _allVideos.value ?: return
        _filteredVideos.value = MediaUtils.getVideosInFolder(all, folderId)
    }

    fun clearFolderFilter() {
        currentFolderId = -1L
        _filteredVideos.value = _allVideos.value ?: emptyList()
    }

    fun search(query: String) {
        currentSearchQuery = query
        val all = _allVideos.value ?: return
        _searchResults.value = if (query.isBlank()) emptyList()
        else MediaUtils.searchVideos(all, query)
    }

    fun sortBy(order: Int) {
        viewModelScope.launch {
            prefs.setSortOrder(order)
            val all = _allVideos.value ?: return@launch
            val sorted = sortVideos(all, order)
            _allVideos.value = sorted
            _filteredVideos.value = if (currentFolderId >= 0)
                MediaUtils.getVideosInFolder(sorted, currentFolderId)
            else sorted
        }
    }

    private fun sortVideos(videos: List<VideoItem>, order: Int): List<VideoItem> {
        return when (order) {
            PreferenceUtils.SORT_BY_NAME -> videos.sortedBy { it.displayName.lowercase() }
            PreferenceUtils.SORT_BY_DATE -> videos.sortedByDescending { it.dateModified }
            PreferenceUtils.SORT_BY_SIZE -> videos.sortedByDescending { it.size }
            PreferenceUtils.SORT_BY_DURATION -> videos.sortedByDescending { it.duration }
            else -> videos.sortedByDescending { it.dateModified }
        }
    }

    fun saveWatchHistory(
        path: String,
        title: String,
        duration: Long,
        position: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertOrUpdate(
                WatchHistory(
                    videoPath = path,
                    title = title,
                    duration = duration,
                    lastPosition = position,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getResumePosition(path: String, callback: (Long) -> Unit) {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { dao.getHistoryForVideo(path) }
            callback(history?.lastPosition ?: 0L)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearAll() }
    }

    fun deleteHistoryItem(path: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteByPath(path) }
    }
}
