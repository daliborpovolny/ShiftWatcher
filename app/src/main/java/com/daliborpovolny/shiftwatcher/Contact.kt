package com.daliborpovolny.shiftwatcher

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val name: String,
    val priority: Int = 0
)