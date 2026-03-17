package com.roox.ecgreader.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.ecgreader.R
import com.roox.ecgreader.service.AiService
import com.roox.ecgreader.service.MedicalModels
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val spinnerModel = findViewById<Spinner>(R.id.spinnerModel)
        val tvModelDesc = findViewById<TextView>(R.id.tvModelDesc)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("ecg_prefs", MODE_PRIVATE)

        // Load saved key
        etApiKey.setText(prefs.getString("openrouter_api_key", ""))

        // Setup model spinner
        val modelNames = MedicalModels.models.map { m ->
            val tag = if (m.isFree) " (FREE)" else ""
            "${m.name}$tag"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = adapter

        // Select saved model
        val savedModel = prefs.getString("openrouter_model", MedicalModels.getDefault().id) ?: ""
        val savedIdx = MedicalModels.models.indexOfFirst { it.id == savedModel }
        if (savedIdx >= 0) spinnerModel.setSelection(savedIdx)

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                val m = MedicalModels.models[pos]
                tvModelDesc.text = "📝 ${m.description}\n🆔 ${m.id}"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            val selectedIdx = spinnerModel.selectedItemPosition
            val selectedModel = MedicalModels.models[selectedIdx]
            prefs.edit()
                .putString("openrouter_api_key", etApiKey.text.toString().trim())
                .putString("openrouter_model", selectedModel.id)
                .apply()
            tvStatus.text = "✅ Saved! Using ${selectedModel.name}"
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            btnSave.performClick()
            tvStatus.text = "🔄 Testing connection..."
            btnTest.isEnabled = false
            lifecycleScope.launch {
                try {
                    val service = AiService.fromPrefs(prefs)
                    val ok = service.testConnection()
                    tvStatus.text = if (ok) "✅ Connected! AI is ready." else "❌ Failed. Check API key."
                } catch (e: Exception) {
                    tvStatus.text = "❌ Error: ${e.message}"
                } finally {
                    btnTest.isEnabled = true
                }
            }
        }
    }
}
