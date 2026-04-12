package com.daliborpovolny.shiftwatcher

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EscalationContact::class, InfoContact::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}