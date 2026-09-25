package com.example.tvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.tvapp.R
import com.example.tvapp.data.Channel

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onChannelSelected: (Channel) -> Unit,
    private val onSettingsClicked: () -> Unit,
    private val onEPGClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CHANNEL = 1
        private const val TYPE_SETTINGS = 2
        private const val TYPE_EPG = 3
    }

    private var currentProgramsMap = mapOf<String, String>()

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelLogo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val channelName: TextView = itemView.findViewById(R.id.channelName)
        private val programTitle: TextView = itemView.findViewById(R.id.programTitle)

        fun bind(channel: Channel) {
            channelName.text = channel.name
            
            // Загрузка логотипа канала
            Glide.with(itemView.context)
                .load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(channelLogo)
            
            // Отображение названия передачи
            val programTitleText = currentProgramsMap[channel.id]
            programTitle.text = programTitleText ?: itemView.context.getString(R.string.program_title_placeholder)
            
            // Обработка выбора канала с пульта
            itemView.setOnClickListener {
                onChannelSelected(channel)
            }
            
            // Фокус для навигации пультом
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.scaleX = 1.1f
                    view.scaleY = 1.1f
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                }
            }
        }
    }

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val settingsText: TextView = itemView.findViewById(R.id.settingsText)

        fun bind() {
            settingsText.text = itemView.context.getString(R.string.settings)
            
            itemView.setOnClickListener {
                onSettingsClicked()
            }
            
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.scaleX = 1.1f
                    view.scaleY = 1.1f
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                }
            }
        }
    }

    inner class EPGViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val epgText: TextView = itemView.findViewById(R.id.epgText)

        fun bind() {
            epgText.text = itemView.context.getString(R.string.epg)
            
            itemView.setOnClickListener {
                onEPGClicked()
            }
            
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.scaleX = 1.1f
                    view.scaleY = 1.1f
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_SETTINGS
            1 -> TYPE_EPG
            else -> TYPE_CHANNEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SETTINGS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_settings, parent, false)
                SettingsViewHolder(view)
            }
            TYPE_EPG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_epg, parent, false)
                EPGViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_channel, parent, false)
                ChannelViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SettingsViewHolder -> holder.bind()
            is EPGViewHolder -> holder.bind()
            is ChannelViewHolder -> {
                // Позиция в списке каналов (с учетом 2 специальных элементов)
                val channelPosition = position - 2
                if (channelPosition >= 0 && channelPosition < channels.size) {
                    holder.bind(channels[channelPosition])
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return channels.size + 2 // Каналы + Настройки + EPG
    }

    fun updateCurrentPrograms(programsMap: Map<String, String>) {
        currentProgramsMap = programsMap
        notifyDataSetChanged()
    }
}
