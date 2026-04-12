package com.daliborpovolny.shiftwatcher

import android.Manifest
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.CountDownTimer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


// Production values
// const val PRIMARY_CHECKUP_INTERVAL_MS = 60 * 60 * 1000L
// const val ESCALATION_GRACE_PERIOD_MS = 15 * 60 * 1000L

// Testing values
const val PRIMARY_CHECKUP_INTERVAL_MS = 10 * 1000L // 10 seconds
const val ESCALATION_GRACE_PERIOD_MS = 10 * 1000L  // 10 seconds

enum class ShiftState {
    INACTIVE,   // App opened, nothing started
    ACTIVE,     // 1-hour timer running
    ALARMING    // 10s passed, phone screaming
}

class WatcherService : Service() {

    companion object {
        // This is a "State" that the UI can observe
        var currentState by mutableStateOf(ShiftState.INACTIVE)
        // We can use this for the countdown later
        var remainingSeconds by mutableIntStateOf(3600)
    }

    private var ringtone: Ringtone? = null

    private var countDownTimer: CountDownTimer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is called when we click "START" in the UI
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "SHIFT_WATCHER_CHANNEL")
            .setContentTitle("Shift Watcher Active")
            .setContentText("Your safety is being monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true) // User cannot swipe it away
            .build()

        // This turns the service into a "Foreground Service"
        startForeground(1, notification)

        when (intent?.action) {
            "CHECK_IN_PROMPT" -> {
                triggerLoudAlarm()
                currentState = ShiftState.ALARMING
                println("in trigger loud alarm in intent action")

            }
            "STOP_ALARM" -> {
                stopAlarmAndReset()
                currentState = ShiftState.ACTIVE
                println("in stop alarm in intent action")

            }
            "ESCALATE_ALARM" -> {
                println("escalating...!")
                println("in escalating in intent action")

            }
            "END_SHIFT" -> {
                println("in end shift in intent action")
                currentState = ShiftState.INACTIVE
                cancelAllAlarms()
                stopForeground(STOP_FOREGROUND_REMOVE) // Removes the persistent notification
                stopSelf() // Actually kills the service

            }

            else -> {
                // This happens the very first time we click START
                println("in else in intent action")
                scheduleNextCheckIn()
                currentState = ShiftState.ACTIVE
                // Here you would also send the initial SMS
            }
        }

        return START_STICKY // Tells Android to restart the service if it gets killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        // Use a separate channel for the actual ALARM so it can be loud/high priority
        val channel = NotificationChannel(
            "ALARM_CHANNEL",
            "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH // REQUIRED for full-screen
        ).apply {
            description = "Used for shift check-ins"
            setBypassDnd(true) // Allows it to ring even in Do Not Disturb
        }
        manager.createNotificationChannel(channel)
    }

private fun scheduleNextCheckIn() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Safety check for Android 12+ (API 31)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            // Option A: Log an error
            // Option B: Send a notification to the user to grant permission
            println("Cannot schedule exact alarms! Permission missing.")
            return
        }
    }

    val intent = Intent(this, WatcherService::class.java).apply {
        action = "CHECK_IN_PROMPT"
    }

    val pendingIntent = PendingIntent.getService(
        this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerTime = SystemClock.elapsedRealtime() + PRIMARY_CHECKUP_INTERVAL_MS // 10s for testing

    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerTime,
            pendingIntent
        )
    } catch (e: SecurityException) {
        // Fallback or log error
        e.printStackTrace()
    }

    startVisualTimer((PRIMARY_CHECKUP_INTERVAL_MS / 1000).toInt())

}

    private fun triggerLoudAlarm() {

        // play the ringtone

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, alarmUri).apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
        }
        ringtone?.play()

        // schedule the escalation

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WatcherService::class.java).apply {
            action = "ESCALATE_EMERGENCY"
        }
        val pendingIntent = PendingIntent.getService(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val escalationTime = SystemClock.elapsedRealtime() + ESCALATION_GRACE_PERIOD_MS

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            escalationTime,
            pendingIntent
        )

        // pop up the ui

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = "SHOW_CONFIRMATION"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build a specific "Alarm" notification
        val alarmNotification = NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CHECK-IN REQUIRED!")
            .setContentText("Please confirm you are okay.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // THE KEY LINE
            .setOngoing(true)
            .build()

        // Notify the user - this is what triggers the pop-up
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, alarmNotification) // Use ID 2 so it doesn't replace the status notification
    }

    private fun stopAlarmAndReset() {
        ringtone?.stop()

        // CANCEL ESCALATION
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WatcherService::class.java).apply { action = "ESCALATE_EMERGENCY" }
        val pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        pendingIntent?.let { alarmManager.cancel(it) }

        scheduleNextCheckIn()
    }

    private fun cancelAllAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Stop the Ticker
        countDownTimer?.cancel()

        // Cancel Check-in Alarm
        val checkInIntent = Intent(this, WatcherService::class.java).apply { action = "CHECK_IN_PROMPT" }
        PendingIntent.getService(this, 0, checkInIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        // Cancel Escalation Alarm
        val escalationIntent = Intent(this, WatcherService::class.java).apply { action = "ESCALATE_EMERGENCY" }
        PendingIntent.getService(this, 1, escalationIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        ringtone?.stop()
    }

    private fun startVisualTimer(seconds: Int) {
        countDownTimer?.cancel() // Stop any existing timer
        remainingSeconds = seconds

        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                remainingSeconds = 0
            }
        }.start()
    }
}