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
        val optionsBuilder = ValidatedImageClassifierOptions.Builder()
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
        return when (label.lowercase()) {
            "laptop", "phone_tablet", "headphones_earbuds", "charger_cable" -> "Electronics"
            "clothing", "hat" -> "Clothing"
            "watch", "glasses_sunglasses", "backpack_bag", "wallet", "umbrella", "water bottle" -> "Accessories"
            "folder_envelope", "book_notebook" -> "Documents"
            "key" -> "Keys"
            else -> "Others"
        }
    }
}

// Wrapper to avoid import issues if different versions
typealias ValidatedImageClassifierOptions = org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
