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
import androidx.compose.foundation.Image
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
import com.example.lostandfound.ui.theme.CityTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.R
import com.example.lostandfound.utils.findPotentialMatches
import com.example.lostandfound.utils.uploadImageToStorage
import com.example.lostandfound.utils.getReadableAddress
import com.example.lostandfound.utils.TFLiteClassifier
import androidx.compose.material.icons.filled.Add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import androidx.compose.material.icons.filled.MyLocation

// Function to create a temporary image file uri
private fun createImageUri(context: Context): Uri {
    val imageFile = java.io.File.createTempFile(
        "JPEG_${System.currentTimeMillis()}_",
        ".jpg",
        context.cacheDir
    )
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider", // authority
        imageFile
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportLostItemScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    var itemName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var category by remember { mutableStateOf("") }
    var dateLost by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isCheckingMatches by remember { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var showNoImageWarning by remember { mutableStateOf(false) }

    // Map State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(16.0359, 120.3601), 15f)
    }

    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // State for Algorithm Matches Dialog
    var showMatchesDialog by remember { mutableStateOf(false) }
    var potentialMatches by remember { mutableStateOf<List<Pair<FoundItem, Double>>>(emptyList()) }

    // Category Dropdown State
    var expandedCategory by remember { mutableStateOf(false) }
    var isAutoClassified by remember { mutableStateOf(false) }

    val classifier = remember { TFLiteClassifier(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }.copy(android.graphics.Bitmap.Config.ARGB_8888, true)

                val results = classifier.classify(bitmap)
                if (results.isNotEmpty()) {
                    val topResult = results[0]
                    category = classifier.mapLabelToCategory(topResult)
                    isAutoClassified = true
                    Toast.makeText(context, "Classified as: $topResult", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri = tempImageUri
            tempImageUri?.let { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }.copy(android.graphics.Bitmap.Config.ARGB_8888, true)

                    val results = classifier.classify(bitmap)
                    if (results.isNotEmpty()) {
                        val topResult = results[0]
                        category = classifier.mapLabelToCategory(topResult)
                        isAutoClassified = true
                        Toast.makeText(context, "Classified as: $topResult", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempImageUri = createImageUri(context)
            cameraLauncher.launch(tempImageUri)
        } else {
            Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            isFetchingLocation = true
            try {
                val cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { loc ->
                        isFetchingLocation = false
                        if (loc != null) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            location = "${loc.latitude}, ${loc.longitude}"
                            Toast.makeText(context, "Location fetched!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not get location.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { isFetchingLocation = false }
            } catch (e: SecurityException) { isFetchingLocation = false }
        }
    }

    fun saveToFirestore(imageUrl: String?) {
        isSubmitting = true
        val dateObj = try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            if (dateLost.isNotBlank()) format.parse(dateLost) else Date()
        } catch (e: Exception) { Date() }

        val newItem = LostItem(
            userId = currentUser?.uid ?: "",
            email = currentUser?.email ?: "",
            name = itemName,
            description = description,
            location = location,
            latitude = latitude,
            longitude = longitude,
            category = category,
            dateLost = dateObj ?: Date(),
            imageUrl = imageUrl ?: "",
            status = "Lost",
            createdAt = Date()
        )

        db.collection("lost_items")
            .add(newItem)
            .addOnSuccessListener { lostItemRef ->
                // 1. Persist potential matches as notifications
                potentialMatches.forEach { (foundItem, score) ->
                    val matchNotif = com.example.lostandfound.model.MatchNotification(
                        lostItemId = lostItemRef.id,
                        foundItemId = foundItem.id,
                        lostItemOwnerId = currentUser?.uid ?: "",
                        lostItemName = itemName,
                        foundItemName = foundItem.name,
                        matchScore = score,
                        status = "UNREAD",
                        createdAt = java.util.Date()
                    )
                    db.collection("match_notifications").add(matchNotif).addOnSuccessListener { ref ->
                        ref.update("id", ref.id)
                    }
                }

                // 2. Check if user is admin, if so, log it
                if (com.example.lostandfound.data.AuthManager.isCurrentUserAdmin()) {
                    val action = com.example.lostandfound.model.AdminAction(
                        adminId = currentUser?.uid ?: "",
                        adminName = currentUser?.email ?: "",
                        actionType = "ADDED_LOST_ITEM",
                        itemTitle = itemName,
                        itemId = lostItemRef.id
                    )
                    db.collection("admin_history").add(action).addOnSuccessListener { doc ->
                        db.collection("admin_history").document(doc.id).update("id", doc.id)
                    }
                }

                isSubmitting = false
                Toast.makeText(context, "Report Submitted", Toast.LENGTH_SHORT).show()
                navController.navigate("home") { popUpTo("home") { inclusive = true } }
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, "Submission Error", Toast.LENGTH_SHORT).show()
            }
    }

    fun finalizeReportUpload() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            try {
                val imageUrl = selectedImageUri?.let { uploadImageToStorage(it, userId = currentUser?.uid ?: "anonymous", userEmail = currentUser?.email ?: "", itemType = "lost_items") }
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

    fun startMatchingAndUpload(context: Context, db: FirebaseFirestore, scope: CoroutineScope) {
        scope.launch {
            isCheckingMatches = true
            db.collection("found_items").get().addOnSuccessListener { result ->
                // FIX: Include document ID in each FoundItem
                val allFoundItems = result.documents.mapNotNull { doc ->
                    doc.toObject(FoundItem::class.java)?.copy(id = doc.id)
                }
                scope.launch {
                    val matches = withContext(Dispatchers.Default) {
                        findPotentialMatches(itemName, description, category, allFoundItems)
                    }
                    isCheckingMatches = false
                    if (matches.isNotEmpty()) {
                        potentialMatches = matches
                        showMatchesDialog = true
                    } else {
                        finalizeReportUpload()
                    }
                }
            }.addOnFailureListener {
                isCheckingMatches = false
                finalizeReportUpload()
            }
        }
    }

    // --- DIALOGS ---
    if (showMatchesDialog) {
        AlertDialog(
            onDismissRequest = { showMatchesDialog = false },
            title = { Text(stringResource(R.string.potential_matches_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.potential_matches_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(250.dp)) {
                        items(potentialMatches) { (item, score) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = stringResource(R.string.match_percentage, (score * 100).toInt()),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Text(stringResource(R.string.location_label) + ": ${item.location}", style = MaterialTheme.typography.bodyMedium)
                                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (item.userId.isNotBlank()) {
                                        // Police Station Mode: No direct messaging
                                        Button(onClick = {
                                            navController.navigate("found_item_detail/${item.id}")
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.view_details_claim_button))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMatchesDialog = false; finalizeReportUpload() }) { Text(stringResource(R.string.none_of_these_mine_button)) } },
            dismissButton = { TextButton(onClick = { showMatchesDialog = false }) { Text(stringResource(R.string.cancel_button)) } }
        )
    }

    if (showNoImageWarning) {
        AlertDialog(
            onDismissRequest = { showNoImageWarning = false },
            title = { Text("No Photo Attached") },
            text = { Text("Without a photo, our Smart AI cannot automatically match this to found items. Proceed anyway?") },
            confirmButton = { TextButton(onClick = { showNoImageWarning = false; startMatchingAndUpload(context, db, coroutineScope) }) { Text("Yes, Proceed") } },
            dismissButton = { TextButton(onClick = { showNoImageWarning = false }) { Text("Cancel") } }
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
                        dateLost = format.format(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok_button)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel_button)) } }
        ) { DatePicker(state = datePickerState) }
    }

    // --- MAIN UI ---
    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPORT LOST ITEM", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Submit a Lost Item", fontSize = 11.sp, color = CityTheme.GoldLight)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (navController.previousBackStackEntry != null && navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_content_description), tint = CityTheme.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SECTION 1: PHOTO
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(0.dp)
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
                            if (selectedImageUri != null) {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, selectedImageUri!!))
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, selectedImageUri!!)
                                }
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxSize())
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(40.dp), tint = CityTheme.Green.copy(alpha = 0.5f))
                                    Text("Tap to add photo", color = CityTheme.Brown.copy(alpha = 0.4f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        tempImageUri = createImageUri(context)
                                        cameraLauncher.launch(tempImageUri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(CityTheme.Gold))
                            Spacer(Modifier.width(8.dp))
                            Text("Item Details", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CityTheme.Green, letterSpacing = 0.8.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text(stringResource(R.string.item_name_label)) }, modifier = Modifier.fillMaxWidth())
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
                                label = { Text(stringResource(R.string.category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategory,
                                onDismissRequest = { expandedCategory = false }
                            ) {
                                categories.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = { category = opt; expandedCategory = false; isAutoClassified = false })
                                }
                            }
                        }
                        if (isAutoClassified) {
                            Text("✨ Auto-categorized by AI", style = MaterialTheme.typography.labelSmall, color = CityTheme.Gold, modifier = Modifier.padding(top=4.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dateLost,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.date_lost_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.description_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            }

            // SECTION 3: LOCATION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(0.dp)
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
                                label = { Text(stringResource(R.string.location_label)) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    isFetchingLocation = true
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                                        .addOnSuccessListener { loc ->
                                            isFetchingLocation = false
                                            if (loc != null) {
                                                latitude = loc.latitude
                                                longitude = loc.longitude
                                                coroutineScope.launch {
                                                    location = getReadableAddress(context, loc.latitude, loc.longitude)
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f)
                                                    )
                                                }
                                            }
                                        }
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
                                        title = "Lost Location",
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

            // SUBMIT
            item {
                if (isSubmitting || isCheckingMatches) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = CityTheme.Green)
                        Spacer(Modifier.height(8.dp))
                        Text(if (isCheckingMatches) stringResource(R.string.checking_matches) else stringResource(R.string.submitting), color = CityTheme.Brown.copy(alpha = 0.6f))
                    }
                } else {
                    Button(
                        onClick = {
                            if (itemName.isBlank()) {
                                Toast.makeText(context, "Please enter an item name", Toast.LENGTH_SHORT).show()
                            } else if (selectedImageUri == null) {
                                showNoImageWarning = true
                            } else {
                                startMatchingAndUpload(context, db, coroutineScope)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                    ) {
                        Text(stringResource(R.string.submit_report_button), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

