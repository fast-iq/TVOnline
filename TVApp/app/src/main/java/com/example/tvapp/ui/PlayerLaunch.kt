package com.example.tvapp.ui

import android.content.Context
import android.content.Intent
import com.example.tvapp.data.ArchivePlayback
import com.example.tvapp.data.Channel

/** Единая сборка интента плеера (раньше три активности дублировали набор extras). */
internal object PlayerLaunch {

    fun intent(
        context: Context,
        channelId: String,
        channelName: String,
        streamUrl: String,
        logoUrl: String? = null,
        archive: Pair<Long, Long>? = null
    ): Intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("channel_id", channelId)
        putExtra("channel_name", channelName)
        putExtra("stream_url", streamUrl)
        if (logoUrl != null) putExtra("logo_url", logoUrl)
        if (archive != null) {
            putExtra("archive_start", archive.first)
            putExtra("archive_end", archive.second)
        }
    }

    fun forChannel(context: Context, channel: Channel): Intent = intent(context, channel.id, channel.name, channel.streamUrl, channel.logoUrl)

    /** Открытие передачи из архива; false — архивного окна для неё уже нет. */
    fun openArchived(
        context: Context,
        channel: Channel,
        programStart: Long,
        programEnd: Long,
        nowMs: Long
    ): Boolean {
        val window = ArchivePlayback.windowFor(channel, programStart, programEnd, nowMs) ?: return false
        val intent = intent(
            context,
            channel.id,
            channel.name,
            channel.streamUrl,
            channel.logoUrl,
            archive = window
        ).putExtra("archive_url", channel.archiveUrl)
        context.startActivity(intent)
        return true
    }
}
