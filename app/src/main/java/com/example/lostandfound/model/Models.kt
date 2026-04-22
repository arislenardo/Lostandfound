package com.example.lostandfound.model

import java.util.Date

// --- UPDATED DATA MODELS ---
/**
 * Data model for an item that has been found by a user or the station.
 */
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
    val dateFound: Date = Date(),
    val dateFoundText: String = "",
    val imageUrl: String = "",
    val imageVector: List<Double> = emptyList(), // MobileNetV3 embedding for visual matching
    val status: String = ItemStatus.FOUND,
    val claimedLostItemId: String = "",
    val createdAt: Date? = null
)

/**
 * Data model for an item that has been reported as lost by a user.
 */
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
    val dateLost: Date = Date(),
    val imageUrl: String = "",
    val imageVector: List<Double> = emptyList(), // MobileNetV3 embedding for visual matching
    val status: String = ItemStatus.LOST,
    val claimedFoundItemId: String = "",
    val createdAt: Date? = null
)

// --- PROFILE MODELS ---
/**
 * Data model representing a user's profile and role within the application.
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val role: String = "Resident" // Admin or Resident
)

// --- AUDIT LOGGING ---
/**
 * Data model for logging administrative actions (e.g., approvals, deletions) for auditing purposes.
 */
data class AdminAction(
    val id: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val actionType: String = "", // e.g., "APPROVED_CLAIM", "REJECTED_CLAIM", "DELETED_LOST_ITEM", "DELETED_FOUND_ITEM"
    val itemTitle: String = "",
    val itemId: String = "",
    val timestamp: Date = Date()
)