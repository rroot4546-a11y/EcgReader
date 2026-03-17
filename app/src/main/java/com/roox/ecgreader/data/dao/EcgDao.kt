package com.roox.ecgreader.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.roox.ecgreader.data.model.EcgRecord

@Dao
interface EcgDao {
    @Insert
    suspend fun insert(record: EcgRecord): Long

    @Update
    suspend fun update(record: EcgRecord)

    @Delete
    suspend fun delete(record: EcgRecord)

    @Query("SELECT * FROM ecg_records WHERE isTrainingData = 0 ORDER BY timestamp DESC")
    fun getAllRecords(): LiveData<List<EcgRecord>>

    @Query("SELECT * FROM ecg_records WHERE isTrainingData = 1 ORDER BY timestamp DESC")
    fun getTrainingData(): LiveData<List<EcgRecord>>

    @Query("SELECT * FROM ecg_records WHERE id = :id")
    suspend fun getById(id: Int): EcgRecord?

    @Query("SELECT COUNT(*) FROM ecg_records WHERE isTrainingData = 1")
    suspend fun getTrainingCount(): Int

    @Query("SELECT * FROM ecg_records WHERE isTrainingData = 1")
    suspend fun getAllTrainingDataSync(): List<EcgRecord>
}
