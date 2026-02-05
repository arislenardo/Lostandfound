package com.example.lostandfound.screens 

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.model.MatchNotification
import com.example.lostandfound.utils.findLostMatches
import androidx.compose.material.icons.filled.Add
import com.example.lostandfound.utils.TFLiteClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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


// --- SCREEN 3: REPORT FOUND ITEM FORM (Data Entry) ---
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

    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // State for Algorithm Matches Dialog
    var showOwnerDialog by remember { mutableStateOf(false) }
    var potentialOwners by remember { mutableStateOf<List<Pair<LostItem, Double>>>(emptyList()) }

    // Category Dropdown State
    var expandedCategory by remember { mutableStateOf(false) }
    var isAutoClassified by remember { mutableStateOf(false) }

    val classifier = remember { TFLiteClassifier(context) }

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

    val db = FirebaseFirestore.getInstance()

    fun saveToFirestore() {
        isSubmitting = true
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
            status = "Found"
        )

        db.collection("found_items")
            .add(newItem)
            .addOnSuccessListener { foundItemRef ->
                // Create match notifications for potential owners
                if (potentialOwners.isNotEmpty()) {
                    potentialOwners.forEach { (lostItem, score) ->
                        val notification = MatchNotification(
                            lostItemId = lostItem.id,
                            foundItemId = foundItemRef.id,
                            lostItemOwnerId = lostItem.userId,
                            lostItemName = lostItem.name,
                            foundItemName = itemName,
                            matchScore = score,
                            status = "UNREAD",
                            createdAt = java.util.Date()
                        )
                        db.collection("match_notifications").add(notification)
                    }
                }
                isSubmitting = false
                Toast.makeText(context, "Report Submitted!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, "Error submitting report", Toast.LENGTH_SHORT).show()
            }
    }

    // --- POPUPS & DIALOGS ---
    if (showOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerDialog = false },
            title = { Text("Potential Owner Found!") },
            text = {
                Column {
                    Text("This item looks similar to something reported lost. Is it one of these?")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(250.dp)) {
                        items(potentialOwners) { (item, score) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${(score * 100).toInt()}% Match",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Text("Category: ${item.category}", style = MaterialTheme.typography.bodyMedium)
                                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (item.userId.isNotBlank()) {
                                        // Police Station Mode: No direct messaging
                                        Text(
                                            "Match Detected. Submit report for officer verification.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOwnerDialog = false; saveToFirestore() }) { Text("Continue to Submit") } },
            dismissButton = { TextButton(onClick = { showOwnerDialog = false }) { Text("Cancel") } }
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("REPORT FOUND ITEM", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Item Photo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Text("Tap to add photo", color = MaterialTheme.colorScheme.secondary)
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
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Camera") }
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) { Text("Gallery") }
                        }
                    }
                }
            }

            // SECTION 2: DETAILS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Item Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("What is it?") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Category
                        val categories = listOf("Phone / Tablet", "Keys", "Wallet", "Glasses", "Headphones", "Bag", "Clothing", "Laptop", "Other")
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
                            Text("✨ Auto-categorized by AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top=4.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dateFoundText,
                            onValueChange = {},
                            label = { Text("Date Found") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null) } }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Color, Brand, etc.)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            }

            // SECTION 3: LOCATION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Location", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Where was it found?") },
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
                                                location = "${loc.latitude}, ${loc.longitude}"
                                            }
                                        }
                                } else {
                                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }) {
                                if (isFetchingLocation) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Icon(Icons.Default.LocationOn, null)
                            }
                        }
                    }
                }
            }

            // SUBMIT BUTTON
            item {
                if (isSubmitting || isCheckingMatches) {
                    CircularProgressIndicator()
                    Text(if (isCheckingMatches) "Checking for owners..." else "Submitting...")
                } else {
                    Button(
                        onClick = {
                            if (itemName.isBlank() || location.isBlank()) {
                                Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
                            } else {
                                coroutineScope.launch {
                                    isCheckingMatches = true
                                    db.collection("lost_items").get().addOnSuccessListener { result ->
                                        // FIX: Include document ID in each LostItem
                                        val allLostItems = result.documents.mapNotNull { doc ->
                                            doc.toObject(LostItem::class.java)?.copy(id = doc.id)
                                        }
                                        coroutineScope.launch {
                                            val matches = withContext(Dispatchers.Default) { findLostMatches(itemName, description, allLostItems) }
                                            isCheckingMatches = false
                                            if (matches.isNotEmpty()) {
                                                potentialOwners = matches
                                                showOwnerDialog = true
                                            } else {
                                                saveToFirestore()
                                            }
                                        }
                                    }.addOnFailureListener {
                                        isCheckingMatches = false
                                        saveToFirestore()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("SUBMIT REPORT", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
