package com.roox.ecgreader.service

import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates clean ECG waveforms based on AI-described parameters.
 * Instead of noisy pixel extraction, we synthesize accurate waveforms
 * from the diagnosis/parameters the AI provides.
 */
object EcgWaveformExtractor {

    private const val TAG = "EcgSynth"
    private const val SAMPLES = 1000

    data class EcgParams(
        val heartRate: Int = 75,
        val rhythm: String = "sinus",          // sinus, afib, aflutter, vtach, vfib, svt, bradycardia
        val pWavePresent: Boolean = true,
        val pWaveAmplitude: Float = 0.15f,
        val prInterval: Float = 0.16f,          // seconds (normal 0.12-0.20)
        val qrsWidth: Float = 0.08f,            // seconds (normal <0.12)
        val qrsAmplitude: Float = 1.0f,
        val qWave: Boolean = false,             // pathological Q
        val stElevation: Float = 0f,            // mm (positive = elevation)
        val stDepression: Float = 0f,           // mm (positive = depression)
        val tWaveInverted: Boolean = false,
        val tWaveAmplitude: Float = 0.25f,
        val tallTWave: Boolean = false,         // hyperkalemia
        val lfbb: Boolean = false,              // left bundle branch block
        val rbbb: Boolean = false,              // right bundle branch block
        val deltaWave: Boolean = false,         // WPW
        val longQT: Boolean = false
    )

    /**
     * Parse AI analysis text to extract ECG parameters
     */
    fun parseFromAiText(text: String): EcgParams {
        val lower = text.lowercase()
        var params = EcgParams()

        // Heart rate
        val hrMatch = Regex("""(?:heart rate|hr)[:\s]*(\d+)""").find(lower)
        if (hrMatch != null) {
            params = params.copy(heartRate = hrMatch.groupValues[1].toIntOrNull() ?: 75)
        }

        // Rhythm
        params = params.copy(rhythm = when {
            lower.contains("atrial fibrillation") || lower.contains("a-fib") || lower.contains("afib") -> "afib"
            lower.contains("atrial flutter") || lower.contains("aflutter") -> "aflutter"
            lower.contains("ventricular tachycardia") || lower.contains("v-tach") || lower.contains("vtach") -> "vtach"
            lower.contains("ventricular fibrillation") || lower.contains("v-fib") || lower.contains("vfib") -> "vfib"
            lower.contains("svt") || lower.contains("supraventricular tachycardia") -> "svt"
            lower.contains("bradycardia") || lower.contains("sinus brady") -> "bradycardia"
            lower.contains("heart block") || lower.contains("av block") -> "heartblock"
            else -> "sinus"
        })

        // ST changes
        if (lower.contains("st elevation") || lower.contains("st-elevation") || lower.contains("stemi")) {
            params = params.copy(stElevation = 3f)
        }
        if (lower.contains("st depression")) {
            params = params.copy(stDepression = 2f)
        }

        // T wave
        if (lower.contains("t wave inversion") || lower.contains("inverted t") || lower.contains("t-wave inversion")) {
            params = params.copy(tWaveInverted = true)
        }
        if (lower.contains("tall t") || lower.contains("peaked t") || lower.contains("hyperkalemia")) {
            params = params.copy(tallTWave = true, tWaveAmplitude = 0.6f)
        }

        // Bundle branch blocks
        if (lower.contains("lbbb") || lower.contains("left bundle branch block")) {
            params = params.copy(lfbb = true, qrsWidth = 0.14f)
        }
        if (lower.contains("rbbb") || lower.contains("right bundle branch block")) {
            params = params.copy(rbbb = true, qrsWidth = 0.14f)
        }

        // QRS
        if (lower.contains("wide qrs") || lower.contains("qrs.*>.*12")) {
            params = params.copy(qrsWidth = 0.14f)
        }

        // P wave
        if (lower.contains("no p wave") || lower.contains("absent p") || params.rhythm == "afib") {
            params = params.copy(pWavePresent = false)
        }

        // Pathological Q
        if (lower.contains("pathological q") || lower.contains("q wave")) {
            params = params.copy(qWave = true)
        }

        // PR interval
        val prMatch = Regex("""pr.*?(\d{3}).*?ms""").find(lower)
        if (prMatch != null) {
            params = params.copy(prInterval = (prMatch.groupValues[1].toIntOrNull() ?: 160) / 1000f)
        }
        if (lower.contains("prolonged pr") || lower.contains("first.degree")) {
            params = params.copy(prInterval = 0.24f)
        }

        // Delta wave (WPW)
        if (lower.contains("delta wave") || lower.contains("wpw") || lower.contains("wolff")) {
            params = params.copy(deltaWave = true, prInterval = 0.10f)
        }

        // Long QT
        if (lower.contains("long qt") || lower.contains("prolonged qt")) {
            params = params.copy(longQT = true)
        }

        Log.d(TAG, "Parsed params: $params")
        return params
    }

