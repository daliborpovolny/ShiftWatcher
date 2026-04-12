package com.daliborpovolny.shiftwatcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    contacts: List<Contact>,

    onAdd: (String, String) -> Unit,
    onDelete: (Contact) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,

    ) {


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
                    onAdd(newNumber, newName)
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
            itemsIndexed(contacts, key = { _, contact -> contact.id }) { index, contact ->
                ContactRow(
                    contact = contact,
                    isFirst = index == 0,
                    isLast = index == contacts.size - 1,
                    onDelete = { onDelete(contact) },
                    onMoveUp = {
                        onMoveUp(index)
                    },
                    onMoveDown = {
                        onMoveDown(index)
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

//@Preview(showBackground = true)
//@Composable
//fun SettingsPreview() {
//    ShiftWatcherTheme {
//        SettingsScreen()
//    }
//}