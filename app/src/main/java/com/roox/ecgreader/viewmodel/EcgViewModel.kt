package com.roox.ecgreader.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.*
import com.roox.ecgreader.EcgApplication
import com.roox.ecgreader.data.model.EcgRecord
import com.roox.ecgreader.service.AiService
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class EcgViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as EcgApplication).repository

    val allRecords = repository.allRecords
    val trainingData = repository.trainingData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _analysisResult = MutableLiveData<String>()
    val analysisResult: LiveData<String> = _analysisResult

    private val _currentRecord = MutableLiveData<EcgRecord?>()
    val currentRecord: LiveData<EcgRecord?> = _currentRecord

    private val _trainingCount = MutableLiveData(0)
    val trainingCount: LiveData<Int> = _trainingCount

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun analyzeEcg(
        imageUri: Uri,
        symptoms: String,
        age: String,
        gender: String,
        prefs: android.content.SharedPreferences
    ) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                _analysisResult.postValue("")

                // Convert image to base64
                val context = getApplication<EcgApplication>()
                val base64 = imageToBase64(context, imageUri)

                if (base64.isBlank()) {
                    _analysisResult.postValue("❌ Could not read the image. Try a different file.")
                    return@launch
                }

                // Get training context
                val trainingContext = buildTrainingContext()

                // Call AI
                val aiService = AiService.fromPrefs(prefs)
                val result = aiService.analyzeEcg(
                    imageBase64 = base64,
                    symptoms = symptoms,
                    age = age,
                    gender = gender,
                    trainingContext = trainingContext
                )

                _analysisResult.postValue(result)

                // Save record
                val record = EcgRecord(
                    patientAge = age,
                    patientGender = gender,
                    symptoms = symptoms,
                    imagePath = imageUri.toString(),
                    diagnosis = extractDiagnosis(result),
                    aiExplanation = result
                )
                val id = repository.insert(record)
                _currentRecord.postValue(record.copy(id = id.toInt()))

            } catch (e: Exception) {
                Log.e("EcgVM", "Analysis error", e)
                _analysisResult.postValue("❌ Error: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun addTrainingData(
        imageUri: Uri,
        diagnosis: String,
        symptoms: String = ""
    ) {
        viewModelScope.launch {
            try {
                val record = EcgRecord(
                    symptoms = symptoms,
                    imagePath = imageUri.toString(),
                    diagnosis = diagnosis,
                    isTrainingData = true
                )
                repository.insert(record)
                refreshTrainingCount()
            } catch (e: Exception) {
                _errorMessage.postValue("Error: ${e.message}")
            }
        }
    }

    fun refreshTrainingCount() {
        viewModelScope.launch {
            _trainingCount.postValue(repository.getTrainingCount())
        }
    }

    fun deleteRecord(record: EcgRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    private suspend fun buildTrainingContext(): String {
        val training = repository.getAllTrainingDataSync()
        if (training.isEmpty()) return ""

        return buildString {
            append("Known ECG patterns from training data (${training.size} cases):\n")
            training.take(20).forEachIndexed { i, record ->
                append("Case ${i + 1}: Diagnosis=${record.diagnosis}")
                if (record.symptoms.isNotBlank()) append(", Symptoms=${record.symptoms}")
                append("\n")
            }
        }
    }

    private fun extractDiagnosis(result: String): String {
        // Try to extract the primary diagnosis from AI response
        val lines = result.lines()
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("primary diagnosis") || lower.contains("interpretation")) {
                val clean = line.replace(Regex("[*#🔍-]"), "").trim()
                if (clean.length > 5) return clean.take(200)
            }
        }
        return result.take(200)
    }

    private fun imageToBase64(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Resize if too large (max 1024px)
            val maxSize = 1024
            val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("EcgVM", "Image error", e)
            ""
        }
    }
}
