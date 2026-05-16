package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.model.AdminAction
import com.example.lostandfound.model.Claim
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.model.ItemStatus
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/**
 * Displays a list of pending and disputed claims for station administrators to review.
 * Admins can approve, reject, or message the claimant directly from this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminClaimsScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    var claims by remember { mutableStateOf<List<Claim>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedClaim by remember { mutableStateOf<Claim?>(null) }
    var pendingStatus by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("claims")
            .whereIn("status", listOf(ClaimStatus.PENDING, ClaimStatus.DISPUTED))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    claims = snapshot.documents.mapNotNull { it.toObject(Claim::class.java)?.copy(id = it.id) }
                }
                isLoading = false
            }
    }

    fun updateStatus(claim: Claim, newStatus: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val adminId = currentUser?.uid ?: ""
        val adminName = currentUser?.email ?: ""
        
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "reviewedBy" to adminId,
            "reviewerEmail" to adminName
        )
        
        db.collection("claims").document(claim.id).update(updates)
            .addOnSuccessListener {
                // ALSO: Sync this status back to the original lost item if it exists
                if (claim.lostItemId.isNotBlank()) {
                    db.collection("lost_items").document(claim.lostItemId).update("status", newStatus)
                }
                // ALSO: If approved, mark the found item as CLAIMED so it disappears from public search
                if (newStatus == ClaimStatus.APPROVED) {
                    val map = mutableMapOf<String, Any>("status" to ItemStatus.CLAIMED)
                    if (claim.lostItemId.isNotBlank()) {
                        map["claimedLostItemId"] = claim.lostItemId
                    }
                    db.collection("found_items").document(claim.itemId).update(map)
                }
                // 1. Create a notification for the user
                val notification = com.example.lostandfound.model.ClaimNotification(
                    claimId = claim.id,
                    userId = claim.userId,
                    itemId = claim.itemId,
                    itemName = if (claim.itemName.isNotBlank()) claim.itemName else "your item",
                    status = newStatus,
                    timestamp = java.util.Date()
                )
                db.collection("claim_notifications").add(notification).addOnSuccessListener { ref ->
                    ref.update("id", ref.id)
                    
                    // --- NEW: TRIGGER EMAIL NOTIFICATION ---
                    if (claim.userEmail.isNotBlank()) {
                        com.example.lostandfound.data.EmailService.sendClaimStatusNotification(
                            userEmail = claim.userEmail,
                            itemName = claim.itemName,
                            status = newStatus
                        )
                    }
                    // ----------------------------------------
                }

                // 2. Log the action
                val actionType = if (newStatus == ClaimStatus.APPROVED) "APPROVED_CLAIM" else "REJECTED_CLAIM"
                val action = AdminAction(
                    adminId = adminId,
                    adminName = adminName,
                    actionType = actionType,
                    itemTitle = "Claim by ${claim.userEmail}",
                    itemId = claim.id
                )
                db.collection("admin_history").add(action).addOnSuccessListener { doc ->
                    db.collection("admin_history").document(doc.id).update("id", doc.id)
                }

                Toast.makeText(context, "Claim ${newStatus.lowercase()}! Notif sent to ${claim.userId.take(5)}...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update claim: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MANAGE CLAIMS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Pending Approvals", fontSize = 11.sp, color = CityTheme.GoldLight)
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
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
            claims.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(56.dp), tint = CityTheme.Green.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("All caught up!", fontWeight = FontWeight.Bold, color = CityTheme.Brown)
                    Text("No pending claims to review.", fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
            }
            else -> {
                val totalPages = maxOf(1, (claims.size + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = claims.drop(safePage * 10).take(10)
                val listState = rememberLazyListState()
                
                LaunchedEffect(safePage) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageItems) { claim ->
                            CityClaimReviewCard(
                                claim = claim,
                                onApprove = { 
                                    selectedClaim = claim
                                    pendingStatus = ClaimStatus.APPROVED
                                    showConfirmDialog = true
                                },
                                onReject = { 
                                    selectedClaim = claim
                                    pendingStatus = ClaimStatus.REJECTED
                                    showConfirmDialog = true
                                },
                                onViewItem = { navController.navigate("found_item_detail/${claim.itemId}") },
                                onViewLostReport = { navController.navigate("item_detail/${claim.lostItemId}") },
                                onMessage = {
                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                    val adminId = currentUser?.uid ?: ""
                                    
                                    // Inject the claim proof message into the chat history between claimant and this admin
                                    db.collection("messages")
                                        .whereEqualTo("itemId", claim.itemId)
                                        .whereEqualTo("senderId", claim.userId)
                                        .whereEqualTo("receiverId", adminId)
                                        .get()
                                        .addOnSuccessListener { snap ->
                                            if (snap.isEmpty) {
                                                val msg = com.example.lostandfound.model.Message(
                                                    senderId = claim.userId,
                                                    senderName = claim.userName,
                                                    receiverId = adminId,
                                                    receiverName = currentUser?.email?.substringBefore("@") ?: "Admin",
                                                    text = "Claim proof submitted for '${claim.itemName}': ${claim.proofDescription}",
                                                    timestamp = claim.timestamp,
                                                    itemId = claim.itemId,
                                                    imageUrl = claim.imageUrl
                                                )
                                                db.collection("messages").add(msg)
                                            }
                                            val userName = claim.userEmail.substringBefore("@")
                                            navController.navigate("chat/${claim.userId}/${userName}")
                                        }
                                }
                            )
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
        
        if (showConfirmDialog && selectedClaim != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(if (pendingStatus == ClaimStatus.APPROVED) "Approve Claim?" else "Reject Claim?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to ${pendingStatus.lowercase()} this claim for '${selectedClaim!!.itemName}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            updateStatus(selectedClaim!!, pendingStatus)
                            showConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pendingStatus == "APPROVED") CityTheme.Green else CityTheme.Error)
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel", color = CityTheme.Brown)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * Renders a card displaying the details of a user's claim, including their proof description,
 * uploaded proof image, and actionable buttons for the admin (Message, Reject, Approve).
 */
