package com.ryzix.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.ryzix.player.databinding.ItemHistoryCardBinding
import com.ryzix.player.db.WatchHistory
import com.ryzix.player.model.VideoItem

class HistoryCarouselAdapter(
    private val onClick: (WatchHistory, VideoItem) -> Unit
) : ListAdapter<Pair<WatchHistory, VideoItem>, HistoryCarouselAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemHistoryCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(pair: Pair<WatchHistory, VideoItem>) {
            val (history, video) = pair
            b.tvTitle.text = history.title.substringBeforeLast(".")

            b.thumbnail.load(video.uri) {
                videoFrameMillis(history.lastPosition.coerceAtLeast(1_000))
                crossfade(true)
                error(com.ryzix.player.R.drawable.ic_video_placeholder)
                placeholder(com.ryzix.player.R.drawable.ic_video_placeholder)
            }

            if (history.duration > 0) {
                b.progressResume.max = history.duration.toInt()
                b.progressResume.progress = history.lastPosition.toInt()
            }

            b.root.setOnClickListener { onClick(history, video) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pair<WatchHistory, VideoItem>>() {
            override fun areItemsTheSame(o: Pair<WatchHistory, VideoItem>, n: Pair<WatchHistory, VideoItem>) =
                o.first.videoPath == n.first.videoPath
            override fun areContentsTheSame(o: Pair<WatchHistory, VideoItem>, n: Pair<WatchHistory, VideoItem>) =
                o == n
        }
    }
}
