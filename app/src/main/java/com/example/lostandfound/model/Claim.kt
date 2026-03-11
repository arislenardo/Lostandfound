package com.example.lostandfound.model

import java.util.Date

data class Claim(
    val id: String = "",
    val itemId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val proofDescription: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val reviewedBy: String = "",     // Admin userId who reviewed
    val reviewerEmail: String = "",  // Admin email for display
    val itemName: String = "",       // Added for notifications and admin view
    val imageUrl: String = "",       // Proof photo for the claim
    val timestamp: Date = Date()
)
