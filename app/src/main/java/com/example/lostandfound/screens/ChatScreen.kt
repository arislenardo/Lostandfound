package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.data.ChatManager
import com.example.lostandfound.model.Message
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.lostandfound.utils.uploadImageToStorage
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import com.example.lostandfound.components.FullScreenImageDialog

/**
 * Real-time messaging interface between a citizen and an administrator.
 * Supports sending text messages, uploading images, and allows admins to conclude (close) a chat session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, receiverId: String, receiverName: String, initialEmail: String = "") {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val currentUserId = currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var newMessageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isAdmin = remember { AuthManager.isCurrentUserAdmin() }
    var showEndChatDialog by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf<String?>(null) }

    var isChatClosed by remember { mutableStateOf(false) }
    var receiverEmail by remember { mutableStateOf(initialEmail) }

    LaunchedEffect(receiverId) {
        if (receiverId.isNotBlank()) {
            db.collection("users").document(receiverId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        receiverEmail = document.getString("email") ?: ""
                    }
                }
        }
    }

    showFullScreenImage?.let { url ->
        FullScreenImageDialog(url) { showFullScreenImage = null }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    // Generate deterministic chat ID
    val chatId = remember(currentUserId, receiverId) {
        ChatManager.getConversationId(currentUserId, receiverId)
    }

    DisposableEffect(chatId) {
        if (chatId.isBlank()) return@DisposableEffect onDispose { }
        val listener = db.collection("closed_chats").document(chatId).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            isChatClosed = snapshot?.getBoolean("closed") ?: false
        }
        onDispose { listener.remove() }
    }

    DisposableEffect(currentUserId, receiverId) {
        if (currentUserId.isBlank()) return@DisposableEffect onDispose { }
        
        var q1Messages = listOf<Message>()
        var q2Messages = listOf<Message>()

        fun mergeAndSet() {
            val allMessages = (q1Messages + q2Messages).sortedBy { it.timestamp }
            messages = allMessages
            
            // Mark incoming messages as read in batch
            val unread = allMessages.filter { it.receiverId == currentUserId && it.isRead != true }
            if (unread.isNotEmpty()) {
                val batch = db.batch()
                unread.forEach { msg ->
                    batch.update(db.collection("messages").document(msg.id), "isRead", true)
                }
                batch.commit()
            }
        }

        val listener1 = db.collection("messages")
            .whereEqualTo("senderId", currentUserId)
            .whereEqualTo("receiverId", receiverId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                q1Messages = snapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(Message::class.java)?.copy(id = doc.id) } catch (ex: Exception) { null }
                }
                mergeAndSet()
            }

        val listener2 = db.collection("messages")
            .whereEqualTo("senderId", receiverId)
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(receiverName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CityTheme.White)
                        if (receiverEmail.isNotBlank()) {
                            Text(receiverEmail, fontSize = 11.sp, color = CityTheme.GoldLight)
                        }
                        Text(if (isChatClosed) "Closed Session" else "Secure Channel", fontSize = 11.sp, color = if (isChatClosed) CityTheme.Error else CityTheme.GoldLight)
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
                    if (isAdmin && !isChatClosed) {
                        IconButton(onClick = { showEndChatDialog = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Conclude Session", tint = CityTheme.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { msg ->
                    CityMessageBubble(
                        message = msg, 
                        isCurrentUser = msg.senderId == currentUserId,
                        onImageClick = { url -> showFullScreenImage = url }
                    )
                }
            }

            // Input bar or Closed Notice
            if (isChatClosed) {
                Surface(
                    color = CityTheme.Brown.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "The session has been concluded. Thank you.",
                        color = CityTheme.Brown.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Surface(
                    shadowElevation = 12.dp,
                    color = CityTheme.White
                ) {
                    Column {
                        // Image Preview
                        if (selectedImageUri != null) {
                            Box(modifier = Modifier.padding(12.dp).size(100.dp).clip(RoundedCornerShape(12.dp))) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(CityTheme.White.copy(0.7f), androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = CityTheme.Error)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.AddAPhoto, null, tint = CityTheme.Green)
                            }
                            
                            OutlinedTextField(
                                value = newMessageText,
                                onValueChange = { newMessageText = it },
                                placeholder = { Text("Type a message…", color = CityTheme.Brown.copy(alpha = 0.4f)) },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CityTheme.Green,
                                    unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.2f),
                                    cursorColor = CityTheme.Green
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (newMessageText.isNotBlank() || selectedImageUri != null) CityTheme.Green else CityTheme.Brown.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CityTheme.White, strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (!isChatClosed && (newMessageText.isNotBlank() || selectedImageUri != null)) {
                                                isSending = true
                                                coroutineScope.launch {
                                                    try {
                                                        val url = selectedImageUri?.let {
                                                            uploadImageToStorage(it, userId = currentUserId, userEmail = auth.currentUser?.email ?: "", itemType = "chat")
                                                        } ?: ""
                                                        
                                                        val msg = Message(
                                                            senderId = currentUserId,
                                                            senderName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User",
                                                            senderImageUrl = auth.currentUser?.photoUrl?.toString() ?: "",
                                                            receiverId = receiverId,
                                                            receiverName = receiverName,
                                                            text = newMessageText.trim(),
                                                            imageUrl = url,
                                                            timestamp = Date()
                                                        )

                                                        ChatManager.sendMessage(
                                                            message = msg,
                                                            skipEmail = !isAdmin,
                                                            onSuccess = { 
                                                                // --- TRIGGER EMAIL NOTIFICATION TO ADMIN IF CITIZEN SENDS ---
                                                                if (!isAdmin) {
                                                                    com.example.lostandfound.data.EmailService.sendTargetedAdminNotification(
                                                                        adminUid = receiverId,
                                                                        type = "CITIZEN REPLY/DISPUTE",
                                                                        itemName = "Ongoing Thread",
                                                                        reporterName = auth.currentUser?.displayName ?: "Citizen",
                                                                        reporterEmail = auth.currentUser?.email ?: "Unknown",
                                                                        details = "A citizen is responding to your message: ${newMessageText.trim()}"
                                                                    )
                                                                }
                                                                // ------------------------------------------------------------
                                                                
                                                                newMessageText = ""
                                                                selectedImageUri = null
                                                                isSending = false
                                                            },
                                                            onFailure = { 
                                                                isSending = false
                                                                Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    } catch (e: Exception) {
                                                        isSending = false
                                                        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isChatClosed && (newMessageText.isNotBlank() || selectedImageUri != null)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send, "Send",
                                            tint = if (newMessageText.isNotBlank() || selectedImageUri != null) CityTheme.White else CityTheme.Brown.copy(alpha = 0.3f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showEndChatDialog) {
                AlertDialog(
                    onDismissRequest = { showEndChatDialog = false },
                    title = { Text("End Chat Session?", fontWeight = FontWeight.Bold) },
                    text = { Text("This will send a final status message and conclude this session. No further replies will be possible.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val closingMsg = Message(
                                    senderId = currentUserId,
                                    senderName = "Official Station Admin",
                                    receiverId = receiverId,
                                    receiverName = receiverName,
                                    text = "The session has been concluded. Thank you.",
                                    timestamp = Date()
                                )
                                ChatManager.sendMessage(
                                    message = closingMsg,
                                    skipEmail = true, // We don't want an email for the closing message
                                    onSuccess = { 
                                        // Write to closed_chats collection
                                        db.collection("closed_chats").document(chatId).set(mapOf("closed" to true))
                                            .addOnSuccessListener {
                                                showEndChatDialog = false
                                                Toast.makeText(context, "Session Concluded", Toast.LENGTH_SHORT).show()
                                            }
                                    },
                                    onFailure = { 
                                        Toast.makeText(context, "Failed to send closing message", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Gold)
                        ) { Text("Send & End", color = CityTheme.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndChatDialog = false }) { Text("Cancel", color = CityTheme.Green) }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

/**
 * Renders an individual message bubble within the chat screen, styling it differently
 * based on whether it was sent by the current user or the receiver.
 */
