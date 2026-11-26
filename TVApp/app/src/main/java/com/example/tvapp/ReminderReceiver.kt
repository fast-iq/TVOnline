package com.example.tvapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ChannelRepository
import com.example.tvapp.data.ReminderScheduler

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = ReminderScheduler.markKeyExtra(intent) ?: return
        val appContext = context.applicationContext
        val mark = AppPreferences(appContext).markedPrograms.firstOrNull { it.key == key } ?: return
        if (mark.endTime <= System.currentTimeMillis()) return
        val freshUrl = runCatching {
            ChannelRepository(appContext).loadFromDisk()?.find { it.id == mark.channelId }?.streamUrl
        }.getOrNull()
        NotificationHelper.showUpcomingProgramNotification(appContext, mark, freshUrl)
    }
}
