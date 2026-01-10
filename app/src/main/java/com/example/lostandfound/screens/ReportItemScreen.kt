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
import com.example.lostandfound.utils.findLostMatches
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

    // Category Dropdown State
    var expandedCategory by remember { mutableStateOf(false) }
    val categories = listOf("Electronics", "Clothing", "Accessories", "Documents", "Keys", "Others")
    var textFieldSize by remember { mutableStateOf(Size.Zero) }
    val iconCategory = if (expandedCategory) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown

    // State for Algorithm Matches Dialog
    var showOwnerDialog by remember { mutableStateOf(false) }
    var potentialOwners by remember { mutableStateOf<List<Pair<LostItem, Double>>>(emptyList()) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri = tempImageUri
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempImageUri = createImageUri(context)
            cameraLauncher.launch(tempImageUri)
        } else {
            Toast.makeText(context, "Camera permission needed to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            // Permission granted, fetch location
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
                            Toast.makeText(context, "Could not get location. Try enabling GPS.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        isFetchingLocation = false
                        Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: SecurityException) {
                isFetchingLocation = false
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission needed", Toast.LENGTH_SHORT).show()
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

        // Note: Image uploading requires Firebase Storage.

        db.collection("found_items")
            .add(newItem)
            .addOnSuccessListener {
                isSubmitting = false
                Toast.makeText(context, "Report Submitted!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, "Error submitting report", Toast.LENGTH_SHORT).show()
            }
    }

    // MATCHES DIALOG (FOR FINDER TO SEE POTENTIAL OWNERS)
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${(score * 100).toInt()}% Match",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                    Text("Category: ${item.category}", style = MaterialTheme.typography.bodyMedium)
                                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Date Lost: ${item.dateLost}", style = MaterialTheme.typography.labelSmall)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (item.email.isNotBlank()) {
                                        Button(onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:${item.email}")
                                                putExtra(Intent.EXTRA_SUBJECT, "Found Item: ${item.name}")
                                                putExtra(Intent.EXTRA_TEXT, "Hello, I think I found your ${item.name}. Please contact me.")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Message Owner")
                                        }
                                    } else {
                                        Button(
                                            onClick = {},
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = false,
                                            colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Text("No contact info. Please submit report.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showOwnerDialog = false
                    saveToFirestore()
                }) {
                    Text("Continue to Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerDialog = false }) {
                    Text("Cancel")
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
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Found Item") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)) {

            // Camera Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (capturedImageUri != null) {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, capturedImageUri!!))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, capturedImageUri!!)
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Image",
                        modifier = Modifier.fillMaxSize()
                    )
                    Button(
                        onClick = {
                            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                tempImageUri = createImageUri(context)
                                cameraLauncher.launch(tempImageUri)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Text("Retake")
                    }
                } else {
                    Button(onClick = {
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            tempImageUri = createImageUri(context)
                            cameraLauncher.launch(tempImageUri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Text("Take Photo")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            // Location Field with GPS Button
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val permissionCheckFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val permissionCheckCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

                    if (permissionCheckFine == PackageManager.PERMISSION_GRANTED || permissionCheckCoarse == PackageManager.PERMISSION_GRANTED) {
                        // Permission granted, fetch location
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
                                        Toast.makeText(context, "Could not get location. Try enabling GPS.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener {
                                    isFetchingLocation = false
                                    Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
                                }
                        } catch (e: SecurityException) {
                            isFetchingLocation = false
                            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = "Get Location")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(modifier = Modifier.height(8.dp))

            // Category Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            textFieldSize = coordinates.size.toSize()
                        },
                    label = { Text("Category") },
                    trailingIcon = {
                        Icon(iconCategory, "contentDescription",
                            Modifier.clickable { expandedCategory = !expandedCategory })
                    },
                    readOnly = true // Make it read-only so keyboard doesn't pop up
                )

                // Transparent clickable surface to cover the text field for dropdown trigger
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedCategory = !expandedCategory }
                )

                DropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false },
                    modifier = Modifier
                        .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                ) {
                    categories.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                category = label
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date Picker Field
            OutlinedTextField(
                value = dateFoundText,
                onValueChange = {},
                label = { Text("Date Found") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSubmitting || isCheckingMatches) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if(isCheckingMatches) "Checking against lost items..." else "Submitting...")
                }
            } else {
                Button(
                    onClick = {
                        if (itemName.isBlank() || location.isBlank()) {
                            Toast.makeText(context, "Please fill in required fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            isCheckingMatches = true
                            // 1. Check against Lost Items
                            db.collection("lost_items").get()
                                .addOnSuccessListener { result ->
                                    coroutineScope.launch {
                                        val allLostItems = result.toObjects(LostItem::class.java)

                                        // 2. Run Algorithm (Reverse check)
                                        val matches = withContext(Dispatchers.Default) {
                                            findLostMatches(itemName, description, latitude, longitude, allLostItems)
                                        }

                                        isCheckingMatches = false

                                        if (matches.isNotEmpty()) {
                                            // 3a. Show Matches
                                            potentialOwners = matches
                                            showOwnerDialog = true
                                        } else {
                                            // 3b. No Matches -> Save directly
                                            saveToFirestore()
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    isCheckingMatches = false
                                    saveToFirestore()
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Report")
                }
            }
        }
    }
}
