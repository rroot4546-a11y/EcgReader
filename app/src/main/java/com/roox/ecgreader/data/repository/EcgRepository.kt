package com.roox.ecgreader.data.repository

import androidx.lifecycle.LiveData
import com.roox.ecgreader.data.dao.EcgDao
import com.roox.ecgreader.data.model.EcgRecord

class EcgRepository(private val dao: EcgDao) {
    val allRecords: LiveData<List<EcgRecord>> = dao.getAllRecords()
    val trainingData: LiveData<List<EcgRecord>> = dao.getTrainingData()

    suspend fun insert(record: EcgRecord): Long = dao.insert(record)
    suspend fun update(record: EcgRecord) = dao.update(record)
    suspend fun delete(record: EcgRecord) = dao.delete(record)
    suspend fun getById(id: Int): EcgRecord? = dao.getById(id)
    suspend fun getTrainingCount(): Int = dao.getTrainingCount()
    suspend fun getAllTrainingDataSync(): List<EcgRecord> = dao.getAllTrainingDataSync()
}
