package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.media3.ui.PlayerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.player.TVPlayerManager
import android.widget.TextView

class PlayerActivity : BaseActivity() {

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
                    infoText.text = getString(R.string.playback_error, error)
                    infoText.visibility = View.VISIBLE
                    Toast.makeText(this@PlayerActivity, getString(R.string.error_prefix, error), Toast.LENGTH_LONG).show()
                }
            }

            override fun onBuffering(isBuffering: Boolean) {
                runOnUiThread {
                    if (isBuffering) {
                        infoText.text = getString(R.string.buffering)
                        infoText.visibility = View.VISIBLE
                    } else {
                        infoText.visibility = View.GONE
                    }
                }
            }

            override fun onFallbackUsed(fallbackUrl: String) {
                runOnUiThread {
                    infoText.text = getString(R.string.switching_source)
                    infoText.visibility = View.VISIBLE
                }
            }
        })

        if (currentChannel != null) {
            infoText.text = getString(R.string.loading_channel, channelName)
            infoText.visibility = View.VISIBLE
            playerManager.playChannel(currentChannel!!, streamUrl)
        } else if (!streamUrl.isNullOrEmpty()) {
            val url = streamUrl!!
            infoText.text = getString(R.string.loading_channel, channelName)
            infoText.visibility = View.VISIBLE
            val tempChannel = Channel(
                id = channelId ?: "unknown",
                name = channelName ?: getString(R.string.unknown_channel),
                logoUrl = "",
                streamUrl = url
            )
            currentChannel = tempChannel
            playerManager.playChannel(tempChannel, url)
        } else {
            infoText.text = getString(R.string.no_stream_url)
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
