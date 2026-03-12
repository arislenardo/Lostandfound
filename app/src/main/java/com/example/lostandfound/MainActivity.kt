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

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.example.lostandfound.ui.theme.CityTheme
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

    var showResidencyPrompt by rememberSaveable { mutableStateOf(true) }
    var showNonResidentNotice by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AuthManager.fetchAdminUids()
    }

    if (showResidencyPrompt) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal by tapping outside */ },
            shape = RoundedCornerShape(20.dp),
            containerColor = CityTheme.Cream,
            title = {
                Text(
                    "Welcome to Lost & Found",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = CityTheme.Green,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Are you a resident of Calasiao?",
                        fontSize = 16.sp,
                        color = CityTheme.Brown,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Only residents of Calasiao can use the platform directly for found items.",
                        fontSize = 12.sp,
                        color = CityTheme.Brown.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showResidencyPrompt = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Yes, I am")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showResidencyPrompt = false
                        showNonResidentNotice = true 
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text("No, I'm not", color = CityTheme.Error)
                }
            }
        )
    }

    if (showNonResidentNotice) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal */ },
            shape = RoundedCornerShape(20.dp),
            containerColor = CityTheme.White,
            title = {
                Text(
                    "Notice for Non-Residents",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = CityTheme.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "Non-Calasiao residents must immediately surrender any found items directly to the Calasiao Police Station (PNP). You cannot use the app to hold or report items.",
                    fontSize = 14.sp,
                    color = CityTheme.Brown,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        (context as? Activity)?.finishAffinity() // Exit App
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Exit App")
                }
            }
        )
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
            route = "found_item_detail/{itemId}?lostItemId={lostItemId}",
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("lostItemId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            val lostItemId = backStackEntry.arguments?.getString("lostItemId")
            if (itemId != null) {
                FoundItemDetailScreen(navController = navController, itemId = itemId, lostItemId = lostItemId)
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
        composable("notification_inbox") {
            NotificationInboxScreen(navController = navController)
        }
        composable("my_matches") {
            MyMatchesScreen(navController = navController)
        }
        composable("browse_by_category") {
            BrowseByCategoryScreen(navController = navController)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
        composable("history") {
            HistoryScreen(navController = navController)
        }
    }
}
