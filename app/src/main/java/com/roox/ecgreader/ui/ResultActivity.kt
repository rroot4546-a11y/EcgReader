package com.roox.ecgreader.ui

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.roox.ecgreader.R
import com.roox.ecgreader.service.EcgWaveformExtractor
import com.roox.ecgreader.view.EcgGraphView
import com.roox.ecgreader.viewmodel.EcgViewModel

class ResultActivity : AppCompatActivity() {

    private lateinit var viewModel: EcgViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        viewModel = ViewModelProvider(this)[EcgViewModel::class.java]

        val ivEcg = findViewById<ImageView>(R.id.ivEcgImage)
        val ecgGraph = findViewById<EcgGraphView>(R.id.ecgGraphView)
        val tvDiagnosis = findViewById<TextView>(R.id.tvDiagnosis)
        val tvExplanation = findViewById<TextView>(R.id.tvExplanation)
        val tvModel = findViewById<TextView>(R.id.tvModelUsed)
        val tvGraphLabel = findViewById<TextView>(R.id.tvGraphLabel)
        val btnBack = findViewById<TextView>(R.id.btnBack)

        val resultText = intent.getStringExtra("result_text") ?: ""
        val imageUriStr = intent.getStringExtra("image_uri") ?: ""

        // Show original ECG image
        if (imageUriStr.isNotBlank()) {
            try { ivEcg.setImageURI(Uri.parse(imageUriStr)) } catch (_: Exception) { }
        }

        // Show model used
        val prefs = getSharedPreferences("ecg_prefs", MODE_PRIVATE)
        val modelId = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: ""
        tvModel.text = "🤖 Model: $modelId"

        if (resultText.isNotBlank()) {
            tvExplanation.text = resultText

            // Parse AI text to extract ECG parameters, then synthesize a clean waveform
            val params = EcgWaveformExtractor.parseFromAiText(resultText)
            val waveform = EcgWaveformExtractor.generateWaveform(params, 800)
            ecgGraph.setEcgData(waveform, "Lead II (${params.rhythm.uppercase()})")

            // Build graph label
            val rhythmName = when (params.rhythm) {
                "afib" -> "Atrial Fibrillation"
                "aflutter" -> "Atrial Flutter"
                "vtach" -> "Ventricular Tachycardia"
                "vfib" -> "Ventricular Fibrillation"
                "svt" -> "SVT"
                "bradycardia" -> "Sinus Bradycardia"
                "heartblock" -> "Heart Block"
                else -> "Sinus Rhythm"
            }
            tvGraphLabel.text = "📊 $rhythmName — ${params.heartRate} bpm" +
                if (params.stElevation > 0) " | ST↑" else "" +
                if (params.stDepression > 0) " | ST↓" else "" +
                if (params.tWaveInverted) " | T inv" else ""

            // Extract primary diagnosis
            tvDiagnosis.text = extractPrimaryDiagnosis(resultText)
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun extractPrimaryDiagnosis(text: String): String {
        val lines = text.lines()
        var foundSection = false
        for (line in lines) {
            if (line.contains("PRIMARY DIAGNOSIS")) {
                foundSection = true
                continue
            }
            if (foundSection) {
                val clean = line.replace(Regex("[═━\\[\\]]"), "").trim()
                if (clean.isNotBlank() && clean.length > 3) {
                    return "🫀 $clean"
                }
            }
        }
        return "🫀 ECG Analysis Complete"
    }
}
