package com.ryzix.player.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.ryzix.player.model.Folder
import com.ryzix.player.model.MusicItem
import com.ryzix.player.model.VideoItem

object MediaUtils {
    fun getAllVideos(context: Context): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Video.Media.DURATION} >= ?", arrayOf("1000"),
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
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
                videos.add(VideoItem(
                    id = id, title = cursor.getString(titleCol) ?: "",
                    displayName = cursor.getString(nameCol) ?: "",
                    path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else "",
                    uri = contentUri, duration = cursor.getLong(durationCol),
                    size = cursor.getLong(sizeCol), width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    mimeType = cursor.getString(mimeCol) ?: "video/*",
                    dateAdded = cursor.getLong(dateAddedCol),
                    dateModified = cursor.getLong(dateModCol),
                    folderId = cursor.getLong(bucketIdCol),
                    folderName = cursor.getString(bucketNameCol) ?: "Unknown"
                ))
            }
        }
        return videos
    }

    fun getAllAudio(context: Context): List<MusicItem> {
        val tracks = mutableListOf<MusicItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.DURATION} >= ?", arrayOf("5000"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol    = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val dateCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                tracks.add(MusicItem(
                    id = id,
                    title   = cursor.getString(titleCol)  ?: "",
                    artist  = cursor.getString(artistCol) ?: "",
                    album   = cursor.getString(albumCol)  ?: "",
                    duration = cursor.getLong(durCol),
                    size    = cursor.getLong(sizeCol),
                    path    = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else "",
                    uri     = uri,
                    dateAdded = cursor.getLong(dateCol)
                ))
            }
        }
        return tracks
    }

    fun groupByFolder(videos: List<VideoItem>): List<Folder> =
        videos.groupBy { it.folderId }.map { (folderId, items) ->
            Folder(id = folderId, name = items.first().folderName,
                path = items.first().path.substringBeforeLast("/"),
                videoCount = items.size, thumbnailUri = items.first().uri)
        }.sortedBy { it.name }

    fun getVideosInFolder(videos: List<VideoItem>, folderId: Long) =
        videos.filter { it.folderId == folderId }

    fun searchVideos(videos: List<VideoItem>, query: String): List<VideoItem> {
        val lower = query.lowercase()
        return videos.filter {
            it.title.lowercase().contains(lower) ||
                it.displayName.lowercase().contains(lower) ||
                it.folderName.lowercase().contains(lower)
        }
    }

    fun searchAudio(tracks: List<MusicItem>, query: String): List<MusicItem> {
        val lower = query.lowercase()
        return tracks.filter {
            it.title.lowercase().contains(lower) ||
                it.artist.lowercase().contains(lower) ||
                it.album.lowercase().contains(lower)
        }
    }

    fun findSubtitleFile(videoPath: String): String? {
        val basePath = videoPath.substringBeforeLast(".")
        for (ext in listOf("srt", "ass", "ssa", "vtt", "sub")) {
            val f = java.io.File("$basePath.$ext")
            if (f.exists()) return f.absolutePath
        }
        return null
    }
}
