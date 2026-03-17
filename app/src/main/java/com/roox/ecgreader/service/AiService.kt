package com.roox.ecgreader.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AiService(
    private val apiKey: String,
    private val model: String = "google/gemini-2.0-flash-001"
) {
    companion object {
        private const val TAG = "AiService"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            return AiService(
                apiKey = prefs.getString("openrouter_api_key", "") ?: "",
                model = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"
            )
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Analyze an ECG image with patient symptoms — sends the image as base64 to a vision model
     */
    suspend fun analyzeEcg(
        imageBase64: String,
        symptoms: String,
        age: String,
        gender: String,
        trainingContext: String = ""
    ): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("⚠️ No API key configured.\n\nGo to ⚙️ Settings → enter your OpenRouter API key.\nGet one at: openrouter.ai/keys")
            return@suspendCoroutine
        }

        val systemPrompt = buildString {
            append("You are a senior cardiologist and ECG interpretation expert. ")
            append("Analyze the provided 12-lead ECG image with the patient's clinical information.\n\n")
            append("Provide a structured analysis:\n\n")
            append("📊 ECG FINDINGS:\n")
            append("- Rate, Rhythm, Axis\n")
            append("- P waves, PR interval, QRS complex, ST segment, T waves, QT interval\n\n")
            append("🔍 INTERPRETATION:\n")
            append("- Primary diagnosis\n")
            append("- Secondary findings (if any)\n\n")
            append("🏥 CLINICAL CORRELATION:\n")
            append("- How findings relate to the patient's symptoms\n")
            append("- Differential diagnoses to consider\n\n")
            append("⚠️ URGENCY:\n")
            append("- Is this urgent/emergent?\n")
            append("- Recommended next steps\n\n")
            append("📚 REFERENCE:\n")
            append("- Cite relevant Harrison's/Braunwald's guidelines\n\n")
            append("Be thorough but concise. Use medical terminology with clear explanations.")
            if (trainingContext.isNotBlank()) {
                append("\n\nRelevant training cases:\n$trainingContext")
            }
        }

        val userPrompt = buildString {
            append("Patient Information:\n")
            append("- Age: $age\n")
            append("- Gender: $gender\n")
            append("- Symptoms: $symptoms\n\n")
            append("Please analyze this 12-lead ECG image.")
        }

        // Build message with image (vision model)
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to listOf(
                mapOf("type" to "text", "text" to userPrompt),
                mapOf("type" to "image_url", "image_url" to mapOf(
                    "url" to "data:image/jpeg;base64,$imageBase64"
                ))
            ))
        )

        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to messages,
            "max_tokens" to 3000
        ))

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/rroot4546-a11y/EcgReader")
            .addHeader("X-Title", "ECG Reader App")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network error", e)
                cont.resume("❌ Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "API ${response.code}: $responseBody")
                        val msg = when (response.code) {
                            401 -> "❌ Invalid API key."
                            402 -> "❌ Insufficient credits."
                            429 -> "❌ Rate limited. Wait and retry."
                            else -> "❌ API error (${response.code})"
                        }
                        cont.resume(msg)
                        return
                    }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        cont.resume("No response from AI.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    cont.resume("❌ Parse error: ${e.message}")
                }
            }
        })
    }

    suspend fun testConnection(): Boolean {
        return try {
            val result = analyzeText("Reply OK if you can read this.")
            result.isNotBlank() && !result.startsWith("❌") && !result.startsWith("⚠️")
        } catch (e: Exception) { false }
    }

    private suspend fun analyzeText(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) { cont.resume("⚠️ No API key"); return@suspendCoroutine }

        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 100
        ))

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cont.resume("❌ ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val rb = response.body?.string() ?: ""
                    if (!response.isSuccessful) { cont.resume("❌ ${response.code}"); return }
                    val json = JsonParser.parseString(rb).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        cont.resume(choices.get(0).asJsonObject.getAsJsonObject("message").get("content").asString)
                    } else cont.resume("")
                } catch (e: Exception) { cont.resume("❌ ${e.message}") }
            }
        })
    }
}
