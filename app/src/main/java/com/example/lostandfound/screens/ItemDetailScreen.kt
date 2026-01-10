package com.example.lostandfound.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.LostItem
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(navController: NavController, itemId: String) {
    var item by remember { mutableStateOf<LostItem?>(null) }

    // Fetch the specific item from Firestore
    LaunchedEffect(itemId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("lost_items").document(itemId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    item = document.toObject(LostItem::class.java)
                }
            }
            .addOnFailureListener {
                // Handle error - e.g., show a toast or log
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lost Item Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (item != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Item Name:", item!!.name)
                DetailRow("Description:", item!!.description)
                DetailRow("Location Lost:", item!!.location)
                DetailRow("Date Lost:", item!!.dateLost)
                DetailRow("Category:", item!!.category)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Reporter Information", style = MaterialTheme.typography.titleMedium)
                DetailRow("User Email:", item!!.email)
                DetailRow("User ID:", item!!.userId)
                if (item!!.latitude != null && item!!.longitude != null) {
                    DetailRow("Coordinates:", "${item!!.latitude}, ${item!!.longitude}")
                }
            }
        } else {
            // Show a loading indicator or a not found message
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
