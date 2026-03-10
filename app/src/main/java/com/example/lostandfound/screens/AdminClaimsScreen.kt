package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.lostandfound.model.Claim

import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminClaimsScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    var claims by remember { mutableStateOf<List<Claim>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Fetch Pending Claims
    LaunchedEffect(Unit) {
        db.collection("claims")
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { snapshot ->
                claims = snapshot.documents.mapNotNull { it.toObject(Claim::class.java)?.copy(id = it.id) }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    fun updateStatus(claim: Claim, newStatus: String) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "reviewedBy" to (currentUser?.uid ?: ""),
            "reviewerEmail" to (currentUser?.email ?: "")
        )
        db.collection("claims").document(claim.id).update(updates)
            .addOnSuccessListener {
                claims = claims.filter { it.id != claim.id } // Remove from waiting list
                Toast.makeText(context, "Claim $newStatus", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MANAGE CLAIMS", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (navController.previousBackStackEntry != null && 
                            navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                            navController.popBackStack() 
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (claims.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No pending claims to review.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(claims) { claim ->
                    ClaimReviewCard(
                        claim = claim,
                        onApprove = { updateStatus(claim, "APPROVED") },
                        onReject = { updateStatus(claim, "REJECTED") },
                        onMessage = {
                            val userName = claim.userEmail.substringBefore("@")
                            navController.navigate("chat/${claim.userId}/${userName}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClaimReviewCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit, onMessage: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Claimant: ${claim.userEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Proof Provided:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(claim.proofDescription, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Message")
                }
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}
