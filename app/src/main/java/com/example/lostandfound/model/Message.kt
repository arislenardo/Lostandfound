package com.example.lostandfound.model

import java.util.Date

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "", // Added for easier display
    val receiverId: String = "",
    val receiverName: String = "", // Tracks the receiver's name for conversation lists
    val text: String = "",
    val timestamp: Date = Date(),
    val itemId: String = "", // Optional: Link chat to a specific item context
    val imageUrl: String = "", // For sending photos in chat
    val isRead: Boolean = false
)
