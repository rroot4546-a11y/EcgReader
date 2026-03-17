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
        val btnBack = findViewById<TextView>(R.id.btnBack)

        // Load from intent
        val resultText = intent.getStringExtra("result_text") ?: ""
        val imageUriStr = intent.getStringExtra("image_uri") ?: ""
        val recordId = intent.getIntExtra("record_id", 0)

        // Show uploaded ECG image
        if (imageUriStr.isNotBlank()) {
            try {
                val uri = Uri.parse(imageUriStr)
                ivEcg.setImageURI(uri)

                // Extract waveform from the uploaded ECG image and redraw it cleanly
                val waveform = EcgWaveformExtractor.extractFromUri(this, uri, 800)
                if (waveform.isNotEmpty()) {
                    ecgGraph.setEcgDataFromList(waveform, "Extracted ECG")
                } else {
                    // Fallback: show demo
                    ecgGraph.setEcgData(EcgGraphView.generateNormalSinus(75, 500), "Lead II (Demo)")
                }
            } catch (_: Exception) {
                ecgGraph.setEcgData(EcgGraphView.generateNormalSinus(75, 500), "Lead II (Demo)")
            }
        }

        // Show model used
        val prefs = getSharedPreferences("ecg_prefs", MODE_PRIVATE)
        val modelId = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: ""
        tvModel.text = "🤖 Model: $modelId"

        if (resultText.isNotBlank()) {
            tvExplanation.text = resultText
            // Extract primary diagnosis from result
            val diagnosis = extractPrimaryDiagnosis(resultText)
            tvDiagnosis.text = diagnosis
        } else if (recordId > 0) {
            tvDiagnosis.text = "Loading..."
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
                val clean = line.replace(Regex("[═━]"), "").trim()
                if (clean.isNotBlank() && !clean.startsWith("═") && !clean.startsWith("[")) {
                    return "🫀 $clean"
                }
            }
        }
        return "🫀 ECG Analysis Complete"
    }
}
