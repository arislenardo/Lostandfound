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
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.android.gms.tasks.Task
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.lostandfound.ui.theme.LostandfoundTheme

/**
 * The main entry point of the application.
 * Handles the initialization of Google Places API, checks for push notification permissions,
 * and sets up the primary Jetpack Compose navigation graph.
 */
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

        // Install App Check debug provider for local debug builds to bypass Play Integrity.
        // This is automatically excluded from release builds by debugImplementation in build.gradle.kts.
        if (BuildConfig.DEBUG) {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        }
        
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
/**
 * Sets up the NavHost and defines the navigation routes for the entire application.
 * Also synchronizes the user's FCM token upon successful login.
 */
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


    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)) }
    ) {
        composable("login") { PreventClickThrough { LoginScreen(navController) } }
        composable("home") { PreventClickThrough { HomeScreen(navController) } }
        composable("report") { PreventClickThrough { ReportItemScreen(navController) } }
        composable("lost") { PreventClickThrough { LostItemsScreen(navController) } }
        composable("report_lost") { PreventClickThrough { ReportLostItemScreen(navController) } }
        composable("my_items") { PreventClickThrough { MyItemsScreen(navController) } }
        composable(
            route = "item_detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            if (itemId != null) {
                PreventClickThrough { ItemDetailScreen(navController = navController, itemId = itemId) }
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
                PreventClickThrough { FoundItemDetailScreen(navController = navController, itemId = itemId, lostItemId = lostItemId) }
            }
        }
        composable("conversations") {
            PreventClickThrough { ConversationListScreen(navController = navController) }
        }
        composable(
            route = "chat/{userId}/{userName}?email={email}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType },
                navArgument("email") { type = NavType.StringType; defaultValue = ""; nullable = true }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: "User"
            val email = backStackEntry.arguments?.getString("email") ?: ""
            PreventClickThrough { ChatScreen(navController = navController, receiverId = userId, receiverName = userName, initialEmail = email) }
        }
        composable("admin_claims") {
            PreventClickThrough { AdminClaimsScreen(navController = navController) }
        }
        composable("notification_inbox") {
            PreventClickThrough { NotificationInboxScreen(navController = navController) }
        }
        composable("my_matches") {
            PreventClickThrough { MyMatchesScreen(navController = navController) }
        }
        composable("browse_by_category") {
            PreventClickThrough { BrowseByCategoryScreen(navController = navController) }
        }
        composable("profile") {
            PreventClickThrough { ProfileScreen(navController = navController) }
        }
        composable("history") {
            PreventClickThrough { HistoryScreen(navController = navController) }
        }
    }
}

/**
 * Wraps a screen to prevent click-through issues during Compose navigation transitions.
 * By tracking the Lifecycle state, we disable interactions on screens that are animating out
 * or animating in (not RESUMED), completely preventing accidental taps on background screens.
 */
@Composable
fun PreventClickThrough(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isResumed) {
                if (!isResumed) {
                    // When animating out or not fully focused, consume ALL touch events immediately
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else {
                    // When fully resumed, just consume empty taps to prevent them falling through
                    detectTapGestures {}
                }
            }
    ) {
        content()
    }
}
