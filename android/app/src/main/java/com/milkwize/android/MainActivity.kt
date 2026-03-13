package com.milkwize.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// --- MilkWize Vibrant Dashboard Palette ---
val RoyalBlue = Color(0xFF002366)
val ElectricBlue = Color(0xFF2962FF)
val DeepPurple = Color(0xFF6200EA)
val SuccessGreen = Color(0xFF00C853)
val AlertRed = Color(0xFFD50000)
val PureWhite = Color(0xFFFFFFFF)
val JetBlack = Color(0xFF121212)
val SlateGray = Color(0xFF455A64)
val SurfaceGray = Color(0xFFF0F2F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = SurfaceGray) {
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
            
            var allSuccessful = true
            pending.forEach { localEvent ->
                try {
                    val supabaseEvent = MilkingEvent(
                        cowId = localEvent.cowId,
                        ownerId = localEvent.ownerId,
                        recordedBy = localEvent.ownerId,
                        milkLiters = localEvent.milkLiters,
                        milkingTime = localEvent.timestamp // Ensure timestamp is synced
                    )
                    // Explicitly calling insert and awaiting
                    supabase.postgrest["milking_events"].insert(supabaseEvent)
                    
                    milkingDao.update(localEvent.copy(isSynced = true))
                    Log.d("Sync", "Successfully synced event for cow ${localEvent.cowId}")
                } catch (e: Exception) {
                    allSuccessful = false
                    Log.e("SyncError", "Failed to sync record: ${e.message}", e)
                }
            }
            allSuccessful
        } catch (e: Exception) {
            Log.e("SyncError", "Batch sync failed: ${e.message}", e)
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
        var statusMessage by remember { mutableStateOf("Systems Active") }
        
        var showAddCowDialog by remember { mutableStateOf(false) }
        var showManageHerdDialog by remember { mutableStateOf(false) }
        
        var isSyncing by remember { mutableStateOf(false) }
        var showSyncSuccess by remember { mutableStateOf(false) }

        // Red when storage is holding data, Green when cloud is synced
        val statusColor by animateColorAsState(
            if (unsyncedCount > 0) AlertRed else SuccessGreen,
            label = "statusColor"
        )

        Scaffold(
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = { showManageHerdDialog = true },
                        containerColor = PureWhite,
                        contentColor = DeepPurple,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, "Herd")
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = { showAddCowDialog = true },
                        containerColor = RoyalBlue,
                        contentColor = PureWhite
                    ) {
                        Icon(Icons.Default.Add, "Register Cow")
                    }
                }
            },
            topBar = {
                Surface(shadowElevation = 8.dp, color = RoyalBlue) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MilkWize", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = PureWhite))
                            Text(user?.email ?: "Farmer Account", style = MaterialTheme.typography.bodySmall, color = PureWhite.copy(alpha = 0.8f))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Smart Status Hub
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .clickable {
                                        if (unsyncedCount > 0 && !isSyncing) {
                                            scope.launch {
                                                isSyncing = true
                                                statusMessage = "Uploading to Cloud..."
                                                val success = syncPendingRecords(currentUserId, milkingDao, SupabaseClient.client)
                                                isSyncing = false
                                                if (success) {
                                                    showSyncSuccess = true
                                                    statusMessage = "Cloud Backup Secure"
                                                    delay(2000)
                                                    showSyncSuccess = false
                                                } else {
                                                    statusMessage = "Sync Error - Check Internet"
                                                }
                                            }
                                        }
                                    },
                                color = statusColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PureWhite)
                                    } else {
                                        Icon(
                                            imageVector = if (showSyncSuccess) Icons.Default.CheckCircle 
                                                         else if (unsyncedCount > 0) Icons.Default.CloudSync 
                                                         else Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = PureWhite,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (unsyncedCount > 0) "SYNC ($unsyncedCount)" else "SECURED",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = PureWhite)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            SupabaseClient.client.auth.signOut()
                                            db.clearAllTables()
                                        }
                                        isLoggedIn = false
                                    } catch (e: Exception) {
                                        statusMessage = "Logout Error: ${e.localizedMessage}"
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = PureWhite)
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
                        Log.e("Supabase", "Fetch cows error: ${e.message}")
                    }
                }
            }

            val totalLiters = localEvents.sumOf { it.milkLiters }
            val uniqueCows = localEvents.map { it.cowId }.distinct().size

            Column(modifier = Modifier.padding(paddingValues).padding(20.dp)) {
                
                // Analytics High-Visibility Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryStatCard("TOTAL PRODUCTION", "${"%.1f".format(totalLiters)} L", ElectricBlue, Icons.Default.WaterDrop, Modifier.weight(1f))
                    SummaryStatCard("HERD IN VIEW", "$uniqueCows", SuccessGreen, Icons.Default.Pets, Modifier.weight(1f))
                }

                Spacer(Modifier.height(24.dp))

                // Log Record Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Record New Yield", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = JetBlack))
                        Spacer(Modifier.height(16.dp))

                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCow?.name ?: "Choose Cow",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Source Cattle", color = JetBlack, fontWeight = FontWeight.Bold) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = JetBlack, fontWeight = FontWeight.Bold)
                            )

                            ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                                cowList.forEach { cow ->
                                    DropdownMenuItem(
                                        text = { Text(cow.name, fontWeight = FontWeight.Bold, color = JetBlack) },
                                        onClick = { selectedCow = cow; isExpanded = false }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newAmount,
                            onValueChange = { newAmount = it },
                            label = { Text("Liters Collected", color = JetBlack, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            prefix = { Text("🥛 ", fontSize = 20.sp) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = JetBlack, fontWeight = FontWeight.Bold)
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
                                            statusMessage = "Saved to Tablet ✅"
                                            newAmount = ""

                                            // Attempt Immediate Sync
                                            try {
                                                val supabaseEvent = MilkingEvent(
                                                    cowId = cowId, ownerId = currentUserId, 
                                                    recordedBy = currentUserId, milkLiters = amount,
                                                    milkingTime = timestamp
                                                )
                                                withContext(Dispatchers.IO) {
                                                    SupabaseClient.client.postgrest["milking_events"].insert(supabaseEvent)
                                                    milkingDao.update(localEvent.copy(isSynced = true))
                                                }
                                                statusMessage = "Cloud Data Secure ☁️"
                                            } catch (e: Exception) {
                                                statusMessage = "Stored Locally (Queueing Sync) 📶"
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Error: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 24.dp),
                            enabled = !isLoading && selectedCow != null && newAmount.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            if (isLoading) CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                            else Text("CONFIRM ENTRY", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        }
                    }
                }

                Text(statusMessage, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (statusMessage.contains("Error")) AlertRed else SlateGray, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))

                Spacer(Modifier.height(24.dp))

                Text("DAILY JOURNAL", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = JetBlack, letterSpacing = 1.sp))
                
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(localEvents) { event ->
                        ModernEventCard(event, milkingDao, scope)
                    }
                }
            }
        }

        // --- Herd Management Dialog (Update/Delete) ---
        if (showManageHerdDialog) {
            ManageHerdDialog(
                cows = cowList,
                onDismiss = { showManageHerdDialog = false },
                onUpdate = { cow, newName ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                SupabaseClient.client.postgrest["cows"].update(mapOf("tag" to newName)) {
                                    filter { eq("id", cow.id) }
                                }
                                cowList = SupabaseClient.client.postgrest["cows"]
                                    .select { filter { eq("owner_id", currentUserId) } }
                                    .decodeList<Cow>()
                            }
                        } catch (e: Exception) {
                            statusMessage = "Update Failed: ${e.message}"
                        }
                    }
                },
                onDelete = { cow ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                SupabaseClient.client.postgrest["cows"].delete { filter { eq("id", cow.id) } }
                            }
                            cowList = cowList.filter { it.id != cow.id }
                            if (selectedCow?.id == cow.id) selectedCow = null
                        } catch (e: Exception) {
                            statusMessage = "Delete Failed: ${e.message}"
                        }
                    }
                }
            )
        }

        if (showAddCowDialog) {
            AddCowDialog(
                onDismiss = { showAddCowDialog = false },
                onCowAdded = {
                    showAddCowDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            cowList = SupabaseClient.client.postgrest["cows"]
                                .select { filter { eq("owner_id", currentUserId) } }
                                .decodeList<Cow>()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SummaryStatCard(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = SlateGray)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = color))
        }
    }
}

