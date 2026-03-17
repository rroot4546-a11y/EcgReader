package com.roox.ecgreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ecg_records")
data class EcgRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientAge: String = "",
    val patientGender: String = "",
    val symptoms: String = "",
    val imagePath: String = "",
    val diagnosis: String = "",
    val aiExplanation: String = "",
    val ecgData: String = "",       // JSON array of signal values for drawing
    val heartRate: Int = 0,
    val rhythm: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isTrainingData: Boolean = false  // true = uploaded for training
)
