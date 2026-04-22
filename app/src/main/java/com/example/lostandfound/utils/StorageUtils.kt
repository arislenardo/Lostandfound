package com.example.lostandfound.utils

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Asynchronously uploads an image file to Firebase Storage.
 *
 * The image is stored under the path `images/{itemType}/{userId}/{uuid}.jpg`.
 * User information (`userId` and `userEmail`) is attached to the file as custom metadata,
 * allowing administrators to identify the uploader directly from the Firebase Storage console.
 *
 * @param imageUri The local URI of the image to be uploaded.
 * @param userId The ID of the user uploading the image. Defaults to "anonymous".
 * @param userEmail The email of the user uploading the image.
 * @param itemType The category/type of item being uploaded (e.g., "lost", "found", "chat").
 * @return The public download URL of the uploaded image.
 */
suspend fun uploadImageToStorage(
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

    imageRef.putFile(imageUri, metadata).await()
    return imageRef.downloadUrl.await().toString()
}
