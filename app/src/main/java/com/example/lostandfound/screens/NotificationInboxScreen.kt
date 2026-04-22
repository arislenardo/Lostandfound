package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Tasks
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
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.model.MatchNotificationStatus
import com.example.lostandfound.model.Message
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A unified notification data class used to standardize different types of notifications
 * (messages, matches, claim updates) for display in the inbox.
 */
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

/**
 * Enum defining the types of notifications handled by the inbox.
 */
enum class NotificationType { MESSAGE, MATCH, CLAIM_PENDING, CLAIM_UPDATE }

/**
 * In-memory cache to persist the read state of messages across navigation events within the same app session.
 * Prevents cleared cards from reappearing.
 */
private object NotificationReadCache {
    val messageIds = mutableSetOf<String>()
}

/**
 * A central inbox screen displaying unified notifications for messages, matches, and claim updates.
 * Supports clearing all notifications and navigating to the relevant detail screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()
    
    var isAdmin by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Fetch Admin Status
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            isAdmin = AuthManager.refreshAdminStatus()
        }
    }

    // State for tracking if initial snapshots have loaded
    var msgLoaded by remember { mutableStateOf(false) }
    var matchLoaded by remember { mutableStateOf(false) }
    var claimNotifLoaded by remember { mutableStateOf(false) }
    var adminClaimLoaded by remember { mutableStateOf(false) }

    // Unified List State
    val rawMessages = remember { mutableStateListOf<Message>() }
    val rawMatches = remember { mutableStateListOf<MatchNotification>() }
    val rawClaims = remember { mutableStateListOf<Claim>() }
    val rawClaimNotifs = remember { mutableStateListOf<ClaimNotification>() }
    // Reactive Unified List — NO key args so derivedStateOf tracks SnapshotStateList content changes
    val notifications by remember {
        derivedStateOf {
            val combined = mutableListOf<UnifiedNotification>()
            
            // 1. Process Messages — only INCOMING (receiverId == currentUserId)
            // Group by senderId so each conversation thread has one card
            val threads = rawMessages.groupBy { it.senderId }

            threads.forEach { (peerId, msgs) ->
                val hasUnread = msgs.any { it.isRead != true }
                if (hasUnread) {
                    val latestUnread = msgs.filter { it.isRead != true }.maxByOrNull { it.timestamp }!!
                    val peerName = latestUnread.senderName

                    combined.add(
                        UnifiedNotification(
                            id = latestUnread.id,
                            type = NotificationType.MESSAGE,
                            title = "Message from ${if(peerName.isNotBlank()) peerName else "User"}",
                            preview = latestUnread.text,
                            timestamp = latestUnread.timestamp,
                            isUnread = true,
                            actionData = peerId,
                            actionData2 = if (peerName.isNotBlank()) peerName else "User"
                        )
                    )
                }
            }

            // 2. Process Matches (Only unread)
            rawMatches.forEach { match ->
                if (match.status == MatchNotificationStatus.UNREAD) {
                    combined.add(
                        UnifiedNotification(
                            id = match.id,
                            type = NotificationType.MATCH,
                            title = "New Match Found!",
                            preview = "Potential match for: ${match.lostItemName}",
                            timestamp = match.createdAt,
                            isUnread = true,
                            actionData = match.foundItemId,
                            actionData2 = match.lostItemId
                        )
                    )
                }
            }

            // 3. Process Claims (Admin)
            if (isAdmin) {
                rawClaims.forEach { claim ->
                    val isDispute = claim.status == ClaimStatus.DISPUTED
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
            }

            // 4. Process Claim Updates (Only unread)
            rawClaimNotifs.forEach { notif ->
                if (!notif.isRead) {
                    combined.add(
                        UnifiedNotification(
                            id = notif.id,
                            type = NotificationType.CLAIM_UPDATE,
                            title = "Claim ${notif.status}",
                            preview = "Your claim for ${notif.itemName} was ${notif.status.lowercase()}",
                            timestamp = notif.timestamp,
                            isUnread = true,
                            actionData = notif.itemId
                        )
                    )
                }
            }

            // Sort by newest first
            combined.sortedByDescending { it.timestamp }
        }
    }

    // Update isLoading state when everything is initially loaded
    LaunchedEffect(msgLoaded, matchLoaded, claimNotifLoaded, adminClaimLoaded) {
        if (msgLoaded && matchLoaded && claimNotifLoaded && (!isAdmin || adminClaimLoaded)) {
            isLoading = false
        }
    }

    // Track IDs we've locally marked as read — lives in a singleton so navigation can't reset it
    val locallyReadMessageIds = NotificationReadCache.messageIds

    // Merge incoming messages into rawMessages, preserving local read state
    fun updateRawMessages(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val newMsgs = docs.mapNotNull {
            try { it.toObject(Message::class.java)?.copy(id = it.id) } catch(e:Exception) { null }
        }
        rawMessages.clear()
        // Never revert a message we've already locally marked as read
        rawMessages.addAll(newMsgs.map { msg ->
            if (msg.id in locallyReadMessageIds) msg.copy(isRead = true) else msg
        })
        msgLoaded = true
    }

    fun clearAllNotifications() {
        val batch = db.batch()
        var updates = 0

        // 1. Mark messages read Locally & Build Batch
        for (i in rawMessages.indices) {
            val msg = rawMessages[i]
            if (msg.receiverId == currentUserId && msg.isRead != true) {
                locallyReadMessageIds.add(msg.id)       // Prevent snapshot revert
                rawMessages[i] = msg.copy(isRead = true) // Optimistic Update
                batch.update(db.collection("messages").document(msg.id), "isRead", true)
                updates++
            }
        }

        // 2. Mark matches as read Locally & Build Batch
        for (i in rawMatches.indices) {
            val match = rawMatches[i]
            if (match.status == MatchNotificationStatus.UNREAD) {
                rawMatches[i] = match.copy(status = MatchNotificationStatus.READ) // Optimistic Update
                batch.update(db.collection("match_notifications").document(match.id), "status", MatchNotificationStatus.READ)
                updates++
            }
        }

        // 3. Mark claim updates as read Locally & Build Batch
        for (i in rawClaimNotifs.indices) {
            val notif = rawClaimNotifs[i]
            if (!notif.isRead) {
                rawClaimNotifs[i] = notif.copy(isRead = true) // Optimistic Update
                batch.update(db.collection("claim_notifications").document(notif.id), "isRead", true)
                updates++
            }
        }

        if (updates > 0) {
            batch.commit()
                .addOnSuccessListener { Toast.makeText(context, "Inbox cleared", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(context, "Clear failed: ${it.message}", Toast.LENGTH_LONG).show() }
        } else {
            Toast.makeText(context, "Nothing to clear", Toast.LENGTH_SHORT).show()
        }
    }

    // Effect for User-Specific Listeners
    DisposableEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            isLoading = false
            return@DisposableEffect onDispose { }
        }

        // Only listen to INCOMING messages — outgoing never create notifications
        val incomingListener = db.collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .addSnapshotListener { snap, _ ->
                updateRawMessages(snap?.documents ?: emptyList())
            }

        val matchListener = db.collection("match_notifications")
            .whereEqualTo("lostItemOwnerId", currentUserId)
            .addSnapshotListener { snap, _ ->
                rawMatches.clear()
                snap?.documents?.forEach { doc ->
                     doc.toObject(MatchNotification::class.java)?.copy(id = doc.id)?.let { rawMatches.add(it) }
                }
                matchLoaded = true
            }

        val claimNotifListener = db.collection("claim_notifications")
            .whereEqualTo("userId", currentUserId)
            .addSnapshotListener { snap, _ ->
                rawClaimNotifs.clear()
                snap?.documents?.forEach { doc ->
                    doc.toObject(ClaimNotification::class.java)?.copy(id = doc.id)?.let { rawClaimNotifs.add(it) }
                }
                claimNotifLoaded = true
            }

        onDispose {
            incomingListener.remove()
            matchListener.remove()
            claimNotifListener.remove()
            msgLoaded = false
            matchLoaded = false
            claimNotifLoaded = false
        }
    }


    // Effect for Admin-Specific Listeners
    DisposableEffect(currentUserId, isAdmin) {
        if (!isAdmin || currentUserId.isBlank()) {
            adminClaimLoaded = false
            rawClaims.clear()
            // If the user isn't an admin, we might need to trigger stop loading if we were waiting for this
            if (msgLoaded && matchLoaded && claimNotifLoaded) {
                isLoading = false
            }
            return@DisposableEffect onDispose { }
        }

        val claimListener = db.collection("claims")
            .whereIn("status", listOf(ClaimStatus.PENDING, ClaimStatus.DISPUTED))
            .addSnapshotListener { snap, _ ->
                rawClaims.clear()
                snap?.documents?.forEach { doc ->
                    doc.toObject(Claim::class.java)?.copy(id = doc.id)?.let { rawClaims.add(it) }
                }
                adminClaimLoaded = true
            }

        onDispose {
            claimListener.remove()
            adminClaimLoaded = false
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
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear All", tint = CityTheme.White)
                        }
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
            else -> {
                val totalPages = maxOf(1, (notifications.size + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = notifications.drop(safePage * 10).take(10)
                val listState = rememberLazyListState()

                LaunchedEffect(safePage) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageItems, key = { it.id }) { notif ->
                            CityNotificationItem(notif, navController, db, currentUserId, rawMessages, rawMatches, rawClaimNotifs, locallyReadMessageIds)
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
        
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Notifications?") },
                text = { Text("This will mark all notifications as read.") },
                confirmButton = {
                    TextButton(onClick = { 
                        clearAllNotifications()
                        showClearDialog = false 
                    }) {
                        Text("Clear All", color = CityTheme.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Renders a single notification item in the inbox, styling it based on the notification type
 * and handling click actions to navigate to the appropriate screen.
 */
