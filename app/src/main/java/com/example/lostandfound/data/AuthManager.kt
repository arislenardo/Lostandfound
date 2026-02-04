package com.example.lostandfound.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object AuthManager {

    private var adminUids = emptySet<String>()

    suspend fun refreshAdminStatus(): Boolean {
        return try {
            val result = FirebaseFirestore.getInstance().collection("admins").get().await()
            adminUids = result.documents.map { it.id }.toSet()
            isCurrentUserAdmin()
        } catch (e: Exception) {
            // Log error
            false
        }
    }

    // Keep for backward compatibility or simple synchronous checks after data is loaded
    fun fetchAdminUids() {
        // Fire and forget (legacy, try to avoid using this)
        val db = FirebaseFirestore.getInstance()
        db.collection("admins").get().addOnSuccessListener { result ->
            adminUids = result.documents.map { it.id }.toSet()
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.uid in adminUids
    }

    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}