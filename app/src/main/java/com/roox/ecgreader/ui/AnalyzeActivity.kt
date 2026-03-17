package com.roox.ecgreader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.roox.ecgreader.R
import com.roox.ecgreader.viewmodel.EcgViewModel

class AnalyzeActivity : AppCompatActivity() {

    private lateinit var viewModel: EcgViewModel
    private var selectedImageUri: Uri? = null

    private lateinit var ivPreview: ImageView
    private lateinit var btnPickImage: Button
    private lateinit var etAge: TextInputEditText
    private lateinit var etSymptoms: TextInputEditText
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var btnAnalyze: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            ivPreview.setImageURI(it)
            ivPreview.visibility = View.VISIBLE
            btnPickImage.text = "📷 Change Image"
            tvStatus.text = "✅ ECG image loaded"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        viewModel = ViewModelProvider(this)[EcgViewModel::class.java]

        ivPreview = findViewById(R.id.ivPreview)
        btnPickImage = findViewById(R.id.btnPickImage)
        etAge = findViewById(R.id.etAge)
        etSymptoms = findViewById(R.id.etSymptoms)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        btnPickImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnAnalyze.setOnClickListener { analyze() }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            btnAnalyze.isEnabled = !loading
            btnAnalyze.text = if (loading) "🔄 Analyzing..." else "🫀 Analyze ECG"
        }

        viewModel.analysisResult.observe(this) { result ->
            if (result.isNotBlank()) {
                // Go to result
                val intent = Intent(this, ResultActivity::class.java)
                val recordId = viewModel.currentRecord.value?.id ?: 0
                intent.putExtra("record_id", recordId)
                intent.putExtra("result_text", result)
                intent.putExtra("image_uri", selectedImageUri.toString())
                startActivity(intent)
                finish()
            }
        }
    }

    private fun analyze() {
        val uri = selectedImageUri
        if (uri == null) {
            tvStatus.text = "⚠️ Please upload an ECG image first"
            return
        }

        val symptoms = etSymptoms.text.toString().trim()
        if (symptoms.isBlank()) {
            tvStatus.text = "⚠️ Please enter the patient's symptoms"
            return
        }

        val age = etAge.text.toString().trim().ifBlank { "Unknown" }
        val gender = if (radioFemale.isChecked) "Female" else "Male"

        val prefs = getSharedPreferences("ecg_prefs", MODE_PRIVATE)

        if (prefs.getString("openrouter_api_key", "")?.isBlank() != false) {
            tvStatus.text = "⚠️ Set up your API key in Settings first"
            return
        }

        tvStatus.text = "🔄 Analyzing ECG with AI..."
        viewModel.analyzeEcg(uri, symptoms, age, gender, prefs)
    }
}
