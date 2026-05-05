package com.daliborpovolny.shiftwatcher

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "escalation_contacts")
data class EscalationContact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val name: String,
    val priority: Int = 0
)

@Entity(tableName = "info_contacts")
data class InfoContact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val name: String,
//    val priority: Int = 0
)

@Entity(tableName = "user_settings")
data class UserSetting(
    @PrimaryKey val key: String,
    val value: String
)

interface EscalationContactManipulator {
    fun add(name: String, number: String)
    fun delete(contact: EscalationContact)
    fun moveUp(index: Int)
    fun moveDown(index: Int)
}

interface InfoContactManipulator {
    fun add(name: String, number: String)
    fun delete(contact: InfoContact)
}