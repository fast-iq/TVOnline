package com.example.tvapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelRepository(private val context: Context) {

    companion object {
        @Volatile
        var contentVersion: Int = 0
            private set

        fun invalidateContent() {
            contentVersion++
        }

        internal fun cacheKeyFor(limehdRegion: Int, tz: Int): String = "limehd_${limehdRegion}_$tz"
    }

    private val prefs by lazy { AppPreferences(context) }
    private val limeHdClient by lazy { LimeHdClient(context) }

    private var cachedChannels: List<Channel>? = null
    private var cacheKey: String? = null
    private var cachedContentVersion: Int = -1
    private var lastFetchTime: Long = 0L
    private val cacheDurationMs = 10 * 60 * 1000L

    suspend fun getChannels(forceRefresh: Boolean = false): List<Channel> {
        val key = currentCacheKey()
        val versionChanged = cachedContentVersion != contentVersion
        val fresh = !versionChanged && cacheKey == key && System.currentTimeMillis() - lastFetchTime < cacheDurationMs
        if (!forceRefresh && cachedChannels != null && fresh) {
            return cachedChannels!!
        }
        return withContext(Dispatchers.IO) {
            val result: List<Channel> = try {
                val channels = limeHdClient.getChannels()
                if (channels.isNotEmpty()) {
                    Log.d("ChannelRepo", "LimeHD: ${channels.size} channels")
                    cachedChannels = channels
                    cacheKey = key
                    cachedContentVersion = contentVersion
                    lastFetchTime = System.currentTimeMillis()
                    ChannelList.updateChannels(channels)
                    channels
                } else {
                    Log.w("ChannelRepo", "LimeHD returned no channels, keeping previous list")
                    ChannelList.channels
                }
            } catch (e: Exception) {
                Log.e("ChannelRepo", "LimeHD fetch failed: ${e.message}")
                ChannelList.channels
            }
            result
        }
    }

    suspend fun refreshChannels(): List<Channel> = getChannels(forceRefresh = true)

    private fun currentCacheKey(): String = cacheKeyFor(prefs.limehdRegion, prefs.programTimezoneOffset())
}
