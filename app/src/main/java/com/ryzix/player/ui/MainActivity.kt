package com.ryzix.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.adapter.FolderAdapter
import com.ryzix.player.adapter.MusicAdapter
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.databinding.ActivityMainBinding
import com.ryzix.player.model.MusicItem
import com.ryzix.player.model.VideoItem
import com.ryzix.player.utils.MediaUtils
import com.ryzix.player.utils.PreferenceUtils
import com.ryzix.player.viewmodel.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaViewModel by viewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var musicAdapter: MusicAdapter
    private var allMusic: List<MusicItem> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            viewModel.loadVideos()
            loadMusicIfGranted()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupBottomNav()
        setupSearch()
        setupToolbarActions()
        setupObservers()
        checkPermissions()
    }

    private fun setupAdapters() {
        videoAdapter = VideoAdapter(
            onVideoClick  = { openPlayer(it) },
            onVideoLongClick = { showVideoOptions(it) }
        )
        folderAdapter = FolderAdapter(
            onFolderClick = { folder ->
                viewModel.openFolder(folder.id)
                binding.bottomNav.selectedItemId = R.id.nav_local
            }
        )
        musicAdapter = MusicAdapter(
            onClick      = { openMusicPlayer(it) },
            onMoreClick  = { showMusicOptions(it) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = videoAdapter
            setHasFixedSize(true)
            itemAnimator?.changeDuration = 150
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_local -> {
                    updateSearchHint(getString(R.string.search_hint))
                    binding.recyclerView.adapter = videoAdapter
                    viewModel.clearFolderFilter()
                    viewModel.filteredVideos.value?.let { videoAdapter.submitList(it) }
                    showEmpty(videoAdapter.currentList.isEmpty(), getString(R.string.no_videos))
                    true
                }
                R.id.nav_folders -> {
                    updateSearchHint(getString(R.string.search_hint))
                    binding.recyclerView.adapter = folderAdapter
                    viewModel.folders.value?.let { folderAdapter.submitList(it) }
                    showEmpty(folderAdapter.currentList.isEmpty(), getString(R.string.no_folders))
                    true
                }
                R.id.nav_music -> {
                    updateSearchHint(getString(R.string.search_music_hint))
                    binding.recyclerView.adapter = musicAdapter
                    musicAdapter.submitList(allMusic)
                    showEmpty(allMusic.isEmpty(), getString(R.string.no_music))
                    true
                }
                R.id.nav_history -> {
                    updateSearchHint(getString(R.string.search_hint))
                    showHistory()
                    true
                }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_local
    }

    private fun updateSearchHint(hint: String) {
        binding.searchView.queryHint = hint
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean {
                val query = q?.trim() ?: ""
                val tab = binding.bottomNav.selectedItemId
                when {
                    tab == R.id.nav_music -> {
                        val results = if (query.isBlank()) allMusic
                        else MediaUtils.searchAudio(allMusic, query)
                        musicAdapter.submitList(results)
                        showEmpty(results.isEmpty(),
                            if (query.isBlank()) getString(R.string.no_music)
                            else getString(R.string.no_results, query))
                    }
                    query.isBlank() -> {
                        viewModel.filteredVideos.value?.let { videoAdapter.submitList(it) }
                        showEmpty(videoAdapter.currentList.isEmpty(), getString(R.string.no_videos))
                    }
                    else -> {
                        viewModel.search(query)
                        val results = viewModel.searchResults.value ?: emptyList()
                        videoAdapter.submitList(results)
                        showEmpty(results.isEmpty(), getString(R.string.no_results, query))
                    }
                }
                return true
            }
        })
    }

    private fun setupToolbarActions() {
        binding.btnSort.setOnClickListener {
            val options = arrayOf(
                getString(R.string.sort_name), getString(R.string.sort_date),
                getString(R.string.sort_size), getString(R.string.sort_duration)
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sort_by))
                .setItems(options) { _, which ->
                    val key = when (which) {
                        0 -> PreferenceUtils.SORT_BY_NAME
                        1 -> PreferenceUtils.SORT_BY_DATE
                        2 -> PreferenceUtils.SORT_BY_SIZE
                        else -> PreferenceUtils.SORT_BY_DURATION
                    }
                    viewModel.sortBy(key)
                }.show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupObservers() {
        viewModel.filteredVideos.observe(this) { videos ->
            val tab = binding.bottomNav.selectedItemId
            if (tab == R.id.nav_local) {
                videoAdapter.submitList(videos)
                showEmpty(videos.isEmpty() && viewModel.isLoading.value != true,
                    getString(R.string.no_videos))
            }
        }
        viewModel.folders.observe(this) { folders ->
            if (binding.bottomNav.selectedItemId == R.id.nav_folders) {
                folderAdapter.submitList(folders)
                showEmpty(folders.isEmpty(), getString(R.string.no_folders))
            }
        }
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun showEmpty(empty: Boolean, message: String) {
        binding.layoutEmpty.visibility  = if (empty) View.VISIBLE else View.GONE
        binding.tvEmpty.text            = message
        binding.recyclerView.visibility = if (empty) View.GONE   else View.VISIBLE
    }

    private fun showHistory() {
        binding.recyclerView.adapter = videoAdapter
        viewModel.recentHistory.observe(this) { historyList ->
            val histVideos = historyList.mapNotNull { h ->
                viewModel.allVideos.value?.find { it.path == h.videoPath }
            }
            videoAdapter.submitList(histVideos)
            showEmpty(histVideos.isEmpty(), getString(R.string.no_history))
        }
    }

    private fun loadMusicIfGranted() {
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audioPermission) ==
            PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                allMusic = withContext(Dispatchers.IO) { MediaUtils.getAllAudio(this@MainActivity) }
                if (binding.bottomNav.selectedItemId == R.id.nav_music) {
                    musicAdapter.submitList(allMusic)
                    showEmpty(allMusic.isEmpty(), getString(R.string.no_music))
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.loadVideos()
            loadMusicIfGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun openPlayer(video: VideoItem) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, video.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, video.displayName)
            putExtra(PlayerActivity.EXTRA_PATH, video.path)
            putExtra(PlayerActivity.EXTRA_DURATION, video.duration)
        })
    }

    private fun openMusicPlayer(music: MusicItem) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, music.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, music.title.ifBlank { music.path.substringAfterLast("/") })
            putExtra(PlayerActivity.EXTRA_PATH, music.path)
            putExtra(PlayerActivity.EXTRA_DURATION, music.duration)
        })
    }

    private fun showVideoOptions(video: VideoItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(video.displayName.substringBeforeLast("."))
            .setItems(arrayOf(
                getString(R.string.play),
                getString(R.string.share),
                getString(R.string.delete_from_history)
            )) { _, which ->
                when (which) {
                    0 -> openPlayer(video)
                    1 -> shareVideo(video)
                    2 -> viewModel.deleteHistoryItem(video.path)
                }
            }.show()
    }

    private fun showMusicOptions(music: MusicItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(music.title.ifBlank { music.path.substringAfterLast("/") })
            .setItems(arrayOf(getString(R.string.play), getString(R.string.share))) { _, which ->
                when (which) {
                    0 -> openMusicPlayer(music)
                    1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, music.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, getString(R.string.share_via)))
                }
            }.show()
    }

    private fun shareVideo(video: VideoItem) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, getString(R.string.share_via)))
    }

    override fun onResume() {
        super.onResume()
        if (binding.bottomNav.selectedItemId == R.id.nav_local) viewModel.loadVideos()
    }
}
