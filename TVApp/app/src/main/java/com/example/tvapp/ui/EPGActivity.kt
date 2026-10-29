package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.EPGRepository
import com.example.tvapp.data.Program
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    }

    private lateinit var currentTimeText: TextView
    private lateinit var timeScaleContainer: FrameLayout
    private lateinit var labelsList: RecyclerView
    private lateinit var rowsList: RecyclerView
    private lateinit var timeScaleScroll: HorizontalScrollView
    private lateinit var epgHorizontalScroll: HorizontalScrollView

    private val epgRepository by lazy { EPGRepository(applicationContext) }
    private var channels: List<Channel> = emptyList()
    private lateinit var rowAdapter: EpgRowAdapter
    private lateinit var labelAdapter: EpgLabelAdapter
    private var accumulatedPrograms: Map<String, List<Program>> = emptyMap()

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var pixelsPerMinute = 2.5f
    private var totalWidthPx = 0
    private val rowHeight = 70
    private val totalMinutes = 720
    private val leadMinutes = 30
    private val visibleMinutes = 90
    private val nowViewportOffsetMinutes = 30

    private lateinit var displayTz: TimeZone

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
        epgHorizontalScroll.doOnLayout {
            setupEPGGrid()
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

    private fun setupEPGGrid() {
        channels = ChannelList.channels.take(EPG_MAX_CHANNELS)
        val viewportWidth = epgHorizontalScroll.width
        if (viewportWidth <= 0) return
        pixelsPerMinute = viewportWidth.toFloat() / visibleMinutes
        totalWidthPx = (totalMinutes * pixelsPerMinute).toInt()
        createTimeScale()

        rowsList.layoutParams = rowsList.layoutParams.apply { width = totalWidthPx }
        rowsList.layoutManager = LinearLayoutManager(this)
        rowsList.setHasFixedSize(true)
        rowsList.itemAnimator = null
        rowAdapter = EpgRowAdapter(this)
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
        for (hourStart in firstHourMark..(baseTime + totalMinutes * 60_000L) step 3600_000L) {
            val calendar = Calendar.getInstance(displayTz).apply { timeInMillis = hourStart }
            val hourVal = calendar.get(Calendar.HOUR_OF_DAY)
            val offsetMin = (hourStart - baseTime) / 60_000L
            if (offsetMin < 0 || offsetMin > totalMinutes) continue
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
        val windowEnd = baseTime + totalMinutes * 60_000L
        val useful = partial.filterValues { programs ->
            programs.any { it.endTime > baseTime && it.startTime < windowEnd }
        }
        accumulatedPrograms = accumulatedPrograms + useful
        rowAdapter.setData(channels, accumulatedPrograms, baseTime, pixelsPerMinute, totalMinutes, rowHeight, totalWidthPx, displayTz)
    }

    private fun getStartTimeMillis(): Long {
        val calendar = Calendar.getInstance(displayTz).apply {
            timeInMillis = System.currentTimeMillis() - leadMinutes * 60_000L
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun loadEPGForDate() {
        scope.launch {
            try {
                val instant = channels.associate { channel -> channel.id to channel.programs }
                    .filterValues { it.isNotEmpty() }
                if (instant.isNotEmpty()) {
                    updatePrograms(instant)
                    epgHorizontalScroll.post { scrollToCurrentTime() }
                }
                val finalResult = withContext(Dispatchers.IO) {
                    epgRepository.getProgramsWithSurroundings(channels, Date()) { partial ->
                        updatePrograms(partial)
                    }
                }
                updatePrograms(finalResult)
                epgHorizontalScroll.post { scrollToCurrentTime() }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            finish()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            rowsList.smoothScrollBy(0, -rowHeight)
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            rowsList.smoothScrollBy(0, rowHeight)
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            scrollByMinutes(-15)
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            scrollByMinutes(15)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun scrollByMinutes(minutes: Int) {
        val maxScroll = totalWidthPx - epgHorizontalScroll.width
        val newScrollX = (epgHorizontalScroll.scrollX + minutes * pixelsPerMinute).toInt().coerceIn(0, maxScroll.coerceAtLeast(0))
        epgHorizontalScroll.smoothScrollTo(newScrollX, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
