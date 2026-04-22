package com.example.lostandfound.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DateRange
// Explicitly using standard icons to avoid unresolved references if library sync is pending
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.ui.theme.CityTheme
import com.example.lostandfound.ui.theme.CustomIcons
import com.google.firebase.firestore.FirebaseFirestore

// Category metadata
private data class CategoryMeta(val name: String, val icon: ImageVector, val color: androidx.compose.ui.graphics.Color)

private val CATEGORIES = listOf(
    CategoryMeta("Backpacks / Bags",      Icons.Default.Backpack,            CityTheme.Brown),
    CategoryMeta("Books / Notebooks",     Icons.AutoMirrored.Filled.MenuBook,            CityTheme.Brown),
    CategoryMeta("Card",                  Icons.Default.CreditCard,          CityTheme.Brown),
    CategoryMeta("Chargers / Cables",     Icons.Default.Usb,                 CityTheme.Brown),
    CategoryMeta("Clothing",              Icons.Default.Checkroom,           CityTheme.Brown),
    CategoryMeta("Folder / Envelopes",    Icons.Default.Folder,              CityTheme.Brown),
    CategoryMeta("Glasses / Sunglasses",  CustomIcons.Glasses,               CityTheme.Brown),
    CategoryMeta("Hats",                  CustomIcons.Hat,                   CityTheme.Brown),
    CategoryMeta("Headphones / Earbuds",  Icons.Default.Headphones,          CityTheme.Brown),
    CategoryMeta("Keys",                  Icons.Default.Key,                 CityTheme.Brown),
    CategoryMeta("Laptops",               Icons.Default.Laptop,              CityTheme.Brown),
    CategoryMeta("Phone / Tablet",        Icons.Default.PhoneAndroid,        CityTheme.Brown),
    CategoryMeta("Umbrellas",             Icons.Default.BeachAccess,         CityTheme.Brown),
    CategoryMeta("Wallet",                Icons.Default.AccountBalanceWallet, CityTheme.Brown),
    CategoryMeta("Watch",                 Icons.Default.Watch,               CityTheme.Brown),
    CategoryMeta("Water Bottles",         Icons.Default.LocalDrink,          CityTheme.Brown),
    CategoryMeta("Others",                Icons.Default.Widgets,             CityTheme.Brown),
)

/**
 * Displays a grid of item categories. When a category is selected, it shows a tabbed list
 * of Found and Lost items within that specific category, allowing users to filter by date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseByCategoryScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()

    // Counts per category (lost + found combined)
    var categoryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val counts = mutableMapOf<String, Int>()
        db.collection("lost_items").get().addOnSuccessListener { snap ->
            for (doc in snap.documents) {
                val cat = doc.getString("category") ?: "Others"
                counts[cat] = (counts[cat] ?: 0) + 1
            }
            // Also fetch found items
            db.collection("found_items").get().addOnSuccessListener { snap2 ->
                for (doc in snap2.documents) {
                    val cat = doc.getString("category") ?: "Others"
                    counts[cat] = (counts[cat] ?: 0) + 1
                }
                categoryCounts = counts
            }
        }
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedCategory ?: "Browse by Category",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = CityTheme.White
                        )
                        Text(
                            if (selectedCategory == null) "All item categories" else "Lost & Found records",
                            fontSize = 11.sp,
                            color = CityTheme.GoldLight
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCategory != null) {
                            selectedCategory = null
                        } else if (navController.previousBackStackEntry != null &&
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CityTheme.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { padding ->
        if (selectedCategory == null) {
            // ── Category grid ─────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CATEGORIES) { meta ->
                    val count = categoryCounts[meta.name] ?: 0
                    CategoryGridCard(
                        meta = meta,
                        count = count,
                        onClick = { selectedCategory = meta.name }
                    )
                }
            }
        } else {
            // ── Items for selected category ───────────────────────────────────
            CategoryItemsList(
                db = db,
                category = selectedCategory!!,
                navController = navController,
                paddingValues = padding
            )
        }
    }
}

/**
 * Renders a single category card in the category grid, showing its icon, name, and total item count.
 */
