package com.example.tvapp.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelRepository
import com.example.tvapp.data.LimeHdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

class TVPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var mediaSession: MediaSession? = null
    private val preferences = AppPreferences(context)
    private val limeHdClient = LimeHdClient(context)
    private val channelRepository = ChannelRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentChannel: Channel? = null
    private var fallbackIndex = 0
    private var pendingUrl: String? = null
    private var lastWorkingUrl: String? = null
    private var manualSwitch = false
    private val stallWatchdog = StallWatchdog()
    private val bufferHealth = BufferHealth()
    private var watchdogJob: Job? = null

    /** Играем time-shift (архивное окно): при залипании guard вернёт в эфир, а не рефетчит URL. Ставит PlayerActivity. */
    var playingArchive = false

    /** Ступень авто-понижения качества (только режим AUTO): индекс в [AUTO_BITRATE_CAPS]. */
    private var autoCapIndex = 0

    companion object {
        private const val TAG = "TVPlayerManager"
        private const val WATCHDOG_TICK_MS = 5_000L

        /** Лестница потолков битрейта для AUTO: вниз при голодании буфера. */
        private val AUTO_BITRATE_CAPS = intArrayOf(Int.MAX_VALUE, 2_000_000, 1_000_000, 500_000)
    }

    interface PlayerCallback {
        fun onPlaybackReady()
        fun onPlaybackError(error: String)
        fun onBuffering(isBuffering: Boolean)
        fun onFallbackUsed(fallbackUrl: String)

        /** Архивное (time-shift) воспроизведение дошло до конца окна. По умолчанию no-op. */
        fun onPlaybackEnded() {}

        /** Time-shift намертво залип — guard сам вернулся в живой эфир; активности синхронизировать состояние. */
        fun onAutoReturnToLive() {}
    }

    fun initializePlayer(playerView: PlayerView, callback: PlayerCallback? = null) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 60000, 3000, 6000)
            .build()

        trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)

        exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector!!)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                lastWorkingUrl = pendingUrl
                                bufferHealth.reset()
                                callback?.onPlaybackReady()
                            }
                            Player.STATE_BUFFERING -> {
                                callback?.onBuffering(true)
                            }
                            Player.STATE_IDLE, Player.STATE_ENDED -> {
                                callback?.onBuffering(false)
                                bufferHealth.reset()
                                if (state == Player.STATE_ENDED) callback?.onPlaybackEnded()
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val channel = currentChannel ?: return
                        val urls = getCurrentStreamUrls()
                        pendingUrl = null
                        if (manualSwitch) {
                            manualSwitch = false
                            val revert = lastWorkingUrl
                            if (revert != null && urls.indexOf(revert) >= 0) {
                                callback?.onFallbackUsed(revert)
                                playChannel(channel, revert)
                                return
                            }
                        }
                        if (fallbackIndex < urls.size - 1) {
                            fallbackIndex++
                            val nextUrl = urls[fallbackIndex]
                            callback?.onFallbackUsed(nextUrl)
                            playChannel(channel, nextUrl)
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
        // Транспортная сессия: play/pause с пульта/Bluetooth/из PiP без фокуса активности.
        mediaSession = MediaSession.Builder(context, exoPlayer!!)
            .setSessionActivity(sessionActivityIntent(context))
            .build()
        startStallWatchdog(callback)
    }

    /** Тап по системному плейеру/уведомлению ведёт на главный экран. */
    internal fun sessionActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.example.tvapp.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Опрос «заморозки» эфира: ExoPlayer может играть вечно без новых кадров (stalled
     * без ошибки). [StallWatchdog] решение принимает по позиции; при срабатывании
     * берём свежий URL потока (живой токен) и перезапускаем воспроизведение.
     */
    private fun startStallWatchdog(callback: PlayerCallback?) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                val player = exoPlayer ?: continue
                val channel = currentChannel
                // 1) Залипание эфира (position стоит) — свежий URL, как раньше.
                if (channel != null && stallWatchdog.onTick(player.isPlaying, player.currentPosition) == StallAction.RECOVER) {
                    android.util.Log.d(TAG, "stream stalled, restarting playback")
                    callback?.onBuffering(true)
                    val fresh = try {
                        channelRepository.refreshStreamUrl(channel.id)
                    } catch (e: Exception) {
                        null
                    }
                    if (fresh != null) playChannel(channel, fresh) else playChannel(channel, channel.streamUrl)
                    continue
                }
                // 2) Залипание в BUFFERING: в эфире — рефетч URL, в архиве — возврат в эфир.
                when (bufferHealth.onBufferingTick(player.playbackState == Player.STATE_BUFFERING, playingArchive)) {
                    HealthAction.RETURN_TO_LIVE -> {
                        if (channel != null) {
                            android.util.Log.d(TAG, "archive stuck, returning to live")
                            playingArchive = false
                            callback?.onAutoReturnToLive()
                            playChannel(channel, channel.streamUrl)
                        }
                    }
                    HealthAction.REFRESH_URL -> {
                        if (channel != null) {
                            android.util.Log.d(TAG, "buffering stuck, refreshing stream URL")
                            callback?.onBuffering(true)
                            val fresh = try {
                                channelRepository.refreshStreamUrl(channel.id)
                            } catch (e: Exception) {
                                null
                            }
                            if (fresh != null) playChannel(channel, fresh) else playChannel(channel, channel.streamUrl)
                        }
                    }
                    else -> Unit
                }
                // 3) Голодание буфера в AUTO — ступенькой ниже по битрейту.
                if (player.playbackState == Player.STATE_READY) {
                    val lead = player.bufferedPosition - player.currentPosition
                    val auto = preferences.qualityMode == AppPreferences.QualityMode.AUTO
                    if (bufferHealth.onPlaybackTick(player.isPlaying, lead, auto) == HealthAction.DOWNGRADE_QUALITY) {
                        if (autoCapIndex < AUTO_BITRATE_CAPS.size - 1) {
                            autoCapIndex++
                            applyQualityMode()
                            android.util.Log.d(TAG, "buffer starving, auto quality cap -> ${AUTO_BITRATE_CAPS[autoCapIndex]}")
                        }
                    }
                }
            }
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory = object : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val factory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(20_000)
            if (currentChannel?.id?.startsWith("limehd_") == true) {
                factory.setDefaultRequestProperties(limeHdClient.playerRequestHeaders())
            }
            return GzipDetectingDataSource(factory.createDataSource())
        }
    }

    private fun applyQualityMode() {
        val qualityMode = preferences.qualityMode
        val maxBitrate: Int = when (qualityMode) {
            AppPreferences.QualityMode.MINIMUM -> 500_000
            AppPreferences.QualityMode.MEDIUM -> 2_000_000
            AppPreferences.QualityMode.MAXIMUM -> Int.MAX_VALUE
            // AUTO: начинаем с потолка, BufferHealth роняет ступенью вниз при голодании буфера.
            AppPreferences.QualityMode.AUTO -> AUTO_BITRATE_CAPS[autoCapIndex]
        }
        trackSelector?.let { ts ->
            val params = ts.buildUponParameters().setMaxVideoBitrate(maxBitrate).build()
            ts.setParameters(params)
        }
    }

    fun playChannel(channel: Channel, streamUrl: String? = null) {
        val url = streamUrl ?: channel.streamUrl
        val previousChannelId = currentChannel?.id
        currentChannel = channel
        pendingUrl = url
        fallbackIndex = if (streamUrl == null) 0 else getCurrentStreamUrls().indexOf(streamUrl).coerceAtLeast(0)
        stallWatchdog.reset()
        bufferHealth.reset()
        // Ступень авто-капа сбрасываем только на СМЕНЕ канала — рефетч URL тем же каналом её сохраняет.
        if (previousChannelId != channel.id) autoCapIndex = 0
        // Выбор канала фиксируем всегда, даже если плеер ещё/уже не инициализирован.
        preferences.lastChannelId = channel.id

        exoPlayer?.apply {
            clearMediaItems()
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setTag(channel.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(channel.name)
                        .build()
                )
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    fun switchToUrl(url: String) {
        if (currentChannel != null) {
            manualSwitch = true
            playChannel(currentChannel!!, url)
        }
    }

    fun getCurrentStreamUrls(): List<String> {
        val channel = currentChannel ?: return emptyList()
        return (listOf(channel.streamUrl) + channel.fallbackStreamUrls).distinct()
    }

    fun getCurrentUrlIndex(): Int = fallbackIndex

    /** Аудиогруппы текущего потока (пусто до подготовки воспроизведения). */
    fun getAudioTrackGroups(): List<androidx.media3.common.Tracks.Group> = exoPlayer?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()

    /** Субтитровые группы текущего потока. */
    fun getTextTrackGroups(): List<androidx.media3.common.Tracks.Group> = exoPlayer?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()

    /**
     * Выбор аудиодорожки: 0 — «авто» (сброс override), иначе — порядковый
     * номер трека в том же порядке, в котором строит подписи [TrackLabels.audioLabels].
     * Выбор сохраняется на весь плеер (переживает переключение каналов).
     */
    fun selectAudioTrack(index: Int) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon().clearOverrides()
        player.trackSelectionParameters.overrides.values
            .filter { it.getType() != C.TRACK_TYPE_AUDIO }
            .forEach(builder::setOverrideForType)
        if (index > 0) overrideForNthTrack(getAudioTrackGroups(), index)?.let(builder::setOverrideForType)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        player.trackSelectionParameters = builder.build()
    }

    /** Выбор субтитров: 0 — выключены, иначе — порядковый номер трека [TrackLabels.textLabels]. */
    fun selectTextTrack(index: Int) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon().clearOverrides()
        player.trackSelectionParameters.overrides.values
            .filter { it.getType() != C.TRACK_TYPE_TEXT }
            .forEach(builder::setOverrideForType)
        if (index > 0) overrideForNthTrack(getTextTrackGroups(), index)?.let(builder::setOverrideForType)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, index <= 0)
        player.trackSelectionParameters = builder.build()
    }

    /** Override для n-го трека (1-базированный) перечисленных групп; null если индекс вне списка. */
    private fun overrideForNthTrack(groups: List<androidx.media3.common.Tracks.Group>, index: Int): TrackSelectionOverride? {
        var n = 0
        for (group in groups) {
            for (i in 0 until group.length) {
                n++
                if (n == index) return TrackSelectionOverride(group.mediaTrackGroup, i)
            }
        }
        return null
    }

    fun setQualityMode(qualityMode: AppPreferences.QualityMode) {
        preferences.qualityMode = qualityMode
        autoCapIndex = 0
        applyQualityMode()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration ?: 0L
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun releasePlayer() {
        watchdogJob?.cancel()
        watchdogJob = null
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.apply {
            stop()
            release()
        }
        exoPlayer = null
        trackSelector = null
    }
}

private class GzipDetectingDataSource(private val delegate: DataSource) : DataSource {

    private var input: InputStream? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val remaining = delegate.open(dataSpec)
        val upstream = object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

            override fun close() {
                delegate.close()
            }
        }
        val result = wrapGzipResponse(upstream, remaining)
        input = result.first
        return result.second
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = input ?: throw IOException("DataSource is not open")
        return stream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        try {
            input?.close()
        } finally {
            input = null
        }
    }
}

internal fun wrapGzipResponse(stream: InputStream, remaining: Long): Pair<InputStream, Long> {
    val buffered = BufferedInputStream(stream)
    buffered.mark(2)
    val b1 = buffered.read()
    val b2 = buffered.read()
    buffered.reset()
    return if (b1 == 0x1F && b2 == 0x8B) {
        Pair(GZIPInputStream(buffered), C.LENGTH_UNSET.toLong())
    } else {
        Pair(buffered, remaining)
    }
}
