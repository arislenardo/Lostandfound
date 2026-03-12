package com.example.lostandfound.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.nio.MappedByteBuffer

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
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f)) // scale to [0, 1]
                .build()

            var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // MobileNetV3 feature vector output: shape [1, 1280]
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            interp.run(tensorImage.buffer, output)

            output[0].map { it.toDouble() }
        } catch (e: Exception) {
            android.util.Log.e("TFLiteClassifier", "Feature extraction error: ${e.message}", e)
            emptyList()
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
