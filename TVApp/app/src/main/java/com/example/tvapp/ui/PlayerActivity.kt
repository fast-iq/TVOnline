package com.example.tvapp.ui

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.media3.ui.PlayerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ArchivePlayback
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.player.TVPlayerManager
import com.example.tvapp.player.TrackLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : BaseActivity() {

    // Плеер всегда на чёрном фоне: тема задаёт акценты, но не светлые поверхности.
    override fun prefersBlackStage(): Boolean = true

    companion object {
        private const val OVERLAY_VISIBLE_MS = 5000L

        /** Быстрые пресеты отмотки time-shift (минуты назад от «сейчас»). */
        private val TIMESHIFT_OFFSET_MINUTES = listOf(5, 15, 30, 60)

        /**
         * Клавиши, которые в PiP-окне нечего делать: диалоги и оверлеи в
         * плавающем окне невидимы. BACK при этом НЕ глушим: с пульта это
         * штатный «стоп» — останавливает воспроизведение и закрывает окно
         * (крестика на ТВ-пульте нет).
         */
        private val PIP_IGNORED_KEYS = setOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            0x52c1 // кнопка «источник» на части пультов
        )
    }

    /** Чисто для тестов: подавляется ли клавиша в режиме картинки-в-картинке. */
    internal fun pipBlocksKey(keyCode: Int, inPip: Boolean): Boolean = inPip && keyCode in PIP_IGNORED_KEYS

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
    private val preferences by lazy { AppPreferences(applicationContext) }
    private var overlayHideJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerMinutes = 0
    private var numberInput: NumberInputController? = null

    /** Начало архивного окна при просмотре time-shift; null — мы в живом эфире. */
    private var timeshiftFromMs: Long? = null

    /** Воспроизведение идёт в PiP-окне — системный onPause паузу ставить не должен. */
    private var pipActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_player)

            channelId = intent.getStringExtra("channel_id")
            streamUrl = intent.getStringExtra("stream_url")
            channelName = intent.getStringExtra("channel_name")

            currentChannel = ChannelList.channels.find { it.id == channelId }

            val archiveBase = intent.getStringExtra("archive_url").orEmpty()
            val archiveStart = intent.getLongExtra("archive_start", 0L)
            val archiveEnd = intent.getLongExtra("archive_end", 0L)
            val archivePlayUrl = if (archiveBase.isNotEmpty() && archiveStart > 0L && archiveEnd > archiveStart) {
                ArchivePlayback.buildUrl(archiveBase, archiveStart, archiveEnd)
            } else {
                null
            }

            playerView = findViewById(R.id.playerView)
            infoText = findViewById(R.id.infoText)
            programOverlay = findViewById(R.id.programOverlay)
            programNowText = findViewById(R.id.programNowText)
            programNextText = findViewById(R.id.programNextText)

            numberInput = NumberInputController(this, findViewById(R.id.numberOverlay)) { channel ->
                playSelectedChannel(channel)
            }

            playerManager = TVPlayerManager(this)

            playerManager.initializePlayer(
                playerView,
                object : TVPlayerManager.PlayerCallback {
                    override fun onPlaybackReady() {
                        runOnUiThread {
                            infoText.visibility = View.GONE
                        }
                    }

                    override fun onPlaybackError(error: String) {
                        runOnUiThread {
                            val protectedByDrm = currentChannel?.isDrm == true
                            val message = if (protectedByDrm) {
                                getString(R.string.drm_playback_error, error)
                            } else {
                                getString(R.string.playback_error, error)
                            }
                            infoText.text = message
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

                    override fun onPlaybackEnded() {
                        // Архивное окно (time-shift) закончилось — «догоняем» живой эфир.
                        runOnUiThread {
                            if (timeshiftFromMs != null) returnToLive()
                        }
                    }
                }
            )

            if (currentChannel != null) {
                preferences.addToWatchHistory(currentChannel!!.id)
                infoText.text = getString(R.string.loading_channel, channelName)
                infoText.visibility = View.VISIBLE
                val playUrl = archivePlayUrl ?: currentChannel!!.streamUrl.ifBlank { streamUrl ?: "" }
                val playChannel = if (archivePlayUrl != null) {
                    currentChannel!!.copy(streamUrl = archivePlayUrl, fallbackStreamUrls = emptyList())
                } else {
                    currentChannel!!
                }
                playerManager.playChannel(playChannel, playUrl)
                if (archivePlayUrl != null) timeshiftFromMs = archiveStart
                showProgramOverlay(currentChannel!!)
            } else if (!streamUrl.isNullOrEmpty()) {
                val url = archivePlayUrl ?: streamUrl!!
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
        // PiP-окно: система «приостанавливает» активность формально, воспроизведение остаётся.
        if (isInPictureInPictureMode || pipActive) return
        playerManager.pause()
    }

    /**
     * Картинка-в-картинке: HOME во время воспроизведения отправляет плеер в
     * плавающее окно. На устройствах без поддержки (и до запуска воспроизведения) — ничего.
     */
    /** Видимость для теста: сам хук protected в Activity. */
    internal fun userLeaveHint(): Unit = onUserLeaveHint()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isInPictureInPictureMode) return
        if (currentChannel == null) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    val rect = Rect()
                    playerView.getWindowVisibleDisplayFrame(rect)
                    if (!rect.isEmpty) setSourceRectHint(rect)
                }
                .build()
            if (enterPictureInPictureMode(params)) {
                pipActive = true
            }
        } catch (e: Exception) {
            Log.w("PlayerActivity", "PiP enter failed: ${e.message}")
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // В плавающем окне видно только видеополотно — прячем оверлеи и отменяем их таймеры.
            overlayHideJob?.cancel()
            programOverlay.visibility = View.GONE
            infoText.visibility = View.GONE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when {
        pipBlocksKey(keyCode, isInPictureInPictureMode) -> true
        else -> onKeyDownUnlocked(keyCode, event)
    }

    private fun onKeyDownUnlocked(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            val input = numberInput
            if (input != null && input.isActive()) {
                input.cancel()
                true
            } else {
                finish()
                true
            }
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            // Долгое удержание ОК — явный вход в PiP (на пультах без HOME).
            if (event?.isLongPress == true && !isInPictureInPictureMode && currentChannel != null) {
                userLeaveHint()
            } else if (!playerManager.isPlaying()) {
                playerManager.resume()
            }
            true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
            // Работает и в PiP: пульт = плей/пауза, BACK = стоп.
            if (playerManager.isPlaying()) playerManager.pause() else playerManager.resume()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY -> {
            playerManager.resume()
            true
        }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            playerManager.pause()
            true
        }
        KeyEvent.KEYCODE_MENU, 0x52c1 -> {
            showSourceSwitchDialog()
            true
        }
        KeyEvent.KEYCODE_INFO -> {
            showSleepTimerDialog()
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            onTimeshiftKey()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            showAudioTrackDialog()
            true
        }
        KeyEvent.KEYCODE_CAPTIONS -> {
            showSubtitleDialog()
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            switchChannel(1)
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            switchChannel(-1)
            true
        }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            if ((event?.repeatCount ?: 0) == 0) {
                numberInput?.onDigit('0' + (keyCode - KeyEvent.KEYCODE_0))
            }
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun playSelectedChannel(channel: Channel) {
        channelId = channel.id
        channelName = channel.name
        currentChannel = channel
        timeshiftFromMs = null
        preferences.lastChannelId = channel.id
        preferences.addToWatchHistory(channel.id)
        playerManager.playChannel(channel, channel.streamUrl)
        showProgramOverlay(channel)
        infoText.text = getString(R.string.loading_channel, channel.name)
        infoText.visibility = View.VISIBLE
    }

    private fun switchChannel(direction: Int) {
        val channels = ChannelList.channels
        if (channels.isEmpty()) return
        val currentId = channelId ?: return
        val currentIndex = channels.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return
        val newIndex = (currentIndex + direction + channels.size) % channels.size
        val newChannel = channels[newIndex]
        channelId = newChannel.id
        channelName = newChannel.name
        currentChannel = newChannel
        timeshiftFromMs = null
        preferences.addToWatchHistory(newChannel.id)
        playerManager.playChannel(
            newChannel,
            newChannel.streamUrl
        )
        showProgramOverlay(newChannel)
        infoText.text = getString(R.string.loading_channel, newChannel.name)
        infoText.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        numberInput?.release()
        scope.cancel()
        playerManager.releasePlayer()
    }

    /**
     * Time-shift живого эфира через архив канала (LimeHD `index-{from}-{to}.m3u8`):
     * Кнопка ВНИЗ открывает меню отмотки, повторное нажатие в режиме
     * time-shift возвращает в прямой эфир. По окончании архивного окна — авторат в эфир.
     */
    private fun onTimeshiftKey() {
        if (timeshiftFromMs != null) {
            returnToLive()
            return
        }
        val channel = currentChannel ?: return
        if (!ArchivePlayback.hasArchive(channel)) {
            Toast.makeText(this, R.string.timeshift_no_archive, Toast.LENGTH_SHORT).show()
            return
        }
        val offsets = timeshiftMenuOptions(channel, System.currentTimeMillis())
        if (offsets.isEmpty()) {
            Toast.makeText(this, R.string.timeshift_no_archive, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.timeshift_title)
            .setItems(offsets.map { it.first }.toTypedArray()) { _, which -> startTimeshift(offsets[which].second) }
            .show()
    }

    /**
     * Доступные точки старта отмотки: начало текущей передачи (если архив её
     * покрывает) + пресеты «N минут назад». Пусто — предлагать нечего.
     */
    internal fun timeshiftMenuOptions(channel: Channel, nowMs: Long): List<Pair<String, Long>> {
        val offsets = mutableListOf<Pair<String, Long>>()
        val programStart = channel.currentProgramStart
        if (programStart != null && ArchivePlayback.windowFor(channel, programStart, nowMs, nowMs) != null) {
            offsets.add(getString(R.string.timeshift_from_program) to programStart)
        }
        for (minutes in TIMESHIFT_OFFSET_MINUTES) {
            val from = nowMs - minutes * 60_000L
            if (ArchivePlayback.windowFor(channel, from, nowMs, nowMs) != null) {
                offsets.add(getString(R.string.timeshift_minutes_ago, minutes) to from)
            }
        }
        return offsets
    }

    private fun startTimeshift(fromMs: Long) {
        val channel = currentChannel ?: return
        val now = System.currentTimeMillis()
        val window = ArchivePlayback.windowFor(channel, fromMs, now, now) ?: return
        val url = ArchivePlayback.buildUrl(channel.archiveUrl, window.first, window.second)
        timeshiftFromMs = window.first
        // В currentChannel остаётся живой канал — на него возвращаемся по конце окна/по кнопке.
        playerManager.playChannel(channel.copy(streamUrl = url, fallbackStreamUrls = emptyList()), url)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(window.first))
        Toast.makeText(this, getString(R.string.timeshift_started, time), Toast.LENGTH_SHORT).show()
    }

    private fun returnToLive() {
        val channel = currentChannel ?: return
        timeshiftFromMs = null
        playerManager.playChannel(channel, channel.streamUrl)
        Toast.makeText(this, R.string.timeshift_back_live, Toast.LENGTH_SHORT).show()
    }

    /** Диалог аудиодорожек (кнопка ВВЕРХ). */
    private fun showAudioTrackDialog() {
        val groups = playerManager.getAudioTrackGroups()
        val labels = TrackLabels.audioLabels(groups, getString(R.string.track_number))
        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.track_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val current = TrackLabels.selectedTrackIndex(groups)
        val items = (listOf(getString(R.string.audio_track_auto)) + labels.mapIndexed { i, l -> if (i == current) "$l ${getString(R.string.source_current)}" else l })
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_track_title)
            .setItems(items) { _, which ->
                playerManager.selectAudioTrack(which)
                Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** Диалог субтитров (клавиша CAPTIONS на пультах с ней). */
    private fun showSubtitleDialog() {
        val groups = playerManager.getTextTrackGroups()
        val labels = TrackLabels.textLabels(groups, getString(R.string.track_number))
        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.subtitle_none, Toast.LENGTH_SHORT).show()
            return
        }
        val current = TrackLabels.selectedTrackIndex(groups)
        val items = (listOf(getString(R.string.subtitle_off)) + labels.mapIndexed { i, l -> if (i == current) "$l ${getString(R.string.source_current)}" else l })
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.subtitle_title)
            .setItems(items) { _, which ->
                playerManager.selectTextTrack(which)
                Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val options = listOf(0, 15, 30, 60, 90)
        val labels = options.map { minutes ->
            val base = if (minutes == 0) {
                getString(R.string.sleep_timer_off)
            } else {
                getString(R.string.sleep_timer_minutes, minutes)
            }
            val active = sleepTimerJob?.isActive == true && sleepTimerMinutes == minutes
            if (active) "$base ${getString(R.string.source_current)}" else base
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sleep_timer_title))
            .setItems(labels.toTypedArray()) { _, which ->
                applySleepTimer(options[which])
            }
            .show()
    }

    private fun applySleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMinutes = minutes
        if (minutes <= 0) return
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            playerManager.pause()
            Toast.makeText(this@PlayerActivity, R.string.sleep_timer_fired, Toast.LENGTH_LONG).show()
        }
        Toast.makeText(this, getString(R.string.sleep_timer_set, minutes), Toast.LENGTH_SHORT).show()
    }

    private fun showSourceSwitchDialog() {
        val channel = currentChannel ?: return
        val urls = playerManager.getCurrentStreamUrls()
        if (urls.size <= 1) {
            Toast.makeText(this, getString(R.string.only_one_source), Toast.LENGTH_SHORT).show()
            return
        }

        val currentIndex = playerManager.getCurrentUrlIndex()
        val items = mutableListOf<String>()
        for (i in urls.indices) {
            val label = when {
                i == 0 -> getString(R.string.source_official)
                i == 1 -> getString(R.string.source_alternative)
                else -> getString(R.string.source_alternative_indexed, i - 1)
            }
            val marker = if (i == currentIndex) " " + getString(R.string.source_current) else ""
            items.add("$label$marker")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.switch_source_title))
            .setItems(items.toTypedArray()) { _, which ->
                playerManager.switchToUrl(urls[which])
                infoText.text = getString(R.string.loading_channel, channelName)
                infoText.visibility = View.VISIBLE
            }
            .show()
    }

    private fun showProgramOverlay(channel: Channel) {
        val title = channel.currentProgramTitle
        if (title == null || title.isBlank()) {
            programOverlay.visibility = View.GONE
            return
        }

        val tz = preferences.programTimeZone()
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
        scheduleOverlayHide()
    }

    private fun scheduleOverlayHide() {
        overlayHideJob?.cancel()
        overlayHideJob = scope.launch {
            delay(OVERLAY_VISIBLE_MS)
            programOverlay.visibility = View.GONE
        }
    }
}
