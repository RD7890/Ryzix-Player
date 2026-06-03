package com.ryzix.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ryzix.player.databinding.ItemVideosHeaderBinding

class VideoSectionHeaderAdapter : RecyclerView.Adapter<VideoSectionHeaderAdapter.VH>() {

    private var videoCount: Int = 0

    fun setVideoCount(count: Int) {
        videoCount = count
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVideosHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind()

    inner class VH(private val b: ItemVideosHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind() {
            b.tvVideoCount.text = if (videoCount > 0) "$videoCount Videos" else "Videos"
        }
    }
}
