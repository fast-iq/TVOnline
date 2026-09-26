package com.example.tvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tvapp.R
import com.example.tvapp.data.Channel

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onChannelSelected: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private var currentProgramsMap = mapOf<String, String>()

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelLogo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val channelName: TextView = itemView.findViewById(R.id.channelName)
        private val programTitle: TextView = itemView.findViewById(R.id.programTitle)

        fun bind(channel: Channel) {
            channelName.text = channel.name

            Glide.with(itemView.context)
                .load(channel.logoUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(channelLogo)

            val programTitleText = currentProgramsMap[channel.id]
            programTitle.text = programTitleText ?: itemView.context.getString(R.string.program_title_placeholder)

            itemView.setOnClickListener {
                onChannelSelected(channel)
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

    override fun getItemCount(): Int = channels.size

    fun updateCurrentPrograms(programsMap: Map<String, String>) {
        currentProgramsMap = programsMap
        notifyDataSetChanged()
    }
}
