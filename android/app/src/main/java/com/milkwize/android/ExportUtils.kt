package com.milkwize.android

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun exportEventsToCSV(context: Context, events: List<LocalEvent>, cowList: List<Cow>) {
    // Ping to see if the function is even called
    Toast.makeText(context, "Export Started!", Toast.LENGTH_SHORT).show()
    
    try {
        if (events.isEmpty()) {
            Toast.makeText(context, "No events to export", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeader = "Date,Time,Cow Tag,Breed,Liters,Recorded By\n"
        val csvData = events.joinToString("\n") { event ->
            val cow = cowList.find { it.id == event.cowId }
            val dateParts = event.timestamp.split("T")
            val date = dateParts[0]
            val time = dateParts.getOrNull(1)?.take(5) ?: ""

            "$date,$time,${cow?.name ?: "Unknown"},${cow?.breed ?: "N/A"},${event.milkLiters},${event.recordedBy}"
        }

        val fullCsv = csvHeader + csvData

        // Create a temporary file in cache directory
        val fileName = "MilkWize_Report_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        // Write text synchronously
        file.writeText(fullCsv)
        
        // Ensure file exists and has content
        if (!file.exists() || file.length() == 0L) {
            throw Exception("File creation failed or is empty")
        }

        // Use the authority defined in the manifest
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/comma-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            // CRITICAL: Grant read permission to the URI specifically
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Allow starting from non-activity context if necessary
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Export Farm Report")
        // Also add new task flag to the chooser
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        android.util.Log.e("ExportCSV", "Failed: ${e.message}")
    }
}
