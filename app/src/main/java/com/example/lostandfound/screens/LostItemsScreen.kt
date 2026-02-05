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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.data.AuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostItemsScreen(navController: NavController) {
    var foundItems by remember { mutableStateOf<List<FoundItem>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()
    val isAdmin = AuthManager.isCurrentUserAdmin()

    // Initial load
    LaunchedEffect(Unit) {
        db.collection("found_items")
            .orderBy("dateFound", com.google.firebase.firestore.Query.Direction.DESCENDING) // Show newest first
            .limit(100)
            .get()
            .addOnSuccessListener { result ->
                foundItems = result.documents.mapNotNull { doc ->
                    doc.toObject(FoundItem::class.java)?.copy(id = doc.id)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View All Found Items") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)) {

            if (foundItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No found items reported yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(foundItems) { item ->
                        FoundItemCard(item = item, navController = navController, isAdmin = isAdmin)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Can't find what you're looking for?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { navController.navigate("report_lost") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Report a Lost Item")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FoundItemCard(item: FoundItem, navController: NavController, isAdmin: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAdmin) {
                navController.navigate("found_item_detail/${item.id}")
            },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Date Found: ${item.dateFoundText.ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Location: ${item.location}", style = MaterialTheme.typography.bodyMedium)
            
            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = item.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
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
