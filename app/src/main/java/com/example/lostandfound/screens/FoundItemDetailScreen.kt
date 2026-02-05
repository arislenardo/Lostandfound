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
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.Claim
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundItemDetailScreen(navController: NavController, itemId: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isAdmin = AuthManager.isCurrentUserAdmin()

    // Item State
    var item by remember { mutableStateOf<FoundItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Edit Mode State
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Fetch Item & User's Claim Status
    var userClaim by remember { mutableStateOf<Claim?>(null) }
    
    LaunchedEffect(itemId) {
        // 1. Fetch Item
        db.collection("found_items").document(itemId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val loadedItem = document.toObject(FoundItem::class.java)?.copy(id = document.id)
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

        // 2. Fetch User's Claim (if not admin/owner)
        if (!isAdmin && currentUserId != null) {
            db.collection("claims")
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        userClaim = snapshot.documents[0].toObject(Claim::class.java)
                    }
                }
        }
    }

    // UPDATE Function
    fun updateItem() {
        if (item == null) return

        db.collection("found_items").document(item!!.id)
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
            db.collection("found_items").document(item!!.id).delete()
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
                        Text(if (isEditing) "EDIT FOUND ITEM" else "FOUND ITEM DETAILS", style = MaterialTheme.typography.titleMedium)
                        Text("Reference: #${itemId.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                     }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            isEditing = false
                        } else if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
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
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("ITEM INFORMATION", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            DetailRowLabel("Item Name", item!!.name)
                            DetailRowLabel("Category", item!!.category)
                            
                            val dateString = try { dateFormat.format(item!!.dateFound) } catch(e: Exception) { "Unknown Date" }
                            DetailRowLabel("Date Found", dateString)
                        }
                    }

                    // Location & Description Card
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("DETAILS & LOCATION", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            DetailRowLabel("Location", item!!.location)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Description:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(item!!.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Admin/Reporter Info
                    if (isAdmin || item!!.userId == currentUserId) {
                        Card(
                            modifier = Modifier.fillMaxWidth(), 
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("ADMINISTRATION", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailRowLabel("Reporter Email", item!!.email)
                                if (isAdmin) {
                                    DetailRowLabel("User ID", item!!.userId)
                                    DetailRowLabel("Record ID", item!!.id)
                                }
                            }
                        }
                    }

                    // CLAIM SECTION (For Residents)
                    if (!isAdmin && item!!.userId != currentUserId) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var showClaimDialog by remember { mutableStateOf(false) }
                        var proofDescription by remember { mutableStateOf("") }
                        var isSubmittingClaim by remember { mutableStateOf(false) }

                        if (userClaim == null) {
                            Button(
                                onClick = { showClaimDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Claim This Item")
                            }
                        } else {
                            val statusColor = when(userClaim!!.status) {
                                "APPROVED" -> MaterialTheme.colorScheme.primary
                                "REJECTED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("CLAIM STATUS: ${userClaim!!.status}", style = MaterialTheme.typography.titleSmall, color = statusColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (userClaim!!.status == "APPROVED") {
                                        Text("Your claim has been approved! Please pick up the item at the station.", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        DetailRowLabel("Finder Email", item!!.email) // Show contact info now!
                                    } else if (userClaim!!.status == "PENDING") {
                                        Text("Your proof is currently being reviewed by an officer.", style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text("Your claim was rejected. Please contact the station for more info.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        // CLAIM DIALOG
                        if (showClaimDialog) {
                            AlertDialog(
                                onDismissRequest = { showClaimDialog = false },
                                title = { Text("Secure Claim Verification") },
                                text = {
                                    Column {
                                        Text("Please describe unique details about the item that only the owner would know (e.g., scratches, wallpaper, contents).", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedTextField(
                                            value = proofDescription,
                                            onValueChange = { proofDescription = it },
                                            label = { Text("Proof of Ownership") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = !isSubmittingClaim && proofDescription.isNotBlank(),
                                        onClick = {
                                            isSubmittingClaim = true
                                            val newClaim = Claim(
                                                itemId = item!!.id,
                                                userId = currentUserId ?: "",
                                                userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "",
                                                proofDescription = proofDescription,
                                                timestamp = java.util.Date()
                                            )
                                            db.collection("claims").add(newClaim)
                                                .addOnSuccessListener { ref ->
                                                    // Update ID
                                                    ref.update("id", ref.id)
                                                    userClaim = newClaim.copy(id = ref.id)
                                                    showClaimDialog = false
                                                    isSubmittingClaim = false
                                                    Toast.makeText(context, "Claim submitted for review!", Toast.LENGTH_LONG).show()
                                                }
                                                .addOnFailureListener {
                                                    isSubmittingClaim = false
                                                    Toast.makeText(context, "Failed to submit claim.", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    ) {
                                        Text("Submit Claim")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClaimDialog = false }) { Text("Cancel") }
                                }
                            )
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
fun DetailRowLabel(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
