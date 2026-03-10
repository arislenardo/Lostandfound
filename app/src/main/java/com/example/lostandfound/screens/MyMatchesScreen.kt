package com.example.lostandfound.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.MatchNotification
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = AuthManager.getCurrentUserId()
    
    var notifications by remember { mutableStateOf<List<MatchNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch notifications for current user
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            // Simple query - filter DISMISSED status locally to avoid composite index
            db.collection("match_notifications")
                .whereEqualTo("lostItemOwnerId", currentUserId)
                .get()
                .addOnSuccessListener { result ->
                    notifications = result.documents.mapNotNull { doc ->
                        doc.toObject(MatchNotification::class.java)?.copy(id = doc.id)
                    }.filter { it.status != "DISMISSED" }
                        .sortedByDescending { it.createdAt }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("MyMatchesScreen", "Query failed: ${e.message}")
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Potential Matches") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController.previousBackStackEntry != null && 
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No matches found yet",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "When someone finds an item matching your lost report, it will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { notification ->
                    MatchNotificationCard(
                        notification = notification,
                        onViewDetails = {
                            // Mark as read
                            db.collection("match_notifications").document(notification.id)
                                .update("status", "READ")
                            // Navigate to found item detail
                            navController.navigate("found_item_detail/${notification.foundItemId}")
                        },
                        onDismiss = {
                            db.collection("match_notifications").document(notification.id)
                                .update("status", "DISMISSED")
                                .addOnSuccessListener {
                                    notifications = notifications.filter { it.id != notification.id }
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MatchNotificationCard(
    notification: MatchNotification,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUnread = notification.status == "UNREAD"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(if (isUnread) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isUnread) {
                        Text(
                            "NEW MATCH!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Found: ${notification.foundItemName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "${(notification.matchScore * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Matches your lost: ${notification.lostItemName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Details to Claim")
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
