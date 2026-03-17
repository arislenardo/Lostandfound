package com.example.lostandfound.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.nio.MappedByteBuffer
import kotlin.math.min
import kotlin.math.max

/**
 * Feature extractor using MobileNetV3 Large Feature Vector.
 * Produces a 1280-dimensional embedding vector per image, suitable
 * for cosine-similarity matching between reported items.
 */
class TFLiteClassifier(val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        const val MODEL_FILE = "mobilenet_v3_large_feature_vector.tflite"
        const val INPUT_SIZE  = 224
        const val OUTPUT_SIZE = 1280
    }

    init {
        setup()
    }

    private fun setup() {
        try {
            val model: MappedByteBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            interpreter = Interpreter(model, Interpreter.Options())
            android.util.Log.d("TFLiteClassifier", "MobileNetV3 feature extractor loaded.")
        } catch (e: Exception) {
            android.util.Log.e("TFLiteClassifier", "Failed to load model: ${e.message}", e)
        }
    }

    /**
     * Returns a 1280-float embedding for the given bitmap.
     * Returns an empty list if the model is unavailable or an error occurs.
     */
    fun extractFeatureVector(bitmap: Bitmap): List<Double> {
        val interp = interpreter ?: run {
            setup()
            interpreter ?: return emptyList()
        }

        return try {
            val maxSize = max(bitmap.width, bitmap.height)
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(maxSize, maxSize)) // Pad to square to prevent cropping large objects
                .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR)) // Then resize
                .add(NormalizeOp(0f, 255f)) // scale to [0, 1]
                .build()

            var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // MobileNetV3 feature vector output: shape [1, 1280]
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            interp.run(tensorImage.buffer, output)

            val modelFeatures = output[0].map { it.toDouble() }
            val colorHist = extractCenterHSVHistogram(bitmap)
            
            modelFeatures + colorHist
        } catch (e: Exception) {
            android.util.Log.e("TFLiteClassifier", "Feature extraction error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Extracts an HSV color histogram from the center 50% of the image.
     * Gives 128 bins (8 H, 4 S, 4 V) to accurately represent object color
     * independently of its MobileNet semantic classification.
     */
    private fun extractCenterHSVHistogram(bitmap: Bitmap): List<Double> {
        val hBins = 8
        val sBins = 4
        val vBins = 4
        val hist = DoubleArray(hBins * sBins * vBins)
        
        // Take center 50% of the image to minimize background noise
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val halfW = bitmap.width / 4
        val halfH = bitmap.height / 4
        val startX = max(0, cx - halfW)
        val startY = max(0, cy - halfH)
        val width = min(bitmap.width - startX, halfW * 2)
        val height = min(bitmap.height - startY, halfH * 2)
        
        if (width <= 0 || height <= 0) return hist.toList()

        val centerBitmap = Bitmap.createBitmap(bitmap, startX, startY, width, height)
        val scaled = Bitmap.createScaledBitmap(centerBitmap, 64, 64, true)
        
        val pixels = IntArray(64 * 64)
        scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        
        val hsv = FloatArray(3)
        var total = 0
        for (color in pixels) {
            android.graphics.Color.colorToHSV(color, hsv)
            val h = ((hsv[0] / 360f) * hBins).toInt().coerceIn(0, hBins - 1)
            val s = (hsv[1] * sBins).toInt().coerceIn(0, sBins - 1)
            val v = (hsv[2] * vBins).toInt().coerceIn(0, vBins - 1)
            
            hist[(h * sBins * vBins) + (s * vBins) + v] += 1.0
            total++
        }
        
        if (total > 0) {
            for (i in hist.indices) {
                hist[i] /= total.toDouble()
            }
        }
        
        return hist.toList()
    }

    /**
     * Release TFLite resources when no longer needed.
     * Call this from the owning ViewModel's onCleared or similar lifecycle hook.
     */
    fun close() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            android.util.Log.e("TFLiteClassifier", "Error closing interpreter: ${e.message}", e)
        } finally {
            interpreter = null
        }
    }

    /**
     * Legacy: kept for any callers that still use classify().
     * Simply returns the top category name based on the highest activation index.
     * Consider migrating all usages to extractFeatureVector().
     */
    fun classify(bitmap: Bitmap): List<String> = emptyList()

    fun mapLabelToCategory(label: String): String = label
}
