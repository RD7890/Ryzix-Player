package com.ryzix.player.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ryzix.player.databinding.ItemVideosHeaderBinding
import com.ryzix.player.db.WatchHistory
import com.ryzix.player.model.VideoItem

class VideoSectionHeaderAdapter(
    private val onHistoryItemClick: (WatchHistory, VideoItem) -> Unit
) : RecyclerView.Adapter<VideoSectionHeaderAdapter.VH>() {

    private var historyItems: List<Pair<WatchHistory, VideoItem>> = emptyList()
    private var videoCount: Int = 0

    fun setHistoryItems(items: List<Pair<WatchHistory, VideoItem>>) {
        historyItems = items
        notifyItemChanged(0)
    }

    fun setVideoCount(count: Int) {
        videoCount = count
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVideosHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind()

    inner class VH(private val b: ItemVideosHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        private val carouselAdapter = HistoryCarouselAdapter(onHistoryItemClick)

        init {
            b.rvHistoryCarousel.apply {
                layoutManager = LinearLayoutManager(b.root.context, LinearLayoutManager.HORIZONTAL, false)
                adapter = carouselAdapter
                setHasFixedSize(true)
            }
        }

        fun bind() {
            if (historyItems.isEmpty()) {
                b.sectionRecent.visibility = View.GONE
            } else {
                b.sectionRecent.visibility = View.VISIBLE
                carouselAdapter.submitList(historyItems)
            }
            val count = videoCount
            b.tvVideoCount.text = if (count > 0) "$count Videos" else "Videos"
        }
    }
}
