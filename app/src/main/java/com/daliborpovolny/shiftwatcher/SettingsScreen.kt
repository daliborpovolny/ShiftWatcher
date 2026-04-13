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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

enum class ContactType {
    Escalation, Info
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSettingsScreen(
    infoContacts: List<InfoContact>,
    infoContactsManipulator: InfoContactManipulator,
    escalationContacts: List<EscalationContact>,
    escalationContactsManipulator: EscalationContactManipulator,

    ) {
    var newNumber by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    var selectedType by remember { mutableStateOf(ContactType.Info) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Contact Book", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // --- TOGGLE SWITCH ---
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ContactType.entries.size
                    ),
                    onClick = { selectedType = type },
                    selected = selectedType == type
                ) {
                    Text(type.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Add to the ${selectedType.name} List",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        val escalationInfo =
            "List of contacts, that will be progressively be called top to bottom if an alarm is not dismissed within 15 minutes"
        val infoInfo = "List of contacts, that will each be texted on the start and end of a shift"

        Text(
            text = if (selectedType == ContactType.Escalation) escalationInfo else infoInfo,
            style = MaterialTheme.typography.bodySmall
        )

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
                    if (selectedType == ContactType.Escalation) {
                        escalationContactsManipulator.add(newName, newNumber)
                    } else {
                        infoContactsManipulator.add(newName, newNumber)
                    }
                    newNumber = ""
                    newName = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add to ${selectedType.name}")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedType == ContactType.Escalation) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(
                    escalationContacts,
                    key = { _, contact -> contact.id }) { index, contact ->
                    EscalationContactRow(
                        contact = contact,
                        isFirst = index == 0,
                        isLast = index == escalationContacts.size - 1,
                        onDelete = { escalationContactsManipulator.delete(contact) },
                        onMoveUp = { escalationContactsManipulator.moveUp(index) },
                        onMoveDown = { escalationContactsManipulator.moveDown(index) }
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(infoContacts, key = { _, contact -> contact.id }) { _, contact ->
                    InfoContactRow(
                        contact = contact,
                        onDelete = { infoContactsManipulator.delete(contact) },
                    )
                }
            }
        }


    }
}

@Composable
fun EscalationContactRow(
    contact: EscalationContact,
    isFirst: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Composable
fun InfoContactRow(
    contact: InfoContact,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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

            // Delete Button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SettingsPreview() {

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

        NewSettingsScreen(
            escalationContacts = listOf(
                EscalationContact(number = "123 456 789", name = "John Lennon"),
                EscalationContact(number = "987 654 321", name = "Ringo Starr")
            ),
            infoContacts = listOf(
                InfoContact(number = "123 456 789", name = "George Harrison"),
                InfoContact(number = "987 654 321", name = "Paul MacCartney")
            ),
            escalationContactsManipulator = dummyEscalationManipulator,
            infoContactsManipulator = dummyInfoManipulator

        )
    }
}