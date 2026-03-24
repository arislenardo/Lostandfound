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
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.android.gms.tasks.Task
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import com.example.lostandfound.ui.theme.LostandfoundTheme

class MainActivity : ComponentActivity() {
    
    // Permission launcher for Android 13+ Push Notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM", "Notification permission granted")
        } else {
            Log.w("FCM", "Notification permission completely denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (apiKey != null && !com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initialize(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        askNotificationPermission()

        setContent {
            LostandfoundTheme {
                LostAndFoundApp()
            }
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    LaunchedEffect(auth.currentUser) {
        AuthManager.fetchAdminUids()
        
        // Sync FCM Token
        auth.currentUser?.let { user ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
                if (task.isSuccessful) {
                    val token = task.result
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users").document(user.uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener { _ -> Log.d("FCM", "Token synced for user ${user.uid}") }
                        .addOnFailureListener { e: Exception -> Log.w("FCM", "Failed to sync token", e) }
                }
            }
        }
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
