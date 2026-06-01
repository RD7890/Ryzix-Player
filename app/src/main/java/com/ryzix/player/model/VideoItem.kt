package com.ryzix.player.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val path: String,
    val uri: Uri,
    val duration: Long,        // milliseconds
    val size: Long,            // bytes
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAdded: Long,       // epoch seconds
    val dateModified: Long,    // epoch seconds
    val folderId: Long,
    val folderName: String
) {
    val durationFormatted: String
        get() = formatDuration(duration)

    val sizeFormatted: String
        get() = formatSize(size)

    val resolution: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""

    private fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}
