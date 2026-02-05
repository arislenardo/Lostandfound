package com.example.lostandfound.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.LostItem
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
                // This query requires a composite index: userId ASC, dateLost DESC
                query = query.whereEqualTo("userId", it)
            }
        }

        query.orderBy("dateLost", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { result ->
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
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isAdmin) "ALL LOST ITEMS" else "MY REPORTS", style = MaterialTheme.typography.titleMedium)
                        Text("Lost Item Registry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
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
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (myLostItems.isEmpty()) {
                    item {
                         Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if(isAdmin) "No lost items reported." else "You haven't reported any lost items.", 
                                    style = MaterialTheme.typography.titleMedium, 
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                } else {
                    items(myLostItems) { item ->
                        LostItemCard(item = item, navController = navController, isAdmin = isAdmin)
                    }
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
            .clickable(enabled = isAdmin) { // Only admins can click to edit/view details
                navController.navigate("item_detail/${item.id}")
            },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = item.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                // Format the Date object to string if needed, or rely on item properties
                // Assuming Data model has a date object, we might want to format it. 
                // However, the original code used a String in `dateFoundText` for found items, but `dateLost` is a Date object for LostItem.
                // Let's assume we treat it simply or format it.
                // For now, relying on toString() or existing property access if it was string.
                // Original code: Text(text = "Date Lost: ${item.dateLost}") which implies implicit toString()
                
                val dateString = try {
                     val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                     format.format(item.dateLost)
                } catch(e: Exception) { "Unknown Date" }

                Text(
                    text = "Lost: $dateString",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                if (isAdmin) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reported by: ${item.email}", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isAdmin) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
