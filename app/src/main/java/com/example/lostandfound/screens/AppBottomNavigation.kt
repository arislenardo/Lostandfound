package com.example.lostandfound.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.ui.theme.CityTheme

@Composable
fun AppBottomNavigation(
    navController: NavController,
    currentRoute: String,
    isAdmin: Boolean = false
) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    
    var unreadMessageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        val listener = db.collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                unreadMessageCount = snapshot.documents.size
            }
        // No strict need to cleanup if this sits at the root, but good practice if it recomposes
    }

    NavigationBar(
        containerColor = CityTheme.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 10.sp) },
            selected = currentRoute == "home",
            onClick = {
                if (currentRoute != "home") {
                    navController.navigate("home") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CityTheme.Green,
                selectedTextColor = CityTheme.Green,
                indicatorColor = CityTheme.GreenLight.copy(alpha = 0.2f),
                unselectedIconColor = CityTheme.Brown.copy(alpha = 0.5f),
                unselectedTextColor = CityTheme.Brown.copy(alpha = 0.5f)
            )
        )
        if (isAdmin) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                label = { Text("History", fontSize = 10.sp) },
                selected = currentRoute == "history",
                onClick = {
                    if (currentRoute != "history") {
                        navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CityTheme.Green,
                    selectedTextColor = CityTheme.Green,
                    indicatorColor = CityTheme.GreenLight.copy(alpha = 0.2f),
                    unselectedIconColor = CityTheme.Brown.copy(alpha = 0.5f),
                    unselectedTextColor = CityTheme.Brown.copy(alpha = 0.5f)
                )
            )
        }
        NavigationBarItem(
            icon = { 
                BadgedBox(
                    badge = {
                        if (unreadMessageCount > 0) {
                            Badge(containerColor = CityTheme.Gold) {
                                Text(
                                    if (unreadMessageCount > 9) "9+" else "$unreadMessageCount",
                                    fontSize = 10.sp,
                                    color = CityTheme.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile")
                }
            },
            label = { Text("Profile", fontSize = 10.sp) },
            selected = currentRoute == "profile",
            onClick = {
                if (currentRoute != "profile") {
                    navController.navigate("profile") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = CityTheme.Green,
                selectedTextColor = CityTheme.Green,
                indicatorColor = CityTheme.GreenLight.copy(alpha = 0.2f),
                unselectedIconColor = CityTheme.Brown.copy(alpha = 0.5f),
                unselectedTextColor = CityTheme.Brown.copy(alpha = 0.5f)
            )
        )
    }
}
