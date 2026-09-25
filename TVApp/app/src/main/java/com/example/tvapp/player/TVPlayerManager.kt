package com.example.tvapp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel

class TVPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
    private val preferences = AppPreferences(context)
    private var currentChannel: Channel? = null
    private var fallbackIndex = 0
    private var isAutoFallback = false

    interface PlayerCallback {
        fun onPlaybackReady()
        fun onPlaybackError(error: String)
        fun onBuffering(isBuffering: Boolean)
        fun onFallbackUsed(fallbackUrl: String)
    }

    fun initializePlayer(playerView: PlayerView, callback: PlayerCallback? = null) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 3000, 6000)
            .build()

        trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)

        exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector!!)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> callback?.onPlaybackReady()
                            Player.STATE_BUFFERING -> callback?.onBuffering(true)
                            Player.STATE_IDLE, Player.STATE_ENDED -> callback?.onBuffering(false)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (isAutoFallback && fallbackIndex < getCurrentStreamUrls().size - 1) {
                            fallbackIndex++
                            val nextUrl = getCurrentStreamUrls()[fallbackIndex]
                            callback?.onFallbackUsed(nextUrl)
                            playChannel(currentChannel!!, nextUrl)
                        } else {
                            val errorMessage = when (error.errorCodeName) {
                                "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED" ->
                                    "Ошибка сетевого подключения. Проверьте интернет-соединение."
                                "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE" ->
                                    "Неподдерживаемый формат потока."
                                "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED" ->
                                    "Формат видео не поддерживается."
                                "ERROR_CODE_IO_BAD_HTTP_STATUS" ->
                                    "Сервер вернул ошибку доступа. Поток может быть недоступен."
                                else -> "${error.errorCodeName ?: "Неизвестная ошибка"} (код: ${error.errorCode})"
                            }
                            callback?.onPlaybackError(errorMessage)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) callback?.onBuffering(false)
                    }
                })
            }

        applyQualityMode()
        playerView.player = exoPlayer
    }

    private fun applyQualityMode() {
        val qualityMode = preferences.qualityMode
        val maxBitrate: Int = when (qualityMode) {
            AppPreferences.QualityMode.MINIMUM -> 500_000
            AppPreferences.QualityMode.MEDIUM -> 2_000_000
            AppPreferences.QualityMode.MAXIMUM -> Int.MAX_VALUE
            AppPreferences.QualityMode.AUTO -> Int.MAX_VALUE
        }
        trackSelector?.let { ts ->
            val params = ts.buildUponParameters().setMaxVideoBitrate(maxBitrate).build()
            ts.setParameters(params)
        }
    }

    fun playChannel(channel: Channel, streamUrl: String? = null) {
        val url = streamUrl ?: channel.streamUrl
        currentChannel = channel
        fallbackIndex = if (streamUrl == null) 0 else getCurrentStreamUrls().indexOf(streamUrl).coerceAtLeast(0)

        exoPlayer?.apply {
            clearMediaItems()
            val mediaItem = MediaItem.fromUri(url).buildUpon()
                .setTag(channel.id)
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            preferences.lastChannelId = channel.id
        }
    }

    private fun getCurrentStreamUrls(): List<String> {
        val channel = currentChannel ?: return emptyList()
        return listOf(channel.streamUrl) + channel.fallbackStreamUrls
    }

    fun setQualityMode(qualityMode: AppPreferences.QualityMode) {
        preferences.qualityMode = qualityMode
        applyQualityMode()
    }

    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration ?: 0L
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true
    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }

    fun releasePlayer() {
        exoPlayer?.apply {
            stop()
            release()
        }
        exoPlayer = null
        trackSelector = null
    }
}
