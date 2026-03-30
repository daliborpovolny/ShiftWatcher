package com.daliborpovolny.shiftwatcher

data class Contact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    var name: String,
)