package com.example.lostandfound.utils

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

suspend fun uploadImageToStorage(imageUri: Uri): String {
    val storageRef = FirebaseStorage.getInstance().reference
    val filename = "images/${UUID.randomUUID()}.jpg"
    val imageRef = storageRef.child(filename)
    
    // Upload the file to Firebase Storage
    imageRef.putFile(imageUri).await()
    // Retrieve the download URL
    return imageRef.downloadUrl.await().toString()
}
