package com.daliborpovolny.shiftwatcher

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

// AppConfig holds configuration values for checkups, alerts, and escalations.

// Production values
//const val PRIMARY_CHECKUP_INTERVAL_MS = 60 * 60 * 1000L
//const val ESCALATION_GRACE_PERIOD_MS = 15 * 60 * 1000L

interface AppConfig {

    // interval between check-ups
    val PRIMARY_CHECKUP_INTERVAL_MS: Long

    // interval for which the check-up alarm is active before escalation starts
    val ESCALATION_GRACE_PERIOD_MS: Long

    // interval for which the app waits after contacting a contact before contacting the next one
    val ESCALATION_CONTACT_ANSWER_WAIT_TIME_MS: Long
}

object ProdConfig : AppConfig {
    override val PRIMARY_CHECKUP_INTERVAL_MS = 60 * 60 * 1000L
    override val ESCALATION_GRACE_PERIOD_MS = 15 * 60 * 1000L
    override val ESCALATION_CONTACT_ANSWER_WAIT_TIME_MS = 3 * 60 * 1000L

}

object TestConfig : AppConfig {
    override val PRIMARY_CHECKUP_INTERVAL_MS = 30_000L
    override val ESCALATION_GRACE_PERIOD_MS = 15_000L
    override val ESCALATION_CONTACT_ANSWER_WAIT_TIME_MS = 60_000L
}

val config: AppConfig
    get() = if (WatcherService.useTestConfig) TestConfig else ProdConfig


enum class ShiftState {
    INACTIVE,
    ACTIVE,
    ALARMING,
    ESCALATING,

    STOPPED_ESCALATION
}

class WatcherService : Service() {

