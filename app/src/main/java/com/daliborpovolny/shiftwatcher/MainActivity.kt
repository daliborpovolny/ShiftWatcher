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

        setContent {
            ShiftWatcherTheme {
                val contacts by viewModel.contacts.collectAsState()

                StartUp(contacts,
                    onAdd = { name, num -> viewModel.addContact(name, num) },
                    onDelete = { viewModel.deleteContact(it) },
                    onMoveUp = { viewModel.moveUp(it) },
                    onMoveDown = { viewModel.moveDown(it) })
            }
        }
    }
}

@Composable
fun StartUp(
    contacts: List<Contact>,
    onAdd: (String, String) -> Unit,
    onDelete: (Contact) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
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
                0 -> MainScreen()
                1 -> SettingsScreen(
                    contacts = contacts,
                    onAdd = onAdd,
                    onDelete = onDelete,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown
                )
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun HomePagePreview() {
    ShiftWatcherTheme {
        StartUp(
            contacts = listOf(
                Contact(number = "123 456 789", name = "John"),
                Contact(number = "987 654 321", name = "Lennon")
            ),
            onAdd = { _, _ -> },
            onDelete = { _ -> },
            onMoveUp = { _ -> },
            onMoveDown = { _ -> }
        )
    }
}