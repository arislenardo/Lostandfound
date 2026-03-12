package com.example.lostandfound.model

/**
 * Centralized status values used across Firestore documents.
 * These remain string-based to keep compatibility with existing data.
 */

object ItemStatus {
    const val FOUND = "Found"      // found_items initial status
    const val LOST = "Lost"        // lost_items initial status (if used)
    const val CLAIMED = "CLAIMED"  // found_items when claimed
    const val RETURNED = "RETURNED"// found_items archived
}

object ClaimStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val DISPUTED = "DISPUTED"
    const val CLAIM_PENDING = "CLAIM_PENDING"
    const val FOUND = "FOUND"
}

object MatchNotificationStatus {
    const val UNREAD = "UNREAD"
    const val READ = "READ"
    const val DISMISSED = "DISMISSED"
}

