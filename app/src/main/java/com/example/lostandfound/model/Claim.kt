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
    val timestamp: Date = Date()
)