    /**
     * Generate an ECG waveform from parameters
     */
    fun generateWaveform(params: EcgParams, samples: Int = SAMPLES): FloatArray {
        return when (params.rhythm) {
            "afib" -> generateAfib(params, samples)
            "aflutter" -> generateAFlutter(params, samples)
            "vtach" -> generateVTach(params, samples)
            "vfib" -> generateVFib(params, samples)
            else -> generateSinusRhythm(params, samples)
        }
    }

    private fun generateSinusRhythm(params: EcgParams, samples: Int): FloatArray {
        val data = FloatArray(samples)
        val beatsPerSample = params.heartRate / 60f / (samples / 4f)
        val samplesPerBeat = (samples / (params.heartRate / 60f * 4f)).toInt().coerceAtLeast(40)

        var i = 0
        while (i < samples) {
            // P wave
            if (params.pWavePresent) {
                val pLen = (samplesPerBeat * 0.11f).toInt()
                for (j in 0 until pLen) {
                    if (i + j < samples) {
                        data[i + j] = params.pWaveAmplitude * sin(PI.toFloat() * j / pLen)
                    }
                }
                i += pLen
            }

            // PR segment
            val prSamples = (samplesPerBeat * params.prInterval / 0.8f).toInt().coerceAtLeast(3)
            i += prSamples

            // Delta wave (WPW)
            if (params.deltaWave) {
                val deltaLen = (samplesPerBeat * 0.04f).toInt()
                for (j in 0 until deltaLen) {
                    if (i + j < samples) {
                        data[i + j] = 0.3f * j.toFloat() / deltaLen
                    }
                }
                i += deltaLen
            }

            // QRS complex
            val qrsLen = (samplesPerBeat * params.qrsWidth / 0.8f).toInt().coerceAtLeast(6)

            if (params.lfbb) {
                // LBBB: wide, notched QRS
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        data[i + j] = params.qrsAmplitude * when {
                            t < 0.15f -> -0.2f * (t / 0.15f)
                            t < 0.35f -> -0.2f + 0.8f * ((t - 0.15f) / 0.2f)
                            t < 0.45f -> 0.6f - 0.3f * ((t - 0.35f) / 0.1f)  // notch
                            t < 0.65f -> 0.3f + 0.7f * ((t - 0.45f) / 0.2f)  // second peak
                            t < 0.85f -> 1.0f - 1.3f * ((t - 0.65f) / 0.2f)
                            else -> -0.3f + 0.3f * ((t - 0.85f) / 0.15f)
                        }
                    }
                }
            } else if (params.rbbb) {
                // RBBB: RSR' pattern
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        data[i + j] = params.qrsAmplitude * when {
                            t < 0.2f -> 0.8f * (t / 0.2f)               // R
                            t < 0.4f -> 0.8f - 1.2f * ((t - 0.2f) / 0.2f) // S
                            t < 0.6f -> -0.4f + 0.4f * ((t - 0.4f) / 0.2f) // back up
                            t < 0.8f -> 0.6f * ((t - 0.6f) / 0.2f)        // R'
                            else -> 0.6f - 0.6f * ((t - 0.8f) / 0.2f)
                        }
                    }
                }
            } else {
                // Normal QRS
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        val qDepth = if (params.qWave) -0.3f else -0.1f
                        data[i + j] = params.qrsAmplitude * when {
                            t < 0.15f -> qDepth * (t / 0.15f)
                            t < 0.45f -> qDepth + (1.1f - qDepth) * ((t - 0.15f) / 0.3f)
                            t < 0.7f -> 1.1f - 1.5f * ((t - 0.45f) / 0.25f)
                            else -> -0.4f + 0.4f * ((t - 0.7f) / 0.3f)
                        }
                    }
                }
            }
            i += qrsLen

            // ST segment (with elevation/depression)
            val stLen = (samplesPerBeat * 0.12f).toInt()
            val stShift = (params.stElevation - params.stDepression) * 0.05f
            for (j in 0 until stLen) {
                if (i + j < samples) {
                    data[i + j] = stShift
                }
            }
            i += stLen

            // T wave
            val tAmp = if (params.tWaveInverted) -params.tWaveAmplitude else params.tWaveAmplitude
            val tLen = if (params.longQT) (samplesPerBeat * 0.28f).toInt() else (samplesPerBeat * 0.18f).toInt()
            for (j in 0 until tLen) {
                if (i + j < samples) {
                    data[i + j] = stShift + tAmp * sin(PI.toFloat() * j / tLen)
                }
            }
            i += tLen

            // TP segment (rest)
            val tpLen = (samplesPerBeat * 0.40f).toInt()
            i += tpLen
        }

        return data
    }

    private fun generateAfib(params: EcgParams, samples: Int): FloatArray {
        val data = FloatArray(samples)
        var i = 0
        val rand = java.util.Random(42)

        while (i < samples) {
            // Fibrillatory baseline
            val baseLen = (15 + rand.nextInt(10))
            for (j in 0 until baseLen) {
                if (i + j < samples) {
                    data[i + j] = 0.05f * sin(rand.nextFloat() * PI.toFloat() * 2)
                }
            }
            i += baseLen

            // Irregular QRS (varying R-R intervals)
            val rrVariation = 20 + rand.nextInt(40)
            if (i + rrVariation < samples) {
                i += rrVariation

                // QRS
                val qrsLen = 8
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        data[i + j] = params.qrsAmplitude * when {
                            t < 0.15f -> -0.1f * (t / 0.15f)
                            t < 0.45f -> -0.1f + 1.1f * ((t - 0.15f) / 0.3f)
                            t < 0.7f -> 1.0f - 1.4f * ((t - 0.45f) / 0.25f)
                            else -> -0.4f + 0.4f * ((t - 0.7f) / 0.3f)
                        }
                    }
                }
                i += qrsLen

                // ST + T wave
                val stLen = 6
                val stShift = (params.stElevation - params.stDepression) * 0.05f
                for (j in 0 until stLen) { if (i + j < samples) data[i + j] = stShift }
                i += stLen

                val tAmp = if (params.tWaveInverted) -0.2f else 0.2f
                val tLen = 12
                for (j in 0 until tLen) {
                    if (i + j < samples) data[i + j] = stShift + tAmp * sin(PI.toFloat() * j / tLen)
                }
                i += tLen
            }
        }

        return data
    }

    private fun generateAFlutter(params: EcgParams, samples: Int): FloatArray {
        val data = FloatArray(samples)
        val flutterRate = 300 // typical flutter rate
        val samplesPerFlutter = (samples / (flutterRate / 60f * 4f)).toInt().coerceAtLeast(8)

        var i = 0
        var flutterCount = 0

        while (i < samples) {
            // Sawtooth F wave
            for (j in 0 until samplesPerFlutter) {
                if (i + j < samples) {
                    val t = j.toFloat() / samplesPerFlutter
                    data[i + j] = 0.2f * (1f - 2f * t) // sawtooth pattern
                }
            }
            i += samplesPerFlutter
            flutterCount++

            // QRS every 2-4 flutter waves (2:1, 3:1, 4:1 block)
            if (flutterCount % 3 == 0) {
                val qrsLen = 8
                for (j in 0 until qrsLen) {
                    if (i + j < samples) {
                        val t = j.toFloat() / qrsLen
                        data[i + j] = params.qrsAmplitude * when {
                            t < 0.15f -> -0.1f * (t / 0.15f)
                            t < 0.45f -> -0.1f + 1.1f * ((t - 0.15f) / 0.3f)
                            t < 0.7f -> 1.0f - 1.4f * ((t - 0.45f) / 0.25f)
                            else -> -0.4f + 0.4f * ((t - 0.7f) / 0.3f)
                        }
                    }
                }
                i += qrsLen
            }
        }

        return data
    }

    private fun generateVTach(params: EcgParams, samples: Int): FloatArray {
        val data = FloatArray(samples)
        val rate = params.heartRate.coerceAtLeast(150)
        val samplesPerBeat = (samples / (rate / 60f * 4f)).toInt().coerceAtLeast(15)
        var i = 0

        while (i < samples) {
            // Wide, bizarre QRS
            for (j in 0 until samplesPerBeat) {
                if (i + j < samples) {
                    val t = j.toFloat() / samplesPerBeat
                    data[i + j] = 0.9f * when {
                        t < 0.1f -> 0.3f * (t / 0.1f)
                        t < 0.35f -> 0.3f + 0.7f * ((t - 0.1f) / 0.25f)
                        t < 0.55f -> 1.0f - 2.0f * ((t - 0.35f) / 0.2f)
                        t < 0.75f -> -1.0f + 1.3f * ((t - 0.55f) / 0.2f)
                        else -> 0.3f - 0.3f * ((t - 0.75f) / 0.25f)
                    }
                }
            }
            i += samplesPerBeat
        }

        return data
    }

    private fun generateVFib(params: EcgParams, samples: Int): FloatArray {
        val data = FloatArray(samples)
        val rand = java.util.Random(42)
        for (i in 0 until samples) {
            val freq1 = sin(i * 0.3f + rand.nextFloat() * 2f)
            val freq2 = sin(i * 0.15f + rand.nextFloat())
            data[i] = (0.3f + 0.4f * rand.nextFloat()) * (freq1 * 0.6f + freq2 * 0.4f).toFloat()
        }
        return data
    }
}