@Composable
fun CityNotificationItem(
    notification: UnifiedNotification, 
    navController: NavController,
    db: FirebaseFirestore,
    currentUserId: String,
    rawMessages: androidx.compose.runtime.snapshots.SnapshotStateList<Message>,
    rawMatches: androidx.compose.runtime.snapshots.SnapshotStateList<MatchNotification>,
    rawClaimNotifs: androidx.compose.runtime.snapshots.SnapshotStateList<ClaimNotification>,
    locallyReadMessageIds: MutableSet<String>
) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    
    val (iconText, iconColor) = when (notification.type) {
        NotificationType.MESSAGE -> Pair("✉️", CityTheme.Green)
        NotificationType.MATCH -> Pair("🔍", CityTheme.Gold)
        NotificationType.CLAIM_PENDING -> Pair("⚠️", CityTheme.Error)
                    NotificationType.CLAIM_UPDATE -> if (notification.title.contains(ClaimStatus.APPROVED)) Pair("✅", CityTheme.Green) else Pair("❌", CityTheme.Error)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isUnread) CityTheme.White else androidx.compose.ui.graphics.Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    when(notification.type) {
                        NotificationType.MESSAGE -> {
                            // Mark all messages from this sender as read (optimistic)
                            for (i in rawMessages.indices) {
                                val msg = rawMessages[i]
                                if (msg.senderId == notification.actionData && msg.isRead != true) {
                                    locallyReadMessageIds.add(msg.id)  // Prevent snapshot revert
                                    rawMessages[i] = msg.copy(isRead = true)
                                    db.collection("messages").document(msg.id).update("isRead", true)
                                }
                            }
                            navController.navigate("chat/${notification.actionData}/${notification.actionData2}")
                        }
                        NotificationType.MATCH -> {
                            FirebaseFirestore.getInstance().collection("match_notifications").document(notification.id).update("status", MatchNotificationStatus.READ)
                            navController.navigate("found_item_detail/${notification.actionData}?lostItemId=${notification.actionData2}")
                        }
                        NotificationType.CLAIM_PENDING -> navController.navigate("admin_claims")
                        NotificationType.CLAIM_UPDATE -> {
                            FirebaseFirestore.getInstance().collection("claim_notifications").document(notification.id).update("isRead", true)
                            navController.navigate("found_item_detail/${notification.actionData}")
                        }
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (notification.isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CityTheme.Gold)
                )
                Spacer(Modifier.width(12.dp))
            }
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
                        fontWeight = if (notification.isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = CityTheme.Brown
                    )
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

            Spacer(Modifier.width(8.dp))

            // Delete / Dismiss Icon
            IconButton(
                onClick = {
                    when (notification.type) {
                        NotificationType.MESSAGE -> {
                            // Mark all messages from this sender as read (optimistic dismiss)
                            for (i in rawMessages.indices) {
                                val msg = rawMessages[i]
                                if (msg.senderId == notification.actionData && msg.isRead != true) {
                                    locallyReadMessageIds.add(msg.id)  // Prevent snapshot revert
                                    rawMessages[i] = msg.copy(isRead = true)
                                    db.collection("messages").document(msg.id).update("isRead", true)
                                }
                            }
                        }
                        NotificationType.MATCH -> {
                            // Mark as read (don't delete), update locally and in DB
                            rawMatches.replaceAll { if (it.id == notification.id) it.copy(status = MatchNotificationStatus.READ) else it }
                            db.collection("match_notifications").document(notification.id)
                                .update("status", MatchNotificationStatus.READ)
                                .addOnFailureListener { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                        NotificationType.CLAIM_UPDATE -> {
                            // Mark as read (don't delete), update locally and in DB
                            rawClaimNotifs.replaceAll { if (it.id == notification.id) it.copy(isRead = true) else it }
                            db.collection("claim_notifications").document(notification.id)
                                .update("isRead", true)
                                .addOnFailureListener { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                        NotificationType.CLAIM_PENDING -> {
                            // Admins shouldn't delete pending claims here
                        }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove",
                    tint = CityTheme.Brown.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
