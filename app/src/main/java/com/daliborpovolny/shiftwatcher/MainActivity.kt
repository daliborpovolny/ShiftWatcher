package com.daliborpovolny.shiftwatcher

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.NotificationManager
import android.os.PowerManager
import android.content.Context
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

class MainActivity : ComponentActivity() {

    // 1. The Launcher stays at the top level
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // After the user interacts with the popup, refresh our state
        updatePermissionState()
    }

    // 2. State to track if we should show the banner
    private var arePermissionsGranted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ShiftWatcherApp
        val viewModel: ContactViewModel by viewModels {
            ContactViewModel.Factory(app.database.contactDao())
        }

//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
//        }

        updatePermissionState()

        setContent {
            ShiftWatcherTheme {

//                if (!arePermissionsGranted) {
//                    println("showing permission banner")
//                    PermissionBanner(onGrantClick = {
//                        launchPermissionRequest()
//                    })
//                }

                val escalationContacts by viewModel.escalationContacts.collectAsState()
                val infoContacts by viewModel.infoContacts.collectAsState()
                val userName by viewModel.userName.collectAsState()
                val useTestConfig by viewModel.useTestConfig.collectAsState()
                val batteryThreshold by viewModel.batteryThreshold.collectAsState()
                val primaryCheckupInterval by viewModel.primaryCheckupInterval.collectAsState()
                val escalationGracePeriod by viewModel.escalationGracePeriod.collectAsState()
                val contactAnswerWaitTime by viewModel.contactAnswerWaitTime.collectAsState()

                val escalationManipulator = object : EscalationContactManipulator {
                    override fun add(name: String, number: String) {
                        viewModel.addEscalationContact(name, number)
                    }

                    override fun delete(contact: EscalationContact) {
                        viewModel.deleteEscalationContact(contact)
                    }

                    override fun moveUp(index: Int) {
                        viewModel.moveUp(index)
                    }

                    override fun moveDown(index: Int) {
                        viewModel.moveDown(index)
                    }
                }

                val infoManipulator = object : InfoContactManipulator {
                    override fun add(name: String, number: String) {
                        viewModel.addInfoContact(name, number)
                    }

                    override fun delete(contact: InfoContact) {
                        viewModel.deleteInfoContact(contact)
                    }
                }
                StartUp(
                    showPermissionBanner = !arePermissionsGranted,
                    onResolvePermissions = ::launchPermissionRequest,
                    escalationContacts = escalationContacts,
                    infoContacts = infoContacts,
                    escalationContactsManipulator = escalationManipulator,
                    infoContactsManipulator = infoManipulator,
                    userName = userName,
                    onUserNameChange = viewModel::updateUserName,
                    batteryThreshold = batteryThreshold,
                    onBatteryThresholdChange = viewModel::updateBatteryThreshold,
                    primaryCheckupInterval = primaryCheckupInterval,
                    onPrimaryCheckupIntervalChange = viewModel::updatePrimaryCheckupInterval,
                    escalationGracePeriod = escalationGracePeriod,
                    onEscalationGracePeriodChange = viewModel::updateEscalationGracePeriod,
                    contactAnswerWaitTime = contactAnswerWaitTime,
                    onContactAnswerWaitTimeChange = viewModel::updateContactAnswerWaitTime,
                    onResetTimeIntervalsToDefault = viewModel::resetTimeIntervalsToDefault,
                    useTestConfig = useTestConfig == "true",
                    onUseTestConfigChange = viewModel::updateUseTestConfig
                )
            }
        }
    }

    private fun updatePermissionState() {
        val context = this

        // Check SMS (Both Send and Receive) & Call
        val sms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val call = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        // Check Notifications (API 33+)
        val notify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        // Check Exact Alarm (API 31+)
        val alarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            alarmManager.canScheduleExactAlarms()
        } else true

        // Check if Notification Listener is enabled (for RCS support)
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

        // Check Battery Optimization Exemption
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = powerManager.isIgnoringBatteryOptimizations(packageName)

        // Check Overlay Permission (Draw over other apps)
        val overlay = Settings.canDrawOverlays(context)

        // Check Full Screen Intent Permission (API 34+)
        val fullscreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else true

        // Only set to true if EVERY critical permission is ok
        arePermissionsGranted = sms && call && notify && alarm && listener && battery && overlay && fullscreen
        println("permission state:$arePermissionsGranted")
    }

    private fun launchPermissionRequest() {
        // 1. Handle "Dangerous" Permissions (The Popup)
        val dangerousPermissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dangerousPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 2. Handle "Special" Permission: Exact Alarm (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }

        // 3. Handle "Special" Permission: Notification Listener (for RCS)
        val listenerEnabled =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.contains(packageName) == true
        if (!listenerEnabled) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        // 4. Handle "Special" Permission: Battery Optimization (Android 6.0+)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // 5. Handle "Special" Permission: Draw over other apps / Overlay
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        // 6. Handle "Special" Permission: Full screen intent (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // 7. Launch the standard popup for SMS/Call/Notifications
        requestPermissionLauncher.launch(dangerousPermissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        // Refresh the state in case they just came back from System Settings
        updatePermissionState()
    }
}

