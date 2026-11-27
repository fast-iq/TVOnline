package com.example.tvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.LivePrograms

sealed class GridItem {
    data class Header(val category: String, val title: String) : GridItem()
    data class Card(val channel: Channel) : GridItem()
}

/**
 * Сравнение элементов сетки для DiffUtil: идентичность — по категории заголовка
 * и id канала; содержимое — по всем полям (живые статусы эфира приходят отдельными
 * payload-обновлениями и в сравнение не входят).
 */
internal class ChannelDiffCallback(
    private val oldItems: List<GridItem>,
    private val newItems: List<GridItem>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldItems.size

    override fun getNewListSize(): Int = newItems.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldItems[oldItemPosition]
        val new = newItems[newItemPosition]
        return when {
            old is GridItem.Header && new is GridItem.Header -> old.category == new.category
            old is GridItem.Card && new is GridItem.Card -> old.channel.id == new.channel.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = oldItems[oldPos] == newItems[newPos]
}

class ChannelAdapter(
    private var items: List<GridItem> = emptyList(),
    private val onChannelSelected: (Channel) -> Unit,
    private val onChannelLongPressed: (Channel) -> Unit = {},
    private val onFocusPositionChanged: (Int) -> Unit = {},
    private val isFavorite: (String) -> Boolean = { false }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelLogo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val channelName: TextView = itemView.findViewById(R.id.channelName)
        private val programTitle: TextView = itemView.findViewById(R.id.programTitle)
        private val programProgress: ProgressBar = itemView.findViewById(R.id.programProgress)
        private val channelNumber: TextView = itemView.findViewById(R.id.channelNumber)
        private val drmBadge: TextView = itemView.findViewById(R.id.drmBadge)
        private val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadge)

        fun bind(channel: Channel) {
            channelName.text = channel.name
            favoriteBadge.visibility = if (isFavorite(channel.id)) View.VISIBLE else View.GONE

            if (channel.number > 0) {
                channelNumber.text = itemView.context.getString(R.string.channel_number_format, channel.number)
                channelNumber.visibility = View.VISIBLE
            } else {
                channelNumber.visibility = View.GONE
            }

            drmBadge.visibility = if (channel.isDrm) View.VISIBLE else View.GONE

            Glide.with(itemView.context)
                .load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(channelLogo)

            bindLive(channel)

            itemView.setOnClickListener {
                onChannelSelected(channel)
            }

            itemView.setOnLongClickListener {
                onChannelLongPressed(channel)
                true
            }

            itemView.setOnFocusChangeListener { view, hasFocus ->
                val card = view as CardView
                if (hasFocus) {
                    view.scaleX = 1.08f
                    view.scaleY = 1.08f
                    card.foreground = ContextCompat.getDrawable(view.context, R.drawable.channel_focus_ring)
                    card.cardElevation = dp(view, 8).toFloat()
                    onFocusPositionChanged(bindingAdapterPosition)
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                    card.foreground = defaultForeground
                    card.cardElevation = dp(view, 3).toFloat()
                }
            }
        }

        /** Только текущая передача и полоса прогресса — вызывается по таймеру без перевыпуска карточки. */
        fun bindLive(channel: Channel) {
            val program = LivePrograms.current(channel)
            if (program != null) {
                programTitle.text = program.title
                programTitle.visibility = View.VISIBLE
                updateProgress(program.startTime, program.endTime)
            } else {
                programTitle.text = itemView.context.getString(R.string.program_title_placeholder)
                programTitle.visibility = View.VISIBLE
                programProgress.visibility = View.GONE
            }
        }

        fun bindFavorite(channel: Channel) {
            favoriteBadge.visibility = if (isFavorite(channel.id)) View.VISIBLE else View.GONE
        }

        private val defaultForeground: android.graphics.drawable.Drawable? = itemView.foreground

        private fun dp(view: View, value: Int): Int = (value * view.context.resources.displayMetrics.density).toInt()

        private fun updateProgress(start: Long, end: Long) {
            if (end > start) {
                val now = System.currentTimeMillis()
                val progress = ((now - start).coerceIn(0, end - start) * 100 / (end - start)).toInt()
                programProgress.progress = progress
                programProgress.visibility = View.VISIBLE
            } else {
                programProgress.visibility = View.GONE
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = itemView.findViewById(R.id.sectionHeaderText)

        fun bind(header: GridItem.Header) {
            title.text = header.title
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is GridItem.Header -> TYPE_HEADER
        is GridItem.Card -> TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_channel_section_header, parent, false))
        } else {
            ChannelViewHolder(inflater.inflate(R.layout.item_channel, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is GridItem.Header -> (holder as HeaderViewHolder).bind(item)
            is GridItem.Card -> (holder as ChannelViewHolder).bind(item.channel)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        val item = items[position]
        if (item is GridItem.Card && holder is ChannelViewHolder) {
            if (payloads.contains(PAYLOAD_LIVE)) {
                holder.bindLive(item.channel)
                return
            }
            if (payloads.contains(PAYLOAD_FAVORITE)) {
                holder.bindFavorite(item.channel)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<GridItem>) {
        val oldItems = items
        val result = DiffUtil.calculateDiff(ChannelDiffCallback(oldItems, newItems))
        items = newItems
        result.dispatchUpdatesTo(this)
    }

    /** Обновляет звёздочку у карточек без перевыпуска (после смены избранного). */
    fun refreshFavorites() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_FAVORITE)
    }

    /** Обновляет название передачи и прогресс у видимых карточек, не перевыпуская их (логотипы не мигают). */
    fun refreshLive() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LIVE)
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CARD = 1
        private const val PAYLOAD_LIVE = "live"
        private const val PAYLOAD_FAVORITE = "favorite"
    }
}
