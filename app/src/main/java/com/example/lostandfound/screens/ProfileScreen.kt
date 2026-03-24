package com.example.lostandfound.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
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
import coil.request.ImageRequest
import com.example.lostandfound.ui.theme.CityTheme
import com.example.lostandfound.ui.theme.fieldColors
import com.example.lostandfound.utils.uploadImageToStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Resident") }
    var profileImageUrl by remember { mutableStateOf(currentUser?.photoUrl?.toString() ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val isAdmin = com.example.lostandfound.data.AuthManager.isCurrentUserAdmin()
            
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        name = document.getString("name") ?: currentUser.displayName ?: ""
                        val rawPhone = document.getString("phoneNumber") ?: currentUser.phoneNumber ?: ""
                        phone = if (rawPhone.startsWith("+63")) rawPhone.substring(3) else rawPhone
                        role = if (isAdmin) "Admin" else (document.getString("role") ?: "Resident")
                        profileImageUrl = document.getString("profileImageUrl") ?: currentUser.photoUrl?.toString() ?: ""
                        if (email.isBlank()) {
                            email = document.getString("email") ?: currentUser.email ?: ""
                        }
                    } else {
                        name = currentUser.displayName ?: ""
                        val rawPhone = currentUser.phoneNumber ?: ""
                        phone = if (rawPhone.startsWith("+63")) rawPhone.substring(3) else rawPhone
                        role = if (isAdmin) "Admin" else "Resident"
                        if (email.isBlank()) {
                            email = currentUser.email ?: ""
                        }
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    role = if (isAdmin) "Admin" else "Resident"
                    isLoading = false
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        } else {
            isLoading = false
        }
    }

    fun saveProfile() {
        if (currentUser == null) return
        isSaving = true
        
        coroutineScope.launch {
            try {
                var newImageUrl = profileImageUrl
                if (selectedImageUri != null) {
                    newImageUrl = uploadImageToStorage(selectedImageUri!!, currentUser.uid, currentUser.email ?: "anonymous", "profiles")
                }
                
                // Consistency check for phone number
                val cleanPhone = if (phone.startsWith("0")) phone.substring(1) else phone
                val fullPhone = if (cleanPhone.startsWith("+63")) cleanPhone else "+63$cleanPhone"
                
                val updates = mapOf(
                    "name" to name,
                    "phoneNumber" to fullPhone,
                    "profileImageUrl" to newImageUrl
                )
                
                withContext(Dispatchers.Main) {
                    db.collection("users").document(currentUser.uid)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            // Sync name and photo with Firebase Auth profile
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                            if (newImageUrl.isNotBlank()) {
                                profileUpdates.setPhotoUri(Uri.parse(newImageUrl))
                            }
                            currentUser.updateProfile(profileUpdates.build())

                            profileImageUrl = newImageUrl
                            isSaving = false
                            isEditing = false
                            selectedImageUri = null
                            Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            isSaving = false
                            Toast.makeText(context, "Error updating profile", Toast.LENGTH_SHORT).show()
                        }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSaving = false
                    Toast.makeText(context, "Image upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "MY PROFILE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = CityTheme.White
                        )
                        Text(
                            "Account Settings",
                            fontSize = 11.sp,
                            color = CityTheme.GoldLight
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { saveProfile() }, enabled = !isSaving) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CityTheme.GoldLight)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = CityTheme.GoldLight)
                            }
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = CityTheme.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Green)
            )
        },
        bottomBar = {
            AppBottomNavigation(
                navController = navController,
                currentRoute = "profile",
                isAdmin = (role == "Admin")
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = CityTheme.Green)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar Placeholder
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(CityTheme.Green)
                        .border(4.dp, CityTheme.GoldLight, CircleShape)
                        .clickable { if (isEditing) imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null || profileImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedImageUri ?: profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Avatar",
                            tint = CityTheme.White,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    if (isEditing) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(CityTheme.Brown.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Change Image", tint = CityTheme.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Role Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (role == "Admin") CityTheme.Gold else CityTheme.Brown.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = role.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (role == "Admin") CityTheme.White else CityTheme.Brown,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // User Info Fields
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Personal Details",
                            fontWeight = FontWeight.Bold,
                            color = CityTheme.Green,
                            fontSize = 16.sp
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (isEditing) name = it },
                            label = { Text("Full Name") },
                            enabled = isEditing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { if (isEditing) phone = it.filter { c -> c.isDigit() } },
                            label = { Text("Phone Number") },
                            enabled = isEditing,
                            prefix = { Text("+63 ", color = CityTheme.Brown.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { }, // Email is not editable here
                            label = { Text("Email Address") },
                            enabled = false, // Always disabled
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (isEditing) {
                    Button(
                        onClick = {
                            isEditing = false
                            selectedImageUri = null // Reset image changes
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Brown.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel Editing", fontWeight = FontWeight.Bold, color = CityTheme.White)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(CityTheme.Error.copy(0.4f)))
                    ) {
                        Text("Sign Out", fontWeight = FontWeight.Bold, color = CityTheme.Error)
                    }
                }

                if (showSignOutConfirm) {
                    AlertDialog(
                        onDismissRequest = { showSignOutConfirm = false },
                        title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to log out of your account?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val user = auth.currentUser
                                    if (user != null) {
                                        db.collection("users").document(user.uid)
                                            .update("fcmToken", "")
                                            .addOnCompleteListener {
                                                auth.signOut()
                                                navController.navigate("login") {
                                                    popUpTo(0) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                                showSignOutConfirm = false
                                            }
                                    } else {
                                        auth.signOut()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        showSignOutConfirm = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error)
                            ) { Text("Log Out") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel", color = CityTheme.Brown) }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}