@Composable
fun StartUp(
    showPermissionBanner: Boolean,
    onResolvePermissions: () -> Unit,
    infoContacts: List<InfoContact>,
    infoContactsManipulator: InfoContactManipulator,
    escalationContacts: List<EscalationContact>,
    escalationContactsManipulator: EscalationContactManipulator,
    userName: String?,
    onUserNameChange: (String) -> Unit,
    batteryThreshold: Int,
    onBatteryThresholdChange: (Int) -> Unit,
    primaryCheckupInterval: Int,
    onPrimaryCheckupIntervalChange: (Int) -> Unit,
    escalationGracePeriod: Int,
    onEscalationGracePeriodChange: (Int) -> Unit,
    contactAnswerWaitTime: Int,
    onContactAnswerWaitTimeChange: (Int) -> Unit,
    onResetTimeIntervalsToDefault: () -> Unit,
    useTestConfig: Boolean,
    onUseTestConfigChange: (Boolean) -> Unit
    ) {
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        checkForUpdate(context) { info ->
            updateInfo = info
        }
    }

    if (updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { updateInfo = null }
        )
    }

    // This state tracks which tab is selected
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Domů") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Nastavení") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }
        }
    ) { innerPadding ->
        // We use a Box to apply the padding from the Scaffold
        // (so the bottom bar doesn't cover our content)
        Box(modifier = Modifier.padding(innerPadding)) {
            Column {
                if (showPermissionBanner) {
                    PermissionBanner(
                        onGrantClick = {
                            onResolvePermissions()
                        }
                    )
                }

                when (selectedTab) {
                    0 -> MainScreen(
                        shiftState = WatcherService.currentState,
                        remainingTime = formatTime(WatcherService.remainingSeconds),
                        batteryThreshold = batteryThreshold
                    )

                    1 -> NewSettingsScreen(
                        escalationContacts = escalationContacts,
                        infoContacts = infoContacts,
                        escalationContactsManipulator = escalationContactsManipulator,
                        infoContactsManipulator = infoContactsManipulator,
                        userName = userName,
                        onUserNameChange = onUserNameChange,
                        batteryThreshold = batteryThreshold,
                        onBatteryThresholdChange = onBatteryThresholdChange,
                        primaryCheckupInterval = primaryCheckupInterval,
                        onPrimaryCheckupIntervalChange = onPrimaryCheckupIntervalChange,
                        escalationGracePeriod = escalationGracePeriod,
                        onEscalationGracePeriodChange = onEscalationGracePeriodChange,
                        contactAnswerWaitTime = contactAnswerWaitTime,
                        onContactAnswerWaitTimeChange = onContactAnswerWaitTimeChange,
                        onResetTimeIntervalsToDefault = onResetTimeIntervalsToDefault,
                        useTestConfig = useTestConfig,
                        onUseTestConfigChange = onUseTestConfigChange
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomePagePreview() {
    ShiftWatcherTheme {

        val dummyEscalationManipulator = object : EscalationContactManipulator {
            override fun add(name: String, number: String) {}
            override fun delete(contact: EscalationContact) {}
            override fun moveUp(index: Int) {}
            override fun moveDown(index: Int) {}
        }

        val dummyInfoManipulator = object : InfoContactManipulator {
            override fun add(name: String, number: String) {}
            override fun delete(contact: InfoContact) {}
        }

        StartUp(
            escalationContacts = listOf(
                EscalationContact(number = "123 456 789", name = "John"),
                EscalationContact(number = "987 654 321", name = "Lennon")
            ),
            infoContacts = listOf(
                InfoContact(number = "123 456 789", name = "John"),
                InfoContact(number = "987 654 321", name = "Lennon")
            ),
            escalationContactsManipulator = dummyEscalationManipulator,
            infoContactsManipulator = dummyInfoManipulator,
            showPermissionBanner = true,
            onResolvePermissions = {},
            userName = "Dalibor",
            onUserNameChange = {},
            batteryThreshold = 20,
            onBatteryThresholdChange = {},
            primaryCheckupInterval = 60,
            onPrimaryCheckupIntervalChange = {},
            escalationGracePeriod = 15,
            onEscalationGracePeriodChange = {},
            contactAnswerWaitTime = 3,
            onContactAnswerWaitTimeChange = {},
            onResetTimeIntervalsToDefault = {},
            useTestConfig = false,
            onUseTestConfigChange = {}
        )
    }
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

const val UPDATE_INFO_URL = "https://docs.google.com/uc?export=download&id=1qVaE_PmRQKmprhGxoFM3bfnJAfJ9SWL-"

private fun checkForUpdate(
    context: Context,
    onUpdateAvailable: (UpdateInfo) -> Unit
) {
    Log.d("UpdateCheck", "Starting update check from: $UPDATE_INFO_URL")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL(UPDATE_INFO_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            Log.d("UpdateCheck", "HTTP Response Code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("UpdateCheck", "Raw response from server: $response")
                
                val json = JSONObject(response)
                val serverVersionCode = json.getInt("versionCode")
                val serverVersionName = json.getString("versionName")
                val downloadUrl = json.getString("downloadUrl")
                val changelog = json.optString("changelog", "")
                
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                
                Log.d("UpdateCheck", "Parsed Server Version Code: $serverVersionCode (Name: $serverVersionName)")
                Log.d("UpdateCheck", "Local App Version Code: $currentVersionCode (Name: ${packageInfo.versionName})")
                
                if (serverVersionCode > currentVersionCode) {
                    Log.d("UpdateCheck", "New version available! Prompting user to update.")
                    withContext(Dispatchers.Main) {
                        onUpdateAvailable(
                            UpdateInfo(
                                versionCode = serverVersionCode,
                                versionName = serverVersionName,
                                downloadUrl = downloadUrl,
                                changelog = changelog
                            )
                        )
                    }
                } else {
                    Log.d("UpdateCheck", "App is up to date.")
                }
            } else {
                Log.e("UpdateCheck", "Failed to check update. Connection response not OK ($responseCode)")
            }
        } catch (e: Exception) {
            Log.e("UpdateCheck", "Exception occurred during update check: ${e.message}", e)
        }
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dostupná nová verze") },
        text = {
            Column {
                Text("Byla nalezena nová verze aplikace (${updateInfo.versionName}).")
                if (updateInfo.changelog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Co je nového:\n${updateInfo.changelog}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDismiss()
                }
            ) {
                Text("Stáhnout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}