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
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, receiverId: String, receiverName: String) {
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

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    DisposableEffect(currentUserId, receiverId) {
        if (currentUserId.isBlank()) return@DisposableEffect onDispose { }
        val query = db.collection("messages").orderBy("timestamp", Query.Direction.ASCENDING)
        val listener = query.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot != null) {
                val allMessages = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Message::class.java)?.copy(id = doc.id)
                    } catch (ex: Exception) {
                        null
                    }
                }
                val filtered = allMessages.filter {
                    (it.senderId == currentUserId && it.receiverId == receiverId) ||
                    (it.senderId == receiverId && it.receiverId == currentUserId)
                }
                messages = filtered
                
                // Mark incoming messages as read
                filtered.forEach { msg ->
                    if (msg.receiverId == currentUserId && !msg.isRead) {
                        db.collection("messages").document(msg.id).update("isRead", true)
                    }
                }
            }
        }
        onDispose { listener.remove() }
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
                        Text("Secure Channel", fontSize = 11.sp, color = CityTheme.GoldLight)
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
                    CityMessageBubble(message = msg, isCurrentUser = msg.senderId == currentUserId)
                }
            }

            // Input bar
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
                                        if (newMessageText.isNotBlank() || selectedImageUri != null) {
                                            isSending = true
                                            coroutineScope.launch {
                                                try {
                                                    val url = selectedImageUri?.let {
                                                        uploadImageToStorage(it, userId = currentUserId, userEmail = auth.currentUser?.email ?: "", itemType = "chat")
                                                    } ?: ""
                                                    
                                                    val msg = Message(
                                                        senderId = currentUserId,
                                                        senderName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "User",
                                                        receiverId = receiverId,
                                                        receiverName = receiverName,
                                                        text = newMessageText.trim(),
                                                        imageUrl = url,
                                                        timestamp = Date()
                                                    )
                                                    ChatManager.sendMessage(msg,
                                                        onSuccess = { 
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
                                    enabled = newMessageText.isNotBlank() || selectedImageUri != null
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
    }
}

@Composable
fun CityMessageBubble(message: Message, isCurrentUser: Boolean) {
    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
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
                            .background(CityTheme.Brown.copy(alpha = 0.05f)),
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
    }
}

// Legacy alias
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) = CityMessageBubble(message, isCurrentUser)
