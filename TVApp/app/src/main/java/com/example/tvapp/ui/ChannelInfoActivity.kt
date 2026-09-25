package com.example.tvapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tvapp.R
import com.bumptech.glide.Glide

class ChannelInfoActivity : AppCompatActivity() {

    private var channelId: String? = null
    private var streamUrl: String? = null
    private var channelName: String? = null
    private var logoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_info)

        channelId = intent.getStringExtra("channel_id")
        streamUrl = intent.getStringExtra("stream_url")
        channelName = intent.getStringExtra("channel_name")
        logoUrl = intent.getStringExtra("logo_url")

        val channelNameText: TextView = findViewById(R.id.channelNameText)
        val channelImage: ImageView = findViewById(R.id.channelImage)
        val startButton: TextView = findViewById(R.id.startButton)

        channelNameText.text = channelName ?: "Неизвестный канал"

        if (!logoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(channelImage)
        }

        startButton.setOnClickListener { startPlayback() }
        Toast.makeText(this, "Нажмите ОК для начала просмотра", Toast.LENGTH_LONG).show()
    }

    private fun startPlayback() {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channelId)
            putExtra("channel_name", channelName)
            putExtra("stream_url", streamUrl)
            putExtra("logo_url", logoUrl)
        }
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { finish(); true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { startPlayback(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
