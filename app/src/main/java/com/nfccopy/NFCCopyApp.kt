package com.nfccopy

import android.app.Application
import com.nfccopy.data.db.AppDatabase
import com.nfccopy.data.repository.CardRepository

class NFCCopyApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = CardRepository(database.nfcCardDao())
    }
}
