package com.example.lostandfound.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import com.example.lostandfound.data.AuthManager
import com.example.lostandfound.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// --- SCREEN 1: LOGIN (REAL FIREBASE AUTH) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    // Added 'name' state for Sign Up
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // NEW: Confirm Password State
    var confirmPassword by remember { mutableStateOf("") }

    var isErrorVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Toggles between Login and Register
    var isLoginMode by remember { mutableStateOf(true) }
    // Toggles between Email and Phone tabs
    var selectedTab by remember { mutableIntStateOf(0) } // 0 for Email, 1 for Phone

    // Phone Auth State
    var verificationId by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Added Scope

    // Google Sign In Setup
    val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(stringResource(id = R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        scope.launch {
                            AuthManager.refreshAdminStatus()
                            isLoading = false
                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                        }
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMessage = context.getString(R.string.error_google_sign_in, e.localizedMessage)
                        isErrorVisible = true
                    }
            } catch (e: ApiException) {
                isLoading = false
                errorMessage = context.getString(R.string.error_google_sign_in, e.statusCode.toString())
                isErrorVisible = true
            }
        } else {
            isLoading = false
        }
    }

    // --- MODERNIZED UI ---
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Decorative background shape (optional, keeping it clean for now)
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. HEADER / LOGO
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Lock, // Government/Station look
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.app_title),
                style = MaterialTheme.typography.headlineMedium,
                color = primaryColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "Official Reporting Portal",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. MAIN CARD
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoginMode) stringResource(id = R.string.login_title) else stringResource(id = R.string.create_account_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (!isCodeSent) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = primaryColor
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Email") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Phone") })
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        if (selectedTab == 0) { // Email Tab
                            if (!isLoginMode) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Full Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Person, null) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; isErrorVisible = false },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = isErrorVisible,
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Email, null) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; isErrorVisible = false },
                                label = { Text(stringResource(id = R.string.password_label)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                isError = isErrorVisible,
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Lock, null) }
                            )

                            if (!isLoginMode) {
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it; isErrorVisible = false },
                                    label = { Text(stringResource(id = R.string.confirm_password_label)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = isErrorVisible,
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Lock, null) }
                                )
                            }

                        } else { // Phone Tab
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.filter { char -> char.isDigit() }; isErrorVisible = false },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth(),
                                prefix = { Text("+63 ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                isError = isErrorVisible,
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Phone, null) }
                            )
                        }
                    } else { // OTP Screen
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text(stringResource(id = R.string.enter_sms_code)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Lock, null) }
                        )
                    }

                    if (isErrorVisible) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                isLoading = true
                                if (isCodeSent) {
                                    // VERIFY OTP
                                    if (otpCode.isNotBlank()) {
                                        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                                        auth.signInWithCredential(credential)
                                            .addOnSuccessListener {
                                                scope.launch {
                                                    AuthManager.refreshAdminStatus()
                                                    isLoading = false
                                                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                                }
                                            }
                                            .addOnFailureListener {
                                                isLoading = false
                                                errorMessage = context.getString(R.string.error_invalid_code)
                                                isErrorVisible = true
                                            }
                                    } else {
                                        isLoading = false
                                        errorMessage = "Please enter the code."
                                        isErrorVisible = true
                                    }
                                } else if (selectedTab == 0) {
                                    // EMAIL LOGIN/REGISTER (Same logic as before)
                                    if (email.isNotBlank() && password.isNotBlank()) {
                                        if (isLoginMode) {
                                            auth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener {
                                                    scope.launch {
                                                        AuthManager.refreshAdminStatus()
                                                        isLoading = false
                                                        navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    isLoading = false
                                                    errorMessage = context.getString(R.string.error_login_failed, e.localizedMessage)
                                                    isErrorVisible = true
                                                }
                                        } else {
                                            if (name.isBlank()) {
                                                isLoading = false
                                                errorMessage = "Please enter your name."
                                                isErrorVisible = true
                                            } else if (password != confirmPassword) {
                                                isLoading = false
                                                errorMessage = context.getString(R.string.error_password_mismatch)
                                                isErrorVisible = true
                                            } else {
                                                auth.createUserWithEmailAndPassword(email, password)
                                                    .addOnSuccessListener { result ->
                                                        val profileUpdates = UserProfileChangeRequest.Builder()
                                                            .setDisplayName(name)
                                                            .build()
                                                        result.user?.updateProfile(profileUpdates)
                                                            ?.addOnCompleteListener {
                                                                scope.launch {
                                                                    AuthManager.refreshAdminStatus()
                                                                    isLoading = false
                                                                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                                                }
                                                            }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isLoading = false
                                                        errorMessage = context.getString(R.string.error_registration_failed, e.localizedMessage)
                                                        isErrorVisible = true
                                                    }
                                            }
                                        }
                                    } else {
                                        isLoading = false
                                        errorMessage = "Please fill in all fields."
                                        isErrorVisible = true
                                    }
                                } else {
                                    // PHONE LOGIN (Same logic)
                                    if (phone.isNotBlank()) {
                                        val activity = context.findActivity()
                                        if (activity == null) {
                                            isLoading = false
                                            errorMessage = "Could not find activity context"
                                            isErrorVisible = true
                                        } else {
                                            val cleanPhone = if (phone.startsWith("0")) phone.substring(1) else phone
                                            val fullPhoneNumber = "+63$cleanPhone"
                                            val options = PhoneAuthOptions.newBuilder(auth)
                                                .setPhoneNumber(fullPhoneNumber)
                                                .setTimeout(60L, TimeUnit.SECONDS)
                                                .setActivity(activity)
                                                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                                        auth.signInWithCredential(credential)
                                                            .addOnSuccessListener {
                                                                scope.launch {
                                                                    AuthManager.refreshAdminStatus()
                                                                    isLoading = false
                                                                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                                                }
                                                            }
                                                    }
                                                    override fun onVerificationFailed(e: FirebaseException) {
                                                        isLoading = false
                                                        errorMessage = context.getString(R.string.error_verification_failed, e.message)
                                                        isErrorVisible = true
                                                    }
                                                    override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                                        isLoading = false
                                                        verificationId = vId
                                                        isCodeSent = true
                                                    }
                                                })
                                                .build()
                                            PhoneAuthProvider.verifyPhoneNumber(options)
                                        }
                                    } else {
                                        isLoading = false
                                        errorMessage = "Please enter a phone number."
                                        isErrorVisible = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                if (isCodeSent) stringResource(id = R.string.verify_code)
                                else if (isLoginMode) stringResource(id = R.string.login_title)
                                else stringResource(id = R.string.create_account_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isCodeSent) {
                            OutlinedButton(
                                onClick = {
                                    isLoading = true
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.AccountCircle, // Placeholder for Google
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.sign_in_google))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = {
                                isLoginMode = !isLoginMode
                                isErrorVisible = false
                                errorMessage = ""
                                confirmPassword = ""
                            }) {
                                Text(
                                    if (isLoginMode) stringResource(id = R.string.toggle_to_signup)
                                    else stringResource(id = R.string.toggle_to_login),
                                    color = secondaryColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
