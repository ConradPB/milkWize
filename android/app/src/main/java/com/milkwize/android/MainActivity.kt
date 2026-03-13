package com.milkwize.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkwize.android.ui.theme.AndroidTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MilkingDashboard()
                }
            }
        }
    }
}

suspend fun syncPendingRecords(userId: String, milkingDao: MilkingDao, supabase: io.github.jan.supabase.SupabaseClient): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val pending = milkingDao.getAllUnsynced(userId)
            if (pending.isEmpty()) return@withContext true
            
            pending.forEach { localEvent ->
                val supabaseEvent = MilkingEvent(
                    cowId = localEvent.cowId,
                    ownerId = localEvent.ownerId,
                    recordedBy = localEvent.ownerId,
                    milkLiters = localEvent.milkLiters
                )
                supabase.postgrest["milking_events"].insert(supabaseEvent)
                milkingDao.update(localEvent.copy(isSynced = true))
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkingDashboard() {
    var isLoggedIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val milkingDao = db.milkingDao()

        val user = SupabaseClient.client.auth.currentUserOrNull()
        val currentUserId = user?.id ?: ""

        val localEvents by milkingDao.getAllLocally(currentUserId).collectAsState(initial = emptyList())
        val unsyncedCount by milkingDao.getUnsyncedCount(currentUserId).collectAsState(initial = 0)
        
        var cowList by remember { mutableStateOf(listOf<Cow>()) }
        var selectedCow by remember { mutableStateOf<Cow?>(null) }
        var isExpanded by remember { mutableStateOf(false) }
        var newAmount by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }
        var showAddCowDialog by remember { mutableStateOf(false) }
        
        var isSyncing by remember { mutableStateOf(false) }
        var showSyncSuccess by remember { mutableStateOf(false) }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddCowDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, contentDescription = "Add Cow", tint = Color.White)
                }
            },
            topBar = {
                Surface(shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MilkWize", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                            Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Sync Logic UI
                            if (showSyncSuccess) {
                                Icon(Icons.Default.CheckCircle, "Synced", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                            } else if (unsyncedCount > 0) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = {
                                        scope.launch {
                                            isSyncing = true
                                            val success = syncPendingRecords(currentUserId, milkingDao, SupabaseClient.client)
                                            isSyncing = false
                                            if (success) {
                                                showSyncSuccess = true
                                                delay(2000)
                                                showSyncSuccess = false
                                            } else {
                                                statusMessage = "Sync failed - no connection"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Sync, contentDescription = "Sync Now", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            
                            Spacer(Modifier.width(8.dp))
                            
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            SupabaseClient.client.auth.signOut()
                                            db.clearAllTables() // Fixed thread issue
                                        }
                                        isLoggedIn = false
                                    } catch (e: Exception) {
                                        statusMessage = "Logout Error: ${e.localizedMessage}"
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            LaunchedEffect(currentUserId) {
                if (currentUserId.isNotEmpty()) {
                    try {
                        withContext(Dispatchers.IO) {
                            cowList = SupabaseClient.client.postgrest["cows"]
                                .select { filter { eq("owner_id", currentUserId) } }
                                .decodeList<Cow>()
                        }
                    } catch (e: Exception) {
                        statusMessage = "Load Error: ${e.localizedMessage}"
                    }
                }
            }

            val totalLiters = localEvents.sumOf { it.milkLiters }
            val uniqueCows = localEvents.map { it.cowId }.distinct().size

            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                
                // Analytics Section
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Yield", style = MaterialTheme.typography.labelMedium)
                            Text("${"%.1f".format(totalLiters)} L", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Active Cows", style = MaterialTheme.typography.labelMedium)
                            Text("$uniqueCows", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Log Section
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Log Production", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        
                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedCow?.name ?: "Select Cow",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cow Name") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )

                            ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                                cowList.forEach { cow ->
                                    DropdownMenuItem(
                                        text = { Text(cow.name) },
                                        onClick = { selectedCow = cow; isExpanded = false }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newAmount,
                            onValueChange = { newAmount = it },
                            label = { Text("Liters") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            prefix = { Text("🥛 ") }
                        )

                        Button(
                            onClick = {
                                val amount = newAmount.toDoubleOrNull()
                                val cowId = selectedCow?.id
                                if (cowId != null && amount != null && currentUserId.isNotEmpty()) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val timestamp = OffsetDateTime.now().toString()
                                            val localEvent = LocalEvent(
                                                cowId = cowId, ownerId = currentUserId, recordedBy = currentUserId,
                                                milkLiters = amount, timestamp = timestamp
                                            )
                                            withContext(Dispatchers.IO) { milkingDao.insert(localEvent) }
                                            statusMessage = "Saved Locally ✅"
                                            newAmount = ""

                                            try {
                                                val supabaseEvent = MilkingEvent(cowId = cowId, ownerId = currentUserId, recordedBy = currentUserId, milkLiters = amount)
                                                withContext(Dispatchers.IO) {
                                                    SupabaseClient.client.postgrest["milking_events"].insert(supabaseEvent)
                                                    milkingDao.update(localEvent.copy(isSynced = true))
                                                }
                                                statusMessage = "Synced Successfully ☁️"
                                            } catch (e: Exception) {
                                                statusMessage = "Stored (Offline Mode) 📶"
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Save Error: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
                            enabled = !isLoading && selectedCow != null && newAmount.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("Submit Entry", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = if (statusMessage.contains("Error")) Color.Red else Color.Gray, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Recent Activity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(localEvents) { event ->
                        LocalMilkingEventCard(event, milkingDao, scope)
                    }
                }
            }
        }

        if (showAddCowDialog) {
            AddCowDialog(
                onDismiss = { showAddCowDialog = false },
                onCowAdded = {
                    showAddCowDialog = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                cowList = SupabaseClient.client.postgrest["cows"]
                                    .select { filter { eq("owner_id", currentUserId) } }
                                    .decodeList<Cow>()
                            }
                        } catch (e: Exception) { }
                    }
                }
            )
        }
    }
}

@Composable
fun AddCowDialog(onDismiss: () -> Unit, onCowAdded: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Cow", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Tag ID / Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text("Breed (Optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                val ownerId = SupabaseClient.client.auth.currentUserOrNull()?.id
                if (tag.isNotEmpty() && ownerId != null) {
                    isLoading = true
                    scope.launch {
                        try {
                            val newCow = Cow(id = UUID.randomUUID().toString(), ownerId = ownerId, name = tag, breed = breed)
                            withContext(Dispatchers.IO) { SupabaseClient.client.postgrest["cows"].insert(newCow) }
                            onCowAdded()
                        } catch (e: Exception) { } finally { isLoading = false }
                    }
                }
            }, enabled = !isLoading) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(24.dp)) {
        Card(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isRegistering) "Farm Registration" else "MilkWize Farmer Portal", 
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, 
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

                Button(onClick = {
                    isLoading = true
                    scope.launch {
                        try {
                            if (isRegistering) {
                                SupabaseClient.client.auth.signUpWith(Email) { this.email = email; this.password = password }
                                errorMessage = "Success! Please login."
                                isRegistering = false
                            } else {
                                SupabaseClient.client.auth.signInWith(Email) { this.email = email; this.password = password }
                                onLoginSuccess()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Error connecting"
                        } finally { isLoading = false }
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 24.dp), enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text(if (isRegistering) "Create Account" else "Sign In", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { isRegistering = !isRegistering }) {
                    Text(if (isRegistering) "Already have an account? Login" else "New Farmer? Register Now")
                }
                if (errorMessage.isNotEmpty()) Text(errorMessage, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun LocalMilkingEventCard(event: LocalEvent, milkingDao: MilkingDao, scope: kotlinx.coroutines.CoroutineScope) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Cow: ...${event.cowId.takeLast(6)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("${event.milkLiters} Liters", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Text("Logged: ${event.timestamp.take(16).replace("T", " ")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (event.isSynced) {
                                try { SupabaseClient.client.postgrest["milking_events"].delete { filter { eq("id", event.id) } } } catch (e: Exception) {}
                            }
                            milkingDao.delete(event)
                        }
                    }
                }) { Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray) }

                Icon(
                    imageVector = if (event.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = if (event.isSynced) "Synced" else "Pending",
                    tint = if (event.isSynced) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
