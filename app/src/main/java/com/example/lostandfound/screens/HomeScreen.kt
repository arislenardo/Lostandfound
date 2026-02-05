package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.data.AuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.model.FoundItem
import androidx.compose.material.icons.filled.Settings
import com.example.lostandfound.ui.theme.LocalThemeConfig
import com.example.lostandfound.utils.seedDatabase


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var seedResult by remember { mutableStateOf("") }

    var showResolvedDialog by remember { mutableStateOf(false) }
    var itemToResolve by remember { mutableStateOf<FoundItem?>(null) }
    val currentUser = auth.currentUser
    var isAdmin by remember { mutableStateOf(AuthManager.isCurrentUserAdmin()) }

    LaunchedEffect(Unit) {
        isAdmin = AuthManager.refreshAdminStatus()
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    val themeConfig = LocalThemeConfig.current

    val displayName = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "User"
    val firstName = displayName.split(" ").firstOrNull() ?: displayName

    fun markAsResolved(item: FoundItem) {
        val db = FirebaseFirestore.getInstance()
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

    // (Dialog code removed or kept if needed - keeping logic minimal for dashboard focus)
    // Assuming dialog logic resides elsewhere or is triggered by list view, keeping it dormant here is fine.

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STATION DASHBOARD", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Official Lost & Found", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Theme Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (isAdmin) {
                        IconButton(onClick = {
                            seedDatabase { result ->
                                seedResult = result
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Seed Database", tint = MaterialTheme.colorScheme.primary)
                        }

                    }
                    TextButton(onClick = {
                        auth.signOut()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // WELCOME HEADER
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome, $firstName",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = if (isAdmin) "Administrator Access" else "Resident Access",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Text("QUICK ACTIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            // DASHBOARD GRID
            // Row 1: Reporting
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardCard(
                    title = "I Lost An Item",
                    icon = Icons.Default.Add,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("report_lost") }
                )
                DashboardCard(
                    title = "I Found An Item",
                    icon = androidx.compose.material.icons.Icons.Filled.Edit, // or Visibility
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("report") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Management
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardCard(
                    title = if (isAdmin) "View All Lost Items" else "My Reports",
                    icon = Icons.AutoMirrored.Filled.List,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("my_items") }
                )
                DashboardCard(
                    title = "Messages",
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, // or Message
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("conversations") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Row 2.5: Potential Matches (Non-Admin Only)
            if (!isAdmin) {
                var unreadCount by remember { mutableStateOf(0) }
                val db = FirebaseFirestore.getInstance()
                val userId = currentUser?.uid
                
                LaunchedEffect(userId) {
                    if (userId != null) {
                        db.collection("match_notifications")
                            .whereEqualTo("lostItemOwnerId", userId)
                            .whereEqualTo("status", "UNREAD")
                            .get()
                            .addOnSuccessListener { result ->
                                unreadCount = result.size()
                            }
                    }
                }
                
                Card(
                    onClick = { navController.navigate("my_matches") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (unreadCount > 0) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(if (unreadCount > 0) 4.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Potential Matches",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Items matching your lost reports",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Text("$unreadCount")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Row 3: Admin Only (Search & Claims)
            if (isAdmin) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DashboardCard(
                        title = "View All Found Items",
                        icon = Icons.Default.Search,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("lost") }
                    )
                    DashboardCard(
                        title = "Review Claims",
                        icon = Icons.Default.Person, // Using Person as proxy for "User Claims"
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("admin_claims") }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("App Settings") },
            text = {
                Column {
                    Text("Customize your experience:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Dark Mode", modifier = Modifier.weight(1f))
                        Switch(checked = themeConfig.isDark, onCheckedChange = { themeConfig.toggleDark() })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun DashboardCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(120.dp), // Square-ish
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}