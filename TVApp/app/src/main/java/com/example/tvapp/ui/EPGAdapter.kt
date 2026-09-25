package com.example.tvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.Program
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone

class EPGAdapter(
    private var channels: List<Channel>,
    private var programs: Map<String, List<Program>>,
    private val timezoneOffset: Int
) : RecyclerView.Adapter<EPGAdapter.EPGViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun getPrograms(): Map<String, List<Program>> = programs

    inner class EPGViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelName: TextView = itemView.findViewById(R.id.channelName)
        private val programList: RecyclerView = itemView.findViewById(R.id.programList)

        fun bind(channel: Channel, channelPrograms: List<Program>?) {
            channelName.text = channel.name
            
            val sortedPrograms = channelPrograms?.sortedBy { it.startTime } ?: emptyList()
            
            programList.apply {
                layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
                adapter = ProgramListAdapter(sortedPrograms)
            }
            
            // Фокус для навигации
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.setBackgroundResource(R.drawable.focus_highlight)
                } else {
                    view.setBackgroundResource(android.R.color.transparent)
                }
            }
        }
    }

    inner class ProgramListAdapter(
        private var programList: List<Program>
    ) : RecyclerView.Adapter<ProgramListAdapter.ProgramItemViewHolder>() {

        inner class ProgramItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val programTitle: TextView = itemView.findViewById(R.id.programTitle)
            private val programTime: TextView = itemView.findViewById(R.id.programTime)

            fun bind(program: Program) {
                programTitle.text = program.title
                programTime.text = itemView.context.getString(R.string.time_range, dateFormat.format(Date(program.startTime)), dateFormat.format(Date(program.endTime)))
                
                // Подсветка текущей программы
                val moscowTz = TimeZone.getTimeZone("Europe/Moscow")
                val cal = Calendar.getInstance(moscowTz)
                val currentTime = cal.timeInMillis - moscowTz.getOffset(cal.timeInMillis).toLong()
                if (program.isLive(currentTime)) {
                    itemView.setBackgroundResource(R.drawable.live_indicator)
                } else {
                    itemView.setBackgroundResource(android.R.color.transparent)
                }
                
                // Обработка выбора программы
                itemView.setOnClickListener {
                    // Можно реализовать перемотку к этой программе если это архив
                }
                
                // Фокус
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program, parent, false)
            return ProgramItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ProgramItemViewHolder, position: Int) {
            holder.bind(programList[position])
        }

        override fun getItemCount(): Int = programList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EPGViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_epg, parent, false)
        return EPGViewHolder(view)
    }

    override fun onBindViewHolder(holder: EPGViewHolder, position: Int) {
        val channel = channels[position]
        val channelPrograms = programs[channel.id]
        holder.bind(channel, channelPrograms)
    }

    override fun getItemCount(): Int = channels.size

    fun updatePrograms(newPrograms: Map<String, List<Program>>) {
        programs = newPrograms
        notifyDataSetChanged()
    }
}
