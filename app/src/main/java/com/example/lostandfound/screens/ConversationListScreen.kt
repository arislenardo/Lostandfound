package com.example.lostandfound.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.model.Message
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

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        // Fetch messages where I am sender or receiver
        // Note: Firestore OR queries are tricky. We'll do two queries or one collection group query if structured differently.
        // Simplest for prototype: Listen to "messages" (inefficient for large scale, ok for MVP)
        
        db.collection("messages").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    isLoading = false
                    return@addSnapshotListener
                }

                val allMessages = snapshot.toObjects(Message::class.java)
                
                // Group by the "other" person
                val conversationsMap = mutableMapOf<String, Message>()
                
                for (msg in allMessages) {
                    val otherid = if (msg.senderId == currentUserId) msg.receiverId else msg.senderId
                    // If this is a message involving me
                    if (msg.senderId == currentUserId || msg.receiverId == currentUserId) {
                        // If we haven't seen this user yet, or this message is newer (list is already sorted desc)
                        if (!conversationsMap.containsKey(otherid)) {
                            conversationsMap[otherid] = msg
                        }
                    }
                }
                
                uniqueConversations = conversationsMap.values.toList()
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MESSAGES", style = MaterialTheme.typography.titleMedium)
                        Text("Official Communications", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uniqueConversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(uniqueConversations) { lastMsg ->
                    ConversationItem(lastMsg, currentUserId, navController)
                }
            }
        }
    }
}

@Composable
fun ConversationItem(message: Message, currentUserId: String, navController: NavController) {
    val otherUserId = if (message.senderId == currentUserId) message.receiverId else message.senderId
    // We ideally need the other user's name. 
    // If I sent the last message, I need to know who I sent it to.
    // If I received it, the senderName is there.
    // Ideally user names should be stored in a "Users" collection to fetch.
    // For now, if we don't have the name, we show "User". 
    // Improvement: Fetch user name or pass it around. 
    // HACK: If I am the sender, I don't know the receiver's name from this message struct easily unless I store receiverName too.
    // But if I received it, I have senderName.
    
    val displayName = if (message.senderId != currentUserId) message.senderName else "User" // Fallback if I sent it
    
    // Better logic: We need to know who we are talking to.
    // For the MVP, let's assume we can click to open chat and passing "User" is okay if unknown, 
    // or we can fetch it. 
    // Since we added `senderName`, let's rely on receiving messages to populate names, 
    // or we simply navigate and let ChatScreen handle it (it accepts a name).

    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    ListItem(
        headlineContent = { Text(displayName.ifBlank { "User" }) },
        supportingContent = { 
            Text(
                text = "${if (message.senderId == currentUserId) "You: " else ""}${message.text}",
                maxLines = 1
            ) 
        },
        trailingContent = { Text(dateFormat.format(message.timestamp)) },
        modifier = Modifier.clickable {
            // Navigate to chat
            // Issue: If displayName is "User", it looks ugly. 
            // Real fix: Store receiverName in Message or fetch User profile. 
            // Proceeding with what we have.
            navController.navigate("chat/$otherUserId/${displayName.ifBlank { "User" }}")
        }
    )
    HorizontalDivider()
}