@Composable
private fun CategoryGridCard(meta: CategoryMeta, count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(meta.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(meta.icon, null, tint = meta.color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    meta.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = CityTheme.Brown,
                    maxLines = 2,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (count > 0) "$count item${if (count != 1) "s" else ""}" else "No items",
                    fontSize = 11.sp,
                    color = if (count > 0) meta.color else CityTheme.Brown.copy(alpha = 0.3f),
                    fontWeight = if (count > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Displays the tabbed view (Found vs Lost) and the list of items for the selected category.
 * Includes a date picker filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryItemsList(
    db: FirebaseFirestore,
    category: String,
    navController: NavController,
    paddingValues: PaddingValues
) {
    var lostItems by remember { mutableStateOf<List<LostItem>>(emptyList()) }
    var foundItems by remember { mutableStateOf<List<FoundItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf(0) } // 0=Found, 1=Lost
    var filterDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }

    LaunchedEffect(category, activeTab, filterDateMillis) { currentPage = 0 }

    val filteredFoundItems = remember(foundItems, filterDateMillis) {
        if (filterDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val filterStr = sdf.format(java.util.Date(filterDateMillis!!))
            foundItems.filter { sdf.format(it.dateFound) == filterStr }
        } else foundItems
    }
    
    val filteredLostItems = remember(lostItems, filterDateMillis) {
        if (filterDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val filterStr = sdf.format(java.util.Date(filterDateMillis!!))
            lostItems.filter { sdf.format(it.dateLost) == filterStr }
        } else lostItems
    }

    LaunchedEffect(category) {
        isLoading = true
        db.collection("lost_items").whereEqualTo("category", category).get()
            .addOnSuccessListener { snap ->
                lostItems = snap.documents.mapNotNull { it.toObject(LostItem::class.java)?.copy(id = it.id) }
                isLoading = false
            }
        db.collection("found_items").whereEqualTo("category", category).get()
            .addOnSuccessListener { snap ->
                foundItems = snap.documents.mapNotNull { it.toObject(FoundItem::class.java)?.copy(id = it.id) }
                isLoading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Tab row
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = CityTheme.White,
            contentColor = CityTheme.Green,
            indicator = { tabPositions ->
                if (activeTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        height = 3.dp,
                        color = CityTheme.Green
                    )
                }
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Found (${filteredFoundItems.size})", fontWeight = FontWeight.SemiBold) },
                selectedContentColor = CityTheme.Green,
                unselectedContentColor = CityTheme.Brown.copy(0.5f)
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Lost (${filteredLostItems.size})", fontWeight = FontWeight.SemiBold) },
                selectedContentColor = CityTheme.Green,
                unselectedContentColor = CityTheme.Brown.copy(0.5f)
            )
        }

        // Date Filter Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateStr = if (filterDateMillis != null) {
                java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(filterDateMillis!!))
            } else "All Dates"
            
            Text(
                text = "Filter: $dateStr",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CityTheme.Brown.copy(0.7f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showDatePicker = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Filter by date",
                    tint = if (filterDateMillis != null) CityTheme.Gold else CityTheme.Green,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

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

        when {
            isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
            activeTab == 0 && filteredFoundItems.isEmpty() -> EmptyCategoryPlaceholder(if (filterDateMillis != null) "No found items on selected date" else "No found items in this category")
            activeTab == 1 && filteredLostItems.isEmpty()  -> EmptyCategoryPlaceholder(if (filterDateMillis != null) "No lost items on selected date" else "No lost items in this category")
            else -> {
                val currentItemsSize = if (activeTab == 0) filteredFoundItems.size else filteredLostItems.size
                val totalPages = maxOf(1, (currentItemsSize + 9) / 10)
                val safePage = currentPage.coerceIn(0, totalPages - 1)
                
                val listState = rememberLazyListState()
                
                LaunchedEffect(safePage, activeTab) { listState.scrollToItem(0) }

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeTab == 0) {
                            val pageFoundItems = filteredFoundItems.drop(safePage * 10).take(10)
                            items(pageFoundItems) { item ->
                                FoundItemCard(item = item, navController = navController, isAdmin = true)
                            }
                        } else {
                            val pageLostItems = filteredLostItems.drop(safePage * 10).take(10)
                            items(pageLostItems) { item ->
                                LostItemCard(item = item, navController = navController, isAdmin = true, onFound = {})
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    PaginationBar(currentPage = safePage, totalPages = totalPages, onPageSelected = { currentPage = it })
                }
            }
        }
    }
}

/**
 * Displays a placeholder graphic and text when a category or tab has no items to show.
 */
@Composable
private fun EmptyCategoryPlaceholder(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FindInPage, null, Modifier.size(60.dp), tint = CityTheme.Green.copy(0.25f))
            Spacer(Modifier.height(12.dp))
            Text(message, color = CityTheme.Brown.copy(0.4f), textAlign = TextAlign.Center)
        }
    }
}
