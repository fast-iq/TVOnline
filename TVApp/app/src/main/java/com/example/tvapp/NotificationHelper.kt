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
import com.example.tvapp.data.Channel
import com.example.tvapp.data.Program
import com.example.tvapp.ui.PlayerActivity

object NotificationHelper {

    private const val CHANNEL_ID = "tvapp_epg_reminders"
    private const val CHANNEL_NAME_RES = R.string.notification_channel_name
    private const val CHANNEL_DESC_RES = R.string.notification_channel_description

    fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
    }

    fun showUpcomingProgramNotification(
        context: Context,
        channel: Channel,
        program: Program
    ) {
        ensureChannel(context)
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("channel_name", channel.name)
            putExtra("stream_url", channel.streamUrl)
            putExtra("logo_url", channel.logoUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            program.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_upcoming_title))
            .setContentText(context.getString(R.string.notification_upcoming_text, program.title, channel.name))
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
        NotificationManagerCompat.from(context).notify(program.hashCode(), builder.build())
    }

    fun cancelNotification(context: Context, program: Program) {
        NotificationManagerCompat.from(context).cancel(program.hashCode())
    }
}
