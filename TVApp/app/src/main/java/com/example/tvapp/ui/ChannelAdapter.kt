package com.example.tvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.LivePrograms

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onChannelSelected: (Channel) -> Unit,
    private val onFocusPositionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelLogo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val channelName: TextView = itemView.findViewById(R.id.channelName)
        private val programTitle: TextView = itemView.findViewById(R.id.programTitle)
        private val programProgress: ProgressBar = itemView.findViewById(R.id.programProgress)
        private val channelNumber: TextView = itemView.findViewById(R.id.channelNumber)
        private val drmBadge: TextView = itemView.findViewById(R.id.drmBadge)

        fun bind(channel: Channel) {
            channelName.text = channel.name

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        if (position < channels.size) {
            holder.bind(channels[position])
        }
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_LIVE) && position < channels.size) {
            holder.bindLive(channels[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = channels.size

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    /** Обновляет название передачи и прогресс у видимых карточек, не перевыпуская их (логотипы не мигают). */
    fun refreshLive() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LIVE)
    }

    companion object {
        private const val PAYLOAD_LIVE = "live"
    }
}
