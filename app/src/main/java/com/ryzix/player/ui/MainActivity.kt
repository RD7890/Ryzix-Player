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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.adapter.FolderAdapter
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.databinding.ActivityMainBinding
import com.ryzix.player.model.VideoItem
import com.ryzix.player.utils.PreferenceUtils
import com.ryzix.player.viewmodel.MediaViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaViewModel by viewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.loadVideos()
        else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
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
            onVideoClick = { video -> openPlayer(video) },
            onVideoLongClick = { video -> showVideoOptions(video) }
        )
        folderAdapter = FolderAdapter(
            onFolderClick = { folder ->
                viewModel.openFolder(folder.id)
                binding.bottomNav.selectedItemId = R.id.nav_local
            }
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
                    binding.recyclerView.adapter = videoAdapter
                    viewModel.clearFolderFilter()
                    viewModel.filteredVideos.value?.let { videoAdapter.submitList(it) }
                    showEmpty(videoAdapter.currentList.isEmpty(), getString(R.string.no_videos))
                    true
                }
                R.id.nav_folders -> {
                    binding.recyclerView.adapter = folderAdapter
                    viewModel.folders.value?.let { folderAdapter.submitList(it) }
                    showEmpty(folderAdapter.currentList.isEmpty(), getString(R.string.no_folders))
                    true
                }
                R.id.nav_search -> {
                    binding.recyclerView.adapter = videoAdapter
                    binding.searchView.requestFocus()
                    true
                }
                R.id.nav_history -> {
                    showHistory()
                    true
                }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_local
    }

    private fun setupSearch() {
        val sv = binding.searchView
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(query: String?): Boolean {
                val q = query?.trim() ?: ""
                if (q.isBlank()) {
                    viewModel.filteredVideos.value?.let { videoAdapter.submitList(it) }
                    showEmpty(videoAdapter.currentList.isEmpty(), getString(R.string.no_videos))
                } else {
                    viewModel.search(q)
                    val results = viewModel.searchResults.value ?: emptyList()
                    videoAdapter.submitList(results)
                    showEmpty(results.isEmpty(), getString(R.string.no_results, q))
                }
                return true
            }
        })
    }

    private fun setupToolbarActions() {
        binding.btnSort.setOnClickListener {
            val options = arrayOf(
                getString(R.string.sort_name),
                getString(R.string.sort_date),
                getString(R.string.sort_size),
                getString(R.string.sort_duration)
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sort_by))
                .setItems(options) { _, which ->
                    val sortKey = when (which) {
                        0 -> PreferenceUtils.SORT_BY_NAME
                        1 -> PreferenceUtils.SORT_BY_DATE
                        2 -> PreferenceUtils.SORT_BY_SIZE
                        else -> PreferenceUtils.SORT_BY_DURATION
                    }
                    viewModel.sortBy(sortKey)
                }
                .show()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun setupObservers() {
        viewModel.filteredVideos.observe(this) { videos ->
            if (binding.bottomNav.selectedItemId == R.id.nav_local ||
                binding.bottomNav.selectedItemId == R.id.nav_search) {
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
        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.tvEmpty.text = message
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showHistory() {
        binding.recyclerView.adapter = videoAdapter
        viewModel.recentHistory.observe(this) { historyList ->
            val historyVideos = historyList.mapNotNull { history ->
                viewModel.allVideos.value?.find { it.path == history.videoPath }
            }
            videoAdapter.submitList(historyVideos)
            showEmpty(historyVideos.isEmpty(), getString(R.string.no_history))
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.loadVideos() else permissionLauncher.launch(permissions)
    }

    private fun openPlayer(video: VideoItem) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, video.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, video.displayName)
            putExtra(PlayerActivity.EXTRA_PATH, video.path)
            putExtra(PlayerActivity.EXTRA_DURATION, video.duration)
        })
    }

    private fun showVideoOptions(video: VideoItem) {
        val options = arrayOf(
            getString(R.string.play),
            getString(R.string.share),
            getString(R.string.delete_from_history)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(video.displayName.substringBeforeLast("."))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPlayer(video)
                    1 -> shareVideo(video)
                    2 -> viewModel.deleteHistoryItem(video.path)
                }
            }
            .show()
    }

    private fun shareVideo(video: VideoItem) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, getString(R.string.share_via)))
    }

    private fun showSettingsDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.theme))
            .setItems(themes) { _, which ->
                val mode = when (which) {
                    1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (binding.bottomNav.selectedItemId == R.id.nav_local) viewModel.loadVideos()
    }
}