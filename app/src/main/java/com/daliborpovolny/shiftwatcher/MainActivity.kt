package com.daliborpovolny.shiftwatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.CreationExtras

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiftWatcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { modifier ->
//                    Greeting(
//                        name = "Android1",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                    SettingsScreen()
                }
            }
        }
    }
}

data class Contact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    var name: String
)

@Composable
fun SettingsScreen() {
    // 1. This is our "State". 'remember' makes sure the list
    // survives when the screen redraws.
    val contactList = remember {
        mutableStateListOf(
            Contact(number = "123 456 789", name = "john"),
            Contact(number = "987 654 321", name = "lenon")
        )
    }

    // A temporary variable for what the user is currently typing
    var newNumber by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Emergency Contacts", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Input area to add a new number
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = newNumber,
                onValueChange = { newNumber = it }, // Update the text as user types
                label = { Text("Phone Number") },
                modifier = Modifier.weight(1f) // Takes up remaining space
            )
            TextField(
                value = newName,
                onValueChange = { newName = it }, // Update the text as user types
                label = { Text("Name") },
                modifier = Modifier.weight(1f) // Takes up remaining space
            )
            IconButton(onClick = {
                if (newNumber.isNotBlank()) {
                    contactList.add(Contact(number = newNumber, name = newName))
                    newNumber = "" // Clear input after adding
                    newName = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. The actual scrollable list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(contactList, key = { it.id }) { contact ->
                ContactRow(
                    contact = contact,
                    onDelete = { contactList.remove(contact) }
                )
            }
        }
    }
}

@Composable
fun ContactRow(contact: Contact, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(contact.number, modifier = Modifier.weight(1f))
            Text(contact.name, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    ShiftWatcherTheme {
        SettingsScreen()
    }
}