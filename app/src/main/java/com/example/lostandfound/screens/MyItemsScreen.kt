package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyItemsScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val isAdmin = AuthManager.isCurrentUserAdmin()
    val currentUserId = AuthManager.getCurrentUserId()
    val context = LocalContext.current

    var allItems by remember { mutableStateOf<List<LostItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(0) }
    var filterDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFoundConfirm by remember { mutableStateOf(false) }
    var itemToMarkFound by remember { mutableStateOf<LostItem?>(null) }
    

    DisposableEffect(isAdmin, currentUserId) {
        var q: Query = db.collection("lost_items")
        if (!isAdmin) currentUserId?.let { q = q.whereEqualTo("userId", it) }
        val listener = q.addSnapshotListener { result, _ ->
            if (result != null) {
                allItems = result.documents.mapNotNull { doc ->
                    doc.toObject(LostItem::class.java)?.copy(id = doc.id)
                }.sortedWith(compareByDescending<LostItem> { it.createdAt?.time ?: 0L }.thenByDescending { it.dateLost.time })
            }
            isLoading = false
        }
        onDispose { listener.remove() }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val statuses = listOf("ALL", "SEARCHING", "IN PROGRESS", "RESOLVED")
    val descriptions = listOf(
        "All your reported lost items.",
        "Active search reports with no matching found item yet.",
        "Reports linked to a found item with a claim in progress.",
        "Completed reports where the item was found."
    )

    val filteredItems = remember(allItems, searchQuery, filterDateMillis, selectedTabIndex) {
        var list = if (searchQuery.isBlank()) allItems
        else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) || it.location.contains(searchQuery, ignoreCase = true) }
        
        if (filterDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val filterStr = sdf.format(java.util.Date(filterDateMillis!!))
            list = list.filter { sdf.format(it.dateLost) == filterStr }
        }

        // Tab Filtering
        val selectedStatus = statuses[selectedTabIndex]
        if (selectedStatus != "ALL") {
            list = list.filter { 
                when(selectedStatus) {
                    "SEARCHING" -> it.status != ClaimStatus.FOUND && 
                                  it.status != ClaimStatus.RETURNED &&
                                  it.status != ClaimStatus.CLAIM_PENDING && 
                                  it.status != ClaimStatus.DISPUTED && 
                                  it.status != ClaimStatus.APPROVED && 
                                  it.status != ClaimStatus.REJECTED
                    "IN PROGRESS" -> it.status == ClaimStatus.CLAIM_PENDING || 
                                     it.status == ClaimStatus.DISPUTED || 
                                     it.status == ClaimStatus.APPROVED || 
                                     it.status == ClaimStatus.REJECTED
                    "RESOLVED" -> it.status == ClaimStatus.FOUND || it.status == ClaimStatus.RETURNED
                    else -> true
                }
            }
        }
        list
    }
    LaunchedEffect(searchQuery, filterDateMillis) { currentPage = 0 }

    val totalPages = maxOf(1, (filteredItems.size + 9) / 10)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val pageItems = filteredItems.drop(safePage * 10).take(10)
    val listState = rememberLazyListState()
    LaunchedEffect(safePage) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isAdmin) "LOST ITEMS DATABASE" else "MY REPORTED ITEMS",
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White
                        )
                        Text(
                            if (isAdmin) "Official Station Records" else "Your Lost Item Reports",
                            fontSize = 11.sp, color = CityTheme.GoldLight
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CityTheme.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name or location…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = CityTheme.Green) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Filter by date",
                            tint = if (filterDateMillis != null) CityTheme.Gold else CityTheme.Green
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CityTheme.Green,
                    unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.25f),
                    focusedLabelColor = CityTheme.Green,
                    cursorColor = CityTheme.Green
                )
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                containerColor = CityTheme.Cream,
                contentColor = CityTheme.Green,
                edgePadding = 0.dp,
                divider = {}
            ) {
                statuses.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) CityTheme.Green else CityTheme.Brown.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            Text(
                descriptions[selectedTabIndex],
                fontSize = 11.sp,
                color = CityTheme.Brown.copy(alpha = 0.6f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = filterDateMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            filterDateMillis = datePickerState.selectedDateMillis
                            showDatePicker = false
                        }) { Text("OK", color = CityTheme.Green) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            filterDateMillis = null
                            showDatePicker = false
                        }) { Text("Clear", color = CityTheme.Brown) }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (!isLoading) {
                val start = if (filteredItems.isEmpty()) 0 else safePage * 10 + 1
                val end = minOf((safePage + 1) * 10, filteredItems.size)
                Text(
                    "Showing $start–$end of ${filteredItems.size} results",
                    fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when {
                isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = CityTheme.Green)
                }
                filteredItems.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, Modifier.size(56.dp), tint = CityTheme.Green.copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isBlank()) "No lost items reported yet." else "No results for \"$searchQuery\".",
                            color = CityTheme.Brown.copy(alpha = 0.5f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pageItems) { item ->
                            LostItemCard(
                                item = item, 
                                navController = navController, 
                                isAdmin = isAdmin,
                                onFound = {
                                    itemToMarkFound = item
                                    showFoundConfirm = true
                                }
                            )
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
        
        if (showFoundConfirm && itemToMarkFound != null) {
            AlertDialog(
                onDismissRequest = { showFoundConfirm = false },
                shape = RoundedCornerShape(16.dp),
                title = { Text("Mark as Resolved?", fontWeight = FontWeight.Bold, color = CityTheme.Brown) },
                text = { Text("Are you sure? This will mark the '${itemToMarkFound!!.name}' report as RESOLVED. This action cannot be undone.", color = CityTheme.Brown.copy(0.7f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            db.collection("lost_items").document(itemToMarkFound!!.id).update(
                                mapOf(
                                    "status" to ClaimStatus.FOUND,
                                    "claimedFoundItemId" to "" // Clear link if manually resolved
                                )
                            ).addOnSuccessListener {
                                // Archive any active claims related to this manually resolved item
                                db.collection("claims")
                                    .whereEqualTo("lostItemId", itemToMarkFound!!.id)
                                    .whereIn("status", listOf(ClaimStatus.PENDING, ClaimStatus.APPROVED, ClaimStatus.DISPUTED))
                                    .get()
                                    .addOnSuccessListener { snap ->
                                        val batch = db.batch()
                                        snap.documents.forEach { doc ->
                                            batch.update(doc.reference, "status", "ARCHIVED_MANUAL")
                                        }
                                        if (!snap.isEmpty) batch.commit()
                                    }

                                Toast.makeText(context, "Marked as Resolved! Active claims archived.", Toast.LENGTH_SHORT).show()
                                // Update local state to reflect change without removing from list
                                allItems = allItems.map { if (it.id == itemToMarkFound!!.id) it.copy(status = ClaimStatus.FOUND, claimedFoundItemId = "") else it }
                            }
                            showFoundConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                    ) { Text("Yes, Mark Resolved") }
                },
                dismissButton = {
                    TextButton(onClick = { showFoundConfirm = false }) {
                        Text("Cancel", color = CityTheme.Brown)
                    }
                }
            )
        }
    }
}

