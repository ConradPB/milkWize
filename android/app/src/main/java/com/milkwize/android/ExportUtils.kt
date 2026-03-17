package com.milkwize.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun exportEventsToCSV(context: Context, events: List<LocalEvent>, cowList: List<Cow>) {
    val csvHeader = "Date,Time,Cow Tag,Breed,Liters,Recorded By\n"
    val csvData = events.joinToString("\n") { event ->
        val cow = cowList.find { it.id == event.cowId }
        val dateParts = event.timestamp.split("T")
        val date = dateParts[0]
        val time = dateParts.getOrNull(1)?.take(5) ?: ""

        "$date,$time,${cow?.name ?: "Unknown"},${cow?.breed ?: "N/A"},${event.milkLiters},${event.recordedBy}"
    }

    val fullCsv = csvHeader + csvData

    // Create a temporary file
    val fileName = "MilkWize_Report_${System.currentTimeMillis()}.csv"
    val file = File(context.cacheDir, fileName)
    file.writeText(fullCsv)

    // Share the file
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Farm Report"))
}
