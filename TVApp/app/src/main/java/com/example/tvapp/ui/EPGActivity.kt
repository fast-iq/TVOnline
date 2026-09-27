package com.example.tvapp.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.EPGRepository
import com.example.tvapp.data.Program
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class EPGActivity : BaseActivity() {

    private lateinit var currentTimeText: TextView
    private lateinit var timeScaleContainer: LinearLayout
    private lateinit var channelLabelsContainer: LinearLayout
    private lateinit var epgGridContainer: FrameLayout
    private lateinit var timeScaleScroll: HorizontalScrollView
    private lateinit var epgHorizontalScroll: HorizontalScrollView
    private lateinit var epgVerticalScroll: NestedScrollView
    private lateinit var channelLabelsScroll: NestedScrollView

    private val epgRepository = EPGRepository()
    private var channels: List<Channel> = emptyList()

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val pixelsPerHour = 150f
    private val pixelsPerMinute = pixelsPerHour / 60f
    private val rowHeight = 70
    private val totalHours = 24
    private val leadMinutes = 30

    private val moscowTz = TimeZone.getTimeZone("Europe/Moscow")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)

        currentTimeText = findViewById(R.id.currentTimeText)
        timeScaleContainer = findViewById(R.id.timeScaleContainer)
        channelLabelsContainer = findViewById(R.id.channelLabelsContainer)
        epgGridContainer = findViewById(R.id.epgGridContainer)
        timeScaleScroll = findViewById(R.id.timeScaleScroll)
        epgHorizontalScroll = findViewById(R.id.epgHorizontalScroll)
        epgVerticalScroll = findViewById(R.id.epgVerticalScroll)
        channelLabelsScroll = findViewById(R.id.channelLabelsScroll)

        setupEPGGrid()
        loadEPGForDate()
        updateCurrentTimeDisplay()
        setupScrollSync()
    }

    private fun setupScrollSync() {
        timeScaleScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) epgHorizontalScroll.scrollX = timeScaleScroll.scrollX
        }
        epgHorizontalScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) timeScaleScroll.scrollX = epgHorizontalScroll.scrollX
        }
        channelLabelsScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) epgVerticalScroll.scrollY = channelLabelsScroll.scrollY
        }
        epgVerticalScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!isChangingConfigurations) channelLabelsScroll.scrollY = epgVerticalScroll.scrollY
        }
    }

    private fun setupEPGGrid() {
        channels = ChannelList.channels
        createTimeScale()
        createChannelLabels()
        val totalWidth = (totalHours * pixelsPerHour).toInt()
        val totalHeight = channels.size * rowHeight
        epgGridContainer.layoutParams = FrameLayout.LayoutParams(totalWidth, totalHeight)
    }

    private fun createTimeScale() {
        timeScaleContainer.removeAllViews()
        val baseTime = getStartTimeMillis()
        for (hour in 0 until totalHours) {
            val hourStart = baseTime + hour * 3600_000L
            val calendar = Calendar.getInstance(moscowTz).apply { timeInMillis = hourStart }
            val hourVal = calendar.get(Calendar.HOUR_OF_DAY)
            val timeText = TextView(this).apply {
                text = String.format(Locale.US, "%02d:00", hourVal)
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                width = pixelsPerHour.toInt()
                gravity = android.view.Gravity.CENTER
            }
            timeScaleContainer.addView(timeText)
        }
    }

    private fun createChannelLabels() {
        channelLabelsContainer.removeAllViews()
        for (channel in channels) {
            val channelText = TextView(this).apply {
                text = channel.name
                textSize = 14f
                setTextColor(getColor(R.color.white))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeight
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 0, 16, 0)
                }
            }
            channelLabelsContainer.addView(channelText)
        }
    }

    private fun renderPrograms(programs: Map<String, List<Program>>) {
        epgGridContainer.removeAllViews()
        val baseTime = getStartTimeMillis()

        channels.forEachIndexed { index, channel ->
            val channelPrograms = programs[channel.id] ?: return@forEachIndexed
            channelPrograms.forEach { program ->
                val startTimeOffset = (program.startTime - baseTime) / 1000f / 60f
                val endTimeOffset = (program.endTime - baseTime) / 1000f / 60f
                val duration = (program.endTime - program.startTime) / 1000f / 60f

                if (endTimeOffset < 0f || startTimeOffset > totalHours * 60f) return@forEach

                val leftMargin = (startTimeOffset * pixelsPerMinute).toInt().coerceAtLeast(0)
                val width = (duration * pixelsPerMinute).toInt().coerceAtLeast(40)
                val programView = createProgramBlock(program, width)
                val params = FrameLayout.LayoutParams(width, rowHeight - 8).apply {
                    marginStart = leftMargin
                    topMargin = index * rowHeight + 4
                }
                epgGridContainer.addView(programView, params)
            }
        }
    }

    private fun createProgramBlock(program: Program, width: Int): View {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val container = FrameLayout(this).apply {
            setPadding(8, 8, 8, 8)
            setBackgroundResource(R.drawable.program_item_background)
            isClickable = true
            isFocusable = true
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.scaleX = 1.05f
                    view.scaleY = 1.05f
                    view.setBackgroundResource(R.drawable.focus_highlight)
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                    view.setBackgroundResource(R.drawable.program_item_background)
                }
            }
        }

        val startCal = Calendar.getInstance(moscowTz).apply { timeInMillis = program.startTime }
        val endCal = Calendar.getInstance(moscowTz).apply { timeInMillis = program.endTime }
        val timeStr = getString(R.string.time_range, dateFormat.format(startCal.time), dateFormat.format(endCal.time))

        val programText = TextView(this).apply {
            text = getString(R.string.epg_program_text, timeStr, program.title)
            textSize = 12f
            setTextColor(getColor(R.color.white))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(programText)

        val nowMoscow = getMoscowNow()
        if (program.isLive(nowMoscow)) {
            container.setBackgroundResource(R.drawable.live_indicator)
        }

        return container
    }

    private fun getMoscowNow(): Long {
        return System.currentTimeMillis()
    }

    private fun getStartTimeMillis(): Long {
        val calendar = Calendar.getInstance(moscowTz).apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR_OF_DAY, -12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun loadEPGForDate() {
        scope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    epgRepository.getProgramsForAllChannels(Date())
                }
                renderPrograms(programs)
                epgGridContainer.post { scrollToCurrentTime() }
            } catch (e: Exception) {
                Toast.makeText(this@EPGActivity, R.string.epg_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollToCurrentTime() {
        val baseTime = getStartTimeMillis()
        val nowOffsetMin = (System.currentTimeMillis() - baseTime) / 1000f / 60f
        val targetX = ((nowOffsetMin - leadMinutes) * pixelsPerMinute).toInt().coerceAtLeast(0)
        epgHorizontalScroll.scrollTo(targetX, 0)
    }

    private fun updateCurrentTimeDisplay() {
        val moscowCal = Calendar.getInstance(moscowTz)
        val dateFormat = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault())
        currentTimeText.text = getString(R.string.time_msk, dateFormat.format(moscowCal.time))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (channelLabelsScroll.scrollY > 0) channelLabelsScroll.smoothScrollBy(0, -rowHeight)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val maxScroll = channelLabelsContainer.height - channelLabelsScroll.height
                if (channelLabelsScroll.scrollY < maxScroll) channelLabelsScroll.smoothScrollBy(0, rowHeight)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { scrollByMinutes(-15); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { scrollByMinutes(15); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun scrollByMinutes(minutes: Int) {
        val maxScroll = epgGridContainer.width - epgHorizontalScroll.width
        val newScrollX = (epgHorizontalScroll.scrollX + minutes * pixelsPerMinute).toInt().coerceIn(0, maxScroll.coerceAtLeast(0))
        epgHorizontalScroll.smoothScrollTo(newScrollX, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
