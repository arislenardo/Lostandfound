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
import com.example.lostandfound.model.Claim
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.model.ItemStatus
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.example.lostandfound.utils.uploadImageToStorage
import com.example.lostandfound.utils.getReadableAddress
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory

/**
 * Displays the detailed view of a reported found item.
 * Allows residents to submit a claim for the item and allows administrators to review, edit, delete, or mark the item as returned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundItemDetailScreen(navController: NavController, itemId: String, lostItemId: String? = null) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid
    val isAdmin = AuthManager.isCurrentUserAdmin()

    var item by remember { mutableStateOf<FoundItem?>(null) }
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
    var userClaim by remember { mutableStateOf<Claim?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var selectedClaimImageUri by remember { mutableStateOf<Uri?>(null) }
    var showReturnConfirm by remember { mutableStateOf(false) }
    var showWithdrawConfirm by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf<String?>(null) }

    showFullScreenImage?.let { url ->
        FullScreenImageDialog(url) { showFullScreenImage = null }
    }

    val claimImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedClaimImageUri = uri }

    DisposableEffect(itemId) {
        val listener = db.collection("found_items").document(itemId)
            .addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    val loaded = document.toObject(FoundItem::class.java)?.copy(id = document.id)
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
        onDispose { listener.remove() }
    }
    
    // Reactive Claim Status Listener
    DisposableEffect(itemId, currentUserId, isAdmin) {
        if (!isAdmin && currentUserId != null) {
            val listener = db.collection("claims")
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val doc = snapshot.documents[0]
                        val loaded = doc.toObject(Claim::class.java)?.copy(id = doc.id)
                        
                        // IF the status changed while looking at it, show a Toast
                        if (userClaim != null && loaded != null && userClaim!!.status != loaded.status) {
                            Toast.makeText(context, "Claim status updated to: ${loaded.status}", Toast.LENGTH_LONG).show()
                        }
                        
                        userClaim = loaded
                    }
                }
            onDispose { listener.remove() }
        } else {
            onDispose { }
        }
    }

    /**
     * Updates the found item details in the Firestore database.
     */
    fun updateItem() {
        if (item == null) return
        db.collection("found_items").document(item!!.id)
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
     * Deletes the found item from the Firestore database and logs the action if the user is an admin.
     */
    fun deleteItem() {
        if (item == null) return
        db.collection("found_items").document(item!!.id).delete()
            .addOnSuccessListener {
                if (isAdmin) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val action = AdminAction(
                        adminId = currentUser?.uid ?: "",
                        adminName = currentUser?.email ?: "",
                        actionType = "DELETED_FOUND_ITEM",
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
                        Text(if (isEditing) "EDIT FOUND ITEM" else "FOUND ITEM DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
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
                            IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, "Edit", tint = CityTheme.White) }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete", tint = CityTheme.GoldLight) }
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
                // Image banner
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

                    CityDetailCard("ITEM INFORMATION") {
                        DetailRowLabel("Item Name", item!!.name)
                        DetailRowLabel("Category", item!!.category)
                        DetailRowLabel("Date Found", try { dateFormat.format(item!!.dateFound) } catch (e: Exception) { "Unknown" })
                        DetailRowLabel("Reported At", try { item!!.createdAt?.let { dateTimeFormat.format(it) } ?: "N/A" } catch (e: Exception) { "Unknown" })
                    }
                    CityDetailCard("DETAILS & LOCATION") {
                        DetailRowLabel("Location", item!!.location)
                        
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
                        if (isAdmin || item!!.userId == currentUserId) {
                            Text(item!!.description, fontSize = 14.sp, color = CityTheme.Brown)
                        } else {
                            Text("Description hidden for verification purposes.", fontSize = 14.sp, color = CityTheme.Brown.copy(alpha = 0.5f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }

                    // Admin section
                    if (isAdmin || item!!.userId == currentUserId) {
                        CityDetailCard("ADMINISTRATION", tint = CityTheme.Gold) {
                            DetailRowLabel("Reporter Email", item!!.email)
                            if (isAdmin && item!!.status != ItemStatus.RETURNED) {
                                DetailRowLabel("User ID", item!!.userId)
                                DetailRowLabel("Record ID", item!!.id)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { showReturnConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                                ) { Text("Mark as Returned (Archive)") }
                            } else if (isAdmin) {
                                DetailRowLabel("User ID", item!!.userId)
                                DetailRowLabel("Record ID", item!!.id)
                            }
                        }
                    }

                    if (showReturnConfirm) {
                        AlertDialog(
                            onDismissRequest = { showReturnConfirm = false },
                            title = { Text("Mark as Returned?", fontWeight = FontWeight.Bold) },
                            text = { Text("This will archive the item and inform the system it's no longer at the station. This action cannot be undone.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        db.collection("found_items").document(item!!.id).update("status", ItemStatus.RETURNED)
                                            .addOnSuccessListener {
                                                // Propagate status to claims and lost items
                                                db.collection("claims")
                                                    .whereEqualTo("itemId", item!!.id)
                                                    .get()
                                                    .addOnSuccessListener { snapshot ->
                                                        for (claimDoc in snapshot.documents) {
                                                            val status = claimDoc.getString("status")
                                                            // Only transition from APPROVED to RETURNED
                                                            if (status == ClaimStatus.APPROVED) {
                                                                db.collection("claims").document(claimDoc.id).update("status", ClaimStatus.RETURNED)
                                                                val lostItemId = claimDoc.getString("lostItemId")
                                                                if (!lostItemId.isNullOrBlank()) {
                                                                    db.collection("found_items").document(item!!.id).update("claimedLostItemId", lostItemId)
                                                                    db.collection("lost_items").document(lostItemId).update(
                                                                        "status", ClaimStatus.RETURNED,
                                                                        "claimedFoundItemId", item!!.id
                                                                    )
                                                                }
                                                            } else if (status != ClaimStatus.RETURNED) {
                                                                db.collection("claims").document(claimDoc.id).update("status", "ARCHIVED_SYSTEM")
                                                                val lostItemId = claimDoc.getString("lostItemId")
                                                                if (!lostItemId.isNullOrBlank()) {
                                                                    // Reset other lost items that didn't get this physical item
                                                                    db.collection("lost_items").document(lostItemId).update("status", "")
                                                                }
                                                            }
                                                        }
                                                    }

                                                val action = com.example.lostandfound.model.AdminAction(
                                                    adminId = currentUserId ?: "",
                                                    adminName = auth.currentUser?.email ?: "",
                                                    actionType = "RETURNED_ITEM",
                                                    itemTitle = item?.name ?: "Unknown Item",
                                                    itemId = item?.id ?: ""
                                                )
                                                db.collection("admin_history").add(action).addOnSuccessListener { doc ->
                                                    db.collection("admin_history").document(doc.id).update("id", doc.id)
                                                }
                                                Toast.makeText(context, "Item Returned and Linked Reports Resolved", Toast.LENGTH_SHORT).show()
                                            }
                                        showReturnConfirm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                                ) { Text("Archive Item") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReturnConfirm = false }) { Text("Cancel", color = CityTheme.Brown) }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    // Claim section (residents only)
                    if (!isAdmin && item!!.userId != currentUserId) {
                        var showClaimDialog by remember { mutableStateOf(false) }
                        var proofDescription by remember { mutableStateOf("") }
                        var isSubmittingClaim by remember { mutableStateOf(false) }

                        if (userClaim == null) {
                            Button(
                                onClick = { showClaimDialog = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Claim This Item", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            val claimStatus = userClaim!!.status
                            val statusColor = when (claimStatus) {
                                ClaimStatus.APPROVED -> CityTheme.Green
                                ClaimStatus.REJECTED -> CityTheme.Error
                                else                 -> CityTheme.Gold
                            }
                            CityDetailCard("MY CLAIM", tint = statusColor) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("STATUS: $claimStatus", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                                Spacer(Modifier.height(8.dp))
                                when (claimStatus) {
                                    ClaimStatus.APPROVED -> {
                                        Text("Your claim has been approved! ✅\n\nPlease pick up your item at the Calasiao Police Station. Present this screen and a valid ID to the officer on duty.", fontSize = 14.sp, color = CityTheme.Green, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        DetailRowLabel("Finder/Station Email", item!!.email)
                                    }
                                    ClaimStatus.PENDING  -> Text("Your proof is currently being reviewed by an officer.", fontSize = 13.sp, color = CityTheme.Brown.copy(0.7f))
                                    ClaimStatus.REJECTED -> {
                                        Text("ACTION REQUIRED", fontWeight = FontWeight.ExtraBold, color = CityTheme.Error, fontSize = 12.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Your claim was rejected. ❌\n\nReason: The proof provided was insufficient. You can dispute this decision if you have more evidence or want to talk to the reviewing officer.", fontSize = 13.sp, color = CityTheme.Brown.copy(0.7f))
                                        Spacer(Modifier.height(16.dp))
                                        
                                        // Combined Dispute & Message button
                                        Button(
                                            onClick = {
                                                db.collection("claims").document(userClaim!!.id).update("status", ClaimStatus.DISPUTED)
                                                    .addOnSuccessListener {
                                                        if (userClaim!!.lostItemId.isNotBlank()) {
                                                            db.collection("lost_items").document(userClaim!!.lostItemId).update("status", ClaimStatus.DISPUTED)
                                                        }
                                                        userClaim = userClaim?.copy(status = ClaimStatus.DISPUTED)
                                                        val adminName = if(userClaim!!.reviewerEmail.isNotBlank()) userClaim!!.reviewerEmail.substringBefore("@") else "Admin"
                                                        navController.navigate("chat/${userClaim!!.reviewedBy}/$adminName")
                                                    }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Gold)
                                        ) { 
                                            Icon(Icons.Default.Gavel, null, modifier = Modifier.size(20.dp), tint = CityTheme.White)
                                            Spacer(Modifier.width(10.dp))
                                            Text("TAP TO DISPUTE & MESSAGE OFFICER", fontWeight = FontWeight.ExtraBold, color = CityTheme.White, fontSize = 12.sp) 
                                        }

                                        Spacer(Modifier.height(12.dp))
                                        TextButton(onClick = { showWithdrawConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Withdraw Claim & Close", color = CityTheme.Error, fontSize = 12.sp)
                                        }
                                    }
                                    ClaimStatus.DISPUTED -> {
                                        Text("You have disputed this rejection. An officer will re-review your proof.", fontSize = 13.sp, color = CityTheme.Gold)
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { 
                                                val adminName = if(userClaim!!.reviewerEmail.isNotBlank()) userClaim!!.reviewerEmail.substringBefore("@") else "Admin"
                                                navController.navigate("chat/${userClaim!!.reviewedBy}/$adminName") 
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Brown)
                                        ) { Text("Message Reviewing Admin") }
                                    }
                                }
                            }

                            if (showWithdrawConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showWithdrawConfirm = false },
                                    title = { Text("Withdraw Claim?", fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to withdraw your claim? You will need to submit a new one if you change your mind.") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                db.collection("claims").document(userClaim!!.id).delete().addOnSuccessListener { 
                                                    userClaim = null 
                                                    showWithdrawConfirm = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error)
                                        ) { Text("Withdraw") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showWithdrawConfirm = false }) { Text("Cancel", color = CityTheme.Green) }
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }

                        if (showClaimDialog) {
                            AlertDialog(
                                onDismissRequest = { showClaimDialog = false },
                                shape = RoundedCornerShape(16.dp),
                                title = { Text("Secure Claim Verification", fontWeight = FontWeight.Bold, color = CityTheme.Brown) },
                                text = {
                                    Column {
                                        Text("Provide details and any photo proof about the item to help officers verify ownership.", fontSize = 13.sp, color = CityTheme.Brown.copy(0.7f))
                                        Spacer(Modifier.height(14.dp))
                                        
                                        // Image Selector for Claim
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(CityTheme.Brown.copy(alpha = 0.05f))
                                                .clickable { claimImagePicker.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selectedClaimImageUri != null) {
                                                AsyncImage(
                                                    model = selectedClaimImageUri,
                                                    contentDescription = "Selected Proof",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.AddAPhoto, null, tint = CityTheme.Green.copy(0.5f))
                                                    Text("Attach Photo Proof", fontSize = 12.sp, color = CityTheme.Brown.copy(0.4f))
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(14.dp))
                                        OutlinedTextField(
                                            value = proofDescription, onValueChange = { proofDescription = it },
                                            label = { Text("Describe item details") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CityTheme.Green, unfocusedBorderColor = CityTheme.Brown.copy(0.25f), focusedLabelColor = CityTheme.Green, cursorColor = CityTheme.Green)
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = !isSubmittingClaim && proofDescription.isNotBlank(),
                                        onClick = {
                                            isSubmittingClaim = true
                                            coroutineScope.launch {
                                                try {
                                                    val imageUrl = selectedClaimImageUri?.let {
                                                        uploadImageToStorage(it, userId = currentUserId ?: "anon", userEmail = auth.currentUser?.email ?: "", itemType = "claims")
                                                    } ?: ""
                                                    
                                                    val newClaim = Claim(
                                                        itemId = item!!.id, 
                                                        lostItemId = lostItemId ?: "",
                                                        itemName = item!!.name,
                                                        userId = currentUserId ?: "",
                                                        userName = auth.currentUser?.displayName ?: "User",
                                                        userEmail = auth.currentUser?.email ?: "",
                                                        proofDescription = proofDescription,
                                                        imageUrl = imageUrl,
                                                        timestamp = java.util.Date()
                                                    )
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        db.collection("claims").add(newClaim).addOnSuccessListener { ref ->
                                                            ref.update("id", ref.id)

                                                            // --- NEW: TRIGGER ADMIN NOTIFICATION ---
                                                            com.example.lostandfound.data.EmailService.sendAdminNotification(
                                                                type = "ITEM CLAIM SUBMISSION",
                                                                itemName = item!!.name,
                                                                reporterName = auth.currentUser?.displayName ?: "Citizen",
                                                                reporterEmail = auth.currentUser?.email ?: "Unknown",
                                                                details = "A new claim has been submitted for '${item!!.name}'. Proof description: ${newClaim.proofDescription}"
                                                            )
                                                            // ----------------------------------------
                                                            
                                                            // ALSO: Update the associated lost item status & link if linked
                                                            if (!lostItemId.isNullOrBlank()) {
                                                                db.collection("lost_items").document(lostItemId).update(
                                                                    mapOf(
                                                                        "status" to "CLAIM_PENDING",
                                                                        "claimedFoundItemId" to item!!.id
                                                                    )
                                                                )
                                                                // Mark the match_notification as READ — claim was actioned
                                                                db.collection("match_notifications")
                                                                    .whereEqualTo("lostItemId", lostItemId)
                                                                    .whereEqualTo("foundItemId", item!!.id)
                                                                    .get()
                                                                    .addOnSuccessListener { snap ->
                                                                        val batch = db.batch()
                                                                        snap.documents.forEach { d ->
                                                                            batch.update(d.reference, "status", com.example.lostandfound.model.MatchNotificationStatus.READ)
                                                                        }
                                                                        if (!snap.isEmpty) batch.commit()
                                                                    }
                                                            }
                                                            
                                                            showClaimDialog = false; isSubmittingClaim = false
                                                            Toast.makeText(context, "Claim submitted for review!", Toast.LENGTH_LONG).show()
                                                        }.addOnFailureListener { isSubmittingClaim = false }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        isSubmittingClaim = false
                                                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { 
                                        if (isSubmittingClaim) CircularProgressIndicator(Modifier.size(20.dp), color = CityTheme.White)
                                        else Text("Submit Claim") 
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { if (!isSubmittingClaim) showClaimDialog = false }) { Text("Cancel", color = CityTheme.Brown.copy(0.6f)) }
                                }
                            )
                        }
                    }

                    if (item!!.status == ItemStatus.RETURNED && item!!.claimedLostItemId.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { navController.navigate("item_detail/${item!!.claimedLostItemId}") },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("View Linked Lost Report", color = CityTheme.White, fontWeight = FontWeight.Bold)
                        }
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
 * Reusable component to display a label and value in a standardized row format.
 */
@Composable
fun DetailRowLabel(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = CityTheme.Brown.copy(0.4f))
        Text(value, fontSize = 14.sp, color = CityTheme.Brown, fontWeight = FontWeight.Medium)
    }
}
