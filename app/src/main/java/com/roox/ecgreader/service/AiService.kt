package com.roox.ecgreader.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AI models ranked for medical ECG interpretation.
 * Each has vision capability for reading ECG images.
 */
data class AiModel(
    val id: String,
    val name: String,
    val description: String,
    val isFree: Boolean = false
)

object MedicalModels {
    val models = listOf(
        AiModel("google/gemini-2.5-pro-preview", "Gemini 2.5 Pro", "Best reasoning, excellent medical knowledge", false),
        AiModel("anthropic/claude-sonnet-4", "Claude Sonnet 4", "Strong medical reasoning, detailed analysis", false),
        AiModel("openai/gpt-4o", "GPT-4o", "Good vision + medical knowledge", false),
        AiModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", "Fast & free, good accuracy", true),
        AiModel("meta-llama/llama-4-maverick", "Llama 4 Maverick", "Open-source, strong medical performance", false),
        AiModel("deepseek/deepseek-chat-v3-0324:free", "DeepSeek V3", "Free, decent medical analysis", true),
    )

    fun getById(id: String): AiModel? = models.find { it.id == id }
    fun getDefault(): AiModel = models[3] // Gemini 2.0 Flash
}

class AiService(
    private val apiKey: String,
    private val model: String = MedicalModels.getDefault().id
) {
    companion object {
        private const val TAG = "AiService"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            return AiService(
                apiKey = prefs.getString("openrouter_api_key", "") ?: "",
                model = prefs.getString("openrouter_model", MedicalModels.getDefault().id)
                    ?: MedicalModels.getDefault().id
            )
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeEcg(
        imageBase64: String,
        symptoms: String,
        age: String,
        gender: String,
        trainingContext: String = ""
    ): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("⚠️ No API key.\n\nGo to ⚙️ Settings → enter OpenRouter API key.\nGet one at: openrouter.ai/keys")
            return@suspendCoroutine
        }

        val systemPrompt = buildString {
            append("You are a board-certified cardiologist and ECG interpretation expert with 20+ years of experience.\n")
            append("You are reading a 12-lead ECG for a patient. Analyze it systematically and thoroughly.\n\n")
            append("CRITICAL: Use the latest guidelines from:\n")
            append("- AHA/ACC/HRS ECG Interpretation Guidelines\n")
            append("- Braunwald's Heart Disease (latest edition)\n")
            append("- Harrison's Principles of Internal Medicine\n")
            append("- Marriott's Practical Electrocardiography\n\n")
            append("Provide your analysis in this EXACT structured format:\n\n")
            append("═══════════════════════════════\n")
            append("📊 TECHNICAL PARAMETERS\n")
            append("═══════════════════════════════\n")
            append("• Heart Rate: ___ bpm\n")
            append("• Rhythm: (Regular/Irregular)\n")
            append("• Axis: (Normal/LAD/RAD) ___°\n")
            append("• PR Interval: ___ ms\n")
            append("• QRS Duration: ___ ms\n")
            append("• QT/QTc: ___ ms\n\n")
            append("═══════════════════════════════\n")
            append("🔍 SYSTEMATIC ANALYSIS\n")
            append("═══════════════════════════════\n")
            append("P Waves: \n")
            append("PR Interval: \n")
            append("QRS Complex: \n")
            append("ST Segment: \n")
            append("T Waves: \n")
            append("U Waves: \n\n")
            append("═══════════════════════════════\n")
            append("🫀 PRIMARY DIAGNOSIS\n")
            append("═══════════════════════════════\n")
            append("[Main finding with confidence level]\n\n")
            append("═══════════════════════════════\n")
            append("⚠️ SECONDARY FINDINGS\n")
            append("═══════════════════════════════\n")
            append("[Additional findings]\n\n")
            append("═══════════════════════════════\n")
            append("🏥 CLINICAL CORRELATION\n")
            append("═══════════════════════════════\n")
            append("[How ECG findings relate to symptoms]\n\n")
            append("═══════════════════════════════\n")
            append("🚨 URGENCY ASSESSMENT\n")
            append("═══════════════════════════════\n")
            append("Level: [🟢 Routine / 🟡 Urgent / 🔴 Emergent]\n")
            append("Action: [Recommended next steps]\n\n")
            append("═══════════════════════════════\n")
            append("💊 DIFFERENTIAL DIAGNOSIS\n")
            append("═══════════════════════════════\n")
            append("1. [Most likely]\n2. [Second]\n3. [Third]\n\n")
            append("═══════════════════════════════\n")
            append("📚 REFERENCE\n")
            append("═══════════════════════════════\n")
            append("[Cite relevant guideline/textbook]\n")

            if (trainingContext.isNotBlank()) {
                append("\n\nRelevant training cases for context:\n$trainingContext")
            }
        }

        val userPrompt = buildString {
            append("PATIENT INFORMATION:\n")
            append("━━━━━━━━━━━━━━━━━━━\n")
            append("• Age: $age\n")
            append("• Gender: $gender\n")
            append("• Presenting symptoms: $symptoms\n\n")
            append("Please analyze the attached 12-lead ECG image systematically.")
        }

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
            "max_tokens" to 4000,
            "temperature" to 0.3
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
            val result = analyzeText("Reply with OK")
            result.isNotBlank() && !result.startsWith("❌") && !result.startsWith("⚠️")
        } catch (e: Exception) { false }
    }

    private suspend fun analyzeText(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) { cont.resume("⚠️ No API key"); return@suspendCoroutine }
        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 50
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
