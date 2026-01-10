package com.example.lostandfound.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.lostandfound.FoundItem
import com.example.lostandfound.LostItem
import com.example.lostandfound.R
import com.example.lostandfound.findPotentialMatches
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- SCREEN 5: REPORT LOST ITEM FORM (With Auto-Match Algorithm) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportLostItemScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var itemName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var category by remember { mutableStateOf("") }
    var dateLost by remember { mutableStateOf("") }
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
    var showMatchesDialog by remember { mutableStateOf(false) }
    var potentialMatches by remember { mutableStateOf<List<Pair<FoundItem, Double>>>(emptyList()) }

    val db = FirebaseFirestore.getInstance()

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

    // Helper function to finalize submission
    fun saveToFirestore() {
        isSubmitting = true
        val newItem = LostItem(
            userId = currentUser?.uid ?: "",
            email = currentUser?.email ?: "",
            name = itemName,
            description = description,
            location = location,
            latitude = latitude,
            longitude = longitude,
            category = category,
            dateLost = dateLost,
            status = "Lost"
        )

        db.collection("lost_items")
            .add(newItem)
            .addOnSuccessListener {
                isSubmitting = false
                Toast.makeText(context, context.getString(R.string.report_submitted), Toast.LENGTH_SHORT).show()
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            }
            .addOnFailureListener {
                isSubmitting = false
                Toast.makeText(context, context.getString(R.string.report_submission_error), Toast.LENGTH_SHORT).show()
            }
    }

    // MATCHES DIALOG
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
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
                                    if (item.email.isNotBlank()) {
                                        val emailSubject = stringResource(R.string.email_subject_inquiry, item.name)
                                        val emailBody = stringResource(R.string.email_body_inquiry, item.name)
                                        Button(onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:${item.email}")
                                                putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                                                putExtra(Intent.EXTRA_TEXT, emailBody)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, context.getString(R.string.no_email_app_error), Toast.LENGTH_SHORT).show()
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.message_finder_button))
                                        }
                                    } else {
                                        Button(
                                            onClick = {},
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = false,
                                            colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Text(stringResource(R.string.no_contact_info), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // User confirms none of these are theirs, proceed to save
                    showMatchesDialog = false
                    saveToFirestore()
                }) {
                    Text(stringResource(R.string.none_of_these_mine_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMatchesDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
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
                        dateLost = format.format(date)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.ok_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_lost_item_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text(stringResource(R.string.item_name_label)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.description_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Location Field with GPS Button
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = location, 
                    onValueChange = { location = it }, 
                    label = { Text(stringResource(R.string.location_label)) }, 
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
                    label = { Text(stringResource(R.string.category_label)) },
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
                value = dateLost, 
                onValueChange = {}, 
                label = { Text(stringResource(R.string.date_lost_label)) }, 
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.select_date_content_description))
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (isSubmitting || isCheckingMatches) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if(isCheckingMatches) stringResource(R.string.checking_matches) else stringResource(R.string.submitting))
                }
            } else {
                Button(
                    onClick = {
                        if (itemName.isBlank()) return@Button
                        
                        isCheckingMatches = true
                        
                        // 1. Get all found items
                        db.collection("found_items").get()
                            .addOnSuccessListener { result ->
                                val allFoundItems = result.toObjects(FoundItem::class.java)
                                
                                // 2. Run Algorithm
                                val matches = findPotentialMatches(itemName, description, latitude, longitude, allFoundItems)
                                
                                isCheckingMatches = false
                                
                                if (matches.isNotEmpty()) {
                                    // 3a. Show Matches
                                    potentialMatches = matches
                                    showMatchesDialog = true
                                } else {
                                    // 3b. No Matches -> Save directly
                                    saveToFirestore()
                                }
                            }
                            .addOnFailureListener {
                                // Fallback if checking fails
                                isCheckingMatches = false
                                saveToFirestore()
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.submit_report_button))
                }
            }
        }
    }
}
