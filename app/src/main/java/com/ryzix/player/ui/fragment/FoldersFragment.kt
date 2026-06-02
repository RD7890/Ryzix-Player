package com.ryzix.player.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ryzix.player.adapter.FolderAdapter
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.databinding.FragmentFoldersBinding
import com.ryzix.player.model.VideoItem
import com.ryzix.player.ui.PlayerActivity
import com.ryzix.player.viewmodel.MediaViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FoldersFragment : Fragment() {

    private var _binding: FragmentFoldersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaViewModel by activityViewModels()

    private lateinit var folderAdapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { folder ->
                val videos = viewModel.allVideos.value ?: emptyList()
                val folderVideos = videos.filter { it.folderId == folder.id }
                showFolderVideos(folder.name, folderVideos)
            }
        )
        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewModel.folders.observe(viewLifecycleOwner) { folders ->
            folderAdapter.submitList(folders)
            val isEmpty = folders.isEmpty()
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvFolders.visibility   = if (isEmpty) View.GONE   else View.VISIBLE
        }
    }

    private fun showFolderVideos(folderName: String, videos: List<VideoItem>) {
        if (videos.isEmpty()) return

        val names = videos.map { it.displayName.substringBeforeLast(".") }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(folderName)
            .setItems(names) { _, which ->
                openPlayer(videos[which], videos, which)
            }.show()
    }

    private fun openPlayer(video: VideoItem, playlist: List<VideoItem>, index: Int) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI,   video.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, video.displayName)
            putExtra(PlayerActivity.EXTRA_PATH,  video.path)
            putExtra(PlayerActivity.EXTRA_DURATION, video.duration)
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_URIS,
                ArrayList(playlist.map { it.uri.toString() }))
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_TITLES,
                ArrayList(playlist.map { it.displayName }))
            putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