    companion object {
        var useTestConfig = false

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

        val escalationLogs = mutableStateListOf<String>()

        fun addEscalationLog(message: String) {
            escalationLogs.add(0, message)
        }

        fun normalizePhoneNumber(number: String): String {
            var clean = number.replace(Regex("[\\s\\-\\(\\)]"), "")
            if (clean.length >= 9) {
                clean = clean.takeLast(9)
            }
            return clean
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private var countDownTimer: CountDownTimer? = null
    private lateinit var alarmManager: AlarmManager

    private fun acquireWakeLock(tagSuffix: String, durationMs: Long) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShiftWatcher:$tagSuffix"
            ).apply {
                acquire(durationMs)
            }
            Log.d("WatcherService", "WakeLock acquired for $tagSuffix, duration: $durationMs ms")
        } catch (e: Exception) {
            Log.e("WatcherService", "Error acquiring WakeLock: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d("WatcherService", "WakeLock released")
        } catch (e: Exception) {
            Log.e("WatcherService", "Error releasing WakeLock: ${e.message}", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannels()

        // Load the config state synchronously
        val db = (application as ShiftWatcherApp).database
        runBlocking {
            useTestConfig = db.contactDao().getUserSetting("use_test_config") == "true"
        }
        Log.d("WatcherService", "onCreate: Loaded useTestConfig=$useTestConfig")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
        vibrator?.cancel()
        ringtone?.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SHIFT
        Log.d("WatcherService", "Action Received: $action")

        // Ensure the foreground notification is always running
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())

        when (action) {
            ACTION_START_SHIFT -> {
                if (currentState == ShiftState.INACTIVE) {
                    val db = (application as ShiftWatcherApp).database
                    runBlocking {
                        useTestConfig = db.contactDao().getUserSetting("use_test_config") == "true"
                    }
                    Log.d(
                        "WatcherService",
                        "onStartCommand ACTION_START_SHIFT: Loaded useTestConfig=$useTestConfig"
                    )
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
                escalationLogs.clear()
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

                Log.d("WatcherService", "Escalation stopped")
                addEscalationLog("Eskalace přerušena odkliknutím na telefonu")
            }

            ACTION_END_MESSAGE_RECEIVED -> {
                val sender = intent?.getStringExtra("sender")
                val message = intent?.getStringExtra("message")
                Log.d("WatcherService", "END_MESSAGE_RECEIVED received from $sender: $message")

                if (currentState == ShiftState.ESCALATING) {
                    processIncomingCancellation(sender, message)
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
            .setContentTitle("Hlídač směny aktivní")
            .setContentText("Nastaven kontrolní budík.")
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
        val triggerTime = SystemClock.elapsedRealtime() + config.PRIMARY_CHECKUP_INTERVAL_MS

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
            startVisualTimer((config.PRIMARY_CHECKUP_INTERVAL_MS / 1000).toInt())
            return true
        } catch (e: SecurityException) {
            e.printStackTrace()
            return false
        }
    }

    private fun triggerLoudAlarm() {
        acquireWakeLock("AlarmWakeLock", 30 * 60 * 1000L) // 30 minutes safety limit
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }

        // Trigger Vibration
        val vibService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = vibService
        vibService?.let {
            val pattern = longArrayOf(0, 1000, 1000) // vibrate 1s, pause 1s
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means repeat
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, 0)
            }
        }

        // 2. Schedule Escalation (Using the correct action string)
        val pendingIntent = createPendingIntent(ACTION_ESCALATE, 1)
        val escalationTime = SystemClock.elapsedRealtime() + config.ESCALATION_GRACE_PERIOD_MS
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
            .setContentTitle("Kontrola vyžadována!")
            .setContentText("Prosím potvrďte, že jste v pořádku.")
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
        vibrator?.cancel()
        releaseWakeLock()

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
        vibrator?.cancel()
        releaseWakeLock()

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

    private fun getBatteryInfo(): String {
        val batteryStatus: Intent? =
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val chargeMethod = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeType = when (chargeMethod) {
            BatteryManager.BATTERY_PLUGGED_AC -> " (síť)"
            BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (bezdrát)"
            else -> ""
        }

        val chargingStr = if (isCharging) "nabíjí se$chargeType" else "nenabíjí se"
        val pctStr = if (pct >= 0) "$pct%" else "neznámo"
        return "Stav baterie: $pctStr ($chargingStr)"
    }

    private fun processIncomingCancellation(sender: String?, message: String?) {
        if (sender.isNullOrBlank() || message.isNullOrBlank()) return

        val normalizedMsg = message.trim().lowercase()
        val approvedPhrases = listOf("zastavit eskalaci", "stop eskalaci", "stop escalation")

        // 1. Verify keyword matching
        if (approvedPhrases.none { normalizedMsg.contains(it) }) {
            Log.d(
                "WatcherService",
                "Incoming message doesn't contain any approved cancellation phrases."
            )
            return
        }

        // 2. Verify sender in escalation list
        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        serviceScope.launch {
            val contacts = contactDao.getAllEscalationContactsSync()
            val normalizedSender = normalizePhoneNumber(sender)

            val isEscalationContact = contacts.any { contact ->
                normalizePhoneNumber(contact.number) == normalizedSender ||
                        contact.name.equals(sender, ignoreCase = true)
            }

            Log.d(
                "WatcherService",
                "Found the contact in Escalation contacts: $isEscalationContact"
            )

            //TODO Should only the contacts from the escalation list be able to stop escalation?
            if (isEscalationContact || true) {
                withContext(Dispatchers.Main) {
                    currentState = ShiftState.STOPPED_ESCALATION
                    cancelAllAlarms()
                    Log.d(
                        "WatcherService",
                        "Escalation stopped by message from verified contact: $sender"
                    )
                    addEscalationLog("Eskalace přerušena díky přijaté zprávě od ${sender}")
                }
            } else {
                Log.w(
                    "WatcherService",
                    "Match found for phrase, but sender '$sender' is not in the escalation contact list."
                )
                addEscalationLog("Pokus o přerušení od neznámého čísla: $sender")
            }
        }
    }

    // test escalation policy -> only texts each person in the escalation list
//    private fun executeEscalation() {
//        Log.e("WatcherService", "ESCALATION TRIGGERED. Contacting escalation list.")
//
//        val db = (application as ShiftWatcherApp).database
//        val contactDao = db.contactDao()
//
//        serviceScope.launch {
//            val contacts = contactDao.getAllEscalationContactsSync()
//
//            if (contacts.isEmpty()) {
//                Log.e("WatcherService", "No escalation contacts found!")
//                addEscalationLog("No escalation contacts found!")
//                return@launch
//            }
//
//            val message =
//                "EMERGENCY: \$name has missed a safety check-in on Shift Watcher and is not responding."
//
//            contacts.forEach { contact ->
//                try {
//                    sendSms(contact.number, message)
//                    Log.d("WatcherService", "Escalation SMS sent to ${contact.name}")
//                    addEscalationLog("Sms s upozorněním poslána kontaktu ${contact.name}")
//
//                    // Wait 1 second between messages to avoid spam filters
//                    delay(1000)
//                } catch (e: Exception) {
//                    Log.e(
//                        "WatcherService",
//                        "Failed to send Escalation SMS to ${contact.name}: ${e.message}"
//                    )
//                    addEscalationLog("Nepodařilo se poslat sms kontaktu ${contact.name}")
//                }
//            }
//        }
//    }

    private fun makeEmergencyCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.e("WatcherService", "Permission denied for calling!")
            addEscalationLog("Chybí povolení k volání!")
        }
    }

