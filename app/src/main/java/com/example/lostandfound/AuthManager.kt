package com.example.lostandfound

import com.google.firebase.auth.FirebaseAuth

object AuthManager {

    // IMPORTANT: Replace this with the actual Firebase UIDs of your admin users.
    // You can find a user's UID in the Firebase Authentication console.
    private val adminUids = setOf(
        "wG5k7snbTAg3AJeBrrgaFfUQVo22",
        // You can add more admin UIDs here
    )

    fun isCurrentUserAdmin(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.uid in adminUids
    }

    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}
