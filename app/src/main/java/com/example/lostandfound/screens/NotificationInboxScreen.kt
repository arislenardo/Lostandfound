package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.Claim
import com.example.lostandfound.model.ClaimNotification
import com.example.lostandfound.model.MatchNotification
import com.example.lostandfound.model.Message
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Unified Notification Data Class
data class UnifiedNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val preview: String,
    val timestamp: Date,
    val isUnread: Boolean,
    val actionData: String, // E.g., userId for chat, itemId for matches
    val actionData2: String = "" // E.g., userName for chat
)

enum class NotificationType { MESSAGE, MATCH, CLAIM_PENDING, CLAIM_UPDATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()
    
    var isAdmin by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf<List<UnifiedNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch Admin Status
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            isAdmin = AuthManager.isCurrentUserAdmin()
        }
    }

    // Unified Data Fetcher
    DisposableEffect(currentUserId, isAdmin) {
        if (currentUserId.isBlank()) {
            isLoading = false
            return@DisposableEffect onDispose { }
        }

        val rawMessages = mutableListOf<Message>()
        val rawMatches = mutableListOf<MatchNotification>()
        val rawClaims = mutableListOf<Claim>()
        val rawClaimNotifs = mutableListOf<ClaimNotification>()

        fun updateUnifiedList() {
            val combined = mutableListOf<UnifiedNotification>()
            
            // 1. Process Messages (Only showing unread, or most recent unread per user)
            // Group messages by sender so we only show one notification per conversation
            val activeSenders = rawMessages.filter { it.receiverId == currentUserId && !it.isRead }
                .groupBy { it.senderId }
                
            activeSenders.forEach { (senderId, msgs) ->
                val latest = msgs.maxByOrNull { it.timestamp }
                if (latest != null) {
                    val senderName = if (latest.senderName.isNotBlank()) latest.senderName else "User"
                    combined.add(
                        UnifiedNotification(
                            id = latest.id,
                            type = NotificationType.MESSAGE,
                            title = "New message from $senderName",
                            preview = latest.text,
                            timestamp = latest.timestamp,
                            isUnread = true,
                            actionData = senderId,
                            actionData2 = senderName
                        )
                    )
                }
            }

            // 2. Process Matches
            rawMatches.forEach { match ->
                combined.add(
                    UnifiedNotification(
                        id = match.id,
                        type = NotificationType.MATCH,
                        title = "New Match Found!",
                        preview = "Potential match for: ${match.lostItemName}",
                        timestamp = match.createdAt,
                        isUnread = match.status == "UNREAD",
                        actionData = match.id
                    )
                )
            }

            // 3. Process Claims (Admin Only)
            rawClaims.forEach { claim ->
                val isDispute = claim.status == "DISPUTED"
                combined.add(
                    UnifiedNotification(
                        id = claim.id,
                        type = NotificationType.CLAIM_PENDING,
                        title = if (isDispute) "CLAIM DISPUTED" else "Action Required: Claim",
                        preview = if (isDispute) "${claim.userName} is contesting a rejection" else "${claim.userName} claims: ${if(claim.itemName.isNotBlank()) claim.itemName else "Item #${claim.itemId.take(4)}"}",
                        timestamp = claim.timestamp,
                        isUnread = true,
                        actionData = "admin_claims"
                    )
                )
            }

            // 4. Process Claim Updates (Approved/Rejected)
            rawClaimNotifs.forEach { notif ->
                combined.add(
                    UnifiedNotification(
                        id = notif.id,
                        type = NotificationType.CLAIM_UPDATE,
                        title = "Claim ${notif.status}",
                        preview = "Your claim for ${notif.itemName} was ${notif.status.lowercase()}",
                        timestamp = notif.timestamp,
                        isUnread = !notif.isRead,
                        actionData = notif.itemId
                    )
                )
            }

            // Sort by newest first
            notifications = combined.sortedByDescending { it.timestamp }
            isLoading = false
        }

        // --- Listeners ---
        val msgListener = db.collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, _ ->
                rawMessages.clear()
                snap?.documents?.forEach { doc ->
                    try { doc.toObject(Message::class.java)?.copy(id = doc.id)?.let { rawMessages.add(it) } } catch(e:Exception){}
                }
                updateUnifiedList()
            }

        val matchListener = db.collection("match_notifications")
            .whereEqualTo("lostItemOwnerId", currentUserId)
            .whereNotIn("status", listOf("DISMISSED"))
            .addSnapshotListener { snap, _ ->
                rawMatches.clear()
                snap?.documents?.forEach { doc ->
                     doc.toObject(MatchNotification::class.java)?.copy(id = doc.id)?.let { rawMatches.add(it) }
                }
                updateUnifiedList()
            }

        // Admin Claims Listener
        val claimListener = if (isAdmin) {
             db.collection("claims")
                .whereIn("status", listOf("PENDING", "DISPUTED"))
                .addSnapshotListener { snap, _ ->
                    rawClaims.clear()
                    snap?.documents?.forEach { doc ->
                        doc.toObject(Claim::class.java)?.copy(id = doc.id)?.let { rawClaims.add(it) }
                    }
                    updateUnifiedList()
                }
        } else null

        // Claim Notifications Listener (for Users)
        val claimNotifListener = db.collection("claim_notifications")
            .whereEqualTo("userId", currentUserId)
            .addSnapshotListener { snap, _ ->
                rawClaimNotifs.clear()
                snap?.documents?.forEach { doc ->
                    doc.toObject(ClaimNotification::class.java)?.copy(id = doc.id)?.let { rawClaimNotifs.add(it) }
                }
                updateUnifiedList()
            }

        onDispose {
            msgListener.remove()
            matchListener.remove()
            claimListener?.remove()
            claimNotifListener.remove()
        }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOTIFICATIONS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Inbox", fontSize = 11.sp, color = CityTheme.GoldLight)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController.previousBackStackEntry != null &&
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CityTheme.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
            notifications.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("You're all caught up!", fontWeight = FontWeight.Bold, color = CityTheme.Brown)
                    Text("No new notifications.", fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notif ->
                    CityNotificationItem(notif, navController)
                }
            }
        }
    }
}

