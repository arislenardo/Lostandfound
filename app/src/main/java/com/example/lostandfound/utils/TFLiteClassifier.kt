package com.example.lostandfound.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.TensorLabel
import java.nio.MappedByteBuffer

class TFLiteClassifier(val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    init {
        setupClassifier()
    }

    private fun setupClassifier() {
        try {
            // Load the model
            val model: MappedByteBuffer = FileUtil.loadMappedFile(context, "model_unquant.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(model, options)

            // Load labels
            labels = FileUtil.loadLabels(context, "labels.txt")
            
            android.util.Log.d("TFLiteClassifier", "Model loaded. Labels size: ${labels.size}")

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TFLiteClassifier", "Error initializing classifier", e)
            // Toast removed here to avoid context leaks or background thread issues, 
            // relying on classify logging
        }
    }

    fun classify(bitmap: Bitmap): List<String> {
        if (interpreter == null) {
            setupClassifier()
            if (interpreter == null) {
                return emptyList()
            }
        }

        try {
            // 1. Preprocess the image
            // Teachable Machine standard: 224x224, float32, normalized [0,1]
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f)) // Normalize 0-255 to 0-1
                .build()

            var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // 2. Output buffer
            // Shape: [1, num_classes]
            val outputBuffer = org.tensorflow.lite.support.tensorbuffer.TensorBuffer.createFixedSize(
                intArrayOf(1, labels.size),
                org.tensorflow.lite.DataType.FLOAT32
            )

            // 3. Run inference
            interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())

            // 4. Map output to labels
            val labeledProbability = TensorLabel(labels, outputBuffer).mapWithFloatValue
            
            // 5. Filter and sort (Threshold 0.15)
            return labeledProbability.filter { it.value > 0.15f }
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TFLiteClassifier", "Classification error", e)
            return emptyList()
        }
    }

    fun mapLabelToCategory(label: String): String {
        // Strip leading numbers (e.g. "0 Phone" -> "Phone")
        // Replace underscores with " / "
        val cleaned = label.replaceFirst(Regex("^\\d+\\s+"), "").replace("_", " / ")
        return cleaned
    }
}
