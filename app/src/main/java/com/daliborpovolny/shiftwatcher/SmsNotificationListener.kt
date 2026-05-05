package com.daliborpovolny.shiftwatcher

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class SmsNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("SmsNotificationListener", "Notification Listener Service connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras

        // Extract text content. android.text is usually the message body.
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val title = extras.getCharSequence("android.title")?.toString() ?: ""

        val body = (text ?: bigText ?: "").lowercase()

        Log.d("SmsNotificationListener", "Notification from $packageName ($title): $body")

        if (packageName != "com.google.android.apps.messaging") {
            return
        }


        // Only react to messaging apps or common SMS apps if you want to filter noise
        // But for safety, checking everything is more reliable for custom RCS apps
        if (body.contains("ok") || body.contains("stop") || body.contains("got it")) {
            Log.i("SmsNotificationListener", "Keyword detected in notification. Stopping alarm.")

            val serviceIntent = Intent(this, WatcherService::class.java).apply {
                action = WatcherService.ACTION_END_MESSAGE_RECEIVED
            }

            try {
                // startForegroundService is required for background service starts
                ContextCompat.startForegroundService(this, serviceIntent)
            } catch (e: Exception) {
                Log.e(
                    "SmsNotificationListener",
                    "Failed to start service from notification: ${e.message}"
                )
            }
        }
    }
}
