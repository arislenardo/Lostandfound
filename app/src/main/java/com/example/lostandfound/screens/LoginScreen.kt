@file:Suppress("DEPRECATION")
package com.example.lostandfound.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.R
import com.example.lostandfound.data.AuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

// ── Shared Helper to ensure FCM token is saved before navigating ────────────
private fun syncFCMTokenAndNavigate(
    auth: FirebaseAuth,
    navController: NavController,
    onComplete: () -> Unit
) {
    auth.currentUser?.let { user ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseFirestore.getInstance().collection("users").document(user.uid)
                    .update("fcmToken", token)
                    .addOnCompleteListener {
                        Log.d("Login", "FCM token synced during login")
                        onComplete()
                        navController.navigate("home") { popUpTo("login") { inclusive = true } }
                    }
            } else {
                Log.e("Login", "Failed to get FCM token during login", task.exception)
                // Fallback: still navigate
                onComplete()
                navController.navigate("home") { popUpTo("login") { inclusive = true } }
            }
        }
    } ?: run {
        onComplete()
        navController.navigate("home") { popUpTo("login") { inclusive = true } }
    }
}

// ── City Color Palette ──────────────────────────────────────────────────────
private val CityGreen      = Color(0xFF2D6A4F)   // Deep forest green
private val CityGreenLight = Color(0xFF40916C)   // Lighter green for gradients
private val CityGold       = Color(0xFFD4A017)   // Golden yellow / seal accent
private val CityGoldLight  = Color(0xFFF4C430)   // Bright yellow highlight
private val CityBrown      = Color(0xFF5C3D1E)   // Warm brown for text/outlines
private val CityCream      = Color(0xFFFDF8F0)   // Off-white / cream background
private val CityWhite      = Color(0xFFFFFFFF)
private val CityError      = Color(0xFFB00020)

