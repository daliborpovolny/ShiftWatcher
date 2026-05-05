package com.daliborpovolny.shiftwatcher

import android.content.*
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReplyReceiver", "onReceive: ${intent.action}")

        // Only process if the action matches exactly
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("SmsReplyReceiver", "Ignored action: ${intent.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null) {
            Log.d("SmsReplyReceiver", "No messages found in intent")
            return
        }

        for (sms in messages) {
            val body = sms.messageBody?.lowercase() ?: ""
            Log.d("SmsReplyReceiver", "SMS from ${sms.originatingAddress}: $body")

            if (body.contains("ok") || body.contains("stop") || body.contains("got it")) {
                Log.d("SmsReplyReceiver", "Keyword detected. Triggering escalation stop.")

                val serviceIntent = Intent(context, WatcherService::class.java).apply {
                    action = WatcherService.ACTION_STOP_ALARM
                }

                try {
                    // Use startForegroundService for compatibility when app is in background
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("SmsReplyReceiver", "Failed to start WatcherService: ${e.message}")
                }
            }
        }
    }
}
