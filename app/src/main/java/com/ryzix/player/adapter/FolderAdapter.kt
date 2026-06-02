package com.ryzix.player.adapter

  import android.view.LayoutInflater
  import android.view.ViewGroup
  import androidx.recyclerview.widget.DiffUtil
  import androidx.recyclerview.widget.ListAdapter
  import androidx.recyclerview.widget.RecyclerView
  import coil.load
  import coil.request.videoFrameMillis
  import com.ryzix.player.databinding.ItemFolderBinding
  import com.ryzix.player.model.Folder

  class FolderAdapter(
      private val onFolderClick: (Folder) -> Unit
  ) : ListAdapter<Folder, FolderAdapter.FolderViewHolder>(DIFF_CALLBACK) {

      override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
          val binding = ItemFolderBinding.inflate(
              LayoutInflater.from(parent.context), parent, false
          )
          return FolderViewHolder(binding)
      }

      override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
          holder.bind(getItem(position))
      }

      inner class FolderViewHolder(private val binding: ItemFolderBinding) :
          RecyclerView.ViewHolder(binding.root) {

          fun bind(folder: Folder) {
              binding.apply {
                  tvFolderName.text = folder.name
                  tvVideoCount.text = binding.root.context.resources
                      .getQuantityString(
                          com.ryzix.player.R.plurals.video_count,
                          folder.videoCount,
                          folder.videoCount
                      )

                  folder.thumbnailUri?.let { uri ->
                      imgFolderThumb.load(uri) {
                          videoFrameMillis(1_000)
                          crossfade(true)
                          error(com.ryzix.player.R.drawable.ic_folder_thumb)
                      }
                  } ?: imgFolderThumb.setImageResource(com.ryzix.player.R.drawable.ic_folder_thumb)

                  root.setOnClickListener { onFolderClick(folder) }
              }
          }
      }

      companion object {
          private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Folder>() {
              override fun areItemsTheSame(old: Folder, new: Folder) = old.id == new.id
              override fun areContentsTheSame(old: Folder, new: Folder) = old == new
          }
      }
  }