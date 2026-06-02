package com.ryzix.player.model

import android.net.Uri

data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long,
    val path: String,
    val uri: Uri,
    val dateAdded: Long
) {
    val durationFormatted: String get() {
        val m = duration / 1000 / 60
        val s = duration / 1000 % 60
        return String.format("%d:%02d", m, s)
    }
    val sizeFormatted: String get() = when {
        size >= 1_048_576L -> String.format("%.1f MB", size / 1_048_576.0)
        else -> String.format("%.0f KB", size / 1024.0)
    }
}
