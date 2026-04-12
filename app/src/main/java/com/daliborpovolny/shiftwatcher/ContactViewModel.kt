package com.daliborpovolny.shiftwatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactViewModel(private val dao: ContactDao) : ViewModel() {

    // Converts the database Flow into a StateFlow that Compose can easily watch
    val contacts = dao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addContact(name: String, number: String) {
        viewModelScope.launch {
            // We find the current highest priority to put the new one at the end
            val currentMax = contacts.value.maxOfOrNull { it.priority } ?: -1
            dao.insertContact(Contact(name = name, number = number, priority = currentMax + 1))
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch { dao.deleteContact(contact) }
    }

    fun moveUp(index: Int) {
        if (index > 0) {
            viewModelScope.launch {
                val list = contacts.value.toMutableList()
                val current = list[index]
                val above = list[index - 1]

                // Swap priorities
                dao.updateContact(current.copy(priority = above.priority))
                dao.updateContact(above.copy(priority = current.priority))
            }
        }
    }

    fun moveDown(index: Int) {
        if (index < contacts.value.size - 1) {
            viewModelScope.launch {
                val list = contacts.value.toMutableList()
                val current = list[index]
                val below = list[index + 1]

                // Swap priorities
                dao.updateContact(current.copy(priority = below.priority))
                dao.updateContact(below.copy(priority = current.priority))
            }
        }
    }


    // Factory to help Android create the ViewModel with the DAO
    class Factory(private val dao: ContactDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContactViewModel(dao) as T
        }
    }
}