    // potential escalation policy -> calls sequentially each number in the escalation contact list, if it receives a text message back within 3 minutes it stops the escalation
    private fun executeSmsWaitCallSequence() {
        Log.d("WatcherService", "SmsCallDetect Escalation Sequence initiated")
        addEscalationLog("Eskalace zahájena")

        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        // Acquire WakeLock to keep CPU awake during critical safety escalation
        acquireWakeLock("EscalationWakeLock", 15 * 60 * 1000L)

        serviceScope.launch {
            try {
                val contacts = contactDao.getAllEscalationContactsSync()
                val name = contactDao.getUserSetting("user_name") ?: "Zaměstnanec"

                val waitTimeMs = config.ESCALATION_CONTACT_ANSWER_WAIT_TIME_MS

                contacts.forEach { contact ->
                    if (currentState != ShiftState.ESCALATING) return@launch

                    addEscalationLog("Sms poslána kontaktu ${contact.name}...")
                    sendSms(
                        contact.number,
                        "Hlídač směny - POZOR: $name zmeškal/a budík. Odepište 'zastavit eskalaci', 'stop eskalaci' nebo 'stop escalation', aby se eskalace přerušila."
                    )
                    Log.d("WatcherService", "Escalation SMS sent to ${contact.name}")

                    addEscalationLog("Prozvánění ${contact.name}...")
                    makeEmergencyCall(contact.number)

                    addEscalationLog("Čekání na odpověd od ${contact.name}")

                    // Here is the "Wait for X time" logic
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < waitTimeMs) {
                        delay(2000) // Check state every 2 seconds
                        if (currentState != ShiftState.ESCALATING) {
                            Log.d(
                                "WatcherService",
                                "Escalation sequence interrupted. Exiting."
                            )
                            return@launch
                        }
                    }

                    // Waited and received no answer, logging and moving to the next one
                    addEscalationLog("Žádná odpověd od ${contact.name}")
                }
                addEscalationLog("Všechny kontakty kontaktovány, žádná odpověd.")
            } finally {
                // Safely release the wakeLock when the coroutine is cancelled or finishes
                releaseWakeLock()
            }
        }
    }

    private fun sendShiftStartSms() {
        val db = (application as ShiftWatcherApp).database
        val contactDao = db.contactDao()

        serviceScope.launch {
            val contacts = contactDao.getAllInfoContactsSync()

            if (contacts.isEmpty()) {
                Log.e("WatcherService", "No info contacts found!")
                return@launch
            }

            val name = contactDao.getUserSetting("user_name") ?: "Zaměstnanec"
            val batteryInfo = getBatteryInfo()
            val message = "Hlídač směny - INFO: $name začal/a svou směnu. $batteryInfo"

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

        serviceScope.launch {
            val contacts = contactDao.getAllInfoContactsSync()

            if (contacts.isEmpty()) {
                Log.e("WatcherService", "No info contacts found!")
                return@launch
            }

            val name = contactDao.getUserSetting("user_name") ?: "Zaměstnanec"
            val batteryInfo = getBatteryInfo()
            val message = "INFO: $name skončil/a svou směnu. $batteryInfo"

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
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
    }

}
