package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.lostandfound.model.Message
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    var uniqueConversations by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) { isLoading = false; return@LaunchedEffect }
        db.collection("messages").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) { isLoading = false; return@addSnapshotListener }
                val allMessages = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Message::class.java)?.copy(id = doc.id)
                    } catch (ex: Exception) {
                        null // Prevent crash if a message is malformed
                    }
                }
                val conversationsMap = mutableMapOf<String, Message>()
                for (msg in allMessages) {
                    val otherId = if (msg.senderId == currentUserId) msg.receiverId else msg.senderId
                    if ((msg.senderId == currentUserId || msg.receiverId == currentUserId) && !conversationsMap.containsKey(otherId)) {
                        conversationsMap[otherId] = msg
                    }
                }
                uniqueConversations = conversationsMap.values.toList()
                isLoading = false
            }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MESSAGES", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Official Communications", fontSize = 11.sp, color = CityTheme.GoldLight)
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
            uniqueConversations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No messages yet", fontWeight = FontWeight.Bold, color = CityTheme.Brown)
                    Text("Messages with officers will appear here.", fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
            }
            else -> {
                val totalPages = maxOf(1, (uniqueConversations.size + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = uniqueConversations.drop(safePage * 10).take(10)
                val listState = rememberLazyListState()

                LaunchedEffect(safePage) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageItems) { lastMsg ->
                            CityConversationItem(lastMsg, currentUserId, navController)
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
    }
}

@Composable
fun CityConversationItem(message: Message, currentUserId: String, navController: NavController) {
    val otherUserId = if (message.senderId == currentUserId) message.receiverId else message.senderId
    val displayName = if (message.senderId != currentUserId) message.senderName else message.receiverName
    val isUnread = message.receiverId == currentUserId && !message.isRead
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val initials = displayName.take(1).uppercase()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clickable { navController.navigate("chat/$otherUserId/${displayName.ifBlank { "User" }}") },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CityTheme.Green),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = CityTheme.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(CityTheme.Gold)
                            .border(2.dp, CityTheme.White, CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "User" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = CityTheme.Brown
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${if (message.senderId == currentUserId) "You: " else ""}${message.text}",
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = if (isUnread) CityTheme.Brown else CityTheme.Brown.copy(alpha = 0.5f),
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                dateFormat.format(message.timestamp),
                fontSize = 10.sp,
                color = CityTheme.Brown.copy(alpha = 0.4f)
            )
        }
    }
}

// Legacy alias
@Composable
fun ConversationItem(message: Message, currentUserId: String, navController: NavController) =
    CityConversationItem(message, currentUserId, navController)
