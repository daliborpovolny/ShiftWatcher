package com.daliborpovolny.shiftwatcher

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true

        // Only set to true if EVERY critical permission is ok
        arePermissionsGranted = sms && call && notify && alarm && listener
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
        val listenerEnabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        if (!listenerEnabled) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        // 4. Launch the standard popup for SMS/Call/Notifications
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

    ) {
    // This state tracks which tab is selected
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Main") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Settings") },
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
                        remainingTime = formatTime(WatcherService.remainingSeconds)
                    )

                    1 -> NewSettingsScreen(
                        escalationContacts = escalationContacts,
                        infoContacts = infoContacts,
                        escalationContactsManipulator = escalationContactsManipulator,
                        infoContactsManipulator = infoContactsManipulator
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
        )
    }
}
