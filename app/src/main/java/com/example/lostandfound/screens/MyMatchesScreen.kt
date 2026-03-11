package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.MatchNotification
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = AuthManager.getCurrentUserId()

    var notifications by remember { mutableStateOf<List<MatchNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDismissConfirm by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<MatchNotification?>(null) }

    DisposableEffect(currentUserId) {
        if (currentUserId == null) return@DisposableEffect onDispose { }
        
        val listener = db.collection("match_notifications")
            .whereEqualTo("lostItemOwnerId", currentUserId)
            .addSnapshotListener { result, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                if (result != null) {
                    notifications = result.documents.mapNotNull { doc ->
                        doc.toObject(MatchNotification::class.java)?.copy(id = doc.id)
                    }.filter { it.status != "DISMISSED" }.sortedByDescending { it.createdAt }
                    isLoading = false
                }
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("POTENTIAL MATCHES", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("AI-Matched Found Items", fontSize = 11.sp, color = CityTheme.GoldLight)
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
                    Icon(Icons.Default.Search, null, Modifier.size(64.dp), tint = CityTheme.Green.copy(0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No matches found yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CityTheme.Brown)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "When someone finds an item matching your lost report, it will appear here.",
                        fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f), textAlign = TextAlign.Center
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { notification ->
                    CityMatchNotificationCard(
                        notification = notification,
                        onViewDetails = {
                            db.collection("match_notifications").document(notification.id).update("status", "READ")
                            navController.navigate("found_item_detail/${notification.foundItemId}")
                        },
                        onDismiss = {
                            selectedNotification = notification
                            showDismissConfirm = true
                        }
                    )
                }
            }
        }

        if (showDismissConfirm && selectedNotification != null) {
            AlertDialog(
                onDismissRequest = { showDismissConfirm = false },
                title = { Text("Dismiss Match?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to dismiss this match for '${selectedNotification!!.foundItemName}'? You won't see it again in your matches.") },
                confirmButton = {
                    Button(
                        onClick = {
                            db.collection("match_notifications").document(selectedNotification!!.id)
                                .update("status", "DISMISSED")
                                .addOnSuccessListener {
                                    notifications = notifications.filter { it.id != selectedNotification!!.id }
                                }
                            showDismissConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error)
                    ) { Text("Dismiss") }
                },
                dismissButton = {
                    TextButton(onClick = { showDismissConfirm = false }) { Text("Cancel", color = CityTheme.Brown) }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun CityMatchNotificationCard(
    notification: MatchNotification,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUnread = notification.status == "UNREAD"
    val matchPct = (notification.matchScore * 100).toInt()
    val matchColor = when {
        matchPct >= 70 -> CityTheme.Green
        matchPct >= 40 -> CityTheme.Gold
        else           -> CityTheme.Brown.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(if (isUnread) 6.dp else 2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CityTheme.Gold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("NEW MATCH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CityTheme.White)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("Found: ${notification.foundItemName}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CityTheme.Brown)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$matchPct%", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = matchColor)
                    Text("match", fontSize = 10.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Matches your lost: ${notification.lostItemName}",
                fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                ) {
                    Text("View Details to Claim", fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Dismiss", tint = CityTheme.Brown.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun MatchNotificationCard(notification: MatchNotification, onViewDetails: () -> Unit, onDismiss: () -> Unit) =
    CityMatchNotificationCard(notification, onViewDetails, onDismiss)
