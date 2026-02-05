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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
            status = "Lost"
        )

        db.collection("lost_items")
            .add(newItem)
            .addOnSuccessListener {
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
        coroutineScope.launch {
            isSubmitting = true
            val imageUrl = selectedImageUri?.let { uploadImageToStorage(it) }
            saveToFirestore(imageUrl)
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
                        findPotentialMatches(itemName, description, allFoundItems)
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.report_lost_item_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_content_description))
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
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                        
                        OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text(stringResource(R.string.item_name_label)) }, modifier = Modifier.fillMaxWidth())
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
                            Text("✨ Auto-categorized by AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top=4.dp))
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

            // SUBMIT
            item {
                if (isSubmitting || isCheckingMatches) {
                    CircularProgressIndicator()
                    Text(if (isCheckingMatches) stringResource(R.string.checking_matches) else stringResource(R.string.submitting))
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
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.submit_report_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

suspend fun uploadImageToStorage(imageUri: Uri): String? {
    val storageRef = FirebaseStorage.getInstance().reference
    val imageRef = storageRef.child("images/${UUID.randomUUID()}")
    return try {
        imageRef.putFile(imageUri).await()
        val downloadUrl = imageRef.downloadUrl.await()
        downloadUrl.toString()
    } catch (e: Exception) {
        null
    }
}
