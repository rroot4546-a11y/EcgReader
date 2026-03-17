package com.roox.ecgreader

import android.app.Application
import com.roox.ecgreader.data.database.AppDatabase
import com.roox.ecgreader.data.repository.EcgRepository

class EcgApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { EcgRepository(database.ecgDao()) }
}
