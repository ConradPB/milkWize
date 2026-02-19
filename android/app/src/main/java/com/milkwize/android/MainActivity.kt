package com.milkwize.android // Updated package name

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
fun MilkingDashboard() {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(listOf<MilkingEvent>()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("MilkWize Records", style = MaterialTheme.typography.headlineLarge)

        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        // Fetching from your table
                        val data = SupabaseClient.client.postgrest["milking_events"]
                            .select().decodeList<MilkingEvent>()
                        events = data
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text(if (isLoading) "Loading..." else "Fetch Records")
        }

        LazyColumn {
            items(events) { event ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cow: ${event.cowId}", style = MaterialTheme.typography.titleMedium)
                        Text("Amount: ${event.amount} L")
                        event.createdAt?.let {
                            Text("Date: ${it.substringBefore("T")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
