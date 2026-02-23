package com.milkwize.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milkwize.android.ui.theme.AndroidTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkingDashboard() {
    val scope = rememberCoroutineScope()

    // DATA STATES
    var events by remember { mutableStateOf(listOf<MilkingEvent>()) }
    var cowList by remember { mutableStateOf(listOf<Cow>()) }

    // UI & FORM STATES
    var selectedCow by remember { mutableStateOf<Cow?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var newAmount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing...") }

    // 1. FETCH COWS ON APP LOAD
    LaunchedEffect(Unit) {
        try {
            statusMessage = "Loading cows..."
            val cows = SupabaseClient.client.postgrest["cows"]
                .select().decodeList<Cow>()
            cowList = cows
            statusMessage = if (cows.isEmpty()) "No cows found in database." else "Ready."
        } catch (e: Exception) {
            statusMessage = "Error loading cows: ${e.localizedMessage}"
        }
    }

    // Calculate stats based on the events currently visible in the list
    val totalLiters = events.sumOf { it.milkLiters }
    val uniqueCows = events.map { it.cowId }.distinct().size

    Column(modifier = Modifier.padding(16.dp)) {
        Text("MilkWize", style = MaterialTheme.typography.headlineLarge)
// --- SUMMARY STATS CARD ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Total Liters Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Yield", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "$totalLiters L",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Cow Count Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Cows Milked", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "$uniqueCows",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        // --- THE LOGGING FORM ---
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Log Daily Yield", style = MaterialTheme.typography.titleMedium)

                // DROPDOWN FOR COWS
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
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
                        if (cowId != null && amount != null) {
                            isLoading = true
                            scope.launch {
                                try {
                                    val newEvent = MilkingEvent(cowId = cowId, milkLiters = amount)
                                    SupabaseClient.client.postgrest["milking_events"].insert(newEvent)
                                    statusMessage = "Saved: ${selectedCow?.name} - $amount L"
                                    newAmount = ""
                                    // Refresh list after save
                                    events = SupabaseClient.client.postgrest["milking_events"]
                                        .select().decodeList<MilkingEvent>()
                                } catch (e: Exception) {
                                    statusMessage = "Save Error: ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            statusMessage = "Please select a cow and enter liters."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Saving..." else "Submit Record")
                }
            }
        }

        // SYSTEM STATUS MESSAGE
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusMessage.contains("Error")) Color.Red else Color.Unspecified,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // REFRESH BUTTON
        TextButton(onClick = {
            scope.launch {
                try {
                    events = SupabaseClient.client.postgrest["milking_events"]
                        .select().decodeList<MilkingEvent>()
                } catch (e: Exception) { /* log error */ }
            }
        }) {
            Text("Refresh History")
        }

        // HISTORY LIST
        LazyColumn {
            items(events) { event ->
                MilkingEventCard(event)
            }
        }
    }
}

@Composable
fun MilkingEventCard(event: MilkingEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                // We show the last 6 digits of the ID—enough to identify it, but cleaner to look at
                Text(
                    text = "Cow: ...${event.cowId.takeLast(6)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${event.milkLiters} L",
                    style = MaterialTheme.typography.headlineSmall, // Made this slightly bolder
                    color = MaterialTheme.colorScheme.primary
                )
            }

            event.createdAt?.let {
                Text(
                    text = "Logged: ${it.take(16).replace("T", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}