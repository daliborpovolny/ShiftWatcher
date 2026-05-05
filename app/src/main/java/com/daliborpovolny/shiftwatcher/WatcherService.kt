package com.daliborpovolny.shiftwatcher

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

// Testing values
const val PRIMARY_CHECKUP_INTERVAL_MS = 5 * 1000L
const val ESCALATION_GRACE_PERIOD_MS = 5 * 1000L

enum class ShiftState {
    INACTIVE,
    ACTIVE,
    ALARMING,
    ESCALATING,

    STOPPED_ESCALATION
}

class WatcherService : Service() {

    companion object {
        // Use constants to prevent typos and PendingIntent mismatches
        const val ACTION_START_SHIFT = "START_SHIFT"
        const val ACTION_CHECK_IN_PROMPT = "CHECK_IN_PROMPT"
        const val ACTION_STOP_ALARM = "STOP_ALARM"
        const val ACTION_ESCALATE = "ESCALATE"
        const val ACTION_END_SHIFT = "END_SHIFT"

        const val ACTION_STOP_ESCALATION = "STOP_ESCALATION"

        const val ACTION_END_MESSAGE_RECEIVED = "END_MESSAGE_RECEIVED"

        const val FOREGROUND_NOTIFICATION_ID = 1
        const val ALARM_NOTIFICATION_ID = 2

        var currentState by mutableStateOf(ShiftState.INACTIVE)
        var remainingSeconds by mutableIntStateOf(3600)

        var escalationStatus by mutableStateOf("")
    }

    private var ringtone: Ringtone? = null
    private var countDownTimer: CountDownTimer? = null
    private lateinit var alarmManager: AlarmManager

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SHIFT
        Log.d("WatcherService", "Action Received: $action")

        // Ensure the foreground notification is always running
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())

        when (action) {
            ACTION_START_SHIFT -> {
                if (currentState == ShiftState.INACTIVE) {
                    sendShiftStartSms()

                    if (scheduleNextCheckIn()) {
                        currentState = ShiftState.ACTIVE
                    } else {
                        // Handle permission failure (e.g., stop service, alert user)
                        stopSelf()
                    }
                }
            }

            ACTION_CHECK_IN_PROMPT -> {
                currentState = ShiftState.ALARMING
                triggerLoudAlarm()
            }

            ACTION_STOP_ALARM -> {
                currentState = ShiftState.ACTIVE
                stopAlarmAndReset()
            }

            ACTION_ESCALATE -> {
                currentState = ShiftState.ESCALATING
//                executeEscalation()
                executeSmsWaitCallSequence()
            }

            ACTION_END_SHIFT -> {
                currentState = ShiftState.INACTIVE
                cancelAllAlarms()
                sendShiftEndSms()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_STOP_ESCALATION -> {
                currentState = ShiftState.STOPPED_ESCALATION
                cancelAllAlarms()

                Log.d("WatcherService", "Escalation stopped -- SHOULD BE UNUSED")
            }

            ACTION_END_MESSAGE_RECEIVED -> {
                Log.d("WatcherService", "END_MESSAGE_RECEIVED received")

                if (currentState == ShiftState.ESCALATING) {
                    currentState = ShiftState.STOPPED_ESCALATION
                    cancelAllAlarms()

                    Log.d("WatcherService", "stopped escalation")
                }

            }

        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val persistentChannel = NotificationChannel(
            "SHIFT_WATCHER_CHANNEL",
            "Shift Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(persistentChannel)

        val alarmChannel = NotificationChannel(
            "ALARM_CHANNEL",
            "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Used for shift check-ins"
            setBypassDnd(true)
        }
        manager.createNotificationChannel(alarmChannel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, "SHIFT_WATCHER_CHANNEL")
            .setContentTitle("Shift Watcher Active")
            .setContentText("Your safety is being monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    // Returns boolean indicating if scheduling was successful
    private fun scheduleNextCheckIn(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("WatcherService", "Cannot schedule exact alarms! Permission missing.")
                return false
            }
        }

        val pendingIntent = createPendingIntent(ACTION_CHECK_IN_PROMPT, 0)
        val triggerTime = SystemClock.elapsedRealtime() + PRIMARY_CHECKUP_INTERVAL_MS

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
            startVisualTimer((PRIMARY_CHECKUP_INTERVAL_MS / 1000).toInt())
            return true
        } catch (e: SecurityException) {
            e.printStackTrace()
            return false
        }
    }

    private fun triggerLoudAlarm() {
        // 1. Maximize Volume & Play Sound
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
            audioAttributes =
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            play()
        }

        // 2. Schedule Escalation (Using the correct action string)
        val pendingIntent = createPendingIntent(ACTION_ESCALATE, 1)
        val escalationTime = SystemClock.elapsedRealtime() + ESCALATION_GRACE_PERIOD_MS
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            escalationTime,
            pendingIntent
        )

        // 3. Show Full-Screen UI Notification
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

        val alarmNotification = NotificationCompat.Builder(this, "ALARM_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CHECK-IN REQUIRED!")
            .setContentText("Please confirm you are okay.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(
            ALARM_NOTIFICATION_ID,
            alarmNotification
        )
    }

    private fun stopAlarmAndReset() {
        ringtone?.stop()

        // Remove the loud notification from the screen
        getSystemService(NotificationManager::class.java).cancel(ALARM_NOTIFICATION_ID)

        // Cancel Escalation using the EXACT SAME intent format
        alarmManager.cancel(createPendingIntent(ACTION_ESCALATE, 1))

        scheduleNextCheckIn()
    }

