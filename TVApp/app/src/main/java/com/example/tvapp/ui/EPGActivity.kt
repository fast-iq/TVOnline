package com.example.tvapp.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.NotificationHelper
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ArchivePlayback
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.EPGRepository
import com.example.tvapp.data.MarkedProgram
import com.example.tvapp.data.Program
import com.example.tvapp.data.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EPGActivity : BaseActivity() {

    companion object {
        private const val EPG_MAX_CHANNELS = 80
        private const val MARK_CHECK_INTERVAL_MS = 5_000L
        private const val MARK_NOTIFY_AHEAD_MS = 60_000L
        private const val FULL_DAY_MINUTES = 1440
    }

    private lateinit var currentTimeText: TextView
    private lateinit var timeScaleContainer: FrameLayout
    private lateinit var labelsList: RecyclerView
    private lateinit var rowsList: RecyclerView
    private lateinit var timeScaleScroll: HorizontalScrollView
    private lateinit var epgHorizontalScroll: HorizontalScrollView

    private val epgRepository by lazy { EPGRepository(applicationContext) }
    private val preferences by lazy { AppPreferences(applicationContext) }
    private var channels: List<Channel> = emptyList()
    private lateinit var rowAdapter: EpgRowAdapter
    private lateinit var labelAdapter: EpgLabelAdapter
    private var accumulatedPrograms: Map<String, List<Program>> = emptyMap()

    private var cursorChannel: Channel? = null
    private var cursorProgram: Program? = null
    private val markedPrograms = LinkedHashMap<String, MarkedProgram>()
    private val promptedKeys = mutableSetOf<String>()
    private var promptShowing = false

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var pixelsPerMinute = 2.5f
    private var totalWidthPx = 0
    private val rowHeight = 70
    private val totalMinutes = 720
    private val leadMinutes = 30
    private val visibleMinutes = 90
    private val nowViewportOffsetMinutes = 30

    private lateinit var displayTz: TimeZone

    private var dayDelta = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)

        val prefs = AppPreferences(applicationContext)
        displayTz = prefs.programTimeZone()

        currentTimeText = findViewById(R.id.currentTimeText)
        timeScaleContainer = findViewById(R.id.timeScaleContainer)
        labelsList = findViewById(R.id.epgLabelsList)
        rowsList = findViewById(R.id.epgRowsList)
        timeScaleScroll = findViewById(R.id.timeScaleScroll)
        epgHorizontalScroll = findViewById(R.id.epgHorizontalScroll)

        setupScrollSync()
        updateCurrentTimeDisplay()
        restoreMarks()
        startMarkedCheckLoop()
        epgHorizontalScroll.doOnLayout {
            setupEPGGrid()
            rowAdapter.setSelections(markedPrograms.keys)
            loadEPGForDate()
        }
    }

    private fun setupScrollSync() {
        timeScaleScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) epgHorizontalScroll.scrollX = timeScaleScroll.scrollX
        }
        epgHorizontalScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) timeScaleScroll.scrollX = epgHorizontalScroll.scrollX
        }
        rowsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) labelsList.scrollBy(0, dy)
            }
        })
    }

    @SuppressLint("InvalidSetHasFixedSize")
    private fun setupEPGGrid() {
        channels = ChannelList.channels.take(EPG_MAX_CHANNELS)
        val viewportWidth = epgHorizontalScroll.width
        if (viewportWidth <= 0) return
        pixelsPerMinute = viewportWidth.toFloat() / visibleMinutes
        totalWidthPx = (windowMinutes() * pixelsPerMinute).toInt()
        createTimeScale()

        rowsList.layoutParams = rowsList.layoutParams.apply { width = totalWidthPx }
        rowsList.layoutManager = LinearLayoutManager(this)
        rowsList.setHasFixedSize(true)
        rowsList.itemAnimator = null
        rowAdapter = EpgRowAdapter(this) { channel, program -> onProgramAction(channel, program) }
        rowsList.adapter = rowAdapter

        labelsList.layoutManager = LinearLayoutManager(this)
        labelAdapter = EpgLabelAdapter(rowHeight)
        labelsList.adapter = labelAdapter
        labelAdapter.setChannels(channels)

        accumulatedPrograms = emptyMap()
        updatePrograms(emptyMap())
    }

    private fun createTimeScale() {
        timeScaleContainer.removeAllViews()
        val baseTime = getStartTimeMillis()
        val pixelsPerHour = pixelsPerMinute * 60f
        val firstHourMark = (baseTime / 3600_000L * 3600_000L)
        for (hourStart in firstHourMark..(baseTime + windowMinutes() * 60_000L) step 3600_000L) {
            val calendar = Calendar.getInstance(displayTz).apply { timeInMillis = hourStart }
            val hourVal = calendar.get(Calendar.HOUR_OF_DAY)
            val offsetMin = (hourStart - baseTime) / 60_000L
            if (offsetMin < 0 || offsetMin > windowMinutes()) continue
            val timeText = TextView(this).apply {
                text = String.format(Locale.US, "%02d:00", hourVal)
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = android.view.Gravity.START
            }
            val params = FrameLayout.LayoutParams(pixelsPerHour.toInt(), FrameLayout.LayoutParams.MATCH_PARENT)
            params.marginStart = (offsetMin * pixelsPerMinute).toInt().coerceAtLeast(0)
            timeScaleContainer.addView(timeText, params)
        }
    }

    private fun updatePrograms(partial: Map<String, List<Program>>) {
        val baseTime = getStartTimeMillis()
        val windowEnd = baseTime + windowMinutes() * 60_000L
        val useful = partial.filterValues { programs ->
            programs.any { it.endTime > baseTime && it.startTime < windowEnd }
        }
        accumulatedPrograms = accumulatedPrograms + useful
        rowAdapter.setData(channels, accumulatedPrograms, baseTime, pixelsPerMinute, windowMinutes(), rowHeight, totalWidthPx, displayTz)
        if (cursorProgram == null) selectInitialCursor()
    }

    private fun getStartTimeMillis(): Long {
        val calendar = Calendar.getInstance(displayTz)
        if (dayDelta != 0) {
            calendar.timeInMillis = System.currentTimeMillis() + dayDelta * 24L * 60L * 60L * 1000L
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
        calendar.timeInMillis = System.currentTimeMillis() - leadMinutes * 60_000L
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun windowMinutes(): Int = if (dayDelta == 0) totalMinutes else FULL_DAY_MINUTES

    private fun selectDay(delta: Int) {
        if (delta == dayDelta) return
        dayDelta = delta
        val baseTime = getStartTimeMillis()
        val newWidth = (windowMinutes() * pixelsPerMinute).toInt()
        totalWidthPx = newWidth
        rowsList.layoutParams = rowsList.layoutParams.apply { width = newWidth }
        rowsList.requestLayout()
        createTimeScale()
        accumulatedPrograms = emptyMap()
        cursorChannel = null
        cursorProgram = null
        epgHorizontalScroll.scrollTo(0, 0)
        loadEPGForDate()
    }

    private fun showDaySelectDialog() {
        val labels = (-1..6).map { delta ->
            val base = when (delta) {
                -1 -> getString(R.string.epg_day_yesterday)
                0 -> getString(R.string.epg_day_today)
                1 -> getString(R.string.epg_day_tomorrow)
                else -> resources.getQuantityString(R.plurals.epg_day_format, delta, delta)
            }
            if (delta == dayDelta) "$base ${getString(R.string.source_current)}" else base
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.epg_day_select_title)
            .setItems(labels.toTypedArray()) { _, which ->
                selectDay(which - 1)
            }
            .show()
    }

    private fun showProgramSearchDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.epg_search_hint)
            setSingleLine(true)
            setPadding(48, 32, 48, 32)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.epg_search_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runProgramSearch(input.text.toString())
            }
            .setNegativeButton(R.string.no, null)
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun runProgramSearch(query: String) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return
        val results = mutableListOf<Pair<Channel, Program>>()
        for (channel in channels) {
            accumulatedPrograms[channel.id].orEmpty()
                .filter { it.title.lowercase().contains(normalized) }
                .sortedBy { it.startTime }
                .forEach { results.add(Pair(channel, it)) }
        }
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.epg_search_no_results, Toast.LENGTH_SHORT).show()
            return
        }
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = displayTz }
        val truncated = results.take(50)
        val labels = truncated.map { (channel, program) ->
            getString(
                R.string.epg_search_result,
                dateFormat.format(Date(program.startTime)),
                channel.name,
                program.title
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.epg_search_title)
            .setItems(labels.toTypedArray()) { _, which ->
                val (channel, program) = truncated[which]
                if (visiblePrograms(channel.id).any { it.startTime == program.startTime }) {
                    moveCursor(channel, program)
                } else {
                    openPlayer(channel)
                }
            }
            .show()
    }

    private fun loadEPGForDate() {
        scope.launch {
            try {
                if (dayDelta == 0) {
                    val instant = channels.associate { channel -> channel.id to channel.programs }
                        .filterValues { it.isNotEmpty() }
                    if (instant.isNotEmpty()) {
                        updatePrograms(instant)
                        epgHorizontalScroll.post { scrollToCurrentTime() }
                    }
                }
                val finalResult = withContext(Dispatchers.IO) {
                    if (dayDelta == 0) {
                        epgRepository.getProgramsWithSurroundings(channels, Date()) { partial ->
                            updatePrograms(partial)
                        }
                    } else {
                        val dayDate = Date(getStartTimeMillis())
                        epgRepository.getProgramsForDay(channels, dayDate, dayDelta) { partial ->
                            updatePrograms(partial)
                        }
                    }
                }
                updatePrograms(finalResult)
                epgHorizontalScroll.post {
                    if (dayDelta == 0) scrollToCurrentTime() else epgHorizontalScroll.scrollTo(0, 0)
                }
            } catch (e: Exception) {
                Toast.makeText(this@EPGActivity, R.string.epg_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollToCurrentTime() {
        val baseTime = getStartTimeMillis()
        val nowOffsetMin = (System.currentTimeMillis() - baseTime) / 1000f / 60f
        val targetX = ((nowOffsetMin - nowViewportOffsetMinutes) * pixelsPerMinute).toInt().coerceAtLeast(0)
        epgHorizontalScroll.scrollTo(targetX, 0)
    }

    private fun updateCurrentTimeDisplay() {
        val cal = Calendar.getInstance(displayTz).apply { timeInMillis = System.currentTimeMillis() }
        val dateFormat = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        currentTimeText.text = getString(R.string.time_msk, dateFormat.format(cal.time))
    }

    private fun selectInitialCursor() {
        val now = System.currentTimeMillis()
        channels.forEach { channel ->
            visiblePrograms(channel.id)
                .firstOrNull { it.startTime <= now && it.endTime > now }
                ?.let {
                    moveCursor(channel, it)
                    return
                }
        }
        channels.forEach { channel ->
            visiblePrograms(channel.id)
                .firstOrNull { it.startTime > now }
                ?.let {
                    moveCursor(channel, it)
                    return
                }
        }
        val first = channels.firstOrNull { visiblePrograms(it.id).isNotEmpty() } ?: return
        val firstProgram = visiblePrograms(first.id).firstOrNull() ?: return
        moveCursor(first, firstProgram)
    }

    private fun visiblePrograms(channelId: String): List<Program> {
        val baseTime = getStartTimeMillis()
        val windowEnd = baseTime + windowMinutes() * 60_000L
        return accumulatedPrograms[channelId].orEmpty().filter { it.endTime > baseTime && it.startTime < windowEnd }
    }

    private fun moveCursorVertical(delta: Int) {
        val channel = cursorChannel ?: return
        val time = cursorProgram?.startTime ?: System.currentTimeMillis()
        val currentIndex = channels.indexOfFirst { it.id == channel.id }
        val targetIndex = currentIndex + delta
        if (currentIndex < 0 || targetIndex !in channels.indices) return
        val targetChannel = channels[targetIndex]
        val list = visiblePrograms(targetChannel.id)
        val targetProgram = list.firstOrNull { it.endTime > time } ?: list.lastOrNull() ?: return
        moveCursor(targetChannel, targetProgram)
    }

    private fun moveCursorHorizontal(back: Boolean) {
        val channel = cursorChannel ?: return
        val program = cursorProgram ?: return
        val list = visiblePrograms(channel.id)
        val currentIndex = list.indexOfFirst { it.startTime == program.startTime && it.endTime == program.endTime }
        if (currentIndex < 0) {
            val nearest = list.minByOrNull { kotlin.math.abs(it.startTime - program.startTime) } ?: return
            moveCursor(channel, nearest)
            return
        }
        val targetIndex = currentIndex + if (back) -1 else 1
        if (targetIndex !in list.indices) return
        moveCursor(channel, list[targetIndex])
    }

    private fun moveCursor(channel: Channel, program: Program) {
        cursorChannel = channel
        cursorProgram = program
        rowAdapter.setCursor(EpgRowAdapter.keyOf(channel.id, program))
        ensureCursorVisible()
        scrollRowIntoView(channel)
    }

    private fun ensureCursorVisible() {
        val program = cursorProgram ?: return
        val baseTime = getStartTimeMillis()
        val leftPx = (((program.startTime - baseTime) / 60000f).coerceAtLeast(0f) * pixelsPerMinute).toInt()
        val rightPx = (((program.endTime - baseTime) / 60000f).coerceAtMost(windowMinutes().toFloat()) * pixelsPerMinute).toInt()
        val viewport = epgHorizontalScroll.width
        val scrollX = epgHorizontalScroll.scrollX
        val target = when {
            leftPx < scrollX -> (leftPx - 40).coerceAtLeast(0)
            rightPx > scrollX + viewport -> (rightPx - viewport + 40).coerceAtMost(totalWidthPx - viewport)
            else -> scrollX
        }
        if (target != scrollX) epgHorizontalScroll.smoothScrollTo(target, 0)
    }

    private fun scrollRowIntoView(channel: Channel) {
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index < 0) return
        val manager = rowsList.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first >= 0 && (index < first || index > last)) {
            rowsList.smoothScrollToPosition(index)
        }
    }

    private fun activateCursor() {
        val channel = cursorChannel ?: return
        val program = cursorProgram ?: return
        onProgramAction(channel, program)
    }

    private fun onProgramAction(channel: Channel, program: Program) {
        val now = System.currentTimeMillis()
        when {
            program.endTime <= now -> {
                if (!openArchivedPlayer(channel, program)) {
                    Toast.makeText(this, R.string.epg_program_finished, Toast.LENGTH_SHORT).show()
                }
            }
            program.startTime <= now -> {
                if (ArchivePlayback.hasArchive(channel)) {
                    showLiveOrArchiveDialog(channel, program)
                } else {
                    openPlayer(channel)
                }
            }
            else -> toggleMark(channel, program)
        }
    }

    private fun showLiveOrArchiveDialog(channel: Channel, program: Program) {
        AlertDialog.Builder(this)
            .setTitle(program.title)
            .setPositiveButton(R.string.epg_watch_from_start) { _, _ -> openArchivedPlayer(channel, program) }
            .setNegativeButton(R.string.epg_watch_live) { _, _ -> openPlayer(channel) }
            .show()
    }

    private fun openArchivedPlayer(channel: Channel, program: Program): Boolean {
        val window = ArchivePlayback.windowFor(channel, program.startTime, program.endTime, System.currentTimeMillis())
            ?: return false
        val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("channel_name", channel.name)
            putExtra("stream_url", channel.streamUrl)
            putExtra("logo_url", channel.logoUrl)
            putExtra("archive_url", channel.archiveUrl)
            putExtra("archive_start", window.first)
            putExtra("archive_end", window.second)
        }
        startActivity(intent)
        return true
    }

    private fun restoreMarks() {
        val now = System.currentTimeMillis()
        val stored = preferences.markedPrograms
        val future = stored.filter { it.endTime > now }
        if (future.size != stored.size) {
            preferences.markedPrograms = future
        }
        markedPrograms.clear()
        future.forEach { markedPrograms[it.key] = it }
        ReminderScheduler.sync(applicationContext)
    }

    private fun persistMarks() {
        preferences.markedPrograms = markedPrograms.values.toList()
        ReminderScheduler.sync(applicationContext)
    }

    private fun toggleMark(channel: Channel, program: Program) {
        val key = EpgRowAdapter.keyOf(channel.id, program)
        val removed = markedPrograms.remove(key)
        if (removed != null) {
            promptedKeys.remove(key)
            ReminderScheduler.cancel(applicationContext, removed)
        } else {
            markedPrograms[key] = MarkedProgram(
                channelId = channel.id,
                channelName = channel.name,
                streamUrl = channel.streamUrl,
                title = program.title,
                startTime = program.startTime,
                endTime = program.endTime
            )
        }
        persistMarks()
        rowAdapter.setSelections(markedPrograms.keys)
    }

    private fun startMarkedCheckLoop() {
        scope.launch {
            while (true) {
                delay(MARK_CHECK_INTERVAL_MS)
                checkMarkedPrograms()
            }
        }
    }

    private fun checkMarkedPrograms() {
        if (promptShowing || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || markedPrograms.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = markedPrograms.filter { (key, mark) ->
            key !in promptedKeys &&
                mark.startTime > now &&
                mark.startTime - now <= MARK_NOTIFY_AHEAD_MS
        }
        if (due.isEmpty()) return
        due.keys.forEach { promptedKeys.add(it) }
        due.values.forEach { mark ->
            NotificationHelper.showUpcomingProgramNotification(this, mark)
        }
        showMarkedPrompt(due.values.sortedBy { it.startTime })
    }

    private fun showMarkedPrompt(due: List<MarkedProgram>) {
        promptShowing = true
        val names = due.joinToString("\n") { "• ${it.title} — ${it.channelName}" }
        AlertDialog.Builder(this)
            .setTitle(R.string.epg_marked_title)
            .setMessage(getString(R.string.epg_marked_message, names))
            .setPositiveButton(R.string.yes) { _, _ ->
                val first = due.first()
                due.forEach { mark ->
                    markedPrograms.remove(mark.key)
                    ReminderScheduler.cancel(applicationContext, mark)
                }
                persistMarks()
                rowAdapter.setSelections(markedPrograms.keys)
                openMarkedPlayer(first)
            }
            .setNegativeButton(R.string.no, null)
            .setOnDismissListener { promptShowing = false }
            .show()
    }

    private fun openMarkedPlayer(mark: MarkedProgram) {
        val channel = ChannelList.channels.find { it.id == mark.channelId }
        if (channel != null) {
            openPlayer(channel)
        } else {
            val intent = android.content.Intent(this, PlayerActivity::class.java).apply {
                putExtra("channel_id", mark.channelId)
                putExtra("channel_name", mark.channelName)
                putExtra("stream_url", mark.streamUrl)
            }
            startActivity(intent)
        }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            finish()
            true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            activateCursor()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            moveCursorVertical(-1)
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            moveCursorVertical(1)
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            moveCursorHorizontal(true)
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            moveCursorHorizontal(false)
            true
        }
        KeyEvent.KEYCODE_MENU -> {
            showDaySelectDialog()
            true
        }
        KeyEvent.KEYCODE_INFO -> {
            showProgramSearchDialog()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
