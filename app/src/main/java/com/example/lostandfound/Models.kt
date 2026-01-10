package com.example.lostandfound

import java.util.Date

// --- DATA MODELS ---
data class FoundItem(
    val id: String = "",
    val userId: String = "", // User who reported it
    val email: String = "", // Contact email
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String = "",
    val dateFound: Date = Date(),
    val dateFoundText: String = "",
    val status: String = "Found" // Found, Claimed
)

data class LostItem(
    val id: String = "",
    val userId: String = "", // User who reported it
    val email: String = "", // Contact email
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String = "",
    val dateLost: String = "",
    val status: String = "Lost" // Lost, Found
)
