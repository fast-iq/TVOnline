package com.example.tvapp.data

internal object LivePrograms {

    /**
     * Передача, идущая на канале в момент [nowMs]. Сначала полное расписание канала
     * (оно даёт актуальную передачу без сети), затем снимок из плейлиста.
     */
    fun current(channel: Channel, nowMs: Long = System.currentTimeMillis()): Program? {
        val fromSchedule = channel.programs.firstOrNull { it.isLive(nowMs) }
        if (fromSchedule != null) return fromSchedule
        val start = channel.currentProgramStart
        val end = channel.currentProgramEnd
        val title = channel.currentProgramTitle
        if (start == null || end == null || title.isNullOrBlank()) return null
        if (nowMs !in start..end) return null
        return Program(
            channelId = channel.id,
            title = title,
            description = null,
            startTime = start,
            endTime = end
        )
    }

    fun next(channel: Channel, nowMs: Long = System.currentTimeMillis()): Program? = scheduled(channel).filter { it.startTime > nowMs }.minByOrNull { it.startTime }

    /** Расписание канала: полное, либо одна текущая передача из снимка плейлиста. */
    fun scheduled(channel: Channel): List<Program> {
        if (channel.programs.isNotEmpty()) return channel.programs
        val start = channel.currentProgramStart
        val end = channel.currentProgramEnd
        val title = channel.currentProgramTitle
        if (start == null || end == null || title.isNullOrBlank()) return emptyList()
        return listOf(
            Program(
                channelId = channel.id,
                title = title,
                description = null,
                startTime = start,
                endTime = end
            )
        )
    }
}
