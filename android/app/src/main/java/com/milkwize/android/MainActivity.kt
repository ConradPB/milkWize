package com.milkwize.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// --- MilkWize Brand Palette ---
val RoyalBlue = Color(0xFF002366)
val DeepPurple = Color(0xFF4B0082)
val ElectricBlue = Color(0xFF2962FF)
val SuccessGreen = Color(0xFF1B5E20)
val AlertRed = Color(0xFFB71C1C)
val PureWhite = Color(0xFFFFFFFF)
val JetBlack = Color(0xFF000000)
val BackgroundGray = Color(0xFFF2F4F7)
val HighContrastGray = Color(0xFF333333)
val BorderGrayColor = Color(0xFFB0BEC5)

// --- Welcoming Nature Palette (Login/Register) ---
val MilkWhite = Color(0xFFF9FBF9)
val PaperWhite = Color(0xFFFFFFFF)
val ForestGreen = Color(0xFF2D5A27)
val EarthySlate = Color(0xFF2C3E50)
val WarmGray = Color(0xFF7F8C8D)
val SunlitAmber = Color(0xFFF39C12)
val TerracottaRed = Color(0xFFC0392B)
val WarmCream = Color(0xFFFFFDF5)
val SoftPeach = Color(0xFFFFE0B2)
val GoldenAmber = Color(0xFFFFB300)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundGray) {
                    MilkingDashboard()
                }
            }
        }
    }
}

