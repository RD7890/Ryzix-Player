package com.ryzix.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.ryzix.player.R
import com.ryzix.player.adapter.FolderAdapter
import com.ryzix.player.adapter.VideoAdapter
import com.ryzix.player.databinding.ActivityMainBinding
import com.ryzix.player.model.VideoItem
import com.ryzix.player.viewmodel.MediaViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaViewModel by viewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.loadVideos()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupAdapters()
        setupTabs()
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
                binding.tabs.getTabAt(0)?.select()
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = videoAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupTabs() {
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.recyclerView.adapter = videoAdapter
                        viewModel.clearFolderFilter()
                    }
                    1 -> binding.recyclerView.adapter = folderAdapter
                    2 -> showHistory()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupObservers() {
        viewModel.filteredVideos.observe(this) { videos ->
            videoAdapter.submitList(videos)
            binding.tvEmpty.visibility =
                if (videos.isEmpty() && !(viewModel.isLoading.value ?: false))
                    android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.folders.observe(this) { folders ->
            folderAdapter.submitList(folders)
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility =
                if (loading) android.view.View.VISIBLE else android.view.View.GONE
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

        if (allGranted) {
            viewModel.loadVideos()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun openPlayer(video: VideoItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, video.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, video.displayName)
            putExtra(PlayerActivity.EXTRA_PATH, video.path)
            putExtra(PlayerActivity.EXTRA_DURATION, video.duration)
        }
        startActivity(intent)
    }

    private fun showVideoOptions(video: VideoItem) {
        val options = arrayOf(
            getString(R.string.play),
            getString(R.string.share),
            getString(R.string.delete_from_history)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
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
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun showHistory() {
        // History tab — observe recentHistory and show in recyclerView
        viewModel.recentHistory.observe(this) { historyList ->
            val historyVideos = historyList.mapNotNull { history ->
                viewModel.allVideos.value?.find { it.path == history.videoPath }
            }
            videoAdapter.submitList(historyVideos)
        }
        binding.recyclerView.adapter = videoAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(query: String?): Boolean {
                viewModel.search(query ?: "")
                val results = viewModel.searchResults.value ?: emptyList()
                if (query?.isNotBlank() == true) videoAdapter.submitList(results)
                else viewModel.filteredVideos.value?.let { videoAdapter.submitList(it) }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort_name -> { viewModel.sortBy(com.ryzix.player.utils.PreferenceUtils.SORT_BY_NAME); true }
            R.id.action_sort_date -> { viewModel.sortBy(com.ryzix.player.utils.PreferenceUtils.SORT_BY_DATE); true }
            R.id.action_sort_size -> { viewModel.sortBy(com.ryzix.player.utils.PreferenceUtils.SORT_BY_SIZE); true }
            R.id.action_sort_duration -> { viewModel.sortBy(com.ryzix.player.utils.PreferenceUtils.SORT_BY_DURATION); true }
            R.id.action_network_stream -> {
                startActivity(Intent(this, BrowserActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadVideos()
    }
}
