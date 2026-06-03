package com.ryzix.player.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ryzix.player.adapter.HistoryCarouselAdapter
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.databinding.FragmentVideosBinding
import com.ryzix.player.db.WatchHistory
import com.ryzix.player.model.VideoItem
import com.ryzix.player.ui.PlayerActivity
import com.ryzix.player.viewmodel.MediaViewModel

class VideosFragment : Fragment() {

    private var _binding: FragmentVideosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MediaViewModel by activityViewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var carouselAdapter: HistoryCarouselAdapter

    private var currentVideos: List<VideoItem> = emptyList()
    private var allHistory: List<Pair<WatchHistory, VideoItem>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCarousel()
        setupVideoList()
        setupObservers()
        setupSeeAll()
    }

    // ── Carousel — completely separate RecyclerView, no nesting ──────────────

    private fun setupCarousel() {
        carouselAdapter = HistoryCarouselAdapter { history, video ->
            val idx = currentVideos.indexOfFirst { it.path == video.path }.coerceAtLeast(0)
            openPlayer(video, currentVideos, idx, resumeMs = history.lastPosition)
        }

        binding.rvHistoryCarousel.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = carouselAdapter
            setHasFixedSize(true)
        }
    }

    // ── Video list ────────────────────────────────────────────────────────────

    private fun setupVideoList() {
        videoAdapter = VideoAdapter(
            onVideoClick = { video ->
                val idx = currentVideos.indexOf(video).coerceAtLeast(0)
                openPlayer(video, currentVideos, idx)
            },
            onVideoLongClick = { video -> showVideoOptions(video) }
        )

        binding.rvVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
            setHasFixedSize(false)
        }
    }

    // ── See All history ───────────────────────────────────────────────────────

    private fun setupSeeAll() {
        binding.tvSeeAll.setOnClickListener {
            if (allHistory.isEmpty()) return@setOnClickListener
            // Build title list for dialog
            val titles = allHistory.map { (h, _) -> h.title.substringBeforeLast(".") }
                .toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Recent — ${titles.size} videos")
                .setItems(titles) { _, idx ->
                    val (history, video) = allHistory[idx]
                    val playIdx = currentVideos.indexOfFirst { it.path == video.path }.coerceAtLeast(0)
                    openPlayer(video, currentVideos, playIdx, resumeMs = history.lastPosition)
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.filteredVideos.observe(viewLifecycleOwner) { videos ->
            currentVideos = videos
            videoAdapter.submitList(videos)
            binding.tvVideoCount.text = if (videos.isEmpty()) "Videos" else "${videos.size} Videos"
            updateEmptyState(videos.isEmpty() && viewModel.isLoading.value != true)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.recentHistory.observe(viewLifecycleOwner) { history ->
            val videos = viewModel.allVideos.value ?: emptyList()
            val pairs = history.mapNotNull { h ->
                videos.find { it.path == h.videoPath }?.let { v -> Pair(h, v) }
            }
            allHistory = pairs
            val carouselItems = pairs.take(20)
            carouselAdapter.submitList(carouselItems)
            binding.sectionRecent.visibility = if (carouselItems.isEmpty()) View.GONE else View.VISIBLE
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
            if (resumeMs > 0) putExtra(PlayerActivity.EXTRA_RESUME_MS, resumeMs)
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_URIS,
                ArrayList(playlist.map { it.uri.toString() }))
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_TITLES,
                ArrayList(playlist.map { it.displayName }))
            putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
        })
    }

    private fun showVideoOptions(video: VideoItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(video.displayName)
            .setItems(arrayOf("Play", "Share")) { _, which ->
                when (which) {
                    0 -> openPlayer(video, currentVideos,
                            currentVideos.indexOf(video).coerceAtLeast(0))
                    1 -> {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, video.uri)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }
                }
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
