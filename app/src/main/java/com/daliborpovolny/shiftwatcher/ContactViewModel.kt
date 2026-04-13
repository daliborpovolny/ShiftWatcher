package com.daliborpovolny.shiftwatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactViewModel(private val dao: ContactDao) : ViewModel() {

    // Converts the database Flow into a StateFlow that Compose can easily watch

    //* Escalation Contacts

    val escalationContacts = dao.getAllEscalationContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEscalationContact(name: String, number: String) {
        viewModelScope.launch {
            // We find the current highest priority to put the new one at the end
            val currentMax = escalationContacts.value.maxOfOrNull { it.priority } ?: -1
            dao.insertEscalationContact(
                EscalationContact(
                    name = name,
                    number = number,
                    priority = currentMax + 1
                )
            )
        }
    }

    fun deleteEscalationContact(escalationContact: EscalationContact) {
        viewModelScope.launch { dao.deleteEscalationContact(escalationContact) }
    }

    fun moveUp(index: Int) {
        if (index > 0) {
            viewModelScope.launch {
                val list = escalationContacts.value.toMutableList()
                val current = list[index]
                val above = list[index - 1]

                // Swap priorities
                dao.updateEscalationContact(current.copy(priority = above.priority))
                dao.updateEscalationContact(above.copy(priority = current.priority))
            }
        }
    }

    fun moveDown(index: Int) {
        if (index < escalationContacts.value.size - 1) {
            viewModelScope.launch {
                val list = escalationContacts.value.toMutableList()
                val current = list[index]
                val below = list[index + 1]

                // Swap priorities
                dao.updateEscalationContact(current.copy(priority = below.priority))
                dao.updateEscalationContact(below.copy(priority = current.priority))
            }
        }
    }


    //* Info Contacts

    val infoContacts = dao.getAllInfoContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addInfoContact(name: String, number: String) {
        viewModelScope.launch {
            dao.insertInfoContact(InfoContact(name = name, number = number))
        }
    }

    fun deleteInfoContact(infoContact: InfoContact) {
        viewModelScope.launch { dao.deleteInfoContact(infoContact) }
    }


    // Factory to help Android create the ViewModel with the DAO
    class Factory(private val dao: ContactDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContactViewModel(dao) as T
        }
    }
}