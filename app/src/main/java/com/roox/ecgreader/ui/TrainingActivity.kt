package com.roox.ecgreader.ui

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

class TrainingActivity : AppCompatActivity() {

    private lateinit var viewModel: EcgViewModel
    private var selectedUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedUri = it
            findViewById<ImageView>(R.id.ivTrainingPreview).apply {
                setImageURI(it)
                visibility = View.VISIBLE
            }
            findViewById<Button>(R.id.btnPickTraining).text = "📷 Change Image"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training)

        viewModel = ViewModelProvider(this)[EcgViewModel::class.java]

        val tvCount = findViewById<TextView>(R.id.tvTrainingCount)
        val etDiagnosis = findViewById<TextInputEditText>(R.id.etTrainingDiagnosis)
        val etSymptoms = findViewById<TextInputEditText>(R.id.etTrainingSymptoms)
        val btnPick = findViewById<Button>(R.id.btnPickTraining)
        val btnAdd = findViewById<Button>(R.id.btnAddTraining)
        val tvStatus = findViewById<TextView>(R.id.tvTrainingStatus)

        viewModel.refreshTrainingCount()
        viewModel.trainingCount.observe(this) { count ->
            tvCount.text = "📚 Training data: $count ECG records"
        }

        btnPick.setOnClickListener { pickImage.launch("image/*") }

        btnAdd.setOnClickListener {
            val uri = selectedUri
            val diagnosis = etDiagnosis.text.toString().trim()

            if (uri == null) {
                tvStatus.text = "⚠️ Upload an ECG image first"
                return@setOnClickListener
            }
            if (diagnosis.isBlank()) {
                tvStatus.text = "⚠️ Enter the diagnosis"
                return@setOnClickListener
            }

            viewModel.addTrainingData(uri, diagnosis, etSymptoms.text.toString().trim())

            // Reset
            selectedUri = null
            etDiagnosis.setText("")
            etSymptoms.setText("")
            findViewById<ImageView>(R.id.ivTrainingPreview).visibility = View.GONE
            btnPick.text = "📷 Upload ECG Image"
            tvStatus.text = "✅ Training data added!"
            viewModel.refreshTrainingCount()
        }
    }
}