@Composable
fun CityMessageBubble(message: Message, isCurrentUser: Boolean, onImageClick: (String) -> Unit = {}) {
    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isCurrentUser) {
            if (message.senderImageUrl.isNotBlank()) {
                AsyncImage(
                    model = message.senderImageUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(CityTheme.Brown.copy(alpha=0.2f)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(CityTheme.Green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = CityTheme.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    if (isCurrentUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                )
                .background(if (isCurrentUser) CityTheme.Green else CityTheme.White)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column {
                if (message.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Message Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CityTheme.Brown.copy(alpha = 0.05f))
                            .clickable { onImageClick(message.imageUrl) },
                        contentScale = ContentScale.Crop
                    )
                    if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = if (isCurrentUser) CityTheme.White else CityTheme.Brown,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                
                Text(
                    text = dateFormat.format(message.timestamp),
                    fontSize = 10.sp,
                    color = if (isCurrentUser) CityTheme.White.copy(alpha = 0.6f) else CityTheme.Brown.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
        
        if (isCurrentUser) {
            Spacer(Modifier.width(8.dp))
            if (message.senderImageUrl.isNotBlank()) {
                AsyncImage(
                    model = message.senderImageUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(CityTheme.Brown.copy(alpha=0.2f)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(CityTheme.Green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = CityTheme.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// Legacy alias
/**
 * Legacy alias for [CityMessageBubble].
 */
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) = CityMessageBubble(message, isCurrentUser)
