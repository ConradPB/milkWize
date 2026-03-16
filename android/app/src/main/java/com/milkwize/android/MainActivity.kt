package com.milkwize.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkwize.android.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MilkWhite) {
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
    val sessionStatus by SupabaseClient.client.auth.sessionStatus.collectAsState()
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    val scope = rememberCoroutineScope()

    // Handle authentication state
    when (val status = sessionStatus) {
        is SessionStatus.Authenticated -> {
            val user = status.session.user
            val currentUserId = user?.id ?: ""
            val context = LocalContext.current
            val db = remember { AppDatabase.getDatabase(context) }
            val milkingDao = db.milkingDao()

            // Fetch profile and initial data with fallback to metadata
            LaunchedEffect(currentUserId) {
                if (currentUserId.isNotEmpty()) {
                    try {
                        val profile = withContext(Dispatchers.IO) {
                            SupabaseClient.client.postgrest["profiles"]
                                .select { filter { eq("id", currentUserId) } }
                                .decodeSingle<UserProfile>()
                        }
                        userProfile = profile
                    } catch (e: Exception) {
                        Log.e("Auth", "Profile fetch failed, using fallback: ${e.message}")
                        // Fallback: Use data from user metadata if the profiles table fetch fails or is slow
                        val metadata = user?.userMetadata
                        val role = metadata?.get("role")?.jsonPrimitive?.contentOrNull ?: "farmer"
                        val farmCode = metadata?.get("farm_code")?.jsonPrimitive?.contentOrNull
                        userProfile = UserProfile(
                            id = currentUserId,
                            email = user?.email ?: "",
                            role = role,
                            farm_code = farmCode
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MilkWhite,
                topBar = {
                    Surface(shadowElevation = 8.dp) {
                        CenterAlignedTopAppBar(
                            title = { Text("MILKWIZE", fontWeight = FontWeight.Black, color = PaperWhite, letterSpacing = 2.sp) },
                            actions = {
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { 
                                            SupabaseClient.client.auth.signOut()
                                            db.clearAllTables()
                                        }
                                        userProfile = null // Reset profile on logout
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = PaperWhite)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SunlitAmber)
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = PaperWhite) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Dashboard, "Home") },
                            label = { Text("Log") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, "History") },
                            label = { Text("Reports") }
                        )
                    }
                }
            ) { padding ->
                if (userProfile == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                } else {
                    AnalyticsScreen(localEvents)
                    UnifiedView(padding, userProfile!!, milkingDao, scope)
                }
            }
        }
        is SessionStatus.Initializing -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
            Box(modifier = Modifier.padding(padding)) {
                if (selectedTab == 0) {
                    if (userProfile?.role == "farmer") FarmerView(userProfile!!) else CustomerView(userProfile!!)
                }
        }
        else -> {
            // Not authenticated or other states
            LoginScreen(onLoginSuccess = { /* status will update automatically */ })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedView(padding: PaddingValues, profile: UserProfile, milkingDao: MilkingDao, scope: kotlinx.coroutines.CoroutineScope) {
    val localEvents by milkingDao.getAllLocally(profile.id).collectAsState(initial = emptyList())
    val unsyncedCount by milkingDao.getUnsyncedCount(profile.id).collectAsState(initial = 0)
    
    var cowList by remember { mutableStateOf(listOf<Cow>()) }
    var selectedCow by remember { mutableStateOf<Cow?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var newAmount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    var isSyncing by remember { mutableStateOf(false) }
    var showAddCowDialog by remember { mutableStateOf(false) }
    var showManageHerdDialog by remember { mutableStateOf(false) }
    
    // Trigger for refreshing cow list
    var refreshCowsTrigger by remember { mutableIntStateOf(0) }

    val todayDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")) }

    LaunchedEffect(profile.id, refreshCowsTrigger) {
        try {
            cowList = withContext(Dispatchers.IO) {
                SupabaseClient.client.postgrest["cows"]
                    .select { filter { eq("owner_id", profile.id) } }
                    .decodeList<Cow>()
            }
        } catch (e: Exception) { Log.e("Supabase", "Cow load fail") }
    }

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            // High-Level Stats Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val totalYield = localEvents.sumOf { it.milkLiters }
                SummaryStatCard("TODAY'S YIELD", "${"%.1f".format(totalYield)}L", ForestGreen, Icons.Default.WaterDrop, Modifier.weight(1f))
                SummaryStatCard("SYNC STATUS", if (unsyncedCount > 0) "$unsyncedCount PENDING" else "SECURED", if (unsyncedCount > 0) TerracottaRed else SageSuccess, if (unsyncedCount > 0) Icons.Default.Sync else Icons.Default.CloudDone, Modifier.weight(1f).clickable {
                    if (unsyncedCount > 0 && !isSyncing) {
                        scope.launch {
                            isSyncing = true
                            syncPendingRecords(profile.id, milkingDao, SupabaseClient.client)
                            isSyncing = false
                        }
                    }
                })
            }
            Spacer(Modifier.height(20.dp))
        }

        if (profile.role == "farmer") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PaperWhite),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditCalendar, null, tint = ForestGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(todayDate.uppercase(), style = MaterialTheme.typography.labelMedium, color = ForestGreen, fontWeight = FontWeight.Bold)
                        }
                        Text("Record Daily Yield", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = EarthySlate)
                        Spacer(Modifier.height(16.dp))

                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCow?.name ?: "Select Cow",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cow Identification") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Bold),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, focusedLabelColor = ForestGreen)
                            )
                            ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, modifier = Modifier.background(PaperWhite)) {
                                if (cowList.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No cows found", color = WarmGray) },
                                        onClick = { isExpanded = false }
                                    )
                                } else {
                                    cowList.forEach { cow ->
                                        DropdownMenuItem(
                                            text = { Text(cow.name, fontWeight = FontWeight.Bold) },
                                            onClick = { selectedCow = cow; isExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newAmount,
                            onValueChange = { newAmount = it },
                            label = { Text("Volume (Liters)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            prefix = { Text("🥛 ", fontSize = 18.sp) },
                            textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, focusedLabelColor = ForestGreen)
                        )

                        Button(
                            onClick = {
                                val amount = newAmount.toDoubleOrNull()
                                if (selectedCow != null && amount != null) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                            val local = LocalEvent(cowId = selectedCow!!.id, ownerId = profile.id, recordedBy = profile.id, milkLiters = amount, timestamp = timestamp)
                                            val id = withContext(Dispatchers.IO) { milkingDao.insert(local) }
                                            
                                            val event = MilkingEvent(cowId = selectedCow!!.id, ownerId = profile.id, recordedBy = profile.id, milkLiters = amount, milkingTime = timestamp)
                                            withContext(Dispatchers.IO) { SupabaseClient.client.postgrest["milking_events"].insert(event) }
                                            milkingDao.update(local.copy(id = id.toInt(), isSynced = true))
                                            
                                            newAmount = ""
                                            selectedCow = null
                                        } catch (e: Exception) { Log.e("Entry", "Failed to save") } finally { isLoading = false }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
                            enabled = !isLoading && selectedCow != null && newAmount.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SunlitAmber,
                                disabledContainerColor = EarthySlate.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(color = PaperWhite, modifier = Modifier.size(24.dp))
                            else Text("LOG PRODUCTION", fontWeight = FontWeight.Black, color = PaperWhite)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SunlitAmber.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Customer Access", fontWeight = FontWeight.Bold, color = SunlitAmber)
                        Text("Connected to Farm: ${profile.farm_code ?: "Default"}", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT ACTIVITY", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = EarthySlate)
                if (profile.role == "farmer") {
                    TextButton(onClick = { showManageHerdDialog = true }) {
                        Text("MANAGE HERD", color = ForestGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        items(localEvents.take(10)) { event ->
            val cowName = cowList.find { it.id == event.cowId }?.name ?: "Cow"
            ModernEventCard(event, cowName, milkingDao, scope)
        }
        
        item {
            if (profile.role == "farmer") {
                Button(
                    onClick = { showAddCowDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("REGISTER NEW CATTLE")
                }
            }
        }
    }

    if (showAddCowDialog) AddCowDialog(onDismiss = { showAddCowDialog = false }, onCowAdded = { refreshCowsTrigger++ })
    if (showManageHerdDialog) {
        ManageHerdDialog(
            cows = cowList, 
            onDismiss = { showManageHerdDialog = false }, 
            onUpdate = { cow, newTag ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            SupabaseClient.client.postgrest["cows"]
                                .update({
                                    set("tag", newTag)
                                }) { 
                                    filter { 
                                        eq("id", cow.id) 
                                    } 
                                }
                        }
                        refreshCowsTrigger++
                    } catch (e: Exception) { Log.e("Herd", "Update failed: ${e.message}") }
                }
            }, 
            onDelete = { cow ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            SupabaseClient.client.postgrest["cows"]
                                .delete { 
                                    filter { 
                                        eq("id", cow.id) 
                                    } 
                                }
                        }
                        refreshCowsTrigger++
                    } catch (e: Exception) { Log.e("Herd", "Delete failed: ${e.message}") }
                }
            }
        )
    }
}

@Composable
fun SummaryStatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PaperWhite), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = WarmGray)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = color)
        }
    }
}

