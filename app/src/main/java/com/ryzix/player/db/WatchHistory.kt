package com.ryzix.player.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey
    val videoPath: String,
    val title: String,
    val duration: Long,           // total duration in ms
    val lastPosition: Long,       // resume position in ms
    val watchedAt: Long,          // epoch millis
    val thumbnailPath: String? = null
) {
    val progressPercent: Int
        get() = if (duration > 0) ((lastPosition.toFloat() / duration) * 100).toInt() else 0

    val isCompleted: Boolean
        get() = progressPercent >= 95
}
