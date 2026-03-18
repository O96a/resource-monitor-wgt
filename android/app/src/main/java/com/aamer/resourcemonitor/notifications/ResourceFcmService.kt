package com.aamer.resourcemonitor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aamer.resourcemonitor.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ResourceFcmService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "resource_alarms"
        const val CHANNEL_NAME = "Resource Alarms"
        private var notifId = 0
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Resource Alert"
        val body  = message.notification?.body
            ?: message.data["body"]
            ?: "A resource threshold has been breached."

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        // In production: send token to your server's FCM_DEVICE_TOKEN setting
        android.util.Log.d("FCM", "New device token: $token")
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("tab", "alarms")
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId++, notif)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Oracle server resource threshold alarms"
            enableVibration(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
