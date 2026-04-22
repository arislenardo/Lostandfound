package com.example.lostandfound.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.viewmodel.HomeViewModel

/**
 * The primary dashboard screen of the application.
 * Displays user profile info, notification badges, and quick-action cards tailored
 * to whether the current user is a resident or an administrator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.uiState

    val firstName = state.firstName
    val isAdmin = state.isAdmin
    val notifCount = state.notifCount
    val unreadMessages = state.unreadMessages
    val unreadMatches = state.unreadMatches
    val pendingClaims = state.pendingClaims
    val unreadClaimUpdates = state.unreadClaimUpdates
    val profileImageUrl = state.profileImageUrl

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Log Out", fontWeight = FontWeight.Bold, color = CityTheme.Brown) },
            text = { Text("Are you sure you want to log out?", color = CityTheme.Brown.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    val user = auth.currentUser
                    if (user != null) {
                        FirebaseFirestore.getInstance().collection("users").document(user.uid)
                            .update("fcmToken", "")
                            .addOnCompleteListener {
                                auth.signOut()
                                navController.navigate("login") { 
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                    } else {
                        auth.signOut()
                        navController.navigate("login") { 
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }) { Text("Log Out", color = CityTheme.Error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = CityTheme.Green)
                }
            }
        )
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            Surface(
                shadowElevation = 8.dp,
                color = CityTheme.Green
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "BALIK-CALASIAO",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                letterSpacing = 1.5.sp,
                                color = CityTheme.White
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .height(2.dp)
                                    .width(40.dp)
                                    .background(CityTheme.GoldLight, RoundedCornerShape(1.dp))
                            )
                        }
                    },
                    actions = {
                        // Notification bell with badge
                        BadgedBox(
                            badge = {
                                if (notifCount > 0) {
                                    Badge(
                                        containerColor = CityTheme.Gold,
                                        contentColor = CityTheme.White,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    ) {
                                        Text(
                                            if (notifCount > 9) "9+" else "$notifCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                        ) {
                            IconButton(onClick = { navController.navigate("notification_inbox") }) {
                                Icon(Icons.Default.Notifications, null, tint = CityTheme.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        bottomBar = {
            AppBottomNavigation(
                navController = navController,
                currentRoute = "home",
                isAdmin = isAdmin
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Welcome banner ───────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(CityTheme.Green, CityTheme.GreenLight)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = CityTheme.White.copy(alpha = 0.15f),
                            border = BorderStroke(2.dp, Brush.linearGradient(listOf(CityTheme.GoldLight, CityTheme.Gold)))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (profileImageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(profileImageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, tint = CityTheme.White, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "MABUHAY,",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = CityTheme.GoldLight.copy(alpha = 0.9f)
                            )
                            Text(
                                "$firstName",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = CityTheme.White
                            )
                            Surface(
                                color = CityTheme.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    state.role.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = CityTheme.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Logout link
                        IconButton(
                            onClick = { showLogoutDialog = true }, 
                            modifier = Modifier
                                .size(40.dp)
                                .background(CityTheme.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, "Log Out", tint = CityTheme.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Section label ────────────────────────────────────────────────
            Text(
                "QUICK ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CityTheme.Brown.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            // ── Row 1 ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeDashboardCard(
                    title = "I Lost An Item",
                    subtitle = "Report a lost item",
                    icon = Icons.Default.Add,
                    iconBackground = CityTheme.GreenLight,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("report_lost") }
                )
                HomeDashboardCard(
                    title = "I Found An Item",
                    subtitle = "Submit a found item",
                    icon = Icons.Default.Edit,
                    iconBackground = CityTheme.GoldLight,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("report") }
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Row 2 ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeDashboardCard(
                    title = if (isAdmin) "View All Lost Items" else "My Reports",
                    subtitle = if (isAdmin) "All lost records" else "Your lost reports",
                    icon = Icons.AutoMirrored.Filled.List,
                    iconBackground = CityTheme.Brown,
                    badgeCount = if (isAdmin) 0 else unreadClaimUpdates,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("my_items") }
                )
                HomeDashboardCard(
                    title = "Messages",
                    subtitle = "Official communications",
                    icon = Icons.AutoMirrored.Filled.Send,
                    iconBackground = CityTheme.GreenLight,
                    badgeCount = unreadMessages,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("conversations") }
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Potential Matches (non-admin only) ───────────────────────────
            if (!isAdmin) {
                val hasUnread = unreadMatches > 0
                Card(
                    onClick = { navController.navigate("my_matches") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CityTheme.Green.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Search, null,
                                    tint = CityTheme.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Potential Matches", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = CityTheme.Brown)
                                Text("Items matching your lost reports", fontSize = 12.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                            }
                        }
                        if (hasUnread) {
                            Badge(containerColor = CityTheme.Gold) {
                                Text(if (unreadMatches > 9) "9+" else "$unreadMatches", color = CityTheme.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Admin row ────────────────────────────────────────────────────
            if (isAdmin) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeDashboardCard(
                        title = "View All Found Items",
                        subtitle = "All found records",
                        icon = Icons.Default.Search,
                        iconBackground = CityTheme.GoldLight,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("lost") }
                    )
                    // Check Claims card
                    HomeDashboardCard(
                        title = "Review Claims",
                        subtitle = "Pending approvals",
                        icon = Icons.Default.Person,
                        iconBackground = CityTheme.Brown,
                        badgeCount = pendingClaims,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("admin_claims") }
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Browse by Category — full-width card
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    onClick = { navController.navigate("browse_by_category") }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CityTheme.Brown),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Widgets, null, tint = CityTheme.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Browse by Category", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CityTheme.Brown)
                            Text("Filter all lost & found items by type", fontSize = 12.sp, color = CityTheme.Brown.copy(0.5f))
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = CityTheme.Brown.copy(0.3f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A reusable card component used on the Home screen to represent quick actions
 * (e.g., Report Lost Item, View Messages), optionally displaying a notification badge.
 */
@Composable
fun HomeDashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBackground: Color,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = CityTheme.White, modifier = Modifier.size(24.dp))
                }
                if (badgeCount > 0) {
                    Badge(containerColor = CityTheme.Gold) {
                        Text(if (badgeCount > 9) "9+" else "$badgeCount", color = CityTheme.White, fontSize = 11.sp)
                    }
                }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CityTheme.Brown, maxLines = 2)
                Text(subtitle, fontSize = 11.sp, color = CityTheme.Brown.copy(alpha = 0.5f), maxLines = 1)
            }
        }
    }
}