// ── Login Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {

    // Form state
    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isErrorVisible by remember { mutableStateOf(false) }
    var errorMessage   by remember { mutableStateOf("") }
    var isLoading      by remember { mutableStateOf(false) }

    var isLoginMode  by remember { mutableStateOf(true) }
    var selectedTab  by remember { mutableIntStateOf(0) }   // 0 = Email, 1 = Phone

    // Phone auth state
    var verificationId     by remember { mutableStateOf("") }
    var otpCode            by remember { mutableStateOf("") }
    var isCodeSent         by remember { mutableStateOf(false) }
    var isProfileSetupStep by remember { mutableStateOf(false) }
    var profileName        by remember { mutableStateOf("") }
    var profileEmail       by remember { mutableStateOf("") }

    val auth    = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Google Sign-In
    @Suppress("DEPRECATION")
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
                val account    = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        scope.launch {
                            val user = auth.currentUser
                            if (user != null) {
                                val userData = hashMapOf(
                                    "uid" to user.uid,
                                    "email" to (user.email ?: ""),
                                    "name" to (user.displayName ?: ""),
                                    "phoneNumber" to (user.phoneNumber ?: "")
                                )
                                FirebaseFirestore.getInstance()
                                    .collection("users").document(user.uid)
                                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                    .await()
                            }
                            AuthManager.refreshAdminStatus()
                            syncFCMTokenAndNavigate(auth, navController) { isLoading = false }
                        }
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMessage   = context.getString(R.string.error_google_sign_in, e.localizedMessage)
                        isErrorVisible = true
                    }
            } catch (e: ApiException) {
                isLoading = false
                errorMessage   = context.getString(R.string.error_google_sign_in, e.statusCode.toString())
                isErrorVisible = true
            }
        } else {
            isLoading = false
        }
    }

    // ── Custom text-field colours ──────────────────────────────────────────
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor    = CityGreen,
        unfocusedBorderColor  = CityBrown.copy(alpha = 0.35f),
        focusedLabelColor     = CityGreen,
        unfocusedLabelColor   = CityBrown.copy(alpha = 0.6f),
        cursorColor           = CityGreen,
        focusedLeadingIconColor   = CityGreen,
        unfocusedLeadingIconColor = CityBrown.copy(alpha = 0.5f),
        errorBorderColor      = CityError,
        focusedTextColor      = CityBrown,
        unfocusedTextColor    = CityBrown,
    )

    // ── Root ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityCream)
    ) {
        // ── Decorative green header banner ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CityGreen, CityGreenLight)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Emblem / Icon ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CityGold)
                    .border(3.dp, CityWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = CityWhite,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // App name
            Text(
                text = stringResource(id = R.string.app_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CityWhite,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Official Lost & Found Portal",
                fontSize = 12.sp,
                color = CityGoldLight,
                letterSpacing = 0.8.sp
            )

            // ── Gold divider accent ────────────────────────────────────────
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CityGold)
            )

            Spacer(Modifier.height(28.dp))

            // ── Main card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CityWhite),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Card title
                    val cardTitle = when {
                        isProfileSetupStep -> "Complete Your Profile"
                        isCodeSent         -> "Enter Verification Code"
                        isLoginMode        -> stringResource(R.string.login_title)
                        else               -> stringResource(R.string.create_account_title)
                    }
                    Text(
                        text = cardTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CityBrown
                    )

                    // Subtitle
                    val cardSub = when {
                        isProfileSetupStep -> "Tell us a little about yourself."
                        isCodeSent         -> "We sent a 6-digit code to your phone."
                        isLoginMode        -> "Sign in to your account"
                        else               -> "Create a new account"
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = cardSub,
                        fontSize = 13.sp,
                        color = CityBrown.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // ── Tabs (only on the initial input step) ──────────────
                    if (!isCodeSent && !isProfileSetupStep) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor   = CityCream,
                            contentColor     = CityGreen,
                            indicator        = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color    = CityGold
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick  = { selectedTab = 0; isErrorVisible = false },
                                text = {
                                    Text(
                                        "Email",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (selectedTab == 0) CityGreen else CityBrown.copy(alpha = 0.5f)
                                    )
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick  = { selectedTab = 1; isErrorVisible = false },
                                text = {
                                    Text(
                                        "Phone",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (selectedTab == 1) CityGreen else CityBrown.copy(alpha = 0.5f)
                                    )
                                }
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // ── Form fields ────────────────────────────────────────

                    if (!isCodeSent && !isProfileSetupStep) {

                        if (selectedTab == 0) {  // Email tab
                            if (!isLoginMode) {
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it },
                                    label = { Text("Full Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Filled.Person, null) }
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                            OutlinedTextField(
                                value = email, onValueChange = { email = it; isErrorVisible = false },
                                label = { Text("Email Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = isErrorVisible,
                                colors = fieldColors,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Filled.Email, null) }
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = password, onValueChange = { password = it; isErrorVisible = false },
                                label = { Text(stringResource(R.string.password_label)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = isErrorVisible,
                                colors = fieldColors,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Filled.Lock, null) }
                            )
                            if (!isLoginMode) {
                                Spacer(Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = confirmPassword, onValueChange = { confirmPassword = it; isErrorVisible = false },
                                    label = { Text(stringResource(R.string.confirm_password_label)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = isErrorVisible,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Filled.Lock, null) }
                                )
                            }

                        } else {  // Phone tab
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.filter { c -> c.isDigit() }; isErrorVisible = false },
                                label = { Text("Phone Number") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                prefix = { Text("+63 ", color = CityBrown.copy(alpha = 0.7f)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                isError = isErrorVisible,
                                colors = fieldColors,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Filled.Phone, null) }
                            )
                        }

                    } else if (isProfileSetupStep) {

                        // Profile setup after phone OTP
                        OutlinedTextField(
                            value = profileName, onValueChange = { profileName = it; isErrorVisible = false },
                            label = { Text("Full Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = isErrorVisible,
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Person, null) }
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = profileEmail, onValueChange = { profileEmail = it; isErrorVisible = false },
                            label = { Text("Email (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Email, null) }
                        )

                    } else {  // OTP entry

                        OutlinedTextField(
                            value = otpCode, onValueChange = { otpCode = it },
                            label = { Text(stringResource(R.string.enter_sms_code)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Filled.Lock, null) }
                        )
                    }

                    // ── Error message ──────────────────────────────────────
                    if (isErrorVisible) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CityError.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = CityError, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(errorMessage, color = CityError, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Primary action button ──────────────────────────────
                    if (isLoading) {
                        CircularProgressIndicator(color = CityGreen)
                    } else {
                        Button(
                            onClick = {
                                isLoading = true
                                if (isProfileSetupStep) {
                                    if (profileName.isBlank()) {
                                        isLoading = false; errorMessage = "Please enter your name."; isErrorVisible = true
                                    } else if (profileEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(profileEmail).matches()) {
                                        isLoading = false; errorMessage = "Please enter a valid email address."; isErrorVisible = true
                                    } else {
                                        scope.launch {
                                            try {
                                                val user = auth.currentUser!!
                                                val profileUpdates = UserProfileChangeRequest.Builder()
                                                    .setDisplayName(profileName).build()
                                                user.updateProfile(profileUpdates).await()
                                                val userData = hashMapOf(
                                                    "name"  to profileName,
                                                    "email" to profileEmail,
                                                    "phone" to user.phoneNumber,
                                                    "uid"   to user.uid
                                                )
                                                FirebaseFirestore.getInstance()
                                                    .collection("users").document(user.uid)
                                                    .set(userData).await()
                                                AuthManager.refreshAdminStatus()
                                                syncFCMTokenAndNavigate(auth, navController) { isLoading = false }
                                            } catch (e: Exception) {
                                                isLoading = false; errorMessage = "Failed to save profile: ${e.localizedMessage}"; isErrorVisible = true
                                            }
                                        }
                                    }
                                } else if (isCodeSent) {
                                    if (otpCode.isNotBlank()) {
                                        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                                        auth.signInWithCredential(credential)
                                            .addOnSuccessListener { authResult ->
                                                if (authResult.additionalUserInfo?.isNewUser == true) {
                                                    isLoading = false; isProfileSetupStep = true
                                                } else {
                                                    scope.launch {
                                                        val user = auth.currentUser
                                                        if (user != null) {
                                                            val userData = hashMapOf(
                                                                "uid" to user.uid,
                                                                "phoneNumber" to (user.phoneNumber ?: "")
                                                            )
                                                            FirebaseFirestore.getInstance()
                                                                .collection("users").document(user.uid)
                                                                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                                                .await()
                                                        }
                                                        AuthManager.refreshAdminStatus()
                                                        syncFCMTokenAndNavigate(auth, navController) { isLoading = false }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                isLoading = false; errorMessage = context.getString(R.string.error_invalid_code); isErrorVisible = true
                                            }
                                    } else {
                                        isLoading = false; errorMessage = "Please enter the code."; isErrorVisible = true
                                    }
                                } else if (selectedTab == 0) {
                                    if (email.isNotBlank() && password.isNotBlank()) {
                                        if (isLoginMode) {
                                            auth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener {
                                                    scope.launch {
                                                        val user = auth.currentUser
                                                        if (user != null) {
                                                            val userData = hashMapOf(
                                                                "uid" to user.uid,
                                                                "email" to (user.email ?: "")
                                                            )
                                                            FirebaseFirestore.getInstance()
                                                                .collection("users").document(user.uid)
                                                                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                                                .await()
                                                        }
                                                        AuthManager.refreshAdminStatus()
                                                        syncFCMTokenAndNavigate(auth, navController) { isLoading = false }
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    isLoading = false; errorMessage = context.getString(R.string.error_login_failed, e.localizedMessage); isErrorVisible = true
                                                }
                                        } else {
                                            if (name.isBlank()) {
                                                isLoading = false; errorMessage = "Please enter your name."; isErrorVisible = true
                                            } else if (password != confirmPassword) {
                                                isLoading = false; errorMessage = context.getString(R.string.error_password_mismatch); isErrorVisible = true
                                            } else {
                                                auth.createUserWithEmailAndPassword(email, password)
                                                    .addOnSuccessListener { result ->
                                                        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                                                        result.user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                                            scope.launch {
                                                                val user = auth.currentUser
                                                                if (user != null) {
                                                                    val userData = hashMapOf(
                                                                        "uid" to user.uid,
                                                                        "name" to name,
                                                                        "email" to email
                                                                    )
                                                                    FirebaseFirestore.getInstance()
                                                                        .collection("users").document(user.uid)
                                                                        .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                                                        .await()
                                                                }
                                                                AuthManager.refreshAdminStatus()
                                                                syncFCMTokenAndNavigate(auth, navController) { isLoading = false }
                                                            }
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isLoading = false; errorMessage = context.getString(R.string.error_registration_failed, e.localizedMessage); isErrorVisible = true
                                                    }
                                            }
                                        }
                                    } else {
                                        isLoading = false; errorMessage = "Please fill in all fields."; isErrorVisible = true
                                    }
                                } else {
                                    if (phone.isNotBlank()) {
                                        val activity = context.findActivity()
                                        if (activity == null) {
                                            isLoading = false; errorMessage = "Could not find activity context"; isErrorVisible = true
                                        } else {
                                            val cleanPhone     = if (phone.startsWith("0")) phone.substring(1) else phone
                                            val fullPhoneNumber = "+63$cleanPhone"
                                            val options = PhoneAuthOptions.newBuilder(auth)
                                                .setPhoneNumber(fullPhoneNumber)
                                                .setTimeout(60L, TimeUnit.SECONDS)
                                                .setActivity(activity)
                                                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                                        auth.signInWithCredential(credential).addOnSuccessListener {
                                                            scope.launch {
                                                                val user = auth.currentUser
                                                                if (user != null) {
                                                                    val userData = hashMapOf(
                                                                        "uid" to user.uid,
                                                                        "phoneNumber" to (user.phoneNumber ?: fullPhoneNumber)
                                                                    )
                                                                    FirebaseFirestore.getInstance()
                                                                        .collection("users").document(user.uid)
                                                                        .set(userData, com.google.firebase.firestore.SetOptions.merge())
                                                                        .await()
                                                                }
                                                                AuthManager.refreshAdminStatus()
                                                                isLoading = false
                                                                navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                                            }
                                                        }
                                                    }
                                                    override fun onVerificationFailed(e: FirebaseException) {
                                                        isLoading = false; errorMessage = context.getString(R.string.error_verification_failed, e.message); isErrorVisible = true
                                                    }
                                                    override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                                        isLoading = false; verificationId = vId; isCodeSent = true
                                                    }
                                                })
                                                .build()
                                            PhoneAuthProvider.verifyPhoneNumber(options)
                                        }
                                    } else {
                                        isLoading = false; errorMessage = "Please enter a phone number."; isErrorVisible = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CityGreen,
                                contentColor   = CityWhite
                            )
                        ) {
                            Text(
                                text = when {
                                    isProfileSetupStep -> "Save & Continue"
                                    isCodeSent         -> stringResource(R.string.verify_code)
                                    isLoginMode        -> stringResource(R.string.login_title)
                                    else               -> stringResource(R.string.create_account_title)
                                },
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // ── Google & toggle (only on initial step) ─────────
                        if (!isCodeSent && !isProfileSetupStep) {

                            Spacer(Modifier.height(12.dp))

                            // OR divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = CityBrown.copy(alpha = 0.15f))
                                Text(
                                    "  OR  ",
                                    fontSize = 11.sp,
                                    color    = CityBrown.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = CityBrown.copy(alpha = 0.15f))
                            }

                            Spacer(Modifier.height(12.dp))

                            // Google button
                            OutlinedButton(
                                onClick = { isLoading = true; googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(CityBrown.copy(alpha = 0.3f), CityBrown.copy(alpha = 0.3f)))
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CityBrown)
                            ) {
                                Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(20.dp), tint = CityGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.sign_in_google), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }

                            Spacer(Modifier.height(16.dp))

                            // Toggle login / signup
                            TextButton(onClick = {
                                isLoginMode    = !isLoginMode
                                isErrorVisible = false
                                errorMessage   = ""
                                confirmPassword= ""
                            }) {
                                Text(
                                    text = if (isLoginMode) stringResource(R.string.toggle_to_signup)
                                           else             stringResource(R.string.toggle_to_login),
                                    color      = CityGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity      -> this
    is ContextWrapper -> baseContext.findActivity()
    else             -> null
}
