package com.example.lostandfound.model

import java.util.Date

// --- UPDATED DATA MODELS ---
data class FoundItem(
    val id: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String = "",
    val dateFound: Date = Date(), // Already correct
    val dateFoundText: String = "",
    val imageUrl: String = "",    // Added for photo support
    val status: String = "Found",
    val createdAt: Date? = null
)

data class LostItem(
    val id: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String = "",
    val dateLost: Date = Date(),  // CHANGED: String -> Date
    val imageUrl: String = "",    // Added for photo support
    val status: String = "Lost",
    val createdAt: Date? = null
)

// --- PROFILE MODELS ---
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val role: String = "Resident" // Admin or Resident
)

// --- AUDIT LOGGING ---
data class AdminAction(
    val id: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val actionType: String = "", // e.g., "APPROVED_CLAIM", "REJECTED_CLAIM", "DELETED_LOST_ITEM", "DELETED_FOUND_ITEM"
    val itemTitle: String = "",
    val itemId: String = "",
    val timestamp: Date = Date()
)