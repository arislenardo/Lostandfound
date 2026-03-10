package com.example.lostandfound.utils

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Uploads an image to Firebase Storage under images/{userId}/{uuid}.jpg
 * The userId and userEmail are stored as custom metadata on the file,
 * so you can see who uploaded it directly in the Firebase Storage console.
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
