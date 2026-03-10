package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.LostItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(navController: NavController, itemId: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isAdmin = AuthManager.isCurrentUserAdmin()

    // Item State
    var item by remember { mutableStateOf<LostItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Edit Mode State
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Fetch Item
    LaunchedEffect(itemId) {
        db.collection("lost_items").document(itemId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val loadedItem = document.toObject(LostItem::class.java)?.copy(id = document.id)
                    item = loadedItem
                    // Initialize edit fields
                    if (loadedItem != null) {
                        editName = loadedItem.name
                        editDescription = loadedItem.description
                        editLocation = loadedItem.location
                    }
                    isLoading = false
                }
            }
    }

    // UPDATE Function
    fun updateItem() {
        if (item == null) return

        db.collection("lost_items").document(item!!.id)
            .update(
                mapOf(
                    "name" to editName,
                    "description" to editDescription,
                    "location" to editLocation
                )
            )
            .addOnSuccessListener {
                Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT).show()
                isEditing = false
                // Update local state to reflect changes immediately
                item = item!!.copy(
                    name = editName,
                    description = editDescription,
                    location = editLocation
                )
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update item", Toast.LENGTH_SHORT).show()
            }
    }

    // DELETE Function
    fun deleteItem() {
        if (item?.id != null) {
            db.collection("lost_items").document(item!!.id).delete()
                .addOnSuccessListener {
                    Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error deleting item", Toast.LENGTH_SHORT).show()
                }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Report?") },
            text = { Text("Are you sure you want to remove this post? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isEditing) "EDIT LOST ITEM" else "LOST ITEM DETAILS", style = MaterialTheme.typography.titleMedium)
                        Text("Ref: #${itemId.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                        } else if (navController.previousBackStackEntry != null && navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (item != null && (item!!.userId == currentUserId || isAdmin)) {
                        if (isEditing) {
                            // SAVE Button
                            IconButton(onClick = { updateItem() }) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            // EDIT Button
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            // DELETE Button
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (item != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item!!.imageUrl.isNotBlank() && !isEditing) {
                    Card(modifier = Modifier.fillMaxWidth().height(200.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                        AsyncImage(
                            model = item!!.imageUrl,
                            contentDescription = "Item Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (isEditing) {
                    // --- EDIT MODE UI ---
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Item Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editLocation,
                                onValueChange = { editLocation = it },
                                label = { Text("Location") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editDescription,
                                onValueChange = { editDescription = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                } else {
                    // --- VIEW MODE UI ---
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                    // General Info Card
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("ITEM REPORT", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            DetailRow("Item Name", item!!.name)
                            DetailRow("Category", item!!.category)
                            
                            val dateString = try { dateFormat.format(item!!.dateLost) } catch(e: Exception) { "Unknown Date" }
                            DetailRow("Date Lost", dateString)
                        }
                    }

                    // Location & Description
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("DETAILS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            DetailRow("Location Lost", item!!.location)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Description:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text(item!!.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Admin Footer
                    if (isAdmin || item!!.userId == currentUserId) {
                        Card(
                             modifier = Modifier.fillMaxWidth(),
                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                             Column(modifier = Modifier.padding(16.dp)) {
                                 Text("INTERNAL RECORD", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                 Spacer(modifier = Modifier.height(8.dp))
                                 DetailRow("Reporter Email", item!!.email)
                                 if(isAdmin) DetailRow("User ID", item!!.userId)
                             }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Item not found", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}