@Composable
fun CityNotificationItem(notification: UnifiedNotification, navController: NavController) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    
    val (iconText, iconColor) = when (notification.type) {
        NotificationType.MESSAGE -> Pair("✉️", CityTheme.Green)
        NotificationType.MATCH -> Pair("🔍", CityTheme.Gold)
        NotificationType.CLAIM_PENDING -> Pair("⚠️", CityTheme.Error)
        NotificationType.CLAIM_UPDATE -> if (notification.title.contains("APPROVED")) Pair("✅", CityTheme.Green) else Pair("❌", CityTheme.Error)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (notification.isUnread) 4.dp else 1.dp, RoundedCornerShape(14.dp))
            .clickable { 
                when(notification.type) {
                    NotificationType.MESSAGE -> navController.navigate("chat/${notification.actionData}/${notification.actionData2}")
                    NotificationType.MATCH -> navController.navigate("my_matches")
                    NotificationType.CLAIM_PENDING -> navController.navigate("admin_claims")
                    NotificationType.CLAIM_UPDATE -> {
                        // Mark as read when clicking
                        FirebaseFirestore.getInstance().collection("claim_notifications").document(notification.id).update("isRead", true)
                        navController.navigate("found_item_detail/${notification.actionData}")
                    }
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isUnread) CityTheme.White else CityTheme.White.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, fontSize = 20.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notification.title,
                        fontWeight = if (notification.isUnread) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = CityTheme.Brown
                    )
                    if (notification.isUnread) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CityTheme.Gold))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notification.preview,
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = CityTheme.Brown.copy(alpha = 0.6f)
                )
            }
            
            Text(
                dateFormat.format(notification.timestamp),
                fontSize = 10.sp,
                color = CityTheme.Brown.copy(alpha = 0.4f)
            )
        }
    }
}
