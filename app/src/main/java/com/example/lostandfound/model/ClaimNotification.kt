package com.example.lostandfound.model

import java.util.Date

/**
 * Data class representing a notification sent to a user when the status of their claim changes (e.g., APPROVED or REJECTED).
 */
data class ClaimNotification(
    val id: String = "",
    val claimId: String = "",
    val userId: String = "",       // Recipient (claimant)
    val itemId: String = "",
    val itemName: String = "",     // For display
    val status: String = "",        // APPROVED or REJECTED
    @get:com.google.firebase.firestore.PropertyName("isRead")
    @set:com.google.firebase.firestore.PropertyName("isRead")
    var isRead: Boolean = false,
    val timestamp: Date = Date()
)
