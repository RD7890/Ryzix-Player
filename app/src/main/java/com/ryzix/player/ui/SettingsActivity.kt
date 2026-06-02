package com.ryzix.player.ui

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivitySettingsBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVersion()
        setupTheme()
        setupToggles()
        setupBassAmp()
        setupClearHistory()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupVersion() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "1.0" }
        binding.tvVersionName.text = "Version $versionName"
    }

    private fun setupTheme() {
        val prefs = getSharedPreferences("ryzix_prefs", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val current = modes.indexOfFirst { it == savedTheme }.takeIf { it >= 0 } ?: 0
        binding.tvThemeValue.text = labels[current]

        binding.itemTheme.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.theme))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    val mode = modes[which]
                    prefs.edit().putInt("theme_mode", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                    binding.tvThemeValue.text = labels[which]
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupToggles() {
        val prefs = getSharedPreferences("ryzix_prefs", MODE_PRIVATE)
        binding.switchResume.isChecked    = prefs.getBoolean("resume", true)
        binding.switchBackground.isChecked = prefs.getBoolean("background_play", true)
        binding.switchHardware.isChecked  = prefs.getBoolean("hw_decode", true)

        binding.switchResume.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("resume", v).apply()
        }
        binding.switchBackground.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("background_play", v).apply()
        }
        binding.switchHardware.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("hw_decode", v).apply()
        }
    }

    private fun setupBassAmp() {
        val prefs = getSharedPreferences("ryzix_prefs", MODE_PRIVATE)

        val bassVal = prefs.getInt("bass_boost", 0)
        binding.seekBass.progress = bassVal
        binding.tvBassValue.text = bassVal.toString()

        binding.seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvBassValue.text = progress.toString()
                prefs.edit().putInt("bass_boost", progress).apply()
                applyGlobalBassBoost(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val ampVal = prefs.getInt("amplifier", 0)
        binding.seekAmplifier.progress = ampVal
        binding.tvAmpValue.text = "$ampVal dB"

        binding.seekAmplifier.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvAmpValue.text = "$progress dB"
                prefs.edit().putInt("amplifier", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun applyGlobalBassBoost(level: Int) {
        // Bass boost in settings applies to a global session (session 0)
        // The per-player EQ in EqualizerBottomSheet takes priority during playback
        try {
            val bb = BassBoost(0, 0)
            bb.setStrength((level * 10).toShort().coerceIn(0, 1000))
            bb.enabled = level > 0
            bb.release()
        } catch (_: Exception) { /* Some devices don't support AudioEffect on session 0 */ }
    }

    private fun setupClearHistory() {
        binding.itemClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.clear_history_confirm))
                .setMessage(getString(R.string.clear_history_message))
                .setPositiveButton(getString(R.string.clear)) { _, _ ->
                    clearHistory()
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun clearHistory() {
        // Delete via Room database — use coroutine
        val db = com.ryzix.player.db.AppDatabase.getInstance(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            db.watchHistoryDao().clearAll()
        }
    }
}
