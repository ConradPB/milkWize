package com.milkwize.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkwize.android.ui.theme.*

@Composable
fun AnalyticsScreen(localEvents: List<LocalEvent>, cowList: List<Cow>) {
    val weeklyData = remember(localEvents) {
        // Group last 7 days of data for the chart
        localEvents.take(7).reversed()
    }

    val topEvent = remember(localEvents) {
        localEvents.maxByOrNull { it.milkLiters }
    }
    val topCow = remember(topEvent, cowList) {
        cowList.find { it.id == topEvent?.cowId }
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

            // Top Performer Card right below SimpleBarChart
            if (topEvent != null && topCow != null) {
                TopPerformerCard(
                    cowName = topCow.name,
                    yield = topEvent.milkLiters,
                    breed = topCow.breed ?: "Unknown"
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            Text("Full History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
        }

        items(localEvents) { event ->
            val cowName = cowList.find { it.id == event.cowId }?.name ?: "Cow"
            HistoryListItem(event, cowName)
        }
    }
}

@Composable
fun AggregatedBarChart(dailyTotals: List<Pair<String, Double>>) {
    // Determine the highest total in the last 7 days to scale the bars
    val maxYield = (dailyTotals.maxByOrNull { it.second }?.second ?: 10.0).toFloat()

    Row(
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        dailyTotals.forEachIndexed { index, dataPoint ->
            val dateLabel = dataPoint.first // "2026-03-17"
            val totalLiters = dataPoint.second
            val barHeightFraction = (totalLiters.toFloat() / maxYield).coerceAtLeast(0.1f)

            // Comparison logic: Did we do better than the previous day in the list?
            val isImproved = if (index > 0) totalLiters > dailyTotals[index - 1].second else null

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Total Text + Arrow
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${totalLiters.toInt()}L",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isImproved == true) SageSuccess else if (isImproved == false) TerracottaRed else EarthySlate
                    )
                }

                Spacer(Modifier.height(8.dp))

                // The Bar (Aggregated)
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight(barHeightFraction * 0.75f)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(ForestGreen, ForestGreen.copy(alpha = 0.8f))
                            )
                        )
                )

                Spacer(Modifier.height(8.dp))

                // Date Label (e.g., "17/03")
                val displayDate = try {
                    val parts = dateLabel.split("-")
                    "${parts[2]}/${parts[1]}"
                } catch (e: Exception) { dateLabel }

                Text(displayDate, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarmGray)
            }
        }
    }
}

@Composable
fun TopPerformerCard(cowName: String, yield: Double, breed: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreen),
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

@Composable
fun HistoryListItem(event: LocalEvent, cowName: String) {
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
        }
    }
}
