package com.example.lostandfound.screens 

import android.Manifest
import android.content.Context

import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange

import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.lostandfound.ui.theme.CityTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.model.MatchNotification
import com.example.lostandfound.model.ItemStatus
import com.example.lostandfound.model.MatchNotificationStatus
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.utils.uploadImageToStorage
import com.example.lostandfound.utils.getReadableAddress
import com.example.lostandfound.utils.findLostMatches
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import com.example.lostandfound.utils.TFLiteClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import androidx.compose.material.icons.filled.MyLocation

// Function to create a temporary image file uri
private fun createImageUri(context: Context): Uri {
    val imageFile = File.createTempFile(
        "JPEG_${System.currentTimeMillis()}_",
        ".jpg",
        context.cacheDir
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider", // authority
        imageFile
    )
}


/**
 * Screen for reporting a found item.
 * Allows users to capture/upload an image, provide item details, and pick a location on a map.
 * Extracts image feature vectors for AI matching and generates potential match notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportItemScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    var itemName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var dateFound by remember { mutableStateOf<Date>(Date()) }
    var dateFoundText by remember { mutableStateOf("") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isCheckingMatches by remember { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    // Map State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(16.0359, 120.3601), 15f)
    }

    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // State for Algorithm Matches Dialog
    var showSurrenderDialog by remember { mutableStateOf(false) }
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var potentialOwners by remember { mutableStateOf<List<Pair<LostItem, Double>>>(emptyList()) }

    // Category Dropdown State
    var expandedCategory by remember { mutableStateOf(false) }

    // Image embedding state
    var imageVector by remember { mutableStateOf<List<Double>>(emptyList()) }

    val classifier = remember { TFLiteClassifier(context) }
    DisposableEffect(Unit) {
        onDispose {
            classifier.close()
        }
    }

    // --- LAUNCHERS (Keep logic same) ---
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri = tempImageUri
            tempImageUri?.let { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }.copy(android.graphics.Bitmap.Config.ARGB_8888, true)

                    coroutineScope.launch(Dispatchers.Default) {
                        val vec = classifier.extractFeatureVector(bitmap)
                        withContext(Dispatchers.Main) { imageVector = vec }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            capturedImageUri = uri
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }.copy(android.graphics.Bitmap.Config.ARGB_8888, true)

                coroutineScope.launch(Dispatchers.Default) {
                    val vec = classifier.extractFeatureVector(bitmap)
                    withContext(Dispatchers.Main) { imageVector = vec }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempImageUri = createImageUri(context)
            cameraLauncher.launch(tempImageUri)
        } else {
            Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // User enabled location, try to get it now
            isFetchingLocation = true
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { loc ->
                        isFetchingLocation = false
                        if (loc != null) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            coroutineScope.launch {
                                location = getReadableAddress(context, loc.latitude, loc.longitude)
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
                            }
                        }
                    }
                    .addOnFailureListener { isFetchingLocation = false }
            } catch (e: SecurityException) { isFetchingLocation = false }
        } else {
            Toast.makeText(context, "Location services are required for this feature.", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkLocationSettingsAndFetch() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Settings are satisfied, fetch location
            isFetchingLocation = true
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { loc ->
                        isFetchingLocation = false
                        if (loc != null) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            coroutineScope.launch {
                                location = getReadableAddress(context, loc.latitude, loc.longitude)
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
                            }
                        } else {
                            Toast.makeText(context, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { isFetchingLocation = false }
            } catch (e: SecurityException) { isFetchingLocation = false }
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    // Ignore the error.
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            checkLocationSettingsAndFetch()
        }
    }

    val placesLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { intent ->
                val place = com.google.android.libraries.places.widget.Autocomplete.getPlaceFromIntent(intent)
                location = place.name ?: ""
                latitude = place.latLng?.latitude
                longitude = place.latLng?.longitude
                coroutineScope.launch {
                    if (latitude != null && longitude != null) {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(latitude!!, longitude!!), 16f)
                        )
                    }
                }
            }
        } else if (result.resultCode == 2) {
            result.data?.let { intent ->
                val status = com.google.android.libraries.places.widget.Autocomplete.getStatusFromIntent(intent)
                Toast.makeText(context, "Places API Error: ${status.statusMessage}", Toast.LENGTH_LONG).show()
                android.util.Log.e("PlacesError", "Error: ${status.statusMessage}")
            }
        }
    }

    val db = FirebaseFirestore.getInstance()

    fun saveToFirestore(imageUrl: String? = null) {
        val newItem = FoundItem(
            userId = currentUser?.uid ?: "",
            email = currentUser?.email ?: "",
            name = itemName,
            location = location,
            latitude = latitude,
            longitude = longitude,
            description = description,
            category = category,
            dateFound = dateFound,
            dateFoundText = dateFoundText,
            imageUrl = imageUrl ?: "",
            imageVector = imageVector,
            status = ItemStatus.FOUND,
            createdAt = Date()
        )

        db.collection("found_items")
            .add(newItem)
            .addOnSuccessListener { foundItemRef ->
                foundItemRef.update("id", foundItemRef.id)

                // --- NEW: TRIGGER ADMIN NOTIFICATION ---
                com.example.lostandfound.data.EmailService.sendAdminNotification(
                    type = "FOUND ITEM REPORT",
                    itemName = itemName,
                    reporterName = currentUser?.displayName ?: "Citizen",
                    reporterEmail = currentUser?.email ?: "Unknown",
                    details = "A new found item '$itemName' has been reported at $location. Please check the dashboard to verify."
                )
                // ----------------------------------------

                // Check if user is admin, if so, log it
                if (AuthManager.isCurrentUserAdmin()) {
                    val action = com.example.lostandfound.model.AdminAction(
                        adminId = currentUser?.uid ?: "",
                        adminName = currentUser?.email ?: "",
                        actionType = "ADDED_FOUND_ITEM",
                        itemTitle = itemName,
                        itemId = foundItemRef.id
                    )
                    db.collection("admin_history").add(action).addOnSuccessListener { doc ->
                        db.collection("admin_history").document(doc.id).update("id", doc.id)
                    }
                }

                // Create match notifications for potential owners
                if (potentialOwners.isNotEmpty()) {
                    potentialOwners.forEach { (lostItem, score) ->
                        val notification = MatchNotification(
                            lostItemId = lostItem.id,
                            foundItemId = foundItemRef.id,
                            lostItemOwnerId = lostItem.userId,
                            lostItemName = lostItem.name,
                            foundItemName = itemName,
                            foundItemImageUrl = imageUrl ?: "",
                            foundItemLocation = location,
                            matchScore = score,
                            status = MatchNotificationStatus.UNREAD,
                            createdAt = java.util.Date()
                        )
                        db.collection("match_notifications").add(notification).addOnSuccessListener { ref ->
                            ref.update("id", ref.id)
                            if (lostItem.email.isNotBlank()) {
                                com.example.lostandfound.data.EmailService.sendSmartMatchNotification(
                                    userEmail = lostItem.email,
                                    itemName = lostItem.name,
                                    matchType = "Found Item"
                                )
                            }
                        }

                    }
                }
                isSubmitting = false
                showSurrenderDialog = true
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, "Error submitting report", Toast.LENGTH_SHORT).show()
            }
    }

    fun finalizeReportUpload() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            try {
                val imageUrl = capturedImageUri?.let { uploadImageToStorage(context, it, userId = currentUser?.uid ?: "anonymous", userEmail = currentUser?.email ?: "", itemType = "found_items") }
                withContext(Dispatchers.Main) {
                    saveToFirestore(imageUrl)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSubmitting = false
                    Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- POPUPS & DIALOGS ---

    if (showConfirmSubmitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSubmitDialog = false },
            title = { Text("Submit Report?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure? Please verify the details are accurate before submitting.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSubmitDialog = false
                        coroutineScope.launch {
                            isCheckingMatches = true
                            db.collection("lost_items").get().addOnSuccessListener { result ->
                                val allLostItems: List<LostItem> = result.documents.mapNotNull { doc ->
                                    val obj = doc.toObject(LostItem::class.java)?.copy(id = doc.id)
                                    if (obj != null && !obj.deleted && obj.status != ClaimStatus.FOUND && obj.status != ClaimStatus.APPROVED && obj.status != ClaimStatus.RETURNED && obj.status != ClaimStatus.RESOLVED) obj else null
                                }
                                coroutineScope.launch {
                                    val matches = withContext(Dispatchers.Default) { findLostMatches(itemName, description, category, imageVector, allLostItems) }
                                    isCheckingMatches = false
                                    if (matches.isNotEmpty()) { potentialOwners = matches }
                                    finalizeReportUpload()
                                }
                            }.addOnFailureListener { isCheckingMatches = false; finalizeReportUpload() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSubmitDialog = false }) {
                    Text("Cancel", color = CityTheme.Brown)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CityTheme.White
        )
    }

    if (showSurrenderDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal without confirm if needed, but let's allow it */ },
            shape = RoundedCornerShape(20.dp),
            containerColor = CityTheme.White,
            title = { 
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = CityTheme.Green, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("REPORT SUBMITTED!", fontWeight = FontWeight.ExtraBold, color = CityTheme.Green, fontSize = 20.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Thank you for being a good citizen! To complete the process, please surrender the item to the:",
                        textAlign = TextAlign.Center,
                        color = CityTheme.Brown.copy(0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "CALASIAO POLICE STATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CityTheme.Brown,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Present your Reference ID if requested: #${(itemName.hashCode().toString()).takeLast(6).uppercase()}",
                        fontSize = 11.sp,
                        color = CityTheme.Gold,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSurrenderDialog = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I Understand")
                }
            }
        )
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Date(millis)
                        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFoundText = format.format(date)
                        dateFound = date
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    // --- MAIN UI ---
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
                            Text("REPORT FOUND ITEM", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                            Text("Submit a Found Item", fontSize = 11.sp, color = CityTheme.GoldLight)
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SECTION 1: PHOTO
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(CityTheme.Gold))
                            Spacer(Modifier.width(8.dp))
                            Text("Item Photo", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CityTheme.Green, letterSpacing = 0.8.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (capturedImageUri != null) {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, capturedImageUri!!))
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, capturedImageUri!!)
                                }
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                                
                                // AI Focus Zone Guide
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .border(2.dp, CityTheme.Gold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                        .background(CityTheme.Gold.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "AI MATCHING ZONE",
                                            color = CityTheme.Gold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            "Center item here",
                                            color = CityTheme.Gold.copy(alpha = 0.8f),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(40.dp), tint = CityTheme.Green.copy(alpha = 0.5f))
                                    Text("Tap to add photo", color = CityTheme.Brown.copy(alpha = 0.4f))
                                }
                            }
                        }
                        // Photo tips
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CityTheme.Green.copy(alpha = 0.07f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("📸 Tips for better matching:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CityTheme.Green)
                                Text("• Place item on a flat, plain surface, clear background", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
                                Text("• Use good lighting — avoid dark/blurry shots", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
                                Text("• Capture only the item, whole, close-up & centered", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        tempImageUri = createImageUri(context)
                                        cameraLauncher.launch(tempImageUri)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Green)
                            ) { Text("Camera") }
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Green)
                            ) { Text("Gallery") }
                        }
                    }
                }
            }

            // SECTION 2: DETAILS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(CityTheme.Gold))
                            Spacer(Modifier.width(8.dp))
                            Text("Item Details", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CityTheme.Green, letterSpacing = 0.8.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text("What is it?") },
                            supportingText = { Text("Be specific, e.g. \"Black Samsung Galaxy A54\" not just \"Phone\"", fontSize = 11.sp, color = CityTheme.Brown.copy(0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CityTheme.Green,
                                unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.3f),
                                focusedLabelColor = CityTheme.Green,
                                cursorColor = CityTheme.Green
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Category
                        val categories = listOf("Backpacks / Bags", "Books / Notebooks", "Card", "Chargers / Cables", "Clothing", "Folder / Envelopes", "Glasses / Sunglasses", "Hats", "Headphones / Earbuds", "Keys", "Laptops", "Phone / Tablet", "Umbrellas", "Wallet", "Watch", "Water Bottles", "Others")
                        ExposedDropdownMenuBox(
                            expanded = expandedCategory,
                            onExpandedChange = { expandedCategory = !expandedCategory }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CityTheme.Green,
                                    unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.3f),
                                    focusedLabelColor = CityTheme.Green,
                                    cursorColor = CityTheme.Green
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategory,
                                onDismissRequest = { expandedCategory = false }
                            ) {
                                categories.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = { category = opt; expandedCategory = false })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dateFoundText,
                            onValueChange = {},
                            label = { Text("Date Found") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CityTheme.Green,
                                unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.3f),
                                focusedLabelColor = CityTheme.Green,
                                cursorColor = CityTheme.Green
                            ),
                            trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null, tint = CityTheme.Green) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Additional Details (optional)") },
                            placeholder = { Text("Stickers, color, brand, size, distinguishing marks, contents…", fontSize = 12.sp, color = CityTheme.Brown.copy(0.4f)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CityTheme.Green,
                                unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.3f),
                                focusedLabelColor = CityTheme.Green,
                                cursorColor = CityTheme.Green
                            )
                        )
                    }
                }
            }

            // SECTION 3: LOCATION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(CityTheme.Gold))
                            Spacer(Modifier.width(8.dp))
                            Text("Location", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CityTheme.Green, letterSpacing = 0.8.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))


                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Location where found") },
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        try {
                                            val fields = listOf(
                                                com.google.android.libraries.places.api.model.Place.Field.ID,
                                                com.google.android.libraries.places.api.model.Place.Field.NAME,
                                                com.google.android.libraries.places.api.model.Place.Field.LAT_LNG
                                            )
                                            val intent = com.google.android.libraries.places.widget.Autocomplete.IntentBuilder(
                                                com.google.android.libraries.places.widget.model.AutocompleteActivityMode.OVERLAY, fields
                                            ).build(context)
                                            placesLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Search not available", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Search, "Search Place")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CityTheme.Green,
                                    unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.3f),
                                    focusedLabelColor = CityTheme.Green,
                                    cursorColor = CityTheme.Green
                                )
                            )
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    checkLocationSettingsAndFetch()
                                } else {
                                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }) {
                                if (isFetchingLocation) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Icon(Icons.Default.MyLocation, null)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Google Map Picker
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .shadow(2.dp)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                onMapClick = { latLng ->
                                    latitude = latLng.latitude
                                    longitude = latLng.longitude
                                    coroutineScope.launch {
                                        location = getReadableAddress(context, latLng.latitude, latLng.longitude)
                                    }
                                }
                            ) {
                                if (latitude != null && longitude != null) {
                                    Marker(
                                        state = MarkerState(position = LatLng(latitude!!, longitude!!)),
                                        title = "Found Location",
                                        draggable = true
                                    )
                                }
                            }
                        }
                        
                        Text(
                            "Tap map to pin exact location",
                            fontSize = 11.sp,
                            color = CityTheme.Brown.copy(0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            // SUBMIT BUTTON
            item {
                if (isSubmitting || isCheckingMatches) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = CityTheme.Green)
                        Spacer(Modifier.height(8.dp))
                        Text("Submitting…", color = CityTheme.Brown.copy(alpha = 0.6f))
                    }
                } else {
                    Button(
                        onClick = {
                            if (itemName.isBlank() || location.isBlank()) {
                                Toast.makeText(context, "Please fill in the item name and location", Toast.LENGTH_SHORT).show()
                            } else if (category.isBlank()) {
                                Toast.makeText(context, "Please select a category — it helps us find a match", Toast.LENGTH_LONG).show()
                            } else {
                                showConfirmSubmitDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                    ) {
                        Text("SUBMIT REPORT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
