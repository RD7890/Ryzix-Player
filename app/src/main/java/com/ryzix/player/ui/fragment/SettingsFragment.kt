package com.ryzix.player.ui.fragment

import android.content.Context
import android.media.audiofx.BassBoost
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVersion()
        setupTheme()
        setupToggles()
        setupBassAmp()
        setupClearHistory()
    }

    private fun prefs() = requireContext().getSharedPreferences("ryzix_prefs", Context.MODE_PRIVATE)

    private fun setupVersion() {
        val v = try { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName }
                catch (_: Exception) { "1.0" }
        binding.tvVersionName.text = "Version $v"
    }

    private fun setupTheme() {
        val saved = prefs().getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val labels = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val modes  = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        var current = modes.indexOfFirst { it == saved }.takeIf { it >= 0 } ?: 0
        binding.tvThemeValue.text = labels[current]

        binding.itemTheme.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.theme))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    current = which
                    prefs().edit().putInt("theme_mode", modes[which]).apply()
                    AppCompatDelegate.setDefaultNightMode(modes[which])
                    binding.tvThemeValue.text = labels[which]
                    dialog.dismiss()
                }.show()
        }
    }

    private fun setupToggles() {
        val p = prefs()
        binding.switchResume.isChecked     = p.getBoolean("resume", true)
        binding.switchBackground.isChecked = p.getBoolean("background_play", true)
        binding.switchHardware.isChecked   = p.getBoolean("hw_decode", true)

        binding.switchResume.setOnCheckedChangeListener { _, v -> p.edit().putBoolean("resume", v).apply() }
        binding.switchBackground.setOnCheckedChangeListener { _, v -> p.edit().putBoolean("background_play", v).apply() }
        binding.switchHardware.setOnCheckedChangeListener { _, v -> p.edit().putBoolean("hw_decode", v).apply() }
    }

    private fun setupBassAmp() {
        val p = prefs()
        val bassVal = p.getInt("bass_boost", 0)
        binding.seekBass.progress = bassVal
        binding.tvBassValue.text  = bassVal.toString()

        binding.seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvBassValue.text = progress.toString()
                p.edit().putInt("bass_boost", progress).apply()
                tryBassBoost(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val ampVal = p.getInt("amplifier", 0)
        binding.seekAmplifier.progress = ampVal
        binding.tvAmpValue.text = "$ampVal dB"

        binding.seekAmplifier.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvAmpValue.text = "$progress dB"
                p.edit().putInt("amplifier", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun tryBassBoost(level: Int) {
        try {
            val bb = BassBoost(0, 0)
            bb.setStrength((level * 10).toShort().coerceIn(0, 1000))
            bb.enabled = level > 0
            bb.release()
        } catch (_: Exception) {}
    }

    private fun setupClearHistory() {
        binding.itemClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.clear_history_confirm))
                .setMessage(getString(R.string.clear_history_message))
                .setPositiveButton(getString(R.string.clear)) { _, _ ->
                    val db = com.ryzix.player.db.AppDatabase.getInstance(requireContext())
                    lifecycleScope.launch(Dispatchers.IO) { db.watchHistoryDao().clearAll() }
                    Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
