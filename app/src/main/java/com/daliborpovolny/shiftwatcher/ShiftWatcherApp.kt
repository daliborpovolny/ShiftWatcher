package com.daliborpovolny.shiftwatcher

import android.app.Application
import androidx.room.Room

class ShiftWatcherApp : Application() {
    // 'by lazy' means it only builds the DB when you actually first use it
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "shift_watcher_db"
        )
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                1, 2
            ).build()
    }
}