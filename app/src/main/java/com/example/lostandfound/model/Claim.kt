package com.example.lostandfound.model

import java.util.Date

/**
 * Represents a claim made by a user on a found item.
 * Contains the claim details, proof of ownership, and tracking information for admin review.
 */
data class Claim(
    val id: String = "",
    val itemId: String = "",
    val lostItemId: String = "",   // Link to user's lost report
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val proofDescription: String = "",
    val status: String = ClaimStatus.PENDING,
    val reviewedBy: String = "",     // Admin userId who reviewed
    val reviewerEmail: String = "",  // Admin email for display
    val itemName: String = "",       // Added for notifications and admin view
    val imageUrl: String = "",       // Proof photo for the claim
    val timestamp: Date = Date()
)
