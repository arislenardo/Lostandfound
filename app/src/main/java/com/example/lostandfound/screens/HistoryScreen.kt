package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.model.AdminAction
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

/**
 * Displays an audit log of administrative actions.
 * Fetches and displays a paginated list of `AdminAction` records from Firestore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    var historyLogs by remember { mutableStateOf<List<AdminAction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        db.collection("admin_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                historyLogs = snapshot.documents.mapNotNull { it.toObject(AdminAction::class.java)?.copy(id = it.id) }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ADMIN HISTORY",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = CityTheme.White
                        )
                        Text(
                            "Audit Logs",
                            fontSize = 11.sp,
                            color = CityTheme.GoldLight
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        },
        bottomBar = {
            AppBottomNavigation(
                navController = navController,
                currentRoute = "history",
                isAdmin = true
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
            historyLogs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = CityTheme.Green.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No Activity Yet", fontWeight = FontWeight.Bold, color = CityTheme.Brown)
                    Text("Admin actions will appear here.", fontSize = 13.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
            }
            else -> {
                val totalPages = maxOf(1, (historyLogs.size + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                val pageItems = historyLogs.drop(safePage * 10).take(10)
                val listState = rememberLazyListState()
                
                LaunchedEffect(safePage) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageItems) { log ->
                            AdminActionCard(log)
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
    }
}

/**
 * Renders a single history log entry card displaying the action type, item title, admin name, and timestamp.
 */
@Composable
fun AdminActionCard(action: AdminAction) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val dateStr = sdf.format(action.timestamp)

    val (actionLabel, color) = when (action.actionType) {
        "APPROVED_CLAIM" -> "Approved Claim" to CityTheme.Green
        "REJECTED_CLAIM" -> "Rejected Claim" to CityTheme.Error
        "DELETED_FOUND_ITEM" -> "Deleted Found Item" to CityTheme.Error
        "DELETED_LOST_ITEM" -> "Deleted Lost Item" to CityTheme.Error
        "ADDED_FOUND_ITEM" -> "Added Found Item" to CityTheme.Green
        "ADDED_LOST_ITEM" -> "Added Lost Item" to CityTheme.Green
        "RETURNED_ITEM" -> "Item Marked as Returned" to CityTheme.Green
        else -> action.actionType to CityTheme.Gold
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(actionLabel, fontWeight = FontWeight.ExtraBold, color = color, fontSize = 14.sp)
                    Text(dateStr, fontSize = 10.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Item: ${action.itemTitle}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CityTheme.Brown
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Action by: ${action.adminName}",
                    fontSize = 12.sp,
                    color = CityTheme.Brown.copy(alpha = 0.7f)
                )
                Text(
                    "Item ID: ${action.itemId}",
                    fontSize = 10.sp,
                    color = CityTheme.Brown.copy(alpha = 0.4f)
                )
            }
        }
    }
}
