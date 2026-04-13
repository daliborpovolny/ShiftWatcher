package com.daliborpovolny.shiftwatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.tooling.preview.Preview

import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ShiftWatcherApp
        val viewModel: ContactViewModel by viewModels {
            ContactViewModel.Factory(app.database.contactDao())
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }


        setContent {
            ShiftWatcherTheme {
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
                    escalationContacts = escalationContacts,
                    infoContacts = infoContacts,
                    escalationContactsManipulator = escalationManipulator,
                    infoContactsManipulator = infoManipulator,
                )
            }
        }
    }
}

@Composable
fun StartUp(
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

            )
    }
}