//    private fun executeEscalation() {
//        // Logic for SMS/Calling goes here.
//        Log.e("WatcherService", "ESCALATION TRIGGERED. Contacting list.")
//
//        // Decide if ringtone should keep playing or stop during escalation
//        ringtone?.stop()
//    }

    private fun cancelAllAlarms() {
        // 1. Stop the visual UI ticker
        countDownTimer?.cancel()

        // 2. Cancel the Check-in Alarm (Request code 0)
        val checkInIntent = createPendingIntent(ACTION_CHECK_IN_PROMPT, 0)
        alarmManager.cancel(checkInIntent)

        // 3. Cancel the Escalation Alarm (Request code 1)
        val escalationIntent = createPendingIntent(ACTION_ESCALATE, 1)
        alarmManager.cancel(escalationIntent)

        // 4. Cleanup media and notifications
        ringtone?.stop()
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALARM_NOTIFICATION_ID)
    }

    // Helper function to guarantee matching PendingIntents
    private fun createPendingIntent(actionStr: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, WatcherService::class.java).apply { action = actionStr }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startVisualTimer(seconds: Int) {
        countDownTimer?.cancel()
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

    //* SMS and Call Stuff

    // test escalation policy -> only texts each person in the escalation list
    private fun executeEscalation() {
        Log.e("WatcherService", "ESCALATION TRIGGERED. Contacting escalation list.")

        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        // Launch in a Coroutine
        CoroutineScope(Dispatchers.IO).launch {

            val contacts = contactDao.getAllEscalationContactsSync()

            if (contacts.isEmpty()) {
                Log.e("WatcherService", "No escalation contacts found!")
                return@launch
            }

            val message =
                "EMERGENCY: \$name has missed a safety check-in on Shift Watcher and is not responding."

            contacts.forEach { contact ->
                try {
                    sendSms(contact.number, message)
                    Log.d("WatcherService", "Escalation SMS sent to ${contact.name}")

                    // Wait 1 second between messages to avoid spam filters
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(
                        "WatcherService",
                        "Failed to send Escalation SMS to ${contact.name}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun makeEmergencyCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.e("WatcherService", "Permission denied for calling!")
        }
    }

    // potential escalation policy -> calls sequentially each number in the escalation contact list, if it receives a text message back within 3 minutes it stops the escalation
    private fun executeSmsWaitCallSequence() {
        Log.d("WatcherService", "SmsCallDetect Escalation Sequence initiated")

        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        CoroutineScope(Dispatchers.IO).launch {


            val contacts = contactDao.getAllEscalationContactsSync()

            val waitTimeMs = 3 * 60 * 1000L // 3 minutes

            contacts.forEach { contact ->
                escalationStatus = "Texting ${contact.name}..."
                sendSms(
                    contact.number,
                    "EMERGENCY: \$name needs help. Please reply to this text to stop escalation."
                )
                Log.d("WatcherService", "Escalation SMS sent to ${contact.name}")


                makeEmergencyCall(contact.number)

                escalationStatus = "Waiting for ${contact.name} to reply..."

                // Here is the "Wait for X time" logic
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < waitTimeMs) {
                    if (currentState == ShiftState.ACTIVE) {
                        Log.d(
                            "WatcherService",
                            "Escalation detected STOP action. Exiting sequence."
                        )
                        escalationStatus = "Escalation stopped by reply."
                        return@launch // EXIT ENTIRE SEQUENCE
                    }
                    delay(2000) // Check state every 2 seconds
                }

                // If we reach here, no one stopped the alarm, so we call
                escalationStatus = "No reply. Calling ${contact.name}..."
                delay(30000)
            }
        }
    }

    private fun sendShiftStartSms() {
        // 1. Get your DB and contacts
        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        // 2. Launch in a Coroutine
        CoroutineScope(Dispatchers.IO).launch {

            val contacts = contactDao.getAllInfoContactsSync()

            if (contacts.isEmpty()) {
                Log.e("WatcherService", "No info contacts found!")
                return@launch
            }

            val message =
                "INFO: \$name has BEGUN their shift."

            contacts.forEach { contact ->
                try {
                    sendSms(contact.number, message)
                    Log.d("WatcherService", "Info SMS sent to ${contact.name}")

                    // Wait 1 second between messages to avoid spam filters
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(
                        "WatcherService",
                        "Failed to send Info SMS to ${contact.name}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun sendShiftEndSms() {

        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        // Launch in a Coroutine
        CoroutineScope(Dispatchers.IO).launch {

            val contacts = contactDao.getAllInfoContactsSync()

            if (contacts.isEmpty()) {
                Log.e("WatcherService", "No info contacts found!")
                return@launch
            }

            val message =
                "INFO: \$name has ENDED their a shift."

            contacts.forEach { contact ->
                try {
                    sendSms(contact.number, message)
                    Log.d("WatcherService", "Info SMS sent to ${contact.name}")

                    // Wait 1 second between messages to avoid spam filters
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(
                        "WatcherService",
                        "Failed to send Info SMS to ${contact.name}: ${e.message}"
                    )
                }
            }
        }
    }


    private fun sendSms(phoneNumber: String, message: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
    }

}
