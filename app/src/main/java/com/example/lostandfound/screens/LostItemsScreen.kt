package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.ItemStatus
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private const val FOUND_PAGE_SIZE = 10

/**
 * Displays a searchable and filterable database of found items.
 * Admins can see all items, while residents see only "AVAILABLE" items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostItemsScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val isAdmin = AuthManager.isCurrentUserAdmin()

    var allItems by remember { mutableStateOf<List<FoundItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(0) }
    var filterDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(isAdmin) {
        var query: Query = db.collection("found_items")
        
        // Only filter for "FOUND" if user is NOT admin. Admins see everything.
        if (!isAdmin) {
            query = query.whereEqualTo("status", ItemStatus.FOUND)
        }
    
        query.limit(500).get()
            .addOnSuccessListener { result ->
                allItems = result.documents.mapNotNull { doc -> 
                    doc.toObject(FoundItem::class.java)?.copy(id = doc.id) 
                }.sortedWith(compareByDescending<FoundItem> { it.createdAt?.time ?: 0L }.thenByDescending { it.dateFound.time })
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val statuses = listOf("ALL", "AVAILABLE", "PENDING", "RETURNED")
    val descriptions = listOf(
        "Complete inventory of all found items.",
        "Items currently at the station waiting for a claim.",
        "Items with ongoing claims or verification in progress.",
        "Record of items successfully returned to their owners."
    )

    val filteredItems = remember(allItems, searchQuery, filterDateMillis, selectedTabIndex) {
        var list = if (searchQuery.isBlank()) allItems
        else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) || it.location.contains(searchQuery, ignoreCase = true) }
        
        if (filterDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val filterStr = sdf.format(java.util.Date(filterDateMillis!!))
            list = list.filter { sdf.format(it.dateFound) == filterStr }
        }

        // Tab Filtering
        val selectedStatus = statuses[selectedTabIndex]
        if (selectedStatus != "ALL") {
            list = list.filter { 
                when(selectedStatus) {
                    "AVAILABLE" -> it.status == ItemStatus.FOUND
                    "PENDING"   -> it.status == ItemStatus.CLAIMED
                    "RETURNED"  -> it.status == ItemStatus.RETURNED
                    else -> true
                }
            }
        }
        list
    }
    LaunchedEffect(searchQuery, filterDateMillis) { currentPage = 0 }

    val totalPages = maxOf(1, (filteredItems.size + FOUND_PAGE_SIZE - 1) / FOUND_PAGE_SIZE)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val pageItems = filteredItems.drop(safePage * FOUND_PAGE_SIZE).take(FOUND_PAGE_SIZE)
    val listState = rememberLazyListState()
    LaunchedEffect(safePage) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FOUND ITEMS DATABASE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Official Station Records", fontSize = 11.sp, color = CityTheme.GoldLight)
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
                val start = if (filteredItems.isEmpty()) 0 else safePage * FOUND_PAGE_SIZE + 1
                val end = minOf((safePage + 1) * FOUND_PAGE_SIZE, filteredItems.size)
                Text("Showing $start–$end of ${filteredItems.size} results", fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f), modifier = Modifier.padding(bottom = 8.dp))
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
                            if (searchQuery.isBlank()) "No found items recorded." else "No results for \"$searchQuery\".",
                            color = CityTheme.Brown.copy(0.5f)
                        )
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pageItems) { item ->
                            FoundItemCard(item = item, navController = navController, isAdmin = isAdmin)
                        }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
    }
}

/**
 * A reusable pagination component for navigating through lists of items.
 */
@Composable
fun PaginationBar(currentPage: Int, totalPages: Int, onPageSelected: (Int) -> Unit) {
    if (totalPages <= 1) return
    val pages = buildList {
        add(0)
        if (currentPage - 2 > 1) add(-1)
        for (p in (currentPage - 1)..(currentPage + 1)) { if (p in 1 until totalPages - 1) add(p) }
        if (currentPage + 2 < totalPages - 2) add(-1)
        if (totalPages > 1) add(totalPages - 1)
    }.distinct()

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (currentPage > 0) onPageSelected(currentPage - 1) }, enabled = currentPage > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", tint = if (currentPage > 0) CityTheme.Green else CityTheme.Brown.copy(0.3f))
        }
        for (page in pages) {
            if (page == -1) {
                Text("…", modifier = Modifier.padding(horizontal = 4.dp), color = CityTheme.Brown.copy(0.4f))
            } else {
                val isSelected = page == currentPage
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(CityTheme.Green),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${page + 1}", fontWeight = FontWeight.Bold, color = CityTheme.White, fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = { onPageSelected(page) }, modifier = Modifier.size(34.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("${page + 1}", fontSize = 13.sp, color = CityTheme.Brown.copy(0.6f))
                    }
                }
            }
        }
        IconButton(onClick = { if (currentPage < totalPages - 1) onPageSelected(currentPage + 1) }, enabled = currentPage < totalPages - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = if (currentPage < totalPages - 1) CityTheme.Green else CityTheme.Brown.copy(0.3f))
        }
    }
}

/**
 * A card representing a single found item in the list, showing its thumbnail, name, location, and status.
 */
@Composable
fun FoundItemCard(item: FoundItem, navController: NavController, isAdmin: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp))
            .clickable(enabled = isAdmin) { navController.navigate("found_item_detail/${item.id}") },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl, contentDescription = "Thumbnail",
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)).background(CityTheme.Gold.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", fontSize = 28.sp, color = CityTheme.Gold.copy(0.5f))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CityTheme.Brown)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(13.dp), tint = CityTheme.Gold)
                    Spacer(Modifier.width(3.dp))
                    Text(item.location, fontSize = 12.sp, color = CityTheme.Brown.copy(0.6f))
                }
                Spacer(Modifier.height(4.dp))
                Text("Found: ${item.dateFoundText.ifBlank { "Unknown Date" }}", fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f))
                if (isAdmin) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = CityTheme.Brown.copy(0.08f))
                    Spacer(Modifier.height(6.dp))
                    Text(item.description, fontSize = 12.sp, maxLines = 2, color = CityTheme.Brown.copy(0.7f))
                    Text("By: ${item.email}", fontSize = 10.sp, color = CityTheme.Brown.copy(0.4f))
                }
            }
            if (isAdmin) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = CityTheme.Gold)
            }
        }
    }
}
