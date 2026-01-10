package com.example.lostandfound.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object AuthManager {

    private var adminUids = emptySet<String>()

    suspend fun fetchAdminUids() {
        try {
            val result = FirebaseFirestore.getInstance().collection("admins").get().await()
            adminUids = result.documents.map { it.id }.toSet()
        } catch (e: Exception) {
            // Handle error, e.g., log it
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