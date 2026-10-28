package com.example.tvapp.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String = "general",
    val epgId: String? = null,
    val channelImageUrl: String? = null,
    val fallbackStreamUrls: List<String> = emptyList(),
    val currentProgramTitle: String? = null,
    val currentProgramStart: Long? = null,
    val currentProgramEnd: Long? = null,
    val programs: List<Program> = emptyList(),
    val isDrm: Boolean = false,
    val regionCode: Int = 0,
    val number: Int = 0
)

data class Program(
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val iconUrl: String? = null
) {
    fun isLive(currentTime: Long): Boolean = currentTime in startTime..endTime

    fun getDuration(): Long = endTime - startTime

    fun getElapsedTime(currentTime: Long): Long = if (currentTime > startTime) currentTime - startTime else 0
}

object ChannelList {

    @Volatile
    var channels: List<Channel> = emptyList()
        private set

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
    }
}