suspend fun syncPendingRecords(userId: String, milkingDao: MilkingDao, supabase: io.github.jan.supabase.SupabaseClient): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            val pending = milkingDao.getAllUnsynced(userId)
            if (pending.isEmpty()) return@withContext Result.success(Unit)
            
            pending.forEach { localEvent ->
                try {
                    val supabaseEvent = MilkingEvent(
                        cowId = localEvent.cowId,
                        ownerId = localEvent.ownerId,
                        recordedBy = localEvent.ownerId,
                        milkLiters = localEvent.milkLiters,
                        milkingTime = localEvent.timestamp
                    )
                    supabase.postgrest["milking_events"].insert(supabaseEvent)
                    milkingDao.update(localEvent.copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e("SyncError", "Failed record ${localEvent.id}: ${e.localizedMessage}")
                    return@withContext Result.failure(e)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

        val statusColor by animateColorAsState(
            if (unsyncedCount > 0) AlertRed else SuccessGreen,
            label = "statusColor"
        )

        Scaffold(
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = { showManageHerdDialog = true },
                        containerColor = DeepPurple,
                        contentColor = PureWhite,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, "Herd")
                    }
                    Spacer(Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = { showAddCowDialog = true },
                        containerColor = RoyalBlue,
                        contentColor = PureWhite,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, "Register Cow")
                    }
                }
            },
            topBar = {
                Surface(shadowElevation = 8.dp) {
                    Box(modifier = Modifier.background(Brush.horizontalGradient(listOf(RoyalBlue, DeepPurple)))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("MilkWize", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = PureWhite, letterSpacing = 1.sp))
                                Text(user?.email ?: "Farmer", style = MaterialTheme.typography.bodySmall, color = PureWhite.copy(alpha = 0.9f), fontWeight = FontWeight.Black)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (unsyncedCount > 0 && !isSyncing) {
                                                scope.launch {
                                                    isSyncing = true
                                                    statusMessage = "Pushing to Cloud..."
                                                    val result = syncPendingRecords(currentUserId, milkingDao, SupabaseClient.client)
                                                    isSyncing = false
                                                    if (result.isSuccess) {
                                                        showSyncSuccess = true
                                                        statusMessage = "Cloud Secured"
                                                        delay(2000)
                                                        showSyncSuccess = false
                                                    } else {
                                                        statusMessage = "Sync Error: ${result.exceptionOrNull()?.localizedMessage?.take(20)}"
                                                    }
                                                }
                                            }
                                        },
                                    color = statusColor
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PureWhite)
                                        else Icon(imageVector = if (unsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.Sync, contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
                                        
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = if (unsyncedCount > 0) "SYNC ($unsyncedCount)" else "SECURED", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = PureWhite))
                                    }
                                }
                                
                                Spacer(Modifier.width(10.dp))
                                
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { SupabaseClient.client.auth.signOut(); db.clearAllTables() }
                                        isLoggedIn = false
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = PureWhite)
                                }
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
                            cowList = SupabaseClient.client.postgrest["cows"].select { filter { eq("owner_id", currentUserId) } }.decodeList<Cow>()
                        }
                    } catch (e: Exception) { Log.e("Supabase", "Cow load fail") }
                }
            }

            val totalLiters = localEvents.sumOf { it.milkLiters }
            val uniqueCows = localEvents.map { it.cowId }.distinct().size

            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryStatCard("PRODUCTION", "${"%.1f".format(totalLiters)} L", ElectricBlue, Icons.Default.WaterDrop, Modifier.weight(1f))
                    SummaryStatCard("HERD SIZE", "$uniqueCows COWS", SuccessGreen, Icons.Default.Pets, Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("New Yield Entry", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = JetBlack))
                        Spacer(Modifier.height(16.dp))

                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCow?.name ?: "Select Cow Tag",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cow Tag", color = RoyalBlue, fontWeight = FontWeight.Black) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Black, fontSize = 16.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RoyalBlue,
                                    unfocusedBorderColor = HighContrastGray,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = PureWhite,
                                    focusedTextColor = JetBlack,
                                    unfocusedTextColor = JetBlack
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = isExpanded, 
                                onDismissRequest = { isExpanded = false },
                                modifier = Modifier.background(PureWhite)
                            ) {
                                cowList.forEach { cow ->
                                    DropdownMenuItem(
                                        text = { Text(cow.name, fontWeight = FontWeight.Black, color = JetBlack) },
                                        onClick = { selectedCow = cow; isExpanded = false },
                                        colors = MenuDefaults.itemColors(
                                            textColor = JetBlack,
                                            leadingIconColor = RoyalBlue
                                        )
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newAmount,
                            onValueChange = { newAmount = it },
                            label = { Text("Milk Volume (L)", color = RoyalBlue, fontWeight = FontWeight.Black) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            prefix = { Text("🥛 ", fontSize = 18.sp) },
                            textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Black, fontSize = 16.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoyalBlue,
                                unfocusedBorderColor = HighContrastGray,
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite,
                                focusedTextColor = JetBlack,
                                unfocusedTextColor = JetBlack
                            )
                        )

                        Button(
                            onClick = {
                                val amount = newAmount.toDoubleOrNull()
                                val cow = selectedCow
                                if (cow != null && amount != null && currentUserId.isNotEmpty()) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                            val localEvent = LocalEvent(cowId = cow.id, ownerId = currentUserId, recordedBy = currentUserId, milkLiters = amount, timestamp = timestamp)
                                            val localId = withContext(Dispatchers.IO) { milkingDao.insert(localEvent) }
                                            
                                            statusMessage = "Local Backup Made ✅"
                                            newAmount = ""

                                            try {
                                                val supabaseEvent = MilkingEvent(cowId = cow.id, ownerId = currentUserId, recordedBy = currentUserId, milkLiters = amount, milkingTime = timestamp)
                                                withContext(Dispatchers.IO) {
                                                    SupabaseClient.client.postgrest["milking_events"].insert(supabaseEvent)
                                                    milkingDao.update(localEvent.copy(id = localId.toInt(), isSynced = true))
                                                }
                                                statusMessage = "Cloud Record Sync ☁️"
                                            } catch (e: Exception) { statusMessage = "Waiting for Sync 📶" }
                                        } catch (e: Exception) { statusMessage = "Local Error" } finally { isLoading = false }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 24.dp),
                            enabled = !isLoading && selectedCow != null && newAmount.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite)
                        ) {
                            if (isLoading) CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                            else Text("CONFIRM ENTRY", fontWeight = FontWeight.Black, color = PureWhite, letterSpacing = 1.2.sp)
                        }
                    }
                }

                Text(statusMessage, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = if (statusMessage.contains("Error") || statusMessage.contains("Failed")) AlertRed else DeepPurple, modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally))

                Spacer(Modifier.height(24.dp))

                Text("RECENT LOGS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = JetBlack, letterSpacing = 1.5.sp))
                
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(localEvents) { event ->
                        val cowName = cowList.find { it.id == event.cowId }?.name ?: "Unknown"
                        ModernEventCard(event, cowName, milkingDao, scope)
                    }
                }
            }
        }

        if (showManageHerdDialog) {
            ManageHerdDialog(
                cows = cowList,
                onDismiss = { showManageHerdDialog = false },
                onUpdate = { cow, newName ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                SupabaseClient.client.postgrest["cows"].update(mapOf("tag" to newName)) { filter { eq("id", cow.id) } }
                                cowList = SupabaseClient.client.postgrest["cows"].select { filter { eq("owner_id", currentUserId) } }.decodeList<Cow>()
                            }
                        } catch (e: Exception) { }
                    }
                },
                onDelete = { cow ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { SupabaseClient.client.postgrest["cows"].delete { filter { eq("id", cow.id) } } }
                            cowList = cowList.filter { it.id != cow.id }
                        } catch (e: Exception) { }
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
                            cowList = SupabaseClient.client.postgrest["cows"].select { filter { eq("owner_id", currentUserId) } }.decodeList<Cow>()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SummaryStatCard(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PureWhite), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = color)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = JetBlack))
        }
    }
}

