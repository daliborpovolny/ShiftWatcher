package com.daliborpovolny.shiftwatcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

enum class ScreenType {
    Escalation, Info, Personal
}

fun ScreenTypeToCzechName(st: ScreenType): String {
    return when (st) {
        ScreenType.Escalation -> "Eskalace"
        ScreenType.Info -> "Info"
        ScreenType.Personal -> "Osobní"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSettingsScreen(
    infoContacts: List<InfoContact>,
    infoContactsManipulator: InfoContactManipulator,
    escalationContacts: List<EscalationContact>,
    escalationContactsManipulator: EscalationContactManipulator,
    userName: String?,
    onUserNameChange: (String) -> Unit,
    useTestConfig: Boolean,
    onUseTestConfigChange: (Boolean) -> Unit
) {
    var newNumber by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    var selectedType by remember { mutableStateOf(ScreenType.Personal) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Nastavení", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // --- TOGGLE SWITCH ---
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            ScreenType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ScreenType.entries.size
                    ),
                    onClick = { selectedType = type },
                    selected = selectedType == type
                ) {
                    Text(ScreenTypeToCzechName(type))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedType == ScreenType.Personal) {
            PersonalDetails(
                userName = userName ?: "",
                onUserNameChange = onUserNameChange,
                useTestConfig = useTestConfig,
                onUseTestConfigChange = onUseTestConfigChange
            )
            return
        }

        Text(
            text = "Přidat do ${ScreenTypeToCzechName(selectedType)} seznamu",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        val escalationInfo =
            "Seznam kontaktů, které budou postupně od vrchu kontaktovány, pokud nebude kontrolní budík odkliknut"
        val infoInfo = "Seznam kontaktů, které budou pomocí SMS informovány o začátku a konci směny"

        Text(
            text = if (selectedType == ScreenType.Escalation) escalationInfo else infoInfo,
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Jméno") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newNumber,
            onValueChange = { newNumber = it },
            label = { Text("Telefoní číslo ve formátu: +420777888999") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (newNumber.isNotBlank() && newName.isNotBlank()) {
                    if (selectedType == ScreenType.Escalation) {
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
            Text("Přidat do ${ScreenTypeToCzechName(selectedType)}")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedType == ScreenType.Escalation) {
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
fun PersonalDetails(
    userName: String,
    onUserNameChange: (String) -> Unit,
    useTestConfig: Boolean,
    onUseTestConfigChange: (Boolean) -> Unit
) {
    var tempName by remember(userName) { mutableStateOf(userName) }

    Column {
        Text("Osobní údaje", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tempName,
            onValueChange = { tempName = it },
            label = { Text("Vaše jméno") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onUserNameChange(tempName) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uložit jméno")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Toto jméno bude použito v SMS zprávách zasílaných vašim kontaktům.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // --- TEST SERVICE CONFIGURATION SECTION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (useTestConfig) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Testovací konfigurace služby",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (useTestConfig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (useTestConfig) "Aktivní (Zrychlené časy pro test)" else "Neaktivní (Běžný provoz)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useTestConfig,
                        onCheckedChange = onUseTestConfigChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (useTestConfig) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Varování",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "POZOR - tohle je testovací konfigurace, kde jsou všechny časy velmi zkrácené",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    Text(
                        text = "V běžném režimu jsou intervaly nastaveny na: kontrola každou 1 hodinu, eskalace po 15 minutách nečinnosti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Posunout nahoru")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Posunout dolů")
                }
            }

            // Delete Button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Smazat",
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
                    contentDescription = "Smazat",
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
            infoContactsManipulator = dummyInfoManipulator,
            userName = "...",
            onUserNameChange = {},
            useTestConfig = false,
            onUseTestConfigChange = {}
        )
    }
}
