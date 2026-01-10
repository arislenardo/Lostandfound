package com.example.lostandfound.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.utils.findPotentialMatches

// --- SCREEN 4: SEARCH LOST ITEMS (Real Search) ---
@Composable
fun LostItemsScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var foundItems by remember { mutableStateOf<List<FoundItem>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()

    // Initial load
    // Initial load
    LaunchedEffect(Unit) {
        db.collection("found_items")
            .orderBy("dateFound", com.google.firebase.firestore.Query.Direction.DESCENDING) // Show newest first
            .limit(100) // <--- ADD THIS: Prevents downloading 5,000 items and crashing
            .get()
            .addOnSuccessListener { result ->
                foundItems = result.toObjects(FoundItem::class.java)
            }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Search Found Items", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
            },
            label = { Text("Search for your item...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filter and Calculate Scores
        val filteredItems: List<Pair<FoundItem, Double?>> = if (searchQuery.isBlank()) {
            foundItems.map { it to null }
        } else {
            // Re-use algorithm for local search display as well, cast double to nullable
            // Passing null, null for lat/lon since this is a keyword search
            findPotentialMatches(searchQuery, "", null, null, foundItems).map { it.first to it.second }
        }

        if (filteredItems.isEmpty()) {
            Text("No items found matching your search.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredItems) { (item, score) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                                if (score != null) {
                                    Text(
                                        text = "${(score * 100).toInt()}% Match",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Text(text = "Location: ${item.location}", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "Status: ${item.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Can't find what you're looking for?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("report_lost") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Report a Lost Item")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}
