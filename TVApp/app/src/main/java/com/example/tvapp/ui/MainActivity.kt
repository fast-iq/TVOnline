package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.EPGRepository
import com.example.tvapp.data.AppPreferences
import kotlinx.coroutines.*
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var channelsRecyclerView: RecyclerView
    private lateinit var currentProgramText: TextView
    private lateinit var channelAdapter: ChannelAdapter

    private val epgRepository = EPGRepository()
    private val preferences by lazy { AppPreferences(applicationContext) }

    private var allPrograms = mapOf<String, List<com.example.tvapp.data.Program>>()
    private var lastSelectedChannelId: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var epgLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelsRecyclerView = findViewById(R.id.channelsRecyclerView)
        currentProgramText = findViewById(R.id.currentProgramText)
        val settingsButton: android.widget.ImageView = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener { openSettings() }
        val epgButton: android.widget.ImageView = findViewById(R.id.epgButton)
        epgButton.setOnClickListener { openEPG() }

        setupChannelsGrid()
        loadEPG()
        restoreLastChannel()
    }

    private fun setupChannelsGrid() {
        val spanCount = 5
        channelAdapter = ChannelAdapter(
            channels = ChannelList.channels,
            onChannelSelected = { channel ->
                lastSelectedChannelId = channel.id
                preferences.lastChannelId = channel.id
                openPlayer(channel)
            }
        )

        channelsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanCount)
            adapter = channelAdapter
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }

    private fun loadEPG() {
        epgLoadJob?.cancel()
        epgLoadJob = scope.launch {
            try {
                val date = Date()
                allPrograms = withContext(Dispatchers.IO) {
                    epgRepository.getProgramsForAllChannels(date)
                }
                updateCurrentPrograms()
                launch {
                    while (isActive) {
                        delay(60000)
                        updateCurrentPrograms()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.epg_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMoscowTime(): Long {
        return System.currentTimeMillis()
    }

    private fun updateCurrentPrograms() {
        val moscowNow = getMoscowTime()
        val currentProgramsMap = mutableMapOf<String, String>()

        for ((channelId, programs) in allPrograms) {
            val currentProgram = epgRepository.getCurrentProgram(programs, moscowNow)
            currentProgram?.let {
                currentProgramsMap[channelId] = it.title
            }
        }

        channelAdapter.updateCurrentPrograms(currentProgramsMap)

        val channelIdToShow = lastSelectedChannelId ?: preferences.lastChannelId
        if (channelIdToShow != null) {
            currentProgramsMap[channelIdToShow]?.let {
                currentProgramText.text = getString(R.string.now_playing, it)
            } ?: run {
                currentProgramText.text = getString(R.string.select_channel)
            }
        } else {
            val firstChannelId = ChannelList.channels.firstOrNull()?.id
            if (firstChannelId != null) {
                currentProgramsMap[firstChannelId]?.let {
                    currentProgramText.text = getString(R.string.now_playing, it)
                } ?: run {
                    currentProgramText.text = getString(R.string.select_channel)
                }
            } else {
                currentProgramText.text = getString(R.string.select_channel)
            }
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
                }
                lastSelectedChannelId = lastChannelId
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadEPG()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { openSettings(); true }
            KeyEvent.KEYCODE_INFO -> { openEPG(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