@Composable
fun CityClaimReviewCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit, onMessage: () -> Unit, onViewItem: () -> Unit = {}, onViewLostReport: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Gold accent left border
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(CityTheme.Gold))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Item", fontSize = 10.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                    Text(if (claim.itemName.isNotBlank()) claim.itemName else "Item: ${claim.itemId.takeLast(4)}", fontWeight = FontWeight.Bold, color = CityTheme.Green, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Claimant", fontSize = 10.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                    Text(claim.userEmail, fontWeight = FontWeight.Medium, color = CityTheme.Brown, fontSize = 13.sp)
                }
                if (claim.status == ClaimStatus.DISPUTED) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CityTheme.Error.copy(alpha = 0.1f))
                            .border(1.dp, CityTheme.Error.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("DISPUTED", color = CityTheme.Error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CityTheme.Brown.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            Text("Cross-Reference Records", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CityTheme.Brown.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Link to Found Item
                TextButton(
                    onClick = onViewItem,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = CityTheme.Green.copy(0.08f), contentColor = CityTheme.Green),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Original Found Item", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Link to Lost Report (if exists)
                if (claim.lostItemId.isNotBlank()) {
                    TextButton(
                        onClick = onViewLostReport,
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = CityTheme.Gold.copy(0.12f), contentColor = CityTheme.Gold),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Claimant's Lost Report", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CityTheme.Brown.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            Text("Proof Provided:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CityTheme.Brown.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Text(claim.proofDescription, fontSize = 14.sp, color = CityTheme.Brown)
            
            if (claim.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = claim.imageUrl,
                    contentDescription = "Proof Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CityTheme.Brown.copy(alpha = 0.05f)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Message button
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.horizontalGradient(listOf(CityTheme.Brown.copy(0.3f), CityTheme.Brown.copy(0.3f)))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Brown)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Message", fontSize = 12.sp, maxLines = 1)
                }
                // Reject button
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.horizontalGradient(listOf(CityTheme.Error.copy(0.5f), CityTheme.Error.copy(0.5f)))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", fontSize = 12.sp, maxLines = 1)
                }
                // Approve button
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// Keep the old composable name as an alias for navigation compatibility
/**
 * Legacy alias for [CityClaimReviewCard].
 */
@Composable
fun ClaimReviewCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit, onMessage: () -> Unit) =
    CityClaimReviewCard(claim, onApprove, onReject, onMessage)
