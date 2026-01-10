package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.AuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.FoundItem
import com.example.lostandfound.seedDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var isSeeding by remember { mutableStateOf(false) }
    var showResolvedDialog by remember { mutableStateOf(false) }
    var itemToResolve by remember { mutableStateOf<FoundItem?>(null) }
    val currentUser = auth.currentUser
    val isAdmin = AuthManager.isCurrentUserAdmin()

    fun markAsResolved(item: FoundItem) {
        val db = FirebaseFirestore.getInstance()
        // Note: This assumes the item has a valid Firestore document ID.
        db.collection("found_items").document(item.id)
            .update("status", "Resolved")
            .addOnSuccessListener {
                Toast.makeText(context, "Item marked as resolved!", Toast.LENGTH_SHORT).show()
                navController.navigate("home") { popUpTo("home") { inclusive = true } }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    if (showResolvedDialog && itemToResolve != null) {
        AlertDialog(
            onDismissRequest = { showResolvedDialog = false },
            title = { Text("Mark as Resolved?") },
            text = { Text("Have you returned this item to its owner? This will hide it from the active list.") },
            confirmButton = {
                TextButton(onClick = {
                    markAsResolved(itemToResolve!!)
                    showResolvedDialog = false
                }) { Text("Yes, Resolved") }
            },
            dismissButton = {
                TextButton(onClick = { showResolvedDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lost & Found") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = {
                            isSeeding = true
                            seedDatabase { message ->
                                isSeeding = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                navController.navigate("home") { popUpTo("home") { inclusive = true } }
                            }
                        }) {
                            if (isSeeding) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Data")
                            }
                        }
                    }
                    TextButton(onClick = {
                        auth.signOut()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }) {
                        Text("Logout")
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome, ${if (isAdmin) "Admin" else "Resident"}", style = MaterialTheme.typography.headlineSmall)
            Text("What would you like to do?", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            ActionCard(
                title = "I Lost Something", 
                description = "Search for items that have been found by others.", 
                icon = Icons.Default.Search,
                onClick = { navController.navigate("lost") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActionCard(
                title = "I Found Something", 
                description = "Report an item that you have found to help its owner.", 
                icon = Icons.Default.Add,
                onClick = { navController.navigate("report") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ActionCard(
                title = if (isAdmin) "View All Lost Items" else "My Reported Items",
                description = if (isAdmin) "Review all items reported as lost by users." else "View the status of items you have reported as lost.",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = { navController.navigate("my_items") }
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ActionCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