@Composable
fun ManageHerdDialog(cows: List<Cow>, onDismiss: () -> Unit, onUpdate: (Cow, String) -> Unit, onDelete: (Cow) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Herd Management", fontWeight = FontWeight.Black, color = JetBlack) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(cows) { cow ->
                    var isEditing by remember { mutableStateOf(false) }
                    var editedTag by remember { mutableStateOf(cow.name) }
                    
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                OutlinedTextField(value = editedTag, onValueChange = { editedTag = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                                IconButton(onClick = { onUpdate(cow, editedTag); isEditing = false }) { Icon(Icons.Default.Save, "Save", tint = SuccessGreen) }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cow.name, fontWeight = FontWeight.Black, color = JetBlack, fontSize = 18.sp)
                                    Text(cow.breed ?: "Common Cattle", style = MaterialTheme.typography.bodySmall, color = SlateGray, fontWeight = FontWeight.Bold)
                                }
                                Row {
                                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, "Edit", tint = ElectricBlue) }
                                    IconButton(onClick = { onDelete(cow) }) { Icon(Icons.Default.Delete, "Remove", tint = AlertRed) }
                                }
                            }
                        }
                        HorizontalDivider(color = SurfaceGray)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", fontWeight = FontWeight.Bold, color = JetBlack) } }
    )
}

