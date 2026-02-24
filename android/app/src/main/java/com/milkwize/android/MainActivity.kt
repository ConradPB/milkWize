package com.milkwize.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkingDashboard() {
    var isLoggedIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        // DATA & UI STATES
        var events by remember { mutableStateOf(listOf<MilkingEvent>()) }
        var cowList by remember { mutableStateOf(listOf<Cow>()) }
        var selectedCow by remember { mutableStateOf<Cow?>(null) }
        var isExpanded by remember { mutableStateOf(false) }
        var newAmount by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }

        // Fetch data after login
        LaunchedEffect(Unit) {
            try {
                cowList = SupabaseClient.client.postgrest["cows"].select().decodeList<Cow>()
                events = SupabaseClient.client.postgrest["milking_events"].select().decodeList<MilkingEvent>()
                statusMessage = "Data loaded."
            } catch (e: Exception) {
                statusMessage = "Load Error: ${e.localizedMessage}"
            }
        }

        val totalLiters = events.sumOf { it.milkLiters }
        val uniqueCows = events.map { it.cowId }.distinct().size

        Column(modifier = Modifier.padding(16.dp)) {
            Text("MilkWize Dashboard", style = MaterialTheme.typography.headlineLarge)

            // 1. SUMMARY STATS
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

            // 2. LOGGING FORM
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
                            if (cowId != null && amount != null) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val newEvent = MilkingEvent(cowId = cowId, milkLiters = amount)
                                        SupabaseClient.client.postgrest["milking_events"].insert(newEvent)
                                        statusMessage = "Saved: ${selectedCow?.name} - $amount L"
                                        newAmount = ""
                                        // Refresh list
                                        events = SupabaseClient.client.postgrest["milking_events"].select().decodeList<MilkingEvent>()
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
                        statusMessage = "History refreshed."
                    } catch (e: Exception) {
                        statusMessage = "Fetch Error: ${e.localizedMessage}"
                    }
                }
            }) {
                Text("Refresh History")
            }

            LazyColumn {
                items(events) { event ->
                    MilkingEventCard(event)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin Access", style = MaterialTheme.typography.headlineMedium)
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
                isLoggingIn = true
                scope.launch {
                    try {
                        SupabaseClient.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                        onLoginSuccess()
                    } catch (e: Exception) {
                        errorMessage = "Invalid credentials. Try again."
                    } finally {
                        isLoggingIn = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !isLoggingIn
        ) {
            Text(if (isLoggingIn) "Verifying..." else "Login")
        }

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cow: ...${event.cowId.takeLast(6)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${event.milkLiters} L",
                    style = MaterialTheme.typography.headlineSmall,
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