@Composable
fun LostItemCard(item: LostItem, navController: NavController, isAdmin: Boolean, onFound: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp))
            .clickable { navController.navigate("item_detail/${item.id}") },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail or placeholder
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
                        .background(CityTheme.Green.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", fontSize = 28.sp, color = CityTheme.Green.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CityTheme.Brown)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(13.dp), tint = CityTheme.Gold)
                    Spacer(Modifier.width(3.dp))
                    Text(item.location, fontSize = 12.sp, color = CityTheme.Brown.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(4.dp))
                Text("Lost: ${dateFormat.format(item.dateLost)}", fontSize = 11.sp, color = CityTheme.Brown.copy(alpha = 0.4f))

                if (isAdmin) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = CityTheme.Brown.copy(0.08f))
                    Spacer(Modifier.height(6.dp))
                    Text(item.description, fontSize = 12.sp, maxLines = 2, color = CityTheme.Brown.copy(0.7f))
                    Text("By: ${item.email}", fontSize = 10.sp, color = CityTheme.Brown.copy(0.4f))
                }

                if (item.status != ClaimStatus.APPROVED &&
                    item.status != ClaimStatus.REJECTED &&
                    item.status != ClaimStatus.FOUND &&
                    item.status != ClaimStatus.RETURNED &&
                    item.status != ClaimStatus.CLAIM_PENDING &&
                    item.status != ClaimStatus.DISPUTED
                ) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onFound,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                    ) { Text("Mark as Resolved", fontSize = 13.sp) }
                } else {
                    Spacer(Modifier.height(8.dp))
                    val (statusLabel, statusColor) = when (item.status) {
                        ClaimStatus.APPROVED      -> "APPROVED (Pick up at Station)" to CityTheme.Green
                        ClaimStatus.REJECTED      -> "REJECTED (Tap to Dispute)" to CityTheme.Error
                        ClaimStatus.DISPUTED      -> "DISPUTED (Reviewing Appeal)" to CityTheme.Gold
                        ClaimStatus.FOUND         -> "RESOLVED (FOUND PERSONALLY)" to CityTheme.Green
                        ClaimStatus.RETURNED      -> "RESOLVED (RETURNED BY STATION)" to CityTheme.Green
                        ClaimStatus.CLAIM_PENDING -> "CLAIM SUBMITTED (Reviewing)" to CityTheme.Gold
                        else                      -> "STATUS: ${item.status}" to CityTheme.Gold
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (isAdmin) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = CityTheme.Gold)
            }
        }
    }
}