@Composable
fun AddCowDialog(onDismiss: () -> Unit, onCowAdded: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Cow", fontWeight = FontWeight.Black, color = RoyalBlue) },
        text = {
            Column {
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Ear Tag / Name", fontWeight = FontWeight.Bold) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text("Breed Type", fontWeight = FontWeight.Bold) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
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
                },
                enabled = !isLoading && tag.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ADD TO SYSTEM", fontWeight = FontWeight.Bold)
            }
        }
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

    Box(modifier = Modifier.fillMaxSize().background(RoyalBlue).padding(24.dp)) {
        Card(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = PureWhite)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isRegistering) "Farm Creation" else "Farmer Sign In", 
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = RoyalBlue)
                
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email", fontWeight = FontWeight.Bold) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password", fontWeight = FontWeight.Bold) }, 
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp))

                Button(onClick = {
                    isLoading = true
                    scope.launch {
                        try {
                            if (isRegistering) {
                                SupabaseClient.client.auth.signUpWith(Email) { this.email = email; this.password = password }
                                errorMessage = "Account created. Please log in."
                                isRegistering = false
                            } else {
                                SupabaseClient.client.auth.signInWith(Email) { this.email = email; this.password = password }
                                onLoginSuccess()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Network error"
                        } finally { isLoading = false }
                    }
                }, modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 24.dp), enabled = !isLoading, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)) {
                    if (isLoading) CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                    else Text(if (isRegistering) "JOIN SYSTEM" else "LOG IN", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { isRegistering = !isRegistering }) {
                    Text(if (isRegistering) "Member already? Login" else "New farmer? Join here", color = DeepPurple, fontWeight = FontWeight.Black)
                }
                if (errorMessage.isNotEmpty()) Text(errorMessage, color = AlertRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ModernEventCard(event: LocalEvent, milkingDao: MilkingDao, scope: kotlinx.coroutines.CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("COW ID: ...${event.cowId.takeLast(6)}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = SlateGray))
                Text("${event.milkLiters} LITERS", style = MaterialTheme.typography.headlineSmall, color = RoyalBlue, fontWeight = FontWeight.Black)
                Text("${event.timestamp.take(16).replace("T", " ")}", style = MaterialTheme.typography.bodySmall, color = JetBlack, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { milkingDao.delete(event) }
                    }
                }) { Icon(Icons.Default.Delete, "Delete", tint = AlertRed.copy(alpha = 0.6f)) }

                Icon(
                    imageVector = if (event.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (event.isSynced) SuccessGreen else AlertRed,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
