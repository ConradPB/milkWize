package com.milkwize.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.milkwize.android.ui.theme.AndroidTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

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

suspend fun syncPendingRecords(milkingDao: MilkingDao, supabase: io.github.jan.supabase.SupabaseClient) {
    val pending = milkingDao.getAllUnsynced()
    pending.forEach { localEvent ->
        try {
            val supabaseEvent = MilkingEvent(
                cowId = localEvent.cowId,
                ownerId = localEvent.ownerId,
                milkLiters = localEvent.milkLiters
            )
            supabase.postgrest["milking_events"].insert(supabaseEvent)
            milkingDao.update(localEvent.copy(isSynced = true))
        } catch (e: Exception) {
            return@forEach
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
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val milkingDao = db.milkingDao()

        val localEvents by milkingDao.getAllLocally().collectAsState(initial = emptyList())
        val unsyncedCount by milkingDao.getUnsyncedCount().collectAsState(initial = 0)
        
        var events by remember { mutableStateOf(listOf<MilkingEvent>()) }
        var cowList by remember { mutableStateOf(listOf<Cow>()) }
        var selectedCow by remember { mutableStateOf<Cow?>(null) }
        var isExpanded by remember { mutableStateOf(false) }
        var newAmount by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }

        LaunchedEffect(Unit) {
            try {
                cowList = SupabaseClient.client.postgrest["cows"].select().decodeList<Cow>()
                events = SupabaseClient.client.postgrest["milking_events"].select().decodeList<MilkingEvent>()
                statusMessage = "Data loaded."
            } catch (e: Exception) {
                statusMessage = "Load Error: ${e.localizedMessage}"
            }
        }

        val totalLiters = localEvents.sumOf { it.milkLiters }
        val uniqueCows = localEvents.map { it.cowId }.distinct().size

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MilkWize", style = MaterialTheme.typography.headlineLarge)

                Row {
                    if (unsyncedCount > 0) {
                        IconButton(onClick = {
                            scope.launch {
                                statusMessage = "Syncing..."
                                syncPendingRecords(milkingDao, SupabaseClient.client)
                                statusMessage = "Sync complete."
                            }
                        }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Now", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    IconButton(onClick = {
                        scope.launch {
                            SupabaseClient.client.auth.signOut()
                            // Clear local cache securely
                            db.clearAllTables()
                            isLoggedIn = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (unsyncedCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Manager", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$unsyncedCount records are only on this tablet.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    syncPendingRecords(milkingDao, SupabaseClient.client)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Liters", style = MaterialTheme.typography.labelMedium)
                        Text("${"%.1f".format(totalLiters)} L", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Unique Cows", style = MaterialTheme.typography.labelMedium)
                        Text("$uniqueCows", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log Daily Yield", style = MaterialTheme.typography.titleMedium)

                    ExposedDropdownMenuBox(
                        expanded = isExpanded,
                        onExpandedChange = { isExpanded = !isExpanded },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedCow?.name ?: "Select Cow",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cow Name") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isExpanded,
                            onDismissRequest = { isExpanded = false }
                        ) {
                            cowList.forEach { cow ->
                                DropdownMenuItem(
                                    text = { Text(cow.name) },
                                    onClick = {
                                        selectedCow = cow
                                        isExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("Liters") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )

                    Button(
                        onClick = {
                            val amount = newAmount.toDoubleOrNull()
                            val cowId = selectedCow?.id
                            val ownerId = SupabaseClient.client.auth.currentUserOrNull()?.id
                            if (cowId != null && amount != null && ownerId != null) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val timestamp = java.time.OffsetDateTime.now().toString()
                                        val localEvent = LocalEvent(
                                            cowId = cowId,
                                            ownerId = ownerId,
                                            milkLiters = amount,
                                            timestamp = timestamp
                                        )
                                        milkingDao.insert(localEvent)

                                        statusMessage = "Saved to Tablet ✅"
                                        newAmount = ""

                                        try {
                                            val supabaseEvent = MilkingEvent(cowId = cowId, ownerId = ownerId, milkLiters = amount)
                                            SupabaseClient.client.postgrest["milking_events"].insert(supabaseEvent)
                                            milkingDao.update(localEvent.copy(isSynced = true))
                                            statusMessage = "Synced to Cloud ☁️"
                                        } catch (e: Exception) {
                                            statusMessage = "Saved locally (Offline) 📶"
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Local Save Error: ${e.localizedMessage}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Saving..." else "Submit Record")
                    }
                }
            }

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusMessage.contains("Error")) Color.Red else Color.Unspecified,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TextButton(onClick = {
                scope.launch {
                    try {
                        events = SupabaseClient.client.postgrest["milking_events"].select().decodeList<MilkingEvent>()
                        statusMessage = "History refreshed from Cloud."
                    } catch (e: Exception) {
                        statusMessage = "Fetch Error: ${e.localizedMessage}"
                    }
                }
            }) {
                Text("Refresh Cloud History")
            }

            LazyColumn {
                items(localEvents) { event ->
                    LocalMilkingEventCard(event)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRegistering) "Register Your Farm" else "MilkWize Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        if (isRegistering) {
                            SupabaseClient.client.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            errorMessage = "Registration successful! Please login."
                            isRegistering = false
                        } else {
                            SupabaseClient.client.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            onLoginSuccess()
                        }
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "An error occurred"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Processing..." else if (isRegistering) "Create Account" else "Login")
        }

        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(if (isRegistering) "Already have an account? Login" else "New Farmer? Create an Account")
        }

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun LocalMilkingEventCard(event: LocalEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cow: ...${event.cowId.takeLast(6)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${event.milkLiters} L",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Logged: ${event.timestamp.take(16).replace("T", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Icon(
                imageVector = if (event.isSynced)
                    Icons.Default.CloudDone
                else
                    Icons.Default.CloudOff,
                contentDescription = if (event.isSynced) "Synced" else "Pending Sync",
                tint = if (event.isSynced) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
