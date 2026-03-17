package com.roox.ecgreader.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log

/**
 * Extracts ECG waveform data from an image by tracing the darkest line.
 * Works with standard ECG printouts (dark trace on pink/white graph paper).
 */
class EcgWaveformExtractor {

    companion object {
        private const val TAG = "EcgExtractor"

        /**
         * Extract waveform from ECG image.
         * Returns a list of normalized Y values (-1.0 to 1.0) for each column of the image.
         */
        fun extractFromUri(context: Context, uri: Uri, targetSamples: Int = 800): List<Float> {
            return try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) return emptyList()

                extractFromBitmap(bitmap, targetSamples)
            } catch (e: Exception) {
                Log.e(TAG, "Extract error", e)
                emptyList()
            }
        }

        fun extractFromBitmap(bitmap: Bitmap, targetSamples: Int = 800): List<Float> {
            // Resize for performance
            val maxWidth = 1200
            val scaled = if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val w = scaled.width
            val h = scaled.height

            // For each column, find the Y position of the darkest pixel (the ECG trace)
            val rawPoints = mutableListOf<Float>()

            // Define the ECG trace region (typically middle 70% of image height)
            val topMargin = (h * 0.1f).toInt()
            val bottomMargin = (h * 0.9f).toInt()

            for (x in 0 until w) {
                var darkestY = h / 2
                var darkestValue = 255 * 3 // max brightness

                for (y in topMargin until bottomMargin) {
                    val pixel = scaled.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // Look for dark pixels (the ECG trace line)
                    // ECG trace is usually black/dark blue/dark green
                    val brightness = r + g + b

                    // Also check if it's a "trace-like" color (not pink grid)
                    // Pink grid: high R, low G, low B or high R, high G, high B
                    val isPinkGrid = (r > 150 && g < r - 30 && b < r - 30)
                    val isRedGrid = (r > 180 && g < 100 && b < 100)

                    if (!isPinkGrid && !isRedGrid && brightness < darkestValue) {
                        darkestValue = brightness
                        darkestY = y
                    }
                }

                // Only accept if the darkest pixel is actually dark enough
                if (darkestValue < 300) { // threshold for "dark enough"
                    // Normalize Y: 0=top, h=bottom → we want top=positive
                    val normalized = 1.0f - 2.0f * (darkestY - topMargin).toFloat() / (bottomMargin - topMargin)
                    rawPoints.add(normalized)
                } else {
                    // No dark pixel found, use baseline (0)
                    rawPoints.add(0f)
                }
            }

            // Smooth the data to reduce noise
            val smoothed = smoothData(rawPoints, windowSize = 3)

            // Resample to target samples
            return resample(smoothed, targetSamples)
        }

        private fun smoothData(data: List<Float>, windowSize: Int): List<Float> {
            if (data.size < windowSize) return data
            val half = windowSize / 2
            return List(data.size) { i ->
                val start = maxOf(0, i - half)
                val end = minOf(data.size - 1, i + half)
                var sum = 0f
                for (j in start..end) sum += data[j]
                sum / (end - start + 1)
            }
        }

        private fun resample(data: List<Float>, targetSize: Int): List<Float> {
            if (data.isEmpty()) return List(targetSize) { 0f }
            if (data.size == targetSize) return data

            return List(targetSize) { i ->
                val srcIdx = i.toFloat() * (data.size - 1) / (targetSize - 1)
                val low = srcIdx.toInt().coerceIn(0, data.size - 1)
                val high = (low + 1).coerceIn(0, data.size - 1)
                val frac = srcIdx - low
                data[low] * (1 - frac) + data[high] * frac
            }
        }
    }
}
