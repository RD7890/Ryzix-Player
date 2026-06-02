package com.ryzix.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityMainBinding
import com.ryzix.player.ui.fragment.FoldersFragment
import com.ryzix.player.ui.fragment.MusicFragment
import com.ryzix.player.ui.fragment.VideosFragment
import com.ryzix.player.utils.PreferenceUtils
import com.ryzix.player.viewmodel.MediaViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MediaViewModel by viewModels()

    private lateinit var videosFragment: VideosFragment
    private lateinit var musicFragment: MusicFragment
    private lateinit var foldersFragment: FoldersFragment

    private var isSearchVisible = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            viewModel.loadVideos()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videosFragment  = VideosFragment()
        musicFragment   = MusicFragment()
        foldersFragment = FoldersFragment()

        setupViewPager()
        setupBottomNav()
        setupSearchButton()
        setupSortButton()
        checkPermissions()
    }

    // ── ViewPager2 ────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter()
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.offscreenPageLimit = 2

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val navId = when (position) {
                    0 -> R.id.nav_local
                    1 -> R.id.nav_music
                    2 -> R.id.nav_folders
                    else -> R.id.nav_local
                }
                if (binding.bottomNav.selectedItemId != navId) {
                    binding.bottomNav.selectedItemId = navId
                }
            }
        })
    }

    private inner class MainPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> videosFragment
            1 -> musicFragment
            2 -> foldersFragment
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
                R.id.nav_folders -> {
                    binding.viewPager.setCurrentItem(2, true)
                    updateSearchHint(getString(R.string.search_hint))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false // don't select settings tab in nav
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

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val query = e?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                when (binding.viewPager.currentItem) {
                    0 -> viewModel.setSearchQuery(query)
                    1 -> musicFragment.filterMusic(query)
                    else -> { /* folders don't search */ }
                }
            }
        })
    }

    private fun toggleSearch(show: Boolean) {
        isSearchVisible = show
        binding.layoutSearchBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.etSearch.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.etSearch.text?.clear()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.etSearch.clearFocus()
        }
    }

    private fun updateSearchHint(hint: String) {
        binding.etSearch.hint = hint
    }

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
                    val key = when (which) {
                        0 -> PreferenceUtils.SORT_BY_NAME
                        1 -> PreferenceUtils.SORT_BY_DATE
                        2 -> PreferenceUtils.SORT_BY_SIZE
                        else -> PreferenceUtils.SORT_BY_DURATION
                    }
                    viewModel.sortBy(key)
                }.show()
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun checkPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.loadVideos()
        else permissionLauncher.launch(permissions)
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
