package com.ryzix.player.model

import android.net.Uri

data class Folder(
    val id: Long,
    val name: String,
    val path: String,
    val videoCount: Int,
    val thumbnailUri: Uri?
)
