package com.ryzix.player.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.adapter.MusicAdapter
import com.ryzix.player.databinding.FragmentMusicBinding
import com.ryzix.player.model.MusicItem
import com.ryzix.player.ui.MusicPlayerActivity
import com.ryzix.player.utils.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicAdapter: MusicAdapter
    private var allMusic: List<MusicItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadMusic()
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(
            onClick = { music ->
                val idx = allMusic.indexOf(music).coerceAtLeast(0)
                openMusicPlayer(music, allMusic, idx)
            },
            onMoreClick = { showMusicOptions(it) }
        )
        binding.rvMusic.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = musicAdapter
            setHasFixedSize(true)
        }
    }

    fun filterMusic(query: String) {
        if (query.isBlank()) {
            musicAdapter.submitList(allMusic)
            updateEmptyState(allMusic.isEmpty())
        } else {
            val results = MediaUtils.searchAudio(allMusic, query)
            musicAdapter.submitList(results)
            updateEmptyState(results.isEmpty())
        }
    }

    private fun loadMusic() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            allMusic = withContext(Dispatchers.IO) {
                MediaUtils.getAllAudio(requireContext())
            }
            musicAdapter.submitList(allMusic)
            binding.progressBar.visibility = View.GONE
            updateEmptyState(allMusic.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvMusic.visibility     = if (isEmpty) View.GONE   else View.VISIBLE
    }

    private fun openMusicPlayer(music: MusicItem, playlist: List<MusicItem>, index: Int) {
        startActivity(Intent(requireContext(), MusicPlayerActivity::class.java).apply {
            putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_URIS,
                ArrayList(playlist.map { it.uri.toString() }))
            putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_TITLES,
                ArrayList(playlist.map { it.title.ifBlank { it.path.substringAfterLast("/") } }))
            putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_ARTISTS,
                ArrayList(playlist.map { it.artist.ifBlank { "Unknown Artist" } }))
            val albumIds = playlist.map { it.albumId.toString() }
            putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_ALBUM_IDS, ArrayList(albumIds))
            putExtra(MusicPlayerActivity.EXTRA_PLAYLIST_INDEX, index)
        })
    }

    private fun showMusicOptions(music: MusicItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(music.title.ifBlank { music.path.substringAfterLast("/") })
            .setItems(arrayOf("Play", "Share")) { _, which ->
                when (which) {
                    0 -> {
                        val idx = allMusic.indexOf(music).coerceAtLeast(0)
                        openMusicPlayer(music, allMusic, idx)
                    }
                    1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, music.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share via"))
                }
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
