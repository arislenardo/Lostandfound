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
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.lostandfound.model.Message
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

data class ParticipantInfo(
    val email: String = "",
    val profileImageUrl: String = ""
)

/**
 * Displays a list of unique chat conversations for the current user.
 * It fetches sent and received messages, merges them, and shows the latest message for each conversation thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    var uniqueConversations by remember { mutableStateOf<List<Message>>(emptyList()) }
    var userInfosMap by remember { mutableStateOf<Map<String, ParticipantInfo>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }

    DisposableEffect(currentUserId) {
        if (currentUserId.isBlank()) { isLoading = false; return@DisposableEffect onDispose {} }
        var q1Messages = listOf<Message>()
        var q2Messages = listOf<Message>()
        
        fun mergeAndSet() {
            val allMessages = (q1Messages + q2Messages).sortedByDescending { it.timestamp }
            val conversationsMap = mutableMapOf<String, Message>()
            for (msg in allMessages) {
                val otherId = if (msg.senderId == currentUserId) msg.receiverId else msg.senderId
                if (!conversationsMap.containsKey(otherId)) {
                    conversationsMap[otherId] = msg
                }
            }
            val conversationsList = conversationsMap.values.toList()
            
            val otherUserIds = conversationsList.map { 
                if (it.senderId == currentUserId) it.receiverId else it.senderId 
            }.filter { it.isNotBlank() }.distinct()
            
            if (otherUserIds.isEmpty()) {
                uniqueConversations = conversationsList
                isLoading = false
                return
            }
            
            val tasks = otherUserIds.map { userId ->
                db.collection("users").document(userId).get()
            }
            
            com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnCompleteListener { _ ->
                    val infoMap = mutableMapOf<String, ParticipantInfo>()
                    tasks.forEachIndexed { index, task ->
                        if (task.isSuccessful) {
                            val doc = task.result
                            if (doc != null && doc.exists()) {
                                infoMap[otherUserIds[index]] = ParticipantInfo(
                                    email = doc.getString("email") ?: "",
                                    profileImageUrl = doc.getString("profileImageUrl") ?: ""
                                )
                            }
                        }
                    }
                    userInfosMap = infoMap
                    uniqueConversations = conversationsList
                    isLoading = false
                }
        }

        val listener1 = db.collection("messages")
            .whereEqualTo("senderId", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                q1Messages = snapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(Message::class.java)?.copy(id = doc.id) } catch (ex: Exception) { null }
                }
                mergeAndSet()
            }

        val listener2 = db.collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                q2Messages = snapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(Message::class.java)?.copy(id = doc.id) } catch (ex: Exception) { null }
                }
                mergeAndSet()
            }
            
        onDispose {
            listener1.remove()
            listener2.remove()
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
                            val otherUserId = if (lastMsg.senderId == currentUserId) lastMsg.receiverId else lastMsg.senderId
                            val info = userInfosMap[otherUserId] ?: ParticipantInfo()
                            CityConversationItem(lastMsg, currentUserId, navController, info.email, info.profileImageUrl)
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
    }
}

/**
 * Renders a single conversation thread item showing the other user's name, avatar, 
 * latest message snippet, and an unread badge if applicable.
 */
@Composable
fun CityConversationItem(
    message: Message, 
    currentUserId: String, 
    navController: NavController, 
    otherUserEmail: String,
    profileImageUrl: String
) {
    val otherUserId = if (message.senderId == currentUserId) message.receiverId else message.senderId
    val displayName = if (message.senderId != currentUserId) message.senderName else message.receiverName
    val isUnread = message.receiverId == currentUserId && !message.isRead
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val initials = displayName.take(1).uppercase()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clickable { navController.navigate("chat/$otherUserId/${displayName.ifBlank { "User" }}?email=$otherUserEmail") },
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
                if (profileImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CityTheme.Brown.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CityTheme.Green),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials, color = CityTheme.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
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
                if (otherUserEmail.isNotBlank()) {
                    Text(
                        otherUserEmail,
                        fontSize = 11.sp,
                        color = CityTheme.Brown.copy(alpha = 0.6f)
                    )
                }
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
/**
 * Legacy alias for [CityConversationItem].
 */
@Composable
fun ConversationItem(message: Message, currentUserId: String, navController: NavController) =
    CityConversationItem(message, currentUserId, navController, "", "")
