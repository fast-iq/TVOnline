package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.ChannelRepository
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var channelsRecyclerView: RecyclerView
    private lateinit var currentProgramText: TextView
    private lateinit var channelAdapter: ChannelAdapter

    private val channelRepository by lazy { ChannelRepository(applicationContext) }
    private val preferences by lazy { AppPreferences(applicationContext) }

    private var lastSelectedChannelId: String? = null
    private var lastFocusedPosition: Int = -1
    private var pendingFocusRestore: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelsRecyclerView = findViewById(R.id.channelsRecyclerView)
        currentProgramText = findViewById(R.id.currentProgramText)
        val settingsButton: android.widget.ImageView = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener { openSettings() }
        settingsButton.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.2f else 1.0f
            v.scaleY = if (hasFocus) 1.2f else 1.0f
        }
        val epgButton: android.widget.ImageView = findViewById(R.id.epgButton)
        epgButton.setOnClickListener { openEPG() }
        epgButton.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.2f else 1.0f
            v.scaleY = if (hasFocus) 1.2f else 1.0f
        }

        setupChannelsGrid()
        loadChannels()
    }

    private fun loadChannels() {
        scope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    channelRepository.getChannels()
                }
                if (channels.isNotEmpty()) {
                    val enriched = enrichWithNtvEpg(channels)
                    channelAdapter.updateChannels(enriched)
                    restoreLastChannel()
                    updateHeaderProgram(enriched)
                    if (pendingFocusRestore && lastFocusedPosition >= 0) {
                        pendingFocusRestore = false
                        focusOnPosition(lastFocusedPosition)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun enrichWithNtvEpg(channels: List<Channel>): List<Channel> {
        return try {
            val ntvPrograms = channelRepository.fetchNtvCurrentPrograms()
            if (ntvPrograms.isEmpty()) return channels
            channels.map { ch ->
                val prog = ntvPrograms[ch.id]
                if (prog != null) {
                    ch.copy(
                        currentProgramTitle = prog.title,
                        currentProgramStart = prog.startMs,
                        currentProgramEnd = prog.endMs,
                    )
                } else {
                    ch
                }
            }
        } catch (e: Exception) {
            channels
        }
    }

    private fun focusOnPosition(position: Int) {
        channelsRecyclerView.post {
            val child = channelsRecyclerView.findViewHolderForAdapterPosition(position)?.itemView
            if (child != null) {
                child.requestFocus()
            } else {
                channelsRecyclerView.scrollToPosition(position)
                channelsRecyclerView.post {
                    channelsRecyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                }
            }
        }
    }

    private fun setupChannelsGrid() {
        val spanCount = 5
        channelAdapter = ChannelAdapter(
            channels = ChannelList.channels,
            onChannelSelected = { channel ->
                lastSelectedChannelId = channel.id
                preferences.lastChannelId = channel.id
                openPlayer(channel)
            },
            onFocusPositionChanged = { pos -> lastFocusedPosition = pos },
        )

        channelsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanCount)
            adapter = channelAdapter
        }
    }

    private fun updateHeaderProgram(channels: List<Channel>) {
        val channelIdToShow = lastSelectedChannelId ?: preferences.lastChannelId
        val channel = channels.find { it.id == channelIdToShow }
            ?: channels.firstOrNull()
        if (channel != null) {
            val title = channel.currentProgramTitle
            if (title != null && title.isNotBlank()) {
                currentProgramText.text = getString(R.string.now_playing, title)
            } else {
                currentProgramText.text = getString(R.string.select_channel)
            }
        } else {
            currentProgramText.text = getString(R.string.select_channel)
        }
    }

    private fun restoreLastChannel() {
        val lastChannelId = preferences.lastChannelId
        if (lastChannelId != null) {
            val channel = ChannelList.channels.find { it.id == lastChannelId }
            channel?.let {
                val position = ChannelList.channels.indexOf(it)
                if (position != -1) {
                    channelsRecyclerView.scrollToPosition(position)
                    lastFocusedPosition = position
                }
                lastSelectedChannelId = lastChannelId
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastFocusedPosition >= 0) {
            pendingFocusRestore = true
        }
        loadChannels()
    }

    private fun openPlayer(channel: Channel) {
        val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("channel_name", channel.name)
            putExtra("stream_url", channel.streamUrl)
            putExtra("logo_url", channel.logoUrl)
        }
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = android.content.Intent(this, com.example.tvapp.settings.SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openEPG() {
        val intent = android.content.Intent(this, EPGActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MENU -> {
            openSettings()
            true
        }
        KeyEvent.KEYCODE_INFO -> {
            openEPG()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