@Composable
fun ManageHerdDialog(cows: List<Cow>, onDismiss: () -> Unit, onUpdate: (Cow, String) -> Unit, onDelete: (Cow) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        title = { Text("Herd Management", fontWeight = FontWeight.Black, color = JetBlack) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(cows) { cow ->
                    var isEditing by remember { mutableStateOf(false) }
                    var editedTag by remember { mutableStateOf(cow.name) }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editedTag, onValueChange = { editedTag = it }, modifier = Modifier.weight(1f), 
                                    shape = RoundedCornerShape(8.dp), 
                                    textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Black),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RoyalBlue, unfocusedBorderColor = HighContrastGray)
                                )
                                IconButton(onClick = { onUpdate(cow, editedTag); isEditing = false }) { Icon(Icons.Default.Save, "Save", tint = SuccessGreen) }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cow.name, fontWeight = FontWeight.Black, color = JetBlack, fontSize = 16.sp)
                                    Text(cow.breed ?: "Common", style = MaterialTheme.typography.bodySmall, color = DeepPurple, fontWeight = FontWeight.Black)
                                }
                                Row {
                                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, "Edit", tint = ElectricBlue) }
                                    IconButton(onClick = { onDelete(cow) }) { Icon(Icons.Default.Delete, "Remove", tint = AlertRed) }
                                }
                            }
                        }
                        HorizontalDivider(color = BackgroundGray, thickness = 1.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", fontWeight = FontWeight.Black, color = RoyalBlue) } }
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
        containerColor = PureWhite,
        title = { Text("Register New Cow", fontWeight = FontWeight.Black, color = RoyalBlue) },
        text = {
            Column {
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it }, 
                    label = { Text("Ear Tag / Name", fontWeight = FontWeight.Black, color = RoyalBlue) }, 
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Black, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBlue, 
                        unfocusedBorderColor = HighContrastGray, 
                        focusedContainerColor = PureWhite, 
                        unfocusedContainerColor = PureWhite,
                        focusedTextColor = JetBlack,
                        unfocusedTextColor = JetBlack
                    )
                )
                OutlinedTextField(
                    value = breed, onValueChange = { breed = it }, 
                    label = { Text("Breed Type", fontWeight = FontWeight.Black, color = RoyalBlue) }, 
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Black, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBlue, 
                        unfocusedBorderColor = HighContrastGray, 
                        focusedContainerColor = PureWhite, 
                        unfocusedContainerColor = PureWhite,
                        focusedTextColor = JetBlack,
                        unfocusedTextColor = JetBlack
                    )
                )
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
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ADD COW", fontWeight = FontWeight.Black, color = PureWhite)
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
    var userRole by remember { mutableStateOf("farmer") }
    var farmCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(WarmCream, SoftPeach))).padding(24.dp)) {
        Card(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Agriculture, null, tint = GoldenAmber, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isRegistering) "Join MilkWize" else "Welcome Back",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = JetBlack
                )
                Text(
                    text = if (isRegistering) "Create your MilkWize account" else "Sign in to manage your herd",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HighContrastGray
                )

                if (isRegistering) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MilkWhite).padding(4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val roles = listOf("farmer", "customer")
                        roles.forEach { role ->
                            val isSelected = userRole == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ForestGreen else Color.Transparent)
                                    .clickable { userRole = role }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = role.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) PaperWhite else WarmGray
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email Address", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = GoldenAmber) },
                    textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldenAmber, 
                        unfocusedBorderColor = HighContrastGray,
                        focusedLabelColor = GoldenAmber,
                        focusedContainerColor = WarmCream.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password", fontWeight = FontWeight.Bold) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = GoldenAmber) },
                    textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldenAmber, 
                        unfocusedBorderColor = HighContrastGray,
                        focusedLabelColor = GoldenAmber,
                        focusedContainerColor = WarmCream.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                if (isRegistering && userRole == "customer") {
                    OutlinedTextField(
                        value = farmCode, onValueChange = { farmCode = it },
                        label = { Text("Farm Code", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("e.g. MILK-12") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = SunlitAmber) },
                        textStyle = TextStyle(color = JetBlack, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SunlitAmber, 
                            unfocusedBorderColor = HighContrastGray,
                            focusedLabelColor = SunlitAmber,
                            focusedContainerColor = WarmCream.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }

                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                if (isRegistering) {
                                    SupabaseClient.client.auth.signUpWith(Email) {
                                        this.email = email
                                        this.password = password
                                        data = buildJsonObject {
                                            put("role", userRole)
                                            if (userRole == "customer") put("farm_code", farmCode)
                                        }
                                    }
                                    errorMessage = "Account Created! Please Sign In."
                                    isRegistering = false
                                } else {
                                    SupabaseClient.client.auth.signInWith(Email) { this.email = email; this.password = password }
                                    onLoginSuccess()
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage?.take(50) ?: "Auth Error"
                            } finally { isLoading = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 28.dp),
                    enabled = !isLoading, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber, contentColor = JetBlack)
                ) {
                    if (isLoading) CircularProgressIndicator(color = JetBlack, modifier = Modifier.size(24.dp))
                    else Text(if (isRegistering) "JOIN THE HERD" else "START MILKING", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                TextButton(onClick = { isRegistering = !isRegistering }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        if (isRegistering) "Already a member? Sign in" else "New to MilkWize? Join here",
                        color = RoyalBlue, fontWeight = FontWeight.Bold
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = AlertRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun ModernEventCard(event: LocalEvent, cowName: String, milkingDao: MilkingDao, scope: kotlinx.coroutines.CoroutineScope) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = PureWhite), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cowName.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = RoyalBlue))
                Text("${event.milkLiters} Liters", style = MaterialTheme.typography.titleLarge, color = JetBlack, fontWeight = FontWeight.Black)
                Text(event.timestamp.split("T")[0] + " " + event.timestamp.split("T")[1].take(5), style = MaterialTheme.typography.bodySmall, color = JetBlack, fontWeight = FontWeight.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { milkingDao.delete(event) } } }) { Icon(Icons.Default.Delete, "Delete", tint = AlertRed.copy(alpha = 0.6f)) }
                Icon(imageVector = if (event.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null, tint = if (event.isSynced) SuccessGreen else AlertRed, modifier = Modifier.size(26.dp))
            }
        }
    }
}
