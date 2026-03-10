package com.example.lostandfound.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
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
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage

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
                myLostItems = result.documents.mapNotNull { doc ->
                    doc.toObject(LostItem::class.java)?.copy(id = doc.id)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                android.util.Log.e("MyItemsScreen", "Error loading lost items", e)
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isAdmin) "LOST ITEMS DATABASE" else "MY REPORTED ITEMS",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (isAdmin) "Official Station Records" else "Your Lost Item Reports",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController.previousBackStackEntry != null &&
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
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
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)) {

            if (myLostItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No lost items have been reported yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAdmin) {
                navController.navigate("item_detail/${item.id}")
            },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(end = 16.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = item.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lost: ${dateFormat.format(item.dateLost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (isAdmin) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(text = item.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reported by: ${item.email}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Self-Resolution: User can remove their own lost report
                if (!isAdmin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val db = FirebaseFirestore.getInstance()
                            db.collection("lost_items").document(item.id).delete()
                                .addOnSuccessListener {
                                    android.widget.Toast.makeText(navController.context, "Marked as Found!", android.widget.Toast.LENGTH_SHORT).show()
                                    navController.navigate("my_items") { launchSingleTop = true }
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("I Found It (Remove)")
                    }
                }
            }
            if (isAdmin) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
