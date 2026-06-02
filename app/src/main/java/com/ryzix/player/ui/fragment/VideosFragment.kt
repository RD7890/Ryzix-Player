package com.ryzix.player.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.adapter.VideoSectionHeaderAdapter
import com.ryzix.player.databinding.FragmentVideosBinding
import com.ryzix.player.model.VideoItem
import com.ryzix.player.ui.PlayerActivity
import com.ryzix.player.viewmodel.MediaViewModel

class VideosFragment : Fragment() {

    private var _binding: FragmentVideosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaViewModel by activityViewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var sectionHeaderAdapter: VideoSectionHeaderAdapter
    private var currentVideos: List<VideoItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        sectionHeaderAdapter = VideoSectionHeaderAdapter(
            onHistoryItemClick = { history, video ->
                openPlayer(video, currentVideos,
                    currentVideos.indexOfFirst { it.path == video.path }.coerceAtLeast(0),
                    resumeMs = history.lastPosition)
            }
        )

        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                val idx = currentVideos.indexOf(video).coerceAtLeast(0)
                openPlayer(video, currentVideos, idx)
            },
            onVideoLongClick = { video -> showVideoOptions(video) }
        )

        val concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder().setIsolateViewTypes(true).build(),
            sectionHeaderAdapter,
            videoAdapter
        )

        binding.rvVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = concatAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupObservers() {
        viewModel.filteredVideos.observe(viewLifecycleOwner) { videos ->
            currentVideos = videos
            videoAdapter.submitList(videos)
            sectionHeaderAdapter.setVideoCount(videos.size)
            updateEmptyState(videos.isEmpty() && viewModel.isLoading.value != true)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.recentHistory.observe(viewLifecycleOwner) { history ->
            val videos = viewModel.allVideos.value ?: emptyList()
            val pairs = history
                .mapNotNull { h -> videos.find { it.path == h.videoPath }?.let { v -> Pair(h, v) } }
                .take(12)
            sectionHeaderAdapter.setHistoryItems(pairs)
        }

        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            if (query.isBlank()) {
                viewModel.filteredVideos.value?.let { videos ->
                    videoAdapter.submitList(videos)
                    updateEmptyState(videos.isEmpty())
                }
            } else {
                viewModel.search(query)
                val results = viewModel.searchResults.value ?: emptyList()
                videoAdapter.submitList(results)
                updateEmptyState(results.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvVideos.visibility    = if (isEmpty) View.GONE   else View.VISIBLE
    }

    private fun openPlayer(
        video: VideoItem,
        playlist: List<VideoItem> = emptyList(),
        index: Int = 0,
        resumeMs: Long = -1L
    ) {
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI,   video.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, video.displayName)
            putExtra(PlayerActivity.EXTRA_PATH,  video.path)
            putExtra(PlayerActivity.EXTRA_DURATION, video.duration)
            if (playlist.isNotEmpty()) {
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_URIS,
                    ArrayList(playlist.map { it.uri.toString() }))
                putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_TITLES,
                    ArrayList(playlist.map { it.displayName }))
                putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
            }
            if (resumeMs >= 0) putExtra(PlayerActivity.EXTRA_RESUME_MS, resumeMs)
        })
    }

    private fun showVideoOptions(video: VideoItem) {
        val fragment = requireActivity()
        val items = arrayOf("Play", "Share", "Remove from history")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(fragment)
            .setTitle(video.displayName.substringBeforeLast("."))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openPlayer(video, currentVideos,
                            currentVideos.indexOf(video).coerceAtLeast(0))
                    1 -> shareVideo(video)
                    2 -> viewModel.deleteHistoryItem(video.path)
                }
            }.show()
    }

    private fun shareVideo(video: VideoItem) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share via"))
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadVideos()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}