package com.example.tvapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tvapp.data.ReminderScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.sync(context.applicationContext)
        }
    }
}
