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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.lazy.itemsIndexed

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
    val contactList = remember {
        mutableStateListOf(
            Contact(number = "123 456 789", name = "John"),
            Contact(number = "987 654 321", name = "Lennon")
        )
    }

    var newNumber by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    // Using Scaffold properly to handle paddings
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Emergency Contacts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // --- NEW INPUT LAYOUT ---
        // Stack inputs vertically so they have room to breathe
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newNumber,
            onValueChange = { newNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (newNumber.isNotBlank() && newName.isNotBlank()) {
                    contactList.add(Contact(number = newNumber, name = newName))
                    newNumber = ""
                    newName = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add Contact")
        }

        Spacer(modifier = Modifier.height(24.dp))



        // --- THE LIST ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            // We use itemsIndexed so we know exactly where each contact is
            itemsIndexed(contactList, key = { _, contact -> contact.id }) { index, contact ->
                ContactRow(
                    contact = contact,
                    isFirst = index == 0,
                    isLast = index == contactList.size - 1,
                    onDelete = { contactList.remove(contact) },
                    onMoveUp = {
                        // Swap with previous
                        val item = contactList.removeAt(index)
                        contactList.add(index - 1, item)
                    },
                    onMoveDown = {
                        // Swap with next
                        val item = contactList.removeAt(index)
                        contactList.add(index + 1, item)
                    }
                )
            }
        }
    }
}
@Composable
fun ContactRow(
    contact: Contact,
    isFirst: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(contact.number, style = MaterialTheme.typography.bodySmall)
            }

            // Sorting Buttons
            Column {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                }
            }

            // Delete Button
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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