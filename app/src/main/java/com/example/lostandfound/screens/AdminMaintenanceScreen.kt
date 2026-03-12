package com.example.lostandfound.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.lostandfound.ui.theme.CityTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMaintenanceScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Defined here to persist after dialog closes
    var isProcessing by remember { mutableStateOf(false) }
    var currentAction by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var targetCollection by remember { mutableStateOf("") }
    var targetLabel by remember { mutableStateOf("") }

    val collections = listOf(
        "messages" to "All Chat Messages",
        "lost_items" to "All Lost Item Reports",
        "found_items" to "All Found Item Reports",
        "claims" to "All Claims",
        "match_notifications" to "All Match Notifications",
        "claim_notifications" to "All Claim Notifications"
    )

    suspend fun clearCollection(collectionName: String) {
        isProcessing = true
        try {
            val snapshot = db.collection(collectionName).get().await()
            if (snapshot.isEmpty) {
                Toast.makeText(context, "Collection $collectionName is already empty", Toast.LENGTH_SHORT).show()
                return
            }

            // Batch delete in chunks of 50 to avoid Firestore limits
            val docs = snapshot.documents
            var deletedCount = 0
            
            docs.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
                deletedCount += chunk.size
            }

            Toast.makeText(context, "Successfully deleted $deletedCount documents from $collectionName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isProcessing = false
        }
    }

    Scaffold(
        containerColor = CityTheme.Cream,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SYSTEM MAINTENANCE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CityTheme.White)
                        Text("Danger Zone", fontSize = 11.sp, color = CityTheme.GoldLight)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CityTheme.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CityTheme.Error)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CityTheme.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CityTheme.Error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Danger Zone", fontWeight = FontWeight.Bold, color = CityTheme.Error, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Actions here are permanent and cannot be undone. These tools are provided for system reset and cleaning during testing.",
                        fontSize = 13.sp,
                        color = CityTheme.Brown.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            collections.forEach { (collection, label) ->
                MaintenanceActionCard(
                    label = label,
                    collection = collection,
                    isEnabled = !isProcessing,
                    onClear = {
                        targetCollection = collection
                        targetLabel = label
                        confirmText = ""
                        showConfirmDialog = true
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (isProcessing) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = CityTheme.Error)
                Text("Processing deletion...", modifier = Modifier.padding(top = 8.dp), color = CityTheme.Error, fontSize = 12.sp)
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { if (!isProcessing) showConfirmDialog = false },
                title = { Text("Confirm Deletion", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("You are about to delete ALL data from '$targetLabel'. This action is IRREVERSIBLE.")
                        Spacer(Modifier.height(16.dp))
                        Text("Type 'RESET' below to confirm:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type RESET here") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CityTheme.Error,
                                cursorColor = CityTheme.Error
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (confirmText == "RESET") {
                                showConfirmDialog = false
                                scope.launch { clearCollection(targetCollection) }
                            }
                        },
                        enabled = confirmText == "RESET" && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error)
                    ) {
                        Text("Delete Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }, enabled = !isProcessing) {
                        Text("Cancel", color = CityTheme.Brown)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun MaintenanceActionCard(label: String, collection: String, isEnabled: Boolean, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CityTheme.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, color = CityTheme.Brown)
                Text("Collection: $collection", fontSize = 11.sp, color = CityTheme.Brown.copy(alpha = 0.5f))
            }
            Button(
                onClick = onClear,
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = CityTheme.Error.copy(alpha = 0.1f), contentColor = CityTheme.Error),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", fontSize = 12.sp)
            }
        }
    }
}
