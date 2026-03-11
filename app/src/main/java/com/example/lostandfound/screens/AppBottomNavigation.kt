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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
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