@Composable
fun ModernEventCard(event: LocalEvent, cowName: String, milkingDao: MilkingDao, scope: kotlinx.coroutines.CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PaperWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cowName.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = ForestGreen))
                Text("${event.milkLiters} Liters", style = MaterialTheme.typography.titleMedium, color = EarthySlate, fontWeight = FontWeight.Black)
                Text(event.timestamp.split("T")[0], style = MaterialTheme.typography.bodySmall, color = WarmGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = if (event.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null, tint = if (event.isSynced) SageSuccess else TerracottaRed, modifier = Modifier.size(20.dp))
                IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { milkingDao.delete(event) } } }) { 
                    Icon(Icons.Default.Delete, "Delete", tint = TerracottaRed.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) 
                }
            }
        }
    }
}

@Composable
fun ManageHerdDialog(cows: List<Cow>, onDismiss: () -> Unit, onUpdate: (Cow, String) -> Unit, onDelete: (Cow) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaperWhite,
        title = { Text("Herd Management", fontWeight = FontWeight.Black, color = EarthySlate) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(cows) { cow ->
                    var isEditing by remember { mutableStateOf(false) }
                    var editedTag by remember { mutableStateOf(cow.name) }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                OutlinedTextField(value = editedTag, onValueChange = { editedTag = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Bold))
                                IconButton(onClick = { onUpdate(cow, editedTag); isEditing = false }) { Icon(Icons.Default.Save, "Save", tint = SageSuccess) }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cow.name, fontWeight = FontWeight.Black, color = EarthySlate, fontSize = 16.sp)
                                    Text(cow.breed ?: "Common", style = MaterialTheme.typography.bodySmall, color = WarmGray)
                                }
                                Row {
                                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, "Edit", tint = ForestGreen) }
                                    IconButton(onClick = { onDelete(cow) }) { Icon(Icons.Default.Delete, "Remove", tint = TerracottaRed) }
                                }
                            }
                        }
                        HorizontalDivider(color = MilkWhite, thickness = 1.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", fontWeight = FontWeight.Bold, color = EarthySlate) } }
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
        containerColor = PaperWhite,
        title = { Text("Register New Cow", fontWeight = FontWeight.Black, color = ForestGreen) },
        text = {
            Column {
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it }, 
                    label = { Text("Ear Tag / Name") }, 
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, focusedLabelColor = ForestGreen)
                )
                OutlinedTextField(
                    value = breed, onValueChange = { breed = it }, 
                    label = { Text("Breed Type") }, 
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestGreen, focusedLabelColor = ForestGreen)
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
                                onDismiss()
                            } catch (e: Exception) { } finally { isLoading = false }
                        }
                    }
                },
                enabled = !isLoading && tag.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ADD COW", fontWeight = FontWeight.Black, color = PaperWhite)
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
            colors = CardDefaults.cardColors(containerColor = PaperWhite),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Agriculture, null, tint = ForestGreen, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isRegistering) "Join MilkWize" else "Welcome Back",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = EarthySlate
                )
                Text(
                    text = if (isRegistering) "Create your MilkWize account" else "Sign in to manage your herd",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmGray
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
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = ForestGreen) },
                    textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen, 
                        unfocusedBorderColor = WarmGray,
                        focusedLabelColor = ForestGreen,
                        focusedContainerColor = WarmCream.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password", fontWeight = FontWeight.Bold) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = ForestGreen) },
                    textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen, 
                        unfocusedBorderColor = WarmGray,
                        focusedLabelColor = ForestGreen,
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
                        leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = ForestGreen) },
                        textStyle = TextStyle(color = EarthySlate, fontWeight = FontWeight.Medium, fontSize = 16.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen, 
                            unfocusedBorderColor = WarmGray,
                            focusedLabelColor = ForestGreen,
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
                    colors = ButtonDefaults.buttonColors(containerColor = SunlitAmber, contentColor = PaperWhite)
                ) {
                    if (isLoading) CircularProgressIndicator(color = PaperWhite, modifier = Modifier.size(24.dp))
                    else Text(if (isRegistering) "JOIN THE HERD" else "START MILKING", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                TextButton(onClick = { isRegistering = !isRegistering }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        if (isRegistering) "Already a member? Sign in" else "New to MilkWize? Join here",
                        color = ForestGreen, fontWeight = FontWeight.Bold
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = TerracottaRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(localEvents: List<LocalEvent>) {
    val weeklyData = remember(localEvents) {
        // Group last 7 days of data for the chart
        localEvents.take(7).reversed()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Weekly Performance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))

            // Simple Bar Chart Card
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PaperWhite)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SimpleBarChart(weeklyData)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            Text("Full History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
        }

        items(localEvents) { event ->
            // Use existing ModernEventCard here
            // But styled for a list view
            HistoryListItem(event)
        }
    }
}

    @Composable
    fun SimpleBarChart(data: List<LocalEvent>) {
        val maxYield = (data.maxByOrNull { it.milkLiters }?.milkLiters ?: 10.0).toFloat()

        Row(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { index, event ->
                val barHeightFraction = (event.milkLiters.toFloat() / maxYield).coerceAtLeast(0.1f)

                // Logic for Comparison Line / Indicator
                val isImproved = if (index > 0) {
                    event.milkLiters > data[index - 1].milkLiters
                } else null

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Yield Text with Trend Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${event.milkLiters}L",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isImproved == true) SageSuccess else if (isImproved == false) TerracottaRed else EarthySlate
                        )
                        if (isImproved != null) {
                            Icon(
                                imageVector = if (isImproved) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = if (isImproved) SageSuccess else TerracottaRed
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // The Bar
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(barHeightFraction * 0.7f)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(ForestGreen, ForestGreen.copy(alpha = 0.7f))
                                )
                            )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Short Date Label (e.g., "14 Mar")
                    Text(
                        text = event.timestamp.substring(8, 10) + "/" + event.timestamp.substring(5, 7),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmGray
                    )
                }
            }

            @Composable
            fun TopPerformerCard(cowName: String, yield: Double, breed: String) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreen), // Contrast: Green background
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOP PERFORMER", style = MaterialTheme.typography.labelMedium, color = PaperWhite.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            Text(cowName.uppercase(), style = MaterialTheme.typography.headlineSmall, color = PaperWhite, fontWeight = FontWeight.Black)
                            Text(breed, style = MaterialTheme.typography.bodySmall, color = SageSuccess, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = SunlitAmber, modifier = Modifier.size(32.dp))
                            Text("${"%.1f".format(yield)} L", style = MaterialTheme.typography.titleLarge, color = PaperWhite, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }