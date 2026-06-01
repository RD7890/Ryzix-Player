package com.ryzix.player

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure Coil with video frame decoder for thumbnails
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
