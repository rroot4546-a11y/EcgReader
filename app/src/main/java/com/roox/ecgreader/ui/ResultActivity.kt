package com.roox.ecgreader.ui

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.roox.ecgreader.R
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
        val btnBack = findViewById<TextView>(R.id.btnBack)

        // Show ECG demo graph (normal sinus rhythm)
        val demoData = EcgGraphView.generateNormalSinus(75, 500)
        ecgGraph.setEcgData(demoData, "Lead II")

        // Load from intent
        val resultText = intent.getStringExtra("result_text") ?: ""
        val imageUriStr = intent.getStringExtra("image_uri") ?: ""
        val recordId = intent.getIntExtra("record_id", 0)

        if (imageUriStr.isNotBlank()) {
            try {
                ivEcg.setImageURI(Uri.parse(imageUriStr))
            } catch (_: Exception) { }
        }

        if (resultText.isNotBlank()) {
            tvExplanation.text = resultText
            tvDiagnosis.text = "🫀 ECG Analysis Complete"
        } else if (recordId > 0) {
            // Load from database
            tvDiagnosis.text = "Loading..."
            viewModel.currentRecord.observe(this) { record ->
                record?.let {
                    tvDiagnosis.text = "🫀 ${it.diagnosis}"
                    tvExplanation.text = it.aiExplanation
                    if (it.imagePath.isNotBlank()) {
                        try { ivEcg.setImageURI(Uri.parse(it.imagePath)) } catch (_: Exception) { }
                    }
                }
            }
        }

        btnBack.setOnClickListener { finish() }
    }
}
