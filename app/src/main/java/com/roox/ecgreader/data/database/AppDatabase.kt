package com.roox.ecgreader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.roox.ecgreader.data.dao.EcgDao
import com.roox.ecgreader.data.model.EcgRecord

@Database(entities = [EcgRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ecgDao(): EcgDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecg_reader_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
