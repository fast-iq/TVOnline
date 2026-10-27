package com.example.tvapp.data

import kotlin.math.abs

object StreamSelector {

    private val REGION_URL = Regex("/hls/CH_[A-Z0-9]+_(\\d+)/variant\\.m3u8")

    fun regionOffsetOf(url: String): Int? = REGION_URL.find(url)?.groupValues?.get(1)?.toIntOrNull()

    fun allUrls(channel: Channel): List<String> = (listOf(channel.streamUrl) + channel.fallbackStreamUrls).distinct()

    fun preferredUrl(channel: Channel, regionId: Int): String? {
        val offset = RegionList.offset(regionId)
        if (offset <= 0) return null
        var best: String? = null
        var bestDelta = Int.MAX_VALUE
        for (url in allUrls(channel)) {
            val o = regionOffsetOf(url)
            if (o != null && o > 0) {
                val delta = abs(o - offset)
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = url
                }
            }
        }
        return best
    }
}
