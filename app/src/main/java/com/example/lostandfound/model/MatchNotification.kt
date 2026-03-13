package com.example.lostandfound.model

import java.util.Date

data class MatchNotification(
    val id: String = "",
    val lostItemId: String = "",
    val foundItemId: String = "",
    val lostItemOwnerId: String = "",
    val lostItemName: String = "",      // For display without extra fetch
    val foundItemName: String = "",     // For display without extra fetch
    val foundItemImageUrl: String = "", // For display without extra fetch
    val foundItemLocation: String = "", // For display without extra fetch
    val matchScore: Double = 0.0,
    val status: String = MatchNotificationStatus.UNREAD,
    val createdAt: Date = Date()
)
