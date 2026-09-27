package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.ui.PlayerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.player.TVPlayerManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class PlayerActivity : BaseActivity() {

    private var channelId: String? = null
    private var streamUrl: String? = null
    private var channelName: String? = null
    private var currentChannel: Channel? = null

    private lateinit var playerManager: TVPlayerManager
    private lateinit var playerView: PlayerView
    private lateinit var infoText: TextView
    private lateinit var programOverlay: LinearLayout
    private lateinit var programNowText: TextView
    private lateinit var programNextText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_player)

            channelId = intent.getStringExtra("channel_id")
            streamUrl = intent.getStringExtra("stream_url")
            channelName = intent.getStringExtra("channel_name")

            currentChannel = ChannelList.channels.find { it.id == channelId }

            playerView = findViewById(R.id.playerView)
            infoText = findViewById(R.id.infoText)
            programOverlay = findViewById(R.id.programOverlay)
            programNowText = findViewById(R.id.programNowText)
            programNextText = findViewById(R.id.programNextText)

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
                showProgramOverlay(currentChannel!!)
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
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "Crash in onCreate", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
        scope.cancel()
        playerManager.releasePlayer()
    }

    private fun showProgramOverlay(channel: Channel) {
        val title = channel.currentProgramTitle
        if (title == null || title.isBlank()) return

        val tzOffset = AppPreferences(applicationContext).timezoneOffset
        val tz = TimeZone.getTimeZone("GMT" + (if (tzOffset >= 0) "+" else "") + String.format(Locale.US, "%02d:00", tzOffset))
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = tz }

        val start = channel.currentProgramStart
        val end = channel.currentProgramEnd

        if (start != null && end != null) {
            programNowText.text = getString(R.string.now_playing_with_time, title, sdf.format(Date(start)), sdf.format(Date(end)))
        } else {
            programNowText.text = getString(R.string.now_playing, title)
        }

        val nextStart = end ?: (start?.plus(3600000L))
        if (nextStart != null) {
            programNextText.text = getString(R.string.next_program_at, sdf.format(Date(nextStart)))
        }

        programOverlay.visibility = View.VISIBLE
    }
}
