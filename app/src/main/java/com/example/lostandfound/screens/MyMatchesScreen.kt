package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.MatchNotification
import com.example.lostandfound.model.MatchNotificationStatus
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Displays a list of potential AI-generated matches for the user's reported lost items.
 * Users can view details or dismiss the match notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = AuthManager.getCurrentUserId()

    var notifications by remember { mutableStateOf<List<MatchNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDismissConfirm by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<MatchNotification?>(null) }
    var currentPage by remember { mutableStateOf(0) }

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
                    }.filter { it.status != MatchNotificationStatus.DISMISSED }.sortedByDescending { it.createdAt }
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
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(CityTheme.Green.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(44.dp), tint = CityTheme.Green.copy(0.4f))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("No Matches Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CityTheme.Brown)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "When our AI finds an item that matches your lost report, it will appear here.",
                        fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f), textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                val totalPages = maxOf(1, (notifications.size + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = notifications.drop(safePage * 10).take(10)
                val listState = rememberLazyListState()

                LaunchedEffect(safePage) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(pageItems) { notification ->
                            CityMatchNotificationCard(
                                notification = notification,
                                onViewDetails = {
                                    db.collection("match_notifications").document(notification.id).update("status", MatchNotificationStatus.READ)
                                    navController.navigate("found_item_detail/${notification.foundItemId}?lostItemId=${notification.lostItemId}")
                                },
                                onDismiss = {
                                    selectedNotification = notification
                                    showDismissConfirm = true
                                }
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }

        if (showDismissConfirm && selectedNotification != null) {
            AlertDialog(
                onDismissRequest = { showDismissConfirm = false },
                title = { Text("Dismiss Match?", fontWeight = FontWeight.Bold, color = CityTheme.Brown) },
                text = { Text("Are you sure you want to dismiss the match for '${selectedNotification!!.foundItemName}'? You won't see it again.", color = CityTheme.Brown.copy(alpha = 0.7f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            db.collection("match_notifications").document(selectedNotification!!.id)
                                .update("status", MatchNotificationStatus.DISMISSED)
                                .addOnSuccessListener {
                                    notifications = notifications.filter { it.id != selectedNotification!!.id }
                                }
                            showDismissConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Dismiss") }
                },
                dismissButton = {
                    TextButton(onClick = { showDismissConfirm = false }) { Text("Cancel", color = CityTheme.Brown) }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = CityTheme.White
            )
        }
    }
}

/**
 * Renders a card for a match notification, showing the found item's image, match percentage,
 * and actions to view details or dismiss.
 */
@Composable
fun CityMatchNotificationCard(
    notification: MatchNotification,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isUnread = notification.status == MatchNotificationStatus.UNREAD
    val matchPct = (notification.matchScore * 100).toInt()
    val matchColor = when {
        matchPct >= 70 -> CityTheme.Green
        matchPct >= 40 -> CityTheme.Gold
        else           -> CityTheme.Brown.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isUnread) 5.dp else 2.dp
        )
    ) {
        Column {
            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                if (notification.foundItemImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(notification.foundItemImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = notification.foundItemName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(CityTheme.Cream, CityTheme.Cream.copy(alpha = 0.6f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📦", fontSize = 48.sp)
                    }
                }

                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    CityTheme.White.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                // Match score badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(matchColor)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "$matchPct%",
                        color = CityTheme.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                // Unread indicator dot
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CityTheme.Gold)
                    )
                }
            }

            // Card body
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            notification.foundItemName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CityTheme.Brown,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Matches your lost: ${notification.lostItemName}",
                            fontSize = 12.sp,
                            color = CityTheme.Brown.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Dismiss icon
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Dismiss",
                            tint = CityTheme.Brown.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Location if available
                if (notification.foundItemLocation.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = CityTheme.Green.copy(0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            notification.foundItemLocation,
                            fontSize = 12.sp,
                            color = CityTheme.Brown.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Match label
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(matchColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "$matchPct% AI match confidence",
                            fontSize = 11.sp,
                            color = matchColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onViewDetails,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                ) {
                    Text("View Details & Claim", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Legacy alias for [CityMatchNotificationCard].
 */
@Composable
fun MatchNotificationCard(notification: MatchNotification, onViewDetails: () -> Unit, onDismiss: () -> Unit) =
    CityMatchNotificationCard(notification, onViewDetails, onDismiss)
