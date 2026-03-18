package com.milkwize.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkwize.android.ui.theme.*

@Composable
fun AnalyticsScreen(localEvents: List<LocalEvent>, cowList: List<Cow>) {
    val context = LocalContext.current
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Performance Insight", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                IconButton(onClick = { exportEventsToCSV(context, localEvents, cowList) }) {
                    Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = ForestGreen)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Improved Bar Chart Card
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PaperWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("DAILY PRODUCTION (L)", style = MaterialTheme.typography.labelSmall, color = WarmGray, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    SimpleBarChart(weeklyData)
                }
            }
            Spacer(Modifier.height(24.dp))
            
            // Top Performer Card
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
fun SimpleBarChart(data: List<LocalEvent>) {
    if (data.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available for the last 7 days", color = WarmGray, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxYield = (data.maxByOrNull { it.milkLiters }?.milkLiters ?: 1.0).coerceAtLeast(1.0).toFloat()
    val averageYield = data.map { it.milkLiters }.average().toFloat()

    Box(modifier = Modifier.fillMaxSize()) {
        // Average Line
        val avgLineY = 1f - (averageYield / maxYield).coerceIn(0f, 1f)
        if (data.size > 1) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(avgLineY.coerceAtLeast(0.01f)))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = SageSuccess.copy(alpha = 0.3f)
                )
                Text(
                    "AVG: ${"%.1f".format(averageYield)}L",
                    fontSize = 8.sp,
                    color = SageSuccess,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.weight((1f - avgLineY).coerceAtLeast(0.01f)))
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { index, event ->
                val barHeightFraction by animateFloatAsState(
                    targetValue = (event.milkLiters.toFloat() / maxYield).coerceAtLeast(0.1f),
                    animationSpec = tween(durationMillis = 1000), label = "BarHeight"
                )

                val isImproved = if (index > 0) {
                    event.milkLiters > data[index - 1].milkLiters
                } else null

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .fillMaxHeight(barHeightFraction * 0.75f)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (event.milkLiters >= averageYield) {
                                        listOf(ForestGreen, ForestGreen.copy(alpha = 0.7f))
                                    } else {
                                        listOf(SunlitAmber, SunlitAmber.copy(alpha = 0.7f))
                                    }
                                )
                            )
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (event.timestamp.length >= 10) {
                            event.timestamp.substring(8, 10) + "/" + event.timestamp.substring(5, 7)
                        } else {
                            "N/A"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmGray
                    )
                }
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
