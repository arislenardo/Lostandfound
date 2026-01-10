package com.example.lostandfound.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.AuthManager
import com.example.lostandfound.LostItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyItemsScreen(navController: NavController) {
    var myLostItems by remember { mutableStateOf<List<LostItem>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()
    val isAdmin = AuthManager.isCurrentUserAdmin()
    val currentUserId = AuthManager.getCurrentUserId()

    LaunchedEffect(key1 = isAdmin, key2 = currentUserId) {
        var query: Query = db.collection("lost_items")

        if (!isAdmin) {
            currentUserId?.let {
                query = query.whereEqualTo("userId", it)
            }
        }

        query.orderBy("dateLost", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { result ->
                // Store items with their document IDs
                myLostItems = result.documents.mapNotNull { doc ->
                    doc.toObject(LostItem::class.java)?.copy(id = doc.id)
                }
            }
            .addOnFailureListener {
                // Handle error
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAdmin) "All Reported Lost Items" else "My Reported Items") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (myLostItems.isEmpty()) {
                item {
                    Text("No lost items have been reported yet.")
                }
            } else {
                items(myLostItems) { item ->
                    LostItemCard(item = item, navController = navController, isAdmin = isAdmin)
                }
            }
        }
    }
}

@Composable
fun LostItemCard(item: LostItem, navController: NavController, isAdmin: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAdmin) { // Only admins can click
                navController.navigate("item_detail/${item.id}")
            },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Date Lost: ${item.dateLost}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Location: ${item.location}", style = MaterialTheme.typography.bodyMedium)
            
            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, // Show a snippet of the description
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reported by: ${item.email}", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
