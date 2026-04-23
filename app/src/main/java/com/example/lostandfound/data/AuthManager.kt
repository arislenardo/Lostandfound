package com.example.lostandfound.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manager object responsible for handling authentication-related tasks,
 * including checking admin status and retrieving the current user's UID.
 */
object AuthManager {

    private var adminUids = emptySet<String>()

    /**
     * Refreshes the admin status of the current user by fetching the latest admin list from Firestore.
     * @return true if the current user is an admin, false otherwise.
     */
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
    /**
     * Asynchronously fetches the list of admin UIDs from Firestore and updates the local cache.
  
    fun fetchAdminUids() {
        val db = FirebaseFirestore.getInstance()
        db.collection("admins").get().addOnSuccessListener { result ->
            adminUids = result.documents.map { it.id }.toSet()
        }
    }

    /**
     * Checks if the currently authenticated user is an administrator based on the cached admin list.
     * @return true if the user is an admin, false otherwise.
     */
    fun isCurrentUserAdmin(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.uid in adminUids
    }

    /**
     * Retrieves the unique identifier (UID) of the currently authenticated Firebase user.
     * @return the user's UID or null if no user is signed in.
     */
    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}