package com.ryzix.player.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityMainBinding
import com.ryzix.player.service.PlayerService
import com.ryzix.player.ui.fragment.FoldersFragment
import com.ryzix.player.ui.fragment.MusicFragment
import com.ryzix.player.ui.fragment.SettingsFragment
import com.ryzix.player.ui.fragment.VideosFragment
import com.ryzix.player.utils.PreferenceUtils
import com.ryzix.player.viewmodel.MediaViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaViewModel by viewModels()

    private lateinit var videosFragment: VideosFragment
    private lateinit var musicFragment: MusicFragment
    private lateinit var onlineFragment: FoldersFragment
    private lateinit var settingsFragment: SettingsFragment

    private var isSearchVisible = false

    // ── Now-Playing mini bar ──────────────────────────────────────────────────
    private var playerControllerFuture: ListenableFuture<MediaController>? = null
    private var playerController: MediaController? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) viewModel.loadVideos()
        else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = getSharedPreferences("ryzix_prefs", MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videosFragment  = VideosFragment()
        musicFragment   = MusicFragment()
        onlineFragment  = FoldersFragment()
        settingsFragment = SettingsFragment()

        setupViewPager()
        setupBottomNav()
        setupSearchButton()
        setupSortButton()
        setupNowPlayingBar()
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        connectToPlayerService()
    }

    override fun onStop() {
        super.onStop()
        playerControllerFuture?.let { MediaController.releaseFuture(it) }
        playerController = null
    }

    // ── ViewPager2 ────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter()
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.offscreenPageLimit = 3

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val navId = when (position) {
                    0 -> R.id.nav_local
                    1 -> R.id.nav_music
                    2 -> R.id.nav_online
                    3 -> R.id.nav_settings
                    else -> R.id.nav_local
                }
                if (binding.bottomNav.selectedItemId != navId) {
                    binding.bottomNav.selectedItemId = navId
                }
                val showControls = position <= 1   // only Videos and Music tabs get search/sort
                binding.btnSearch.visibility = if (showControls) View.VISIBLE else View.GONE
                binding.btnSort.visibility   = if (showControls) View.VISIBLE else View.GONE
                if (position >= 2 && isSearchVisible) toggleSearch(false)
            }
        })
    }

    private inner class MainPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 4
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> videosFragment
            1 -> musicFragment
            2 -> onlineFragment
            3 -> settingsFragment
            else -> videosFragment
        }
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_local -> {
                    binding.viewPager.setCurrentItem(0, true)
                    updateSearchHint(getString(R.string.search_hint))
                    true
                }
                R.id.nav_music -> {
                    binding.viewPager.setCurrentItem(1, true)
                    updateSearchHint(getString(R.string.search_music_hint))
                    true
                }
                R.id.nav_online -> {
                    binding.viewPager.setCurrentItem(2, true)
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.setCurrentItem(3, true)
                    true
                }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_local
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener { toggleSearch(true) }
        binding.tvCancelSearch.setOnClickListener {
            toggleSearch(false)
            viewModel.setSearchQuery("")
        }
        binding.btnClearSearch.setOnClickListener { binding.etSearch.text?.clear() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val query = e?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                when (binding.viewPager.currentItem) {
                    0 -> viewModel.setSearchQuery(query)
                    1 -> musicFragment.filterMusic(query)
                }
            }
        })
    }

    private fun toggleSearch(show: Boolean) {
        isSearchVisible = show
        if (show) {
            binding.layoutSearchBar.visibility = View.VISIBLE
            binding.layoutSearchBar.alpha = 0f
            binding.layoutSearchBar.translationY = -24f
            binding.layoutSearchBar.animate().alpha(1f).translationY(0f).setDuration(220).start()
            binding.etSearch.requestFocus()
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.layoutSearchBar.animate().alpha(0f).translationY(-24f).setDuration(180)
                .withEndAction { binding.layoutSearchBar.visibility = View.GONE }.start()
            binding.etSearch.text?.clear()
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.etSearch.clearFocus()
        }
    }

    private fun updateSearchHint(hint: String) { binding.etSearch.hint = hint }

    // ── Sort ──────────────────────────────────────────────────────────────────

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            val options = arrayOf(
                getString(R.string.sort_name), getString(R.string.sort_date),
                getString(R.string.sort_size), getString(R.string.sort_duration)
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sort_by))
                .setItems(options) { _, which ->
                    viewModel.sortBy(when (which) {
                        0 -> PreferenceUtils.SORT_BY_NAME
                        1 -> PreferenceUtils.SORT_BY_DATE
                        2 -> PreferenceUtils.SORT_BY_SIZE
                        else -> PreferenceUtils.SORT_BY_DURATION
                    })
                }.show()
        }
    }

    // ── Now-Playing mini bar ──────────────────────────────────────────────────

    private fun setupNowPlayingBar() {
        binding.layoutNowPlaying.setOnClickListener {
            // Open music player if there's media loaded
            val ctrl = playerController ?: return@setOnClickListener
            if (ctrl.mediaItemCount > 0) {
                startActivity(Intent(this, MusicPlayerActivity::class.java))
            }
        }
        binding.btnNowPlayingPlayPause.setOnClickListener {
            playerController?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        binding.btnNowPlayingNext.setOnClickListener {
            playerController?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
        }
    }

    private fun connectToPlayerService() {
        val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
        playerControllerFuture = MediaController.Builder(this, token).buildAsync()
        playerControllerFuture!!.addListener({
            try {
                playerController = playerControllerFuture!!.get()
                attachNowPlayingListener()
            } catch (_: Exception) { /* service not running */ }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attachNowPlayingListener() {
        val ctrl = playerController ?: return
        ctrl.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNowPlayingBar()
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = refreshNowPlayingBar()
            override fun onPlaybackStateChanged(state: Int) = refreshNowPlayingBar()
        })
        refreshNowPlayingBar()
    }

    private fun refreshNowPlayingBar() {
        val ctrl = playerController
        val hasMedia = ctrl != null && ctrl.mediaItemCount > 0
        binding.layoutNowPlaying.visibility = if (hasMedia) View.VISIBLE else View.GONE
        if (!hasMedia) return

        val meta = ctrl!!.mediaMetadata
        binding.tvNowPlayingTitle.text  = meta.title?.toString() ?: getString(R.string.now_playing)
        binding.tvNowPlayingArtist.text = meta.artist?.toString() ?: ""
        binding.btnNowPlayingPlayPause.setImageResource(
            if (ctrl.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        meta.artworkUri?.let { uri ->
            binding.imgNowPlayingArt.load(uri) {
                crossfade(true)
                error(R.drawable.ic_music_note)
                placeholder(R.drawable.ic_music_note)
            }
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun checkPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.loadVideos() else permissionLauncher.launch(permissions)
    }

    override fun onBackPressed() {
        if (isSearchVisible) {
            toggleSearch(false)
            viewModel.setSearchQuery("")
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
