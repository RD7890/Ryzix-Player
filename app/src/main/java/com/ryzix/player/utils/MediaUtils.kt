package com.ryzix.player.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ryzix.player.model.Folder
import com.ryzix.player.model.VideoItem

object MediaUtils {

    fun getAllVideos(context: Context): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000") // at least 1 second
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""

                videos.add(
                    VideoItem(
                        id = id,
                        title = cursor.getString(titleCol) ?: "",
                        displayName = cursor.getString(nameCol) ?: "",
                        path = path,
                        uri = contentUri,
                        duration = cursor.getLong(durationCol),
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModCol),
                        folderId = cursor.getLong(bucketIdCol),
                        folderName = cursor.getString(bucketNameCol) ?: "Unknown"
                    )
                )
            }
        }

        return videos
    }

    fun groupByFolder(videos: List<VideoItem>): List<Folder> {
        return videos
            .groupBy { it.folderId }
            .map { (folderId, items) ->
                Folder(
                    id = folderId,
                    name = items.first().folderName,
                    path = items.first().path.substringBeforeLast("/"),
                    videoCount = items.size,
                    thumbnailUri = items.first().uri
                )
            }
            .sortedBy { it.name }
    }

    fun getVideosInFolder(videos: List<VideoItem>, folderId: Long): List<VideoItem> {
        return videos.filter { it.folderId == folderId }
    }

    fun searchVideos(videos: List<VideoItem>, query: String): List<VideoItem> {
        val lower = query.lowercase()
        return videos.filter {
            it.title.lowercase().contains(lower) ||
                    it.displayName.lowercase().contains(lower) ||
                    it.folderName.lowercase().contains(lower)
        }
    }

    fun findSubtitleFile(videoPath: String): String? {
        val basePath = videoPath.substringBeforeLast(".")
        val extensions = listOf("srt", "ass", "ssa", "vtt", "sub")
        for (ext in extensions) {
            val subFile = java.io.File("$basePath.$ext")
            if (subFile.exists()) return subFile.absolutePath
        }
        return null
    }
}
