package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.lostandfound.components.FullScreenImageDialog
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.AdminAction
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.utils.getReadableAddress
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch

/**
 * Main composable screen for displaying and editing the details of a reported lost item.
 * It handles fetching the item from Firestore, displaying its status, map location, 
 * and providing editing or deletion capabilities for the owner or administrators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(navController: NavController, itemId: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isAdmin = AuthManager.isCurrentUserAdmin()
    val coroutineScope = rememberCoroutineScope()

    var item by remember { mutableStateOf<LostItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var editLatitude by remember { mutableStateOf<Double?>(null) }
    var editLongitude by remember { mutableStateOf<Double?>(null) }
    var cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(16.0359, 120.3601), 15f)
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf<String?>(null) }

    showFullScreenImage?.let { url ->
        FullScreenImageDialog(url) { showFullScreenImage = null }
    }

    LaunchedEffect(itemId) {
        db.collection("lost_items").document(itemId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val loaded = document.toObject(LostItem::class.java)?.copy(id = document.id)
                    item = loaded
                    loaded?.let { 
                        editName = it.name
                        editDescription = it.description
                        editLocation = it.location
                        editLatitude = it.latitude
                        editLongitude = it.longitude
                        
                        if (it.latitude != null && it.longitude != null) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
                        }
                    }
                    isLoading = false
                }
            }
    }

    /**
     * Updates the current lost item's details in Firestore with the newly edited values
     * (name, description, location, latitude, longitude).
     */
    fun updateItem() {
        if (item == null) return
        db.collection("lost_items").document(item!!.id)
            .update(mapOf(
                "name" to editName, 
                "description" to editDescription, 
                "location" to editLocation,
                "latitude" to editLatitude,
                "longitude" to editLongitude
            ))
            .addOnSuccessListener {
                Toast.makeText(context, "Item updated", Toast.LENGTH_SHORT).show()
                isEditing = false
                item = item!!.copy(name = editName, description = editDescription, location = editLocation)
            }
            .addOnFailureListener { Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show() }
    }

    /**
     * Deletes the current lost item from Firestore.
     * If the user is an admin, it also logs this deletion as an AdminAction for auditing purposes.
     */
    fun deleteItem() {
        if (item == null) return
        db.collection("lost_items").document(item!!.id).delete()
            .addOnSuccessListener {
                if (isAdmin) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val action = AdminAction(
                        adminId = currentUser?.uid ?: "",
                        adminName = currentUser?.email ?: "",
                        actionType = "DELETED_LOST_ITEM",
                        itemTitle = item!!.name,
                        itemId = item!!.id
                    )
                    db.collection("admin_history").add(action).addOnSuccessListener { doc ->
                        db.collection("admin_history").document(doc.id).update("id", doc.id)
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Audit error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            .addOnFailureListener { e -> Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Delete Report?", fontWeight = FontWeight.Bold, color = CityTheme.Brown) },
            text = { Text("Are you sure? This cannot be undone.", color = CityTheme.Brown.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = { deleteItem(); showDeleteDialog = false }) {
                    Text("Delete", color = CityTheme.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = CityTheme.Green) } }
        )
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isEditing) "EDIT LOST ITEM" else "LOST ITEM DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Ref: #${itemId.take(8)}", fontSize = 11.sp, color = CityTheme.GoldLight)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) isEditing = false
                        else if (navController.previousBackStackEntry != null &&
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CityTheme.White)
                    }
                },
                actions = {
                    if (item != null && (item!!.userId == currentUserId || isAdmin)) {
                        if (isEditing) {
                            IconButton(onClick = { updateItem() }) {
                                Icon(Icons.Default.Check, "Save", tint = CityTheme.GoldLight)
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, "Edit", tint = CityTheme.White)
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, "Delete", tint = CityTheme.GoldLight)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
            item != null -> Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Badge
                if (!isEditing) {
                    val (statusLabel, statusColor) = when (item!!.status) {
                        "APPROVED"      -> "APPROVED (Pick up at Station)" to CityTheme.Green
                        "REJECTED"      -> "REJECTED" to CityTheme.Error
                        "DISPUTED"      -> "DISPUTED (Reviewing Appeal)" to CityTheme.Gold
                        "RESOLVED"      -> "RESOLVED" to CityTheme.Green
                        "FOUND"         -> "RESOLVED" to CityTheme.Green
                        "RETURNED"      -> "RESOLVED" to CityTheme.Green
                        "CLAIM_PENDING" -> "CLAIM SUBMITTED (Reviewing)" to CityTheme.Gold
                        else            -> "SEARCHING" to CityTheme.Gold
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Image
                if (item!!.imageUrl.isNotBlank() && !isEditing) {
                    Card(
                        Modifier.fillMaxWidth().height(200.dp).clickable { showFullScreenImage = item!!.imageUrl },
                        RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        AsyncImage(model = item!!.imageUrl, contentDescription = "Item Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }

                if (isEditing) {
                    // Edit fields
                    Card(
                        Modifier.fillMaxWidth(),
                        RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(CityTheme.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val fc = OutlinedTextFieldDefaults.colors(focusedBorderColor = CityTheme.Green, unfocusedBorderColor = CityTheme.Brown.copy(0.25f), focusedLabelColor = CityTheme.Green, cursorColor = CityTheme.Green)
                            OutlinedTextField(editName, { editName = it }, Modifier.fillMaxWidth(), label = { Text("Item Name") }, shape = RoundedCornerShape(12.dp), colors = fc)
                            OutlinedTextField(editLocation, { editLocation = it }, Modifier.fillMaxWidth(), label = { Text("Location") }, shape = RoundedCornerShape(12.dp), colors = fc)
                            
                            // Map Picker in Edit Mode
                            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    onMapClick = { latLng ->
                                        editLatitude = latLng.latitude
                                        editLongitude = latLng.longitude
                                        coroutineScope.launch {
                                            editLocation = getReadableAddress(context, latLng.latitude, latLng.longitude)
                                        }
                                    }
                                ) {
                                    if (editLatitude != null && editLongitude != null) {
                                        Marker(state = MarkerState(position = LatLng(editLatitude!!, editLongitude!!)))
                                    }
                                }
                            }

                            OutlinedTextField(editDescription, { editDescription = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, shape = RoundedCornerShape(12.dp), colors = fc)
                        }
                    }
                } else {
                    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    val dateTimeFormat = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())

                    // Info card
                    CityDetailCard(title = "ITEM REPORT") {
                        DetailRow("Item Name", item!!.name)
                        DetailRow("Category", item!!.category)
                        DetailRow("Date Lost", try { dateFormat.format(item!!.dateLost) } catch (e: Exception) { "Unknown" })
                        DetailRow("Reported At", try { item!!.createdAt?.let { dateTimeFormat.format(it) } ?: "N/A" } catch (e: Exception) { "Unknown" })
                    }

                    // Location & description
                    CityDetailCard(title = "DETAILS") {
                        DetailRow("Location Lost", item!!.location)
                        
                        // Map in View Mode
                        if (item!!.latitude != null && item!!.longitude != null) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false, tiltGesturesEnabled = false, rotationGesturesEnabled = false)
                                ) {
                                    Marker(state = MarkerState(position = LatLng(item!!.latitude!!, item!!.longitude!!)))
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Description", fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f))
                        Text(item!!.description, fontSize = 14.sp, color = CityTheme.Brown)
                    }

                    // Admin/owner footer
                    if (isAdmin || item!!.userId == currentUserId) {
                        CityDetailCard(title = "INTERNAL RECORD", tint = CityTheme.Gold) {
                            DetailRow("Reporter", item!!.email)
                            if (isAdmin) DetailRow("User ID", item!!.userId)
                        }
                    }

                    if (item!!.claimedFoundItemId.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { navController.navigate("found_item_detail/${item!!.claimedFoundItemId}") },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("View Linked Found Item", color = CityTheme.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (item!!.status == "FOUND" || item!!.status == "RETURNED" || item!!.status == "RESOLVED") {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This report has been resolved.",
                            fontSize = 12.sp,
                            color = CityTheme.Brown.copy(alpha = 0.5f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            else -> Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                Text("Item not found", color = CityTheme.Error)
            }
        }
    }
}

/**
 * A reusable, styled card component used to group related details together 
 * under a common title with a colored accent bar.
 */
@Composable
fun CityDetailCard(title: String, tint: androidx.compose.ui.graphics.Color = CityTheme.Green, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = tint, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A reusable component to display a key-value pair (label and value) in a standardized format.
 */
@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f))
        Text(value, fontSize = 14.sp, color = CityTheme.Brown, fontWeight = FontWeight.Medium)
    }
}