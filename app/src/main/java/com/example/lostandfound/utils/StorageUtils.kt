package com.example.lostandfound.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

// Maximum dimension (width or height) for uploaded images, in pixels.
private const val MAX_IMAGE_DIMENSION = 1024
// JPEG compression quality (0–100). 80 gives good visual fidelity at ~70–90% smaller file sizes.
private const val JPEG_QUALITY = 80

/**
 * Compresses and resizes a bitmap from the given URI so that neither its width nor height
 * exceeds [MAX_IMAGE_DIMENSION], while preserving the original aspect ratio.
 * The result is compressed to JPEG at [JPEG_QUALITY] quality.
 *
 * This prevents uploading raw multi-megapixel camera photos to Firebase Storage, which would
 * waste significant bandwidth and storage costs.
 *
 * @param context Android Context needed for [ContentResolver] access.
 * @param imageUri The local URI of the source image (from camera or gallery).
 * @return A [ByteArray] containing the compressed JPEG data, ready for upload.
 */
suspend fun compressAndResizeImage(context: Context, imageUri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        // Step 1: Decode just the dimensions without loading the full bitmap into memory
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val originalWidth  = options.outWidth
        val originalHeight = options.outHeight

        // Step 2: Calculate a power-of-2 sample size to efficiently subsample large images
        var sampleSize = 1
        var width  = originalWidth
        var height = originalHeight
        while (width / 2 >= MAX_IMAGE_DIMENSION || height / 2 >= MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
            width  /= 2
            height /= 2
        }

        // Step 3: Decode the bitmap at the reduced sample size
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampledBitmap: Bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not open input stream for URI: $imageUri")

        // Step 4: Fine-scale to exact max dimension if still oversized after sampling
        val finalBitmap: Bitmap = if (sampledBitmap.width > MAX_IMAGE_DIMENSION || sampledBitmap.height > MAX_IMAGE_DIMENSION) {
            val scaleFactor = MAX_IMAGE_DIMENSION.toFloat() / maxOf(sampledBitmap.width, sampledBitmap.height)
            val targetWidth  = (sampledBitmap.width  * scaleFactor).toInt()
            val targetHeight = (sampledBitmap.height * scaleFactor).toInt()
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true)
            if (scaled !== sampledBitmap) sampledBitmap.recycle()
            scaled
        } else {
            sampledBitmap
        }

        // Step 5: Compress to JPEG bytes and recycle the bitmap
        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        finalBitmap.recycle()
        outputStream.toByteArray()
    }

/**
 * Asynchronously uploads an image to Firebase Storage after compressing and resizing it.
 *
 * The image is stored at path `images/{itemType}/{userId}/{uuid}.jpg`.
 * Uploader metadata (`userId`, `userEmail`) is attached to the file for admin visibility.
 *
 * @param context Android Context required for image compression.
 * @param imageUri The local URI of the image to upload.
 * @param userId The ID of the uploading user.
 * @param userEmail The email of the uploading user.
 * @param itemType Category path segment (e.g. "lost", "found", "claims", "chat").
 * @return The public download URL of the uploaded image as a [String].
 */
suspend fun uploadImageToStorage(
    context: Context,
    imageUri: Uri,
    userId: String = "anonymous",
    userEmail: String = "",
    itemType: String = "uncategorized"
): String {
    val storageRef = FirebaseStorage.getInstance().reference
    val filename = "images/$itemType/$userId/${UUID.randomUUID()}.jpg"
    val imageRef = storageRef.child(filename)

    // Attach uploader info as metadata — visible in Firebase Storage console
    val metadata = StorageMetadata.Builder()
        .setContentType("image/jpeg")
        .setCustomMetadata("userId", userId)
        .setCustomMetadata("userEmail", userEmail)
        .build()

    // Compress the image first, then upload raw bytes (not the file URI)
    val compressedBytes = compressAndResizeImage(context, imageUri)
    imageRef.putBytes(compressedBytes, metadata).await()
    return imageRef.downloadUrl.await().toString()
}
