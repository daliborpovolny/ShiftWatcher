package com.daliborpovolny.shiftwatcher

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EscalationContact::class, InfoContact::class, UserSetting::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}