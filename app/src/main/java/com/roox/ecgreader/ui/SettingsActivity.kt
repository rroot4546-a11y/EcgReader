package com.roox.ecgreader.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.ecgreader.R
import com.roox.ecgreader.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val etModel = findViewById<TextInputEditText>(R.id.etModel)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("ecg_prefs", MODE_PRIVATE)

        etApiKey.setText(prefs.getString("openrouter_api_key", ""))
        etModel.setText(prefs.getString("openrouter_model", "google/gemini-2.0-flash-001"))

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("openrouter_api_key", etApiKey.text.toString().trim())
                .putString("openrouter_model", etModel.text.toString().trim().ifBlank { "google/gemini-2.0-flash-001" })
                .apply()
            tvStatus.text = "✅ Settings saved!"
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            btnSave.performClick()
            tvStatus.text = "🔄 Testing..."
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
