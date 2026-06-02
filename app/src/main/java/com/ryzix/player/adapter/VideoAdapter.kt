package com.ryzix.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.ryzix.player.databinding.ItemVideoBinding
import com.ryzix.player.model.VideoItem

class VideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit,
    private val onVideoLongClick: (VideoItem) -> Unit = {}
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.apply {
                tvTitle.text = video.displayName.substringBeforeLast(".")
                tvDuration.text = video.durationFormatted
                tvSize.text = video.sizeFormatted
                tvResolution.text = video.resolution

                thumbnail.load(video.uri) {
                    videoFrameMillis(1_000)
                    crossfade(true)
                    error(com.ryzix.player.R.drawable.ic_video_placeholder)
                    placeholder(com.ryzix.player.R.drawable.ic_video_placeholder)
                }

                root.setOnClickListener { onVideoClick(video) }
                root.setOnLongClickListener {
                    onVideoLongClick(video)
                    true
                }
                btnMore.setOnClickListener { onVideoLongClick(video) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<VideoItem>() {
            override fun areItemsTheSame(old: VideoItem, new: VideoItem) = old.id == new.id
            override fun areContentsTheSame(old: VideoItem, new: VideoItem) = old == new
        }
    }
}