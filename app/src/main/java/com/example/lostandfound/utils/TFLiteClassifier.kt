package com.example.lostandfound.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions

class TFLiteClassifier(val context: Context) {

    private var imageClassifier: ImageClassifier? = null

    init {
        setupClassifier()
    }

    private fun setupClassifier() {
        val optionsBuilder = ValidatedImageClassifierOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(3)

        val baseOptionsBuilder = BaseOptions.builder()
        // baseOptionsBuilder.useGpu() // Uncomment if using GPU

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            // Using the model name provided by the user
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                "model_unquant.tflite",
                optionsBuilder.build()
            )
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun classify(bitmap: Bitmap): List<String> {
        if (imageClassifier == null) {
            setupClassifier()
        }

        val image = TensorImage.fromBitmap(bitmap)
        val results: List<Classifications> = imageClassifier?.classify(image) ?: emptyList()
        
        return results.flatMap { it.categories }
            .map { it.label }
    }
    
    // Helper to map standardized model labels to our app categories
    fun mapLabelToCategory(label: String): String {
        // The model returns labels like "0 Phone_Tablet" or just "Phone_Tablet" depending on metadata.
        // We will strip the leading number if present and replace underscores with spaces for better UI.
        val cleaned = label.replaceFirst(Regex("^\\d+\\s+"), "").replace("_", " / ")
        return cleaned
    }
}

// Wrapper to avoid import issues if different versions
typealias ValidatedImageClassifierOptions = org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
