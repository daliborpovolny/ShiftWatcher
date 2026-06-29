package com.daliborpovolny.shiftwatcher

import androidx.lifecycle.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
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

    //* User Settings

    val userName = dao.getUserSettingFlow("user_name")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateUserName(name: String) {
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("user_name", name))
        }
    }

    val useTestConfig = dao.getUserSettingFlow("use_test_config")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "false")

    fun updateUseTestConfig(useTest: Boolean) {
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("use_test_config", useTest.toString()))
        }
    }

    val batteryThreshold = dao.getUserSettingFlow("battery_threshold")
        .map { it?.toIntOrNull() ?: WatcherService.DEFAULT_BATTERY_THRESHOLD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatcherService.DEFAULT_BATTERY_THRESHOLD)

    fun updateBatteryThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(0, 60)
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("battery_threshold", clamped.toString()))
        }
    }

    val primaryCheckupInterval = dao.getUserSettingFlow("primary_checkup_interval")
        .map { it?.toIntOrNull() ?: WatcherService.DEFAULT_PRIMARY_CHECKUP_INTERVAL_MIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatcherService.DEFAULT_PRIMARY_CHECKUP_INTERVAL_MIN)

    fun updatePrimaryCheckupInterval(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("primary_checkup_interval", clamped.toString()))
        }
    }

    val escalationGracePeriod = dao.getUserSettingFlow("escalation_grace_period")
        .map { it?.toIntOrNull() ?: WatcherService.DEFAULT_ESCALATION_GRACE_PERIOD_MIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatcherService.DEFAULT_ESCALATION_GRACE_PERIOD_MIN)

    fun updateEscalationGracePeriod(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("escalation_grace_period", clamped.toString()))
        }
    }

    val contactAnswerWaitTime = dao.getUserSettingFlow("contact_answer_wait_time")
        .map { it?.toIntOrNull() ?: WatcherService.DEFAULT_ESCALATION_CONTACT_ANSWER_WAIT_TIME_MIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatcherService.DEFAULT_ESCALATION_CONTACT_ANSWER_WAIT_TIME_MIN)

    fun updateContactAnswerWaitTime(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("contact_answer_wait_time", clamped.toString()))
        }
    }

    fun resetTimeIntervalsToDefault() {
        viewModelScope.launch {
            dao.insertUserSetting(UserSetting("primary_checkup_interval", WatcherService.DEFAULT_PRIMARY_CHECKUP_INTERVAL_MIN.toString()))
            dao.insertUserSetting(UserSetting("escalation_grace_period", WatcherService.DEFAULT_ESCALATION_GRACE_PERIOD_MIN.toString()))
            dao.insertUserSetting(UserSetting("contact_answer_wait_time", WatcherService.DEFAULT_ESCALATION_CONTACT_ANSWER_WAIT_TIME_MIN.toString()))
        }
    }


    // Factory to help Android create the ViewModel with the DAO
    class Factory(private val dao: ContactDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContactViewModel(dao) as T
        }
    }
}