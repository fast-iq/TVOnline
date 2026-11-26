package com.example.tvapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tvapp.data.MarkedProgram
import com.example.tvapp.ui.PlayerActivity

object NotificationHelper {

    private const val CHANNEL_ID = "tvapp_epg_reminders"
    private const val CHANNEL_NAME_RES = R.string.notification_channel_name
    private const val CHANNEL_DESC_RES = R.string.notification_channel_description

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(CHANNEL_NAME_RES),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(CHANNEL_DESC_RES)
            enableLights(true)
            lightColor = 0xFF7C6CFF.toInt()
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showUpcomingProgramNotification(context: Context, mark: MarkedProgram, freshStreamUrl: String? = null) {
        ensureChannel(context)
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("channel_id", mark.channelId)
            putExtra("channel_name", mark.channelName)
            putExtra("stream_url", freshStreamUrl?.takeIf { it.isNotBlank() } ?: mark.streamUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            mark.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_upcoming_title))
            .setContentText(context.getString(R.string.notification_upcoming_text, mark.title, mark.channelName))
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher,
                    context.getString(R.string.notification_switch),
                    pendingIntent
                ).build()
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        NotificationManagerCompat.from(context).notify(mark.key.hashCode(), builder.build())
    }

    fun cancelNotification(context: Context, mark: MarkedProgram) {
        NotificationManagerCompat.from(context).cancel(mark.key.hashCode())
    }
}
