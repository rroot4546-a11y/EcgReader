package com.roox.ecgreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.roox.ecgreader.R
import com.roox.ecgreader.data.model.EcgRecord
import com.roox.ecgreader.viewmodel.EcgViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: EcgViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[EcgViewModel::class.java]

        val recycler = findViewById<RecyclerView>(R.id.rvRecords)
        val tvEmpty = findViewById<View>(R.id.tvEmpty)
        val fabAnalyze = findViewById<FloatingActionButton>(R.id.fabAnalyze)
        val btnSettings = findViewById<View>(R.id.btnSettings)
        val btnTraining = findViewById<View>(R.id.btnTraining)

        recycler.layoutManager = LinearLayoutManager(this)

        val adapter = RecordAdapter { record ->
            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("record_id", record.id)
            startActivity(intent)
        }
        recycler.adapter = adapter

        viewModel.allRecords.observe(this) { records ->
            adapter.submitList(records)
            tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        }

        fabAnalyze.setOnClickListener {
            startActivity(Intent(this, AnalyzeActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnTraining.setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }
    }

    // Simple RecyclerView adapter
    class RecordAdapter(
        private val onClick: (EcgRecord) -> Unit
    ) : RecyclerView.Adapter<RecordAdapter.VH>() {

        private var items: List<EcgRecord> = emptyList()

        fun submitList(list: List<EcgRecord>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvDiagnosis: TextView = view.findViewById(R.id.tvDiagnosis)
            val tvSymptoms: TextView = view.findViewById(R.id.tvSymptoms)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val record = items[position]
            holder.tvDiagnosis.text = record.diagnosis.ifBlank { "No diagnosis" }
            holder.tvSymptoms.text = "🩺 ${record.symptoms.ifBlank { "No symptoms" }}"
            val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(record.timestamp))
            holder.tvDate.text = date
            holder.itemView.setOnClickListener { onClick(record) }
        }

        override fun getItemCount() = items.size
    }
}
