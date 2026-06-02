package com.ryzix.player.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ryzix.player.R
import com.ryzix.player.databinding.ItemMusicBinding
import com.ryzix.player.model.MusicItem

class MusicAdapter(
    private val onClick: (MusicItem) -> Unit,
    private val onMoreClick: (MusicItem) -> Unit = {}
) : ListAdapter<MusicItem, MusicAdapter.MusicViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MusicViewHolder(ItemMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class MusicViewHolder(private val b: ItemMusicBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: MusicItem) {
            b.tvMusicTitle.text  = item.title.ifBlank { item.path.substringAfterLast("/") }
            b.tvMusicArtist.text = item.artist.ifBlank { "Unknown Artist" }
            b.tvMusicDuration.text = item.durationFormatted

            b.imgAlbumArt.load(item.albumArtUri) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ -> b.imgAlbumArtFallback.visibility = View.GONE },
                    onError   = { _, _ -> b.imgAlbumArtFallback.visibility = View.VISIBLE }
                )
            }

            b.root.setOnClickListener { onClick(item) }
            b.btnMusicMore.setOnClickListener { onMoreClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MusicItem>() {
            override fun areItemsTheSame(o: MusicItem, n: MusicItem) = o.id == n.id
            override fun areContentsTheSame(o: MusicItem, n: MusicItem) = o == n
        }
    }
}
