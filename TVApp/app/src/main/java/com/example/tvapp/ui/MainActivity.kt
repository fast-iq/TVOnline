package com.example.tvapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.ChannelRepository
import com.example.tvapp.data.LivePrograms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private enum class ViewMode { ALL, FAVORITES, HISTORY }

    private lateinit var channelsRecyclerView: RecyclerView
    private lateinit var currentProgramText: TextView
    private lateinit var regionText: TextView
    private lateinit var emptyText: TextView
    private lateinit var favoritesButton: ImageView
    private lateinit var historyButton: ImageView
    private lateinit var channelAdapter: ChannelAdapter

    private val channelRepository by lazy { ChannelRepository(applicationContext) }
    private val preferences by lazy { AppPreferences(applicationContext) }

    private var viewMode = ViewMode.ALL
    private var displayedChannels: List<Channel> = emptyList()
    private var lastSelectedChannelId: String? = null
    private var lastFocusedChannelId: String? = null
    private var pendingFocusRestore: Boolean = false
    private var liveTickerJob: Job? = null

    private var numberInput: NumberInputController? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelsRecyclerView = findViewById(R.id.channelsRecyclerView)
        currentProgramText = findViewById(R.id.currentProgramText)
        regionText = findViewById(R.id.regionText)
        emptyText = findViewById(R.id.emptyText)
        numberInput = NumberInputController(this, findViewById(R.id.numberOverlay)) { channel ->
            selectChannelByNumber(channel)
        }
        updateRegionDisplay()
        setupHeaderButton(R.id.settingsButton) { openSettings() }
        setupHeaderButton(R.id.epgButton) { openEPG() }
        setupHeaderButton(R.id.searchButton) { openSearch() }

        favoritesButton = findViewById(R.id.favoritesButton)
        favoritesButton.setOnClickListener { toggleMode(ViewMode.FAVORITES) }
        favoritesButton.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.2f else 1.0f
            v.scaleY = if (hasFocus) 1.2f else 1.0f
            updateModeButtonTints()
        }

        historyButton = findViewById(R.id.historyButton)
        historyButton.setOnClickListener { toggleMode(ViewMode.HISTORY) }
        historyButton.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.2f else 1.0f
            v.scaleY = if (hasFocus) 1.2f else 1.0f
            updateModeButtonTints()
        }

        setupChannelsGrid()
        loadChannels()
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun setupHeaderButton(id: Int, action: () -> Unit) {
        val button: ImageView = findViewById(id)
        button.setOnClickListener { action() }
        button.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.2f else 1.0f
            v.scaleY = if (hasFocus) 1.2f else 1.0f
        }
    }

    private fun loadChannels() {
        scope.launch {
            // 1. Мгновенно показываем последний известный список с диска (логотипы подгрузятся через Glide).
            val disk = withContext(Dispatchers.IO) { channelRepository.loadFromDisk() }
            if (!disk.isNullOrEmpty()) {
                ChannelList.updateChannels(disk)
                applyViewMode()
                updateHeaderProgram(disk)
                restoreFocusIfNeeded()
            } else {
                updateEmptyState()
            }
            // 2. Фоновое обновление с сети — заменит список свежими логотипами/программами.
            try {
                val channels = withContext(Dispatchers.IO) { channelRepository.getChannels() }
                if (channels.isNotEmpty()) {
                    applyViewMode()
                    restoreLastChannel()
                    updateHeaderProgram(channels)
                    restoreFocusIfNeeded()
                }
                updateEmptyState()
            } catch (e: Exception) {
                Log.e("MainActivity", "channel refresh failed: ${e.message}")
                updateEmptyState()
            }
        }
    }

    /** Пересобирает сетку из ChannelList.channels с учётом выбранного режима. */
    private fun applyViewMode() {
        val all = ChannelList.channels
        displayedChannels = when (viewMode) {
            ViewMode.ALL -> all
            ViewMode.FAVORITES -> ChannelGrouping.favoriteChannels(all, preferences.favoriteChannelIds)
            ViewMode.HISTORY -> ChannelGrouping.historyChannels(all, preferences.watchHistory)
        }
        val items = when (viewMode) {
            ViewMode.ALL -> ChannelGrouping.buildItems(displayedChannels) { categoryLabel(this, it) }
            else -> displayedChannels.map { GridItem.Card(it) }
        }
        channelAdapter.updateItems(items)
        updateModeButtonTints()
        updateEmptyState()
    }

    private fun toggleMode(mode: ViewMode) {
        viewMode = if (viewMode == mode) ViewMode.ALL else mode
        applyViewMode()
    }

    private fun updateModeButtonTints() {
        val active = ContextCompat.getColor(this, R.color.accent_secondary)
        val inactive = ContextCompat.getColor(this, R.color.text_secondary)
        favoritesButton.setColorFilter(if (viewMode == ViewMode.FAVORITES) active else inactive)
        historyButton.setColorFilter(if (viewMode == ViewMode.HISTORY) active else inactive)
    }

    private fun toggleFavorite(channel: Channel) {
        val favorites = preferences.favoriteChannelIds.toMutableSet()
        val added: Boolean
        if (favorites.contains(channel.id)) {
            favorites.remove(channel.id)
            added = false
        } else {
            favorites.add(channel.id)
            added = true
        }
        preferences.favoriteChannelIds = favorites
        channelAdapter.refreshFavorites()
        android.widget.Toast.makeText(
            this,
            if (added) R.string.added_to_favorites else R.string.removed_from_favorites,
            android.widget.Toast.LENGTH_SHORT
        ).show()
        if (viewMode == ViewMode.FAVORITES) applyViewMode()
    }

    private fun updateEmptyState() {
        if (channelAdapter.itemCount > 0) {
            emptyText.visibility = View.GONE
            return
        }
        emptyText.visibility = View.VISIBLE
        emptyText.text = when {
            displayedChannels.isEmpty() && ChannelList.channels.isEmpty() -> getString(R.string.loading_channels)
            viewMode == ViewMode.FAVORITES -> getString(R.string.favorites_empty)
            viewMode == ViewMode.HISTORY -> getString(R.string.history_empty)
            else -> getString(R.string.loading_channels)
        }
    }

    private fun startLiveTicker() {
        if (liveTickerJob?.isActive == true) return
        liveTickerJob = scope.launch {
            while (true) {
                delay(LIVE_TICK_MS)
                channelAdapter.refreshLive()
                updateHeaderProgram(ChannelList.channels)
            }
        }
    }

    private fun stopLiveTicker() {
        liveTickerJob?.cancel()
        liveTickerJob = null
    }

    private fun adapterPositionForChannel(channelId: String): Int {
        val index = displayedChannels.indexOfFirst { it.id == channelId }
        if (index < 0) return -1
        val firstChannel = displayedChannels.firstOrNull() ?: return -1
        val firstAdapterPos = if (viewMode == ViewMode.ALL) {
            firstAdapterPositionOf(firstChannel)
        } else {
            0
        }
        return firstAdapterPos + index
    }

    /** В режиме ALL перед каждой категорией стоит заголовок-строка; вычисляем сдвиг. */
    private fun firstAdapterPositionOf(channel: Channel): Int {
        val headers = ChannelGrouping.categoriesInOrder(displayedChannels)
            .takeWhile { it != channel.category }
            .size
        return headers + ChannelGrouping.channelsByCategory(displayedChannels, channel.category)
            .indexOfFirst { it.id == channel.id }
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

    private fun restoreFocusIfNeeded() {
        if (!pendingFocusRestore) return
        val channelId = lastFocusedChannelId ?: return
        val position = adapterPositionForChannel(channelId)
        if (position >= 0) {
            pendingFocusRestore = false
            focusOnPosition(position)
        }
    }

    private fun setupChannelsGrid() {
        val spanCount = GRID_SPAN_COUNT
        channelAdapter = ChannelAdapter(
            onChannelSelected = { channel -> openChannel(channel) },
            onChannelLongPressed = { channel -> toggleFavorite(channel) },
            onFocusPositionChanged = { position ->
                val channel = channelAtAdapterPosition(position)
                if (channel != null) lastFocusedChannelId = channel.id
            },
            isFavorite = { id -> preferences.favoriteChannelIds.contains(id) }
        )

        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (channelAdapter.getItemViewType(position) == ChannelAdapter.TYPE_HEADER) spanCount else 1
        }
        channelsRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = channelAdapter
            itemAnimator = null
        }
        updateEmptyState()
    }

    private fun channelAtAdapterPosition(position: Int): Channel? = displayedChannels.getOrNull(cardIndexAt(position))

    /** Индекс карточки-канала по позиции адаптера (без заголовков категорий). */
    private fun cardIndexAt(position: Int): Int {
        if (viewMode != ViewMode.ALL) return position
        var cards = 0
        for (pos in 0 until position) {
            if (channelAdapter.getItemViewType(pos) != ChannelAdapter.TYPE_HEADER) cards++
        }
        return cards
    }

    private fun updateHeaderProgram(channels: List<Channel>) {
        val channelIdToShow = lastSelectedChannelId ?: preferences.lastChannelId
        val channel = channels.find { it.id == channelIdToShow }
            ?: channels.firstOrNull()
        if (channel != null) {
            val title = LivePrograms.current(channel)?.title
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
        val lastChannelId = preferences.lastChannelId ?: return
        if (lastFocusedChannelId == null) {
            lastFocusedChannelId = ChannelList.channels.find { it.id == lastChannelId }?.let { lastChannelId }
        }
        lastSelectedChannelId = lastChannelId
    }

    private fun updateRegionDisplay() {
        val code = preferences.limehdRegion
        val name = if (code == 0) {
            getString(R.string.region_auto)
        } else {
            preferences.limehdRegionName ?: code.toString()
        }
        regionText.text = getString(R.string.region_prefix, name)
    }

    override fun onResume() {
        super.onResume()
        updateRegionDisplay()
        if (lastFocusedChannelId != null) {
            pendingFocusRestore = true
        }
        loadChannels()
        startLiveTicker()
    }

    override fun onPause() {
        stopLiveTicker()
        super.onPause()
    }

    private fun openChannel(channel: Channel) {
        lastSelectedChannelId = channel.id
        preferences.lastChannelId = channel.id
        preferences.addToWatchHistory(channel.id)
        openPlayer(channel)
    }

    private fun openPlayer(channel: Channel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("channel_name", channel.name)
            putExtra("stream_url", channel.streamUrl)
            putExtra("logo_url", channel.logoUrl)
        }
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, com.example.tvapp.settings.SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openEPG() {
        val intent = Intent(this, EPGActivity::class.java)
        startActivity(intent)
    }

    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
    }

    private fun selectChannelByNumber(channel: Channel) {
        if (!displayedChannels.any { it.id == channel.id } && viewMode != ViewMode.ALL) {
            viewMode = ViewMode.ALL
            applyViewMode()
        }
        openChannel(channel)
        val position = adapterPositionForChannel(channel.id)
        if (position >= 0) {
            lastFocusedChannelId = channel.id
            focusOnPosition(position)
        }
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
        KeyEvent.KEYCODE_BACK -> {
            val input = numberInput
            when {
                input != null && input.isActive() -> {
                    input.cancel()
                    true
                }
                viewMode != ViewMode.ALL -> {
                    viewMode = ViewMode.ALL
                    applyViewMode()
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            if ((event?.repeatCount ?: 0) == 0) {
                numberInput?.onDigit('0' + (keyCode - KeyEvent.KEYCODE_0))
            }
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val LIVE_TICK_MS = 60_000L
        private const val GRID_SPAN_COUNT = 5
    }

    override fun onDestroy() {
        super.onDestroy()
        numberInput?.release()
        scope.cancel()
    }
}
