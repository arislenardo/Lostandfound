package com.example.lostandfound.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lostandfound.data.AuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

data class HomeUiState(
    val isAdmin: Boolean = false,
    val firstName: String = "User",
    val notifCount: Int = 0,
    val unreadMessages: Int = 0,
    val unreadMatches: Int = 0,
    val pendingClaims: Int = 0,
    val unreadClaimUpdates: Int = 0,
)

class HomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var uiState = androidx.compose.runtime.mutableStateOf(HomeUiState())
        private set

    private var msgListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var claimListener: ListenerRegistration? = null
    private var claimNotifListener: ListenerRegistration? = null

    init {
        refreshAdminAndProfile()
        attachRealtimeListeners()
    }

    private fun refreshAdminAndProfile() {
        val currentUser = auth.currentUser
        var isAdmin = AuthManager.isCurrentUserAdmin()
        var displayName = currentUser?.displayName
            ?: currentUser?.email?.substringBefore("@")
            ?: "User"

        uiState.value = uiState.value.copy(
            isAdmin = isAdmin,
            firstName = displayName.split(" ").firstOrNull() ?: displayName
        )

        viewModelScope.launch {
            val admin = AuthManager.refreshAdminStatus()
            isAdmin = admin
            uiState.value = uiState.value.copy(isAdmin = isAdmin)

            if (currentUser != null) {
                db.collection("users").document(currentUser.uid).get()
                    .addOnSuccessListener { doc ->
                        val name = doc.getString("name")
                        if (!name.isNullOrBlank()) {
                            val first = name.split(" ").firstOrNull() ?: name
                            uiState.value = uiState.value.copy(firstName = first)
                        }
                    }
            }
        }
    }

    private fun attachRealtimeListeners() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        msgListener = db.collection("messages")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, _ ->
                val unread = snap?.size() ?: 0
                updateCounts(unreadMessages = unread)
            }

        matchListener = db.collection("match_notifications")
            .whereEqualTo("lostItemOwnerId", userId)
            .whereEqualTo("status", com.example.lostandfound.model.MatchNotificationStatus.UNREAD)
            .addSnapshotListener { snap, _ ->
                val unread = snap?.size() ?: 0
                updateCounts(unreadMatches = unread)
            }

        claimNotifListener = db.collection("claim_notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, _ ->
                val unread = snap?.size() ?: 0
                updateCounts(unreadClaimUpdates = unread)
            }

        claimListener = db.collection("claims")
            .whereIn("status", listOf(com.example.lostandfound.model.ClaimStatus.PENDING, com.example.lostandfound.model.ClaimStatus.DISPUTED))
            .addSnapshotListener { snap, _ ->
                val pending = snap?.size() ?: 0
                updateCounts(pendingClaims = pending)
            }
    }

    private fun updateCounts(
        unreadMessages: Int? = null,
        unreadMatches: Int? = null,
        pendingClaims: Int? = null,
        unreadClaimUpdates: Int? = null
    ) {
        val current = uiState.value
        val newUnreadMessages = unreadMessages ?: current.unreadMessages
        val newUnreadMatches = unreadMatches ?: current.unreadMatches
        val newPendingClaims = pendingClaims ?: current.pendingClaims
        val newUnreadClaimUpdates = unreadClaimUpdates ?: current.unreadClaimUpdates
        val total = newUnreadMessages + newUnreadMatches + newPendingClaims + newUnreadClaimUpdates
        uiState.value = current.copy(
            unreadMessages = newUnreadMessages,
            unreadMatches = newUnreadMatches,
            pendingClaims = newPendingClaims,
            unreadClaimUpdates = newUnreadClaimUpdates,
            notifCount = total
        )
    }

    override fun onCleared() {
        super.onCleared()
        msgListener?.remove()
        matchListener?.remove()
        claimListener?.remove()
        claimNotifListener?.remove()
    }
}

