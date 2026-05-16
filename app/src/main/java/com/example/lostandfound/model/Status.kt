package com.example.lostandfound.model

/**
 * Centralized status values used across Firestore documents.
 * These remain string-based to keep compatibility with existing data.
 */

/**
 * Constants representing the status of a found_items document.
 * All values are ALL_CAPS to match the ClaimStatus convention.
 */
object ItemStatus {
    const val FOUND = "FOUND"              // found_items initial status (available to claim)
    const val LOST = "LOST"                // lost_items initial status
    const val CLAIMED = "CLAIMED"          // found_items when a claim is approved
    const val CLAIM_PENDING = "CLAIM_PENDING" // found_items when a claim is pending review
    const val RETURNED = "RETURNED"        // found_items when physically returned to owner
}

/**
 * Constants representing the status of a claim made by a user.
 */
object ClaimStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val DISPUTED = "DISPUTED"
    const val CLAIM_PENDING = "CLAIM_PENDING"
    const val FOUND = "FOUND"
    const val RETURNED = "RETURNED"
}

/**
 * Constants representing the status of a match notification (e.g., Unread, Read, Dismissed).
 */
object MatchNotificationStatus {
    const val UNREAD = "UNREAD"
    const val READ = "READ"
    const val DISMISSED = "DISMISSED"
}

