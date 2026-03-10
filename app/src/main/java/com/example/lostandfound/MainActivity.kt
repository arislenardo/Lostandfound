package com.example.lostandfound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.example.lostandfound.screens.*
import com.example.lostandfound.data.AuthManager

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.lostandfound.ui.theme.ThemeConfig
import com.example.lostandfound.ui.theme.LocalThemeConfig
import com.example.lostandfound.ui.theme.LostandfoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDark by remember { mutableStateOf(false) } // Default to Light for now
            // Dynamic color removed as per request

            val themeConfig = remember(isDark) {
                ThemeConfig(
                    isDark = isDark,
                    toggleDark = { isDark = !isDark }
                )
            }

            CompositionLocalProvider(LocalThemeConfig provides themeConfig) {
                LostandfoundTheme(darkTheme = isDark) {
                    LostAndFoundApp()
                }
            }
        }
    }
}

// --- NAVIGATION CONTROLLER ---
@Composable
fun LostAndFoundApp() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    val startDestination = if (auth.currentUser != null) "home" else "login"

    LaunchedEffect(Unit) {
        AuthManager.fetchAdminUids()
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("report") { ReportItemScreen(navController) }
        composable("lost") { LostItemsScreen(navController) }
        composable("report_lost") { ReportLostItemScreen(navController) }
        composable("my_items") { MyItemsScreen(navController) }
        composable(
            route = "item_detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            if (itemId != null) {
                ItemDetailScreen(navController = navController, itemId = itemId)
            }
        }
        composable(
            route = "found_item_detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            if (itemId != null) {
                FoundItemDetailScreen(navController = navController, itemId = itemId)
            }
        }
        composable("conversations") {
            ConversationListScreen(navController = navController)
        }
        composable(
            route = "chat/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: "User"
            ChatScreen(navController = navController, receiverId = userId, receiverName = userName)
        }
        composable("admin_claims") {
            AdminClaimsScreen(navController = navController)
        }
        composable("my_matches") {
            MyMatchesScreen(navController = navController)
        }
    }
}
