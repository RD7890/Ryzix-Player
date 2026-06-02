package com.ryzix.player.ui

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.content.DialogInterface
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ryzix.player.R
import com.ryzix.player.databinding.BottomSheetEqualizerBinding

class EqualizerBottomSheet : BottomSheetDialogFragment() {

    var onDismissListener: (() -> Unit)? = null

    private var _binding: BottomSheetEqualizerBinding? = null
    private val binding get() = _binding!!

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val PRESETS = arrayOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Bass Boost", "Vocal")
    // Band presets: 5 bands, center = 50 (0dB), range 0-100 → maps to -15dB..+15dB
    private val PRESET_BANDS = arrayOf(
        intArrayOf(50, 50, 50, 50, 50),       // Flat
        intArrayOf(70, 60, 45, 60, 65),       // Rock
        intArrayOf(55, 65, 60, 55, 50),       // Pop
        intArrayOf(65, 50, 45, 60, 60),       // Jazz
        intArrayOf(70, 55, 45, 55, 70),       // Classical
        intArrayOf(90, 75, 50, 50, 50),       // Bass Boost
        intArrayOf(40, 45, 65, 60, 45)        // Vocal
    )

    companion object {
        private const val ARG_SESSION = "audio_session_id"
        fun newInstance(audioSessionId: Int) = EqualizerBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_SESSION, audioSessionId) }
        }
    }

    override fun getTheme() = R.style.Theme_RyzixPlayer_BottomSheet

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = BottomSheetEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sessionId = arguments?.getInt(ARG_SESSION, 0) ?: 0
        initAudioEffects(sessionId)
        loadSavedSettings()
        setupBandSliders()
        setupBassAmplifier()
        setupPresetChip()
    }

    private fun initAudioEffects(sessionId: Int) {
        if (sessionId == 0) return
        try {
            equalizer = Equalizer(0, sessionId).also { it.enabled = true }
            bassBoost = BassBoost(0, sessionId).also { it.enabled = true }
            loudnessEnhancer = LoudnessEnhancer(sessionId).also { it.enabled = true }
        } catch (_: Exception) { /* Device may not support */ }
    }

    private fun loadSavedSettings() {
        val prefs = requireContext().getSharedPreferences("ryzix_prefs", Context.MODE_PRIVATE)
        val bands = intArrayOf(
            prefs.getInt("eq_band_0", 50), prefs.getInt("eq_band_1", 50),
            prefs.getInt("eq_band_2", 50), prefs.getInt("eq_band_3", 50),
            prefs.getInt("eq_band_4", 50)
        )
        val bandSeeks = listOf(binding.seekBand0, binding.seekBand1, binding.seekBand2,
            binding.seekBand3, binding.seekBand4)
        bandSeeks.forEachIndexed { i, sb -> sb.progress = bands[i] }
        updateBandValueLabels(bands)

        binding.seekBass.progress = prefs.getInt("bass_boost", 0)
        binding.tvBassVal.text = "${prefs.getInt("bass_boost", 0)}%"
        binding.seekAmplifier.progress = prefs.getInt("amplifier", 0)
        binding.tvAmpVal.text = "${prefs.getInt("amplifier", 0)} dB"
        applyAllToEffects(bands)
    }

    private fun setupBandSliders() {
        val bandSeeks = listOf(binding.seekBand0, binding.seekBand1, binding.seekBand2,
            binding.seekBand3, binding.seekBand4)
        val bandLabels = listOf(binding.tvBand0Val, binding.tvBand1Val, binding.tvBand2Val,
            binding.tvBand3Val, binding.tvBand4Val)
        val prefs = requireContext().getSharedPreferences("ryzix_prefs", Context.MODE_PRIVATE)

        bandSeeks.forEachIndexed { i, sb ->
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    val db = (progress - 50) * 15 / 50
                    bandLabels[i].text = if (db >= 0) "+$db" else "$db"
                    prefs.edit().putInt("eq_band_$i", progress).apply()
                    applyBandToEq(i, progress)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
    }

    private fun setupBassAmplifier() {
        val prefs = requireContext().getSharedPreferences("ryzix_prefs", Context.MODE_PRIVATE)

        binding.seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvBassVal.text = "$progress%"
                prefs.edit().putInt("bass_boost", progress).apply()
                try {
                    bassBoost?.setStrength((progress * 10).toShort().coerceIn(0, 1000))
                    bassBoost?.enabled = progress > 0
                } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.seekAmplifier.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvAmpVal.text = "$progress dB"
                prefs.edit().putInt("amplifier", progress).apply()
                try {
                    loudnessEnhancer?.setTargetGain(progress * 100)
                    loudnessEnhancer?.enabled = progress > 0
                } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupPresetChip() {
        binding.chipPreset.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.eq_preset))
                .setItems(PRESETS) { _, which ->
                    applyPreset(which)
                    binding.chipPreset.text = PRESETS[which]
                }
                .show()
        }
    }

    private fun applyPreset(index: Int) {
        val bands = PRESET_BANDS[index]
        val bandSeeks = listOf(binding.seekBand0, binding.seekBand1, binding.seekBand2,
            binding.seekBand3, binding.seekBand4)
        bandSeeks.forEachIndexed { i, sb -> sb.progress = bands[i] }
        updateBandValueLabels(bands)
        applyAllToEffects(bands)
        val prefs = requireContext().getSharedPreferences("ryzix_prefs", Context.MODE_PRIVATE)
        prefs.edit().also { e -> bands.forEachIndexed { i, v -> e.putInt("eq_band_$i", v) } }.apply()
    }

    private fun applyBandToEq(band: Int, progress: Int) {
        try {
            val eq = equalizer ?: return
            if (band >= eq.numberOfBands) return
            val min = eq.bandLevelRange[0]
            val max = eq.bandLevelRange[1]
            val level = (min + (progress / 100.0) * (max - min)).toInt().toShort()
            eq.setBandLevel(band.toShort(), level)
        } catch (_: Exception) {}
    }

    private fun applyAllToEffects(bands: IntArray) {
        bands.forEachIndexed { i, v -> applyBandToEq(i, v) }
    }

    private fun updateBandValueLabels(bands: IntArray) {
        val labels = listOf(binding.tvBand0Val, binding.tvBand1Val, binding.tvBand2Val,
            binding.tvBand3Val, binding.tvBand4Val)
        bands.forEachIndexed { i, v ->
            val db = (v - 50) * 15 / 50
            labels[i].text = if (db >= 0) "+$db" else "$db"
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { equalizer?.release(); bassBoost?.release(); loudnessEnhancer?.release() } catch (_: Exception) {}
        _binding = null
    }
}
