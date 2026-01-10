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
    val status: String = "Found"
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
    val status: String = "Lost"
)