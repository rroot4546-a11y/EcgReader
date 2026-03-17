package com.roox.ecgreader.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that draws ECG waveform on graph paper background
 * Standard ECG paper: 25mm/s speed, 10mm/mV calibration
 * Small box = 1mm (0.04s), Large box = 5mm (0.2s)
 */
class EcgGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ECG data points (normalized -1.0 to 1.0)
    private var ecgData: FloatArray = floatArrayOf()
    private var leadName: String = "II"

    // Grid colors (classic ECG paper)
    private val bgColor = Color.parseColor("#FFF8F0")        // Slightly warm white
    private val smallGridColor = Color.parseColor("#FFD4D4")  // Light pink
    private val largeGridColor = Color.parseColor("#FF9999")  // Darker pink
    private val waveColor = Color.parseColor("#1a1a1a")       // Near black
    private val labelColor = Color.parseColor("#333333")

    // Paints
    private val bgPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
    private val smallGridPaint = Paint().apply {
        color = smallGridColor; style = Paint.Style.STROKE; strokeWidth = 0.5f; isAntiAlias = true
    }
    private val largeGridPaint = Paint().apply {
        color = largeGridColor; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
    }
    private val wavePaint = Paint().apply {
        color = waveColor; style = Paint.Style.STROKE; strokeWidth = 2.5f
        isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint().apply {
        color = labelColor; textSize = 32f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }

    // Grid size in pixels
    private var smallBox = 20f  // 1mm equivalent
    private var largeBox = 100f // 5mm equivalent

    fun setEcgData(data: FloatArray, lead: String = "II") {
        ecgData = data
        leadName = lead
        invalidate()
    }

    fun setEcgDataFromList(data: List<Float>, lead: String = "II") {
        ecgData = data.toFloatArray()
        leadName = lead
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Recalculate grid size
        smallBox = w / 50f  // ~50 small boxes across
        largeBox = smallBox * 5f

        // 1. Draw background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Draw small grid
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, smallGridPaint)
            x += smallBox
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, smallGridPaint)
            y += smallBox
        }

        // 3. Draw large grid
        x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, largeGridPaint)
            x += largeBox
        }
        y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, largeGridPaint)
            y += largeBox
        }

        // 4. Draw lead label
        canvas.drawText(leadName, 16f, 40f, labelPaint)

        // 5. Draw ECG waveform
        if (ecgData.isNotEmpty()) {
            val centerY = h / 2f
            val amplitude = h * 0.35f  // Use 35% of height for amplitude
            val step = w / ecgData.size.toFloat()

            val path = Path()
            path.moveTo(0f, centerY - ecgData[0] * amplitude)

            for (i in 1 until ecgData.size) {
                path.lineTo(i * step, centerY - ecgData[i] * amplitude)
            }

            canvas.drawPath(path, wavePaint)
        } else {
            // Draw flat line
            val centerY = h / 2f
            canvas.drawLine(0f, centerY, w, centerY, wavePaint)
        }
    }

    /**
     * Generate a sample Normal Sinus Rhythm ECG waveform
     */
    companion object {
        fun generateNormalSinus(heartRate: Int = 75, samples: Int = 500): FloatArray {
            val data = FloatArray(samples)
            val samplesPerBeat = (samples * 60f / heartRate / 4f).toInt().coerceAtLeast(50)

            var i = 0
            while (i < samples) {
                // P wave (small upward)
                val pLen = (samplesPerBeat * 0.12f).toInt()
                for (j in 0 until pLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / pLen
                        data[i + j] = 0.15f * kotlin.math.sin(Math.PI.toFloat() * t)
                    }
                }
                i += pLen

                // PR segment (flat)
                val prLen = (samplesPerBeat * 0.06f).toInt()
                i += prLen

                // QRS complex
                val qrsLen = (samplesPerBeat * 0.08f).toInt()
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        data[i + j] = when {
                            t < 0.15f -> -0.1f * (t / 0.15f)          // Q
                            t < 0.5f -> -0.1f + 1.2f * ((t - 0.15f) / 0.35f)  // R up
                            t < 0.7f -> 1.1f - 1.5f * ((t - 0.5f) / 0.2f)     // R down to S
                            else -> -0.4f + 0.4f * ((t - 0.7f) / 0.3f)         // S return
                        }
                    }
                }
                i += qrsLen

                // ST segment
                val stLen = (samplesPerBeat * 0.12f).toInt()
                i += stLen

                // T wave
                val tLen = (samplesPerBeat * 0.18f).toInt()
                for (j in 0 until tLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / tLen
                        data[i + j] = 0.25f * kotlin.math.sin(Math.PI.toFloat() * t)
                    }
                }
                i += tLen

                // TP segment (rest)
                val tpLen = (samplesPerBeat * 0.44f).toInt()
                i += tpLen
            }

            return data
        }
    }
}
