package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.player.TVPlayerManager
import android.widget.TextView

class PlayerActivity : AppCompatActivity() {

    private var channelId: String? = null
    private var streamUrl: String? = null
    private var channelName: String? = null
    private var currentChannel: Channel? = null

    private lateinit var playerManager: TVPlayerManager
    private lateinit var playerView: PlayerView
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        channelId = intent.getStringExtra("channel_id")
        streamUrl = intent.getStringExtra("stream_url")
        channelName = intent.getStringExtra("channel_name")

        currentChannel = ChannelList.channels.find { it.id == channelId }

        playerView = findViewById(R.id.playerView)
        infoText = findViewById(R.id.infoText)

        playerManager = TVPlayerManager(this)

        playerManager.initializePlayer(playerView, object : TVPlayerManager.PlayerCallback {
            override fun onPlaybackReady() {
                runOnUiThread {
                    infoText.visibility = View.GONE
                }
            }

            override fun onPlaybackError(error: String) {
                runOnUiThread {
                    infoText.text = "Ошибка воспроизведения:\n$error"
                    infoText.visibility = View.VISIBLE
                    Toast.makeText(this@PlayerActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                }
            }

            override fun onBuffering(isBuffering: Boolean) {
                runOnUiThread {
                    if (isBuffering) {
                        infoText.text = "Буферизация..."
                        infoText.visibility = View.VISIBLE
                    } else {
                        infoText.visibility = View.GONE
                    }
                }
            }

            override fun onFallbackUsed(fallbackUrl: String) {
                runOnUiThread {
                    infoText.text = "Переключение на резервный источник..."
                    infoText.visibility = View.VISIBLE
                }
            }
        })

        if (currentChannel != null) {
            infoText.text = "Загрузка канала: $channelName..."
            infoText.visibility = View.VISIBLE
            playerManager.playChannel(currentChannel!!, streamUrl)
        } else if (!streamUrl.isNullOrEmpty()) {
            infoText.text = "Загрузка канала: $channelName..."
            infoText.visibility = View.VISIBLE
            val tempChannel = Channel(
                id = channelId ?: "unknown",
                name = channelName ?: "Неизвестный канал",
                logoUrl = "",
                streamUrl = streamUrl
            )
            currentChannel = tempChannel
            playerManager.playChannel(tempChannel, streamUrl)
        } else {
            infoText.text = "Ошибка: URL потока не указан"
            infoText.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        playerManager.resume()
    }

    override fun onPause() {
        super.onPause()
        playerManager.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!playerManager.isPlaying()) {
                    playerManager.resume()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.releasePlayer()
    }
}
