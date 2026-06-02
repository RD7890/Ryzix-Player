package com.ryzix.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ryzix.player.R
import com.ryzix.player.databinding.ActivityBrowserBinding

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.network_stream)

        binding.btnPlay.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isBlank()) {
                binding.etUrl.error = getString(R.string.enter_url)
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://") &&
                !url.startsWith("rtsp://") && !url.startsWith("rtmp://")
            ) {
                Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URI, url)
                putExtra(PlayerActivity.EXTRA_TITLE, url.substringAfterLast("/").ifBlank { url })
                putExtra(PlayerActivity.EXTRA_PATH, url)
                putExtra(PlayerActivity.EXTRA_DURATION, 0L)
            }
            startActivity(intent)
        }

        binding.chipHls.setOnClickListener {
            binding.etUrl.setText("https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8")
        }
        binding.chipDash.setOnClickListener {
            binding.etUrl.setText("https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}