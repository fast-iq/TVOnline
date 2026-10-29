package com.example.tvapp.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.Program
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EpgRowAdapter(
    private val context: Context,
    private val onProgramAction: (Channel, Program) -> Unit
) : RecyclerView.Adapter<EpgRowAdapter.RowHolder>() {

    companion object {
        fun keyOf(channelId: String, program: Program): String = "$channelId|${program.startTime}|${program.endTime}"
    }

    private var channels: List<Channel> = emptyList()
    private var programs: Map<String, List<Program>> = emptyMap()
    private var selectedKeys: Set<String> = emptySet()
    private var cursorKey: String? = null
    private var baseTime = 0L
    private var pixelsPerMinute = 2.5f
    private var totalMinutes = 720
    private var rowHeight = 70
    private var rowWidth = 0
    private var displayTz: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val cursorElevationPx = 6f * context.resources.displayMetrics.density

    fun setData(
        channels: List<Channel>,
        programs: Map<String, List<Program>>,
        baseTime: Long,
        pixelsPerMinute: Float,
        totalMinutes: Int,
        rowHeight: Int,
        rowWidth: Int,
        displayTz: TimeZone
    ) {
        this.channels = channels
        this.programs = programs
        this.baseTime = baseTime
        this.pixelsPerMinute = pixelsPerMinute
        this.totalMinutes = totalMinutes
        this.rowHeight = rowHeight
        this.rowWidth = rowWidth
        this.displayTz = displayTz
        notifyDataSetChanged()
    }

    fun setSelections(keys: Set<String>) {
        val affected = (selectedKeys - keys) + (keys - selectedKeys)
        selectedKeys = keys
        notifyRowsChanged(affected)
    }

    fun setCursor(key: String?) {
        val old = cursorKey
        if (old == key) return
        cursorKey = key
        notifyRowsChanged(setOfNotNull(old, key))
    }

    private fun notifyRowsChanged(keys: Set<String>) {
        val positions = keys.mapNotNull { key ->
            val channelId = key.substringBefore("|")
            channels.indexOfFirst { it.id == channelId }.takeIf { it >= 0 }
        }.toSet()
        positions.forEach { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val root = FrameLayout(parent.context)
        root.layoutParams = RecyclerView.LayoutParams(rowWidth, rowHeight)
        return RowHolder(root)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val row = holder.itemView as FrameLayout
        val channel = channels[position]
        val list = programs[channel.id].orEmpty()
        val keys = list.filter { inWindow(it) }.map { keyOf(channel.id, it) }
        if (holder.keys == keys) {
            updateBlockStates(holder)
        } else {
            rebuildRow(holder, row, channel, list)
        }
    }

    private fun rebuildRow(holder: RowHolder, row: FrameLayout, channel: Channel, list: List<Program>) {
        row.removeAllViews()
        holder.blocks.clear()
        val renderedKeys = mutableListOf<String>()
        val renderedPrograms = mutableListOf<Program>()
        list.forEach { program ->
            if (!inWindow(program)) return@forEach

            val startTimeOffset = (program.startTime - baseTime) / 1000f / 60f
            val endTimeOffset = (program.endTime - baseTime) / 1000f / 60f
            val leftMinutes = startTimeOffset.coerceAtLeast(0f)
            val rightMinutes = endTimeOffset.coerceAtMost(totalMinutes.toFloat())
            val leftMargin = (leftMinutes * pixelsPerMinute).toInt()
            val width = ((rightMinutes - leftMinutes) * pixelsPerMinute).toInt().coerceAtLeast(40)
            val blockParams = FrameLayout.LayoutParams(width, rowHeight - 8)
            blockParams.marginStart = leftMargin
            blockParams.topMargin = 4

            val block = createProgramBlock(channel, program)
            holder.blocks.add(block)
            renderedKeys.add(keyOf(channel.id, program))
            renderedPrograms.add(program)
            row.addView(block, blockParams)
        }
        holder.keys = renderedKeys
        holder.programs = renderedPrograms

        val nowOffset = ((System.currentTimeMillis() - baseTime) / 1000f / 60f).coerceIn(0f, totalMinutes.toFloat())
        val nowLine = View(context)
        nowLine.setBackgroundColor(context.getColor(R.color.accent))
        val nowParams = FrameLayout.LayoutParams(3, rowHeight)
        nowParams.marginStart = (nowOffset * pixelsPerMinute).toInt()
        row.addView(nowLine, nowParams)

        updateBlockStates(holder)
    }

    private fun updateBlockStates(holder: RowHolder) {
        val now = System.currentTimeMillis()
        holder.keys.forEachIndexed { index, key ->
            val block = holder.blocks.getOrNull(index) ?: return@forEachIndexed
            val program = holder.programs.getOrNull(index) ?: return@forEachIndexed
            block.setBackgroundResource(blockBackground(key, program, now))
            block.elevation = if (key == cursorKey) cursorElevationPx else 0f
        }
    }

    private fun inWindow(program: Program): Boolean {
        val startOffset = (program.startTime - baseTime) / 60000f
        val endOffset = (program.endTime - baseTime) / 60000f
        return endOffset >= 0f && startOffset <= totalMinutes.toFloat()
    }

    private fun createProgramBlock(channel: Channel, program: Program): View {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = displayTz }
        val container = FrameLayout(context).apply {
            setPadding(8, 8, 8, 8)
            isClickable = true
            isFocusable = false
            setOnClickListener { onProgramAction(channel, program) }
        }

        val startCal = Calendar.getInstance(displayTz).apply { timeInMillis = program.startTime }
        val endCal = Calendar.getInstance(displayTz).apply { timeInMillis = program.endTime }
        val timeStr = context.getString(R.string.time_range, dateFormat.format(startCal.time), dateFormat.format(endCal.time))

        val programText = TextView(context).apply {
            text = context.getString(R.string.epg_program_text, timeStr, program.title)
            textSize = 12f
            setTextColor(context.getColor(R.color.white))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(programText)

        return container
    }

    private fun blockBackground(key: String, program: Program, now: Long): Int = when {
        key == cursorKey -> R.drawable.program_cursor_background
        key in selectedKeys -> R.drawable.program_selected_background
        program.isLive(now) -> R.drawable.live_indicator
        else -> R.drawable.program_item_background
    }

    override fun getItemCount(): Int = channels.size

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        var keys: List<String> = emptyList()
        var programs: List<Program> = emptyList()
        val blocks = mutableListOf<View>()
    }
}

class EpgLabelAdapter(private val rowHeight: Int) : RecyclerView.Adapter<EpgLabelAdapter.LabelHolder>() {

    private var channels: List<Channel> = emptyList()

    fun setChannels(channels: List<Channel>) {
        this.channels = channels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelHolder {
        val text = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            textSize = 14f
            setTextColor(parent.context.getColor(R.color.white))
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 0, 16, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        return LabelHolder(text)
    }

    override fun onBindViewHolder(holder: LabelHolder, position: Int) {
        (holder.itemView as TextView).text = channels[position].name
    }

    override fun getItemCount(): Int = channels.size

    class LabelHolder(view: View) : RecyclerView.ViewHolder(view)
}
