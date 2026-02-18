package com.milkwize.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milkwize.app.ui.theme.MilkWizeTheme
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MilkWizeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MilkWizeDashboard()
                }
            }
        }
    }
}

@Composable
fun MilkWizeDashboard() {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready to Fetch") }

    Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "MilkWize Admin", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            statusText = "Fetching..."
            // This is our "Async" call
            scope.launch {
                try {
                    // Fetching from your 'milking_events' table
                    val results = SupabaseClient.client.postgrest["milking_events"]
                        .select().decodeList<MilkingEvent>()

                    statusText = "Success! Found ${results.size} records."
                    Log.d("SUPABASE_DATA", results.toString())
                } catch (e: Exception) {
                    statusText = "Error: ${e.message}"
                    Log.e("SUPABASE_ERROR", e.toString())
                }
            }
        }) {
            Text("Fetch Milking Data")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = statusText)
    }
}