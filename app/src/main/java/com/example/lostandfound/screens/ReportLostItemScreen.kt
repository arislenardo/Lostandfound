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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange

import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.model.MatchNotificationStatus
import com.example.lostandfound.model.ClaimStatus
import com.example.lostandfound.R
import com.example.lostandfound.utils.findPotentialMatches
import com.example.lostandfound.utils.uploadImageToStorage
import com.example.lostandfound.utils.getReadableAddress
import com.example.lostandfound.utils.TFLiteClassifier
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
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

    // Image embedding state
    var imageVector by remember { mutableStateOf<List<Double>>(emptyList()) }

    val classifier = remember { TFLiteClassifier(context) }
    DisposableEffect(Unit) {
        onDispose {
            classifier.close()
        }
    }

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

                // Extract visual embedding for similarity matching
                coroutineScope.launch(Dispatchers.Default) {
                    val vec = classifier.extractFeatureVector(bitmap)
                    withContext(Dispatchers.Main) { imageVector = vec }
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

                    // Extract visual embedding for similarity matching
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

    var savedLostItemId by remember { mutableStateOf<String?>(null) }

    fun saveToFirestore(imageUrl: String?, onComplete: ((String) -> Unit)? = null) {
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
            imageVector = imageVector,
            status = ClaimStatus.PENDING,
            createdAt = Date()
        )

        db.collection("lost_items")
            .add(newItem)
            .addOnSuccessListener { lostItemRef ->
                val lostId = lostItemRef.id
                lostItemRef.update("id", lostId)
                savedLostItemId = lostId

                // 1. Persist potential matches as notifications
                potentialMatches.forEach { (foundItem, score) ->
                    val matchNotif = com.example.lostandfound.model.MatchNotification(
                        lostItemId = lostId,
                        foundItemId = foundItem.id,
                        lostItemOwnerId = currentUser?.uid ?: "",
                        lostItemName = itemName,
                        foundItemName = foundItem.name,
                        foundItemImageUrl = foundItem.imageUrl,
                        foundItemLocation = foundItem.location,
                        matchScore = score,
                        status = MatchNotificationStatus.UNREAD,
                        createdAt = java.util.Date()
                    )
                    db.collection("match_notifications").add(matchNotif).addOnSuccessListener { ref ->
                        ref.update("id", ref.id)
                    }
                }

                if (onComplete != null) {
                    onComplete(lostId)
                } else {
                    isSubmitting = false
                    Toast.makeText(context, "Report Submitted", Toast.LENGTH_SHORT).show()
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                }
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, "Submission Error", Toast.LENGTH_SHORT).show()
            }
    }

    fun finalizeReportUpload(onComplete: ((String) -> Unit)? = null) {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            try {
                val imageUrl = selectedImageUri?.let { uploadImageToStorage(it, userId = currentUser?.uid ?: "anonymous", userEmail = currentUser?.email ?: "", itemType = "lost_items") }
                withContext(Dispatchers.Main) {
                    saveToFirestore(imageUrl, onComplete)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSubmitting = false
                    Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun startMatchingAndSave(context: Context, db: FirebaseFirestore, scope: CoroutineScope) {
        scope.launch {
            isCheckingMatches = true
            // Run matching algorithm first to see if we should show the dialog
            db.collection("found_items").get().addOnSuccessListener { result ->
                val allFoundItems = result.documents.mapNotNull { doc ->
                    val obj = doc.toObject(FoundItem::class.java)?.copy(id = doc.id)
                    if (obj != null && obj.status == com.example.lostandfound.model.ItemStatus.FOUND) obj else null
                }
                scope.launch {
                    val matches = withContext(Dispatchers.Default) {
                        findPotentialMatches(itemName, description, category, imageVector, allFoundItems)
                    }
                    isCheckingMatches = false
                    if (matches.isNotEmpty()) {
                        potentialMatches = matches
                        // Save the report FIRST so we have an ID to pass
                        finalizeReportUpload { lostId ->
                            isSubmitting = false
                            showMatchesDialog = true
                        }
                    } else {
                        // No matches, just save and go home
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
    if (isCheckingMatches) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal */ },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    CircularProgressIndicator(color = CityTheme.Green)
                    Spacer(Modifier.height(16.dp))
                    Text("Smart AI Scanning...", color = CityTheme.Brown, fontWeight = FontWeight.Bold)
                    Text("Looking for visual and keyword matches", fontSize = 12.sp, color = CityTheme.Brown.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            },
            confirmButton = {},
            containerColor = CityTheme.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showMatchesDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMatchesDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CityTheme.Cream)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Gradient Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(CityTheme.Green, CityTheme.Green.copy(alpha = 0.75f))
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(CityTheme.Gold.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🏆", fontSize = 26.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Potential Matches Found!",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = CityTheme.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Our AI found ${potentialMatches.size} possible match${if (potentialMatches.size > 1) "es" else ""} for your lost item",
                                fontSize = 12.sp,
                                color = CityTheme.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Cards List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(potentialMatches) { (item, score) ->
                            val matchPct = (score * 100).toInt()
                            val matchColor = when {
                                matchPct >= 70 -> CityTheme.Green
                                matchPct >= 40 -> CityTheme.Gold
                                else           -> CityTheme.Brown.copy(alpha = 0.6f)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column {
                                    // Item image
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                    ) {
                                        if (item.imageUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(item.imageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(CityTheme.Cream),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📦", fontSize = 40.sp)
                                            }
                                        }
                                        // Match badge overlay
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(matchColor)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "$matchPct% match",
                                                color = CityTheme.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    // Card details
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = CityTheme.Brown
                                        )
                                        if (item.category.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                item.category,
                                                fontSize = 11.sp,
                                                color = CityTheme.Green,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (item.location.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    null,
                                                    tint = CityTheme.Brown.copy(0.5f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    item.location,
                                                    fontSize = 12.sp,
                                                    color = CityTheme.Brown.copy(0.55f),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (item.description.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                item.description,
                                                fontSize = 12.sp,
                                                color = CityTheme.Brown.copy(0.6f),
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                showMatchesDialog = false
                                                navController.navigate("found_item_detail/${item.id}?lostItemId=$savedLostItemId")
                                            },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Green)
                                        ) {
                                            Text("View & Claim", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Footer buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CityTheme.White)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showMatchesDialog = false
                                navController.navigate("home") { popUpTo("home") { inclusive = true } }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CityTheme.Brown)
                        ) {
                            Text("None of These Are Mine", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (showNoImageWarning) {
        AlertDialog(
            onDismissRequest = { showNoImageWarning = false },
            title = { Text("No Photo Attached") },
            text = { Text("Without a photo, our Smart AI cannot automatically match this to found items. Proceed anyway?") },
            confirmButton = { TextButton(onClick = { showNoImageWarning = false; startMatchingAndSave(context, db, coroutineScope) }) { Text("Yes, Proceed") } },
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
                        // Photo tips
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CityTheme.Green.copy(alpha = 0.07f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("📸 Tips for better matching:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CityTheme.Green)
                                Text("• Place item on a flat, plain surface", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
                                Text("• Use good lighting — avoid dark/blurry shots", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
                                Text("• Capture the whole item, close-up & centered", fontSize = 11.sp, color = CityTheme.Brown.copy(0.7f))
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
                            label = { Text(stringResource(R.string.item_name_label)) },
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
                                label = { Text(stringResource(R.string.category_label)) },
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
                            value = dateLost,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.date_lost_label)) },
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
                            label = { Text(stringResource(R.string.description_label)) },
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
                                label = { Text(stringResource(R.string.location_label)) },
                                modifier = Modifier.weight(1f),
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
                if (isSubmitting && !isCheckingMatches) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = CityTheme.Green)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.submitting), color = CityTheme.Brown.copy(alpha = 0.6f))
                    }
                } else {
                    Button(
                        onClick = {
                            if (itemName.isBlank()) {
                                Toast.makeText(context, "Please enter an item name", Toast.LENGTH_SHORT).show()
                            } else if (category.isBlank()) {
                                Toast.makeText(context, "Please select a category — it helps us find a match", Toast.LENGTH_LONG).show()
                            } else if (selectedImageUri == null) {
                                showNoImageWarning = true
                            } else {
                                startMatchingAndSave(context, db, coroutineScope)
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


