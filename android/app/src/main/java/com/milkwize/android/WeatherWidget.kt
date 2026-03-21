package com.milkwize.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milkwize.android.ui.theme.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WeatherWidget() {
    var weatherData by remember { mutableStateOf<WeatherResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(WeatherService::class.java)
            
            // Using a default location for now (e.g., Mbarara, Uganda coordinates)
            // In a real app, you'd use FusedLocationProviderClient
            val response = withContext(Dispatchers.IO) {
                service.getWeather(
                    lat = -0.6067, 
                    lon = 30.6558, 
                    apiKey = "YOUR_OPENWEATHER_API_KEY" // Replace with a real key or BuildConfig
                )
            }
            weatherData = response
        } catch (e: Exception) {
            android.util.Log.e("Weather", "Failed: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = ForestGreen)
    } else if (weatherData != null) {
        val temp = weatherData!!.main.temp.toInt()
        val humidity = weatherData!!.main.humidity
        val condition = weatherData!!.weather.firstOrNull()?.main ?: "Clear"
        
        // THI = (1.8 × T + 32) - [(0.55 - 0.0055 × RH) × (1.8 × T - 26)]
        val thi = (1.8 * temp + 32) - ((0.55 - 0.0055 * humidity) * (1.8 * temp - 26))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ForestGreen.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (condition.contains("Cloud")) Icons.Default.Cloud 
                                     else Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = SunlitAmber,
                        modifier = Modifier.size(40.dp)
                    )
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "$temp°C", 
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.Black,
                            color = ForestGreen
                        )
                        Text(
                            text = condition, 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = EarthySlate
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("THI INDEX", style = MaterialTheme.typography.labelSmall, color = WarmGray)
                    Text("${thi.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)
                    
                    val (stressLevel, stressColor) = when {
                        thi >= 79 -> "SEVERE STRESS" to TerracottaRed
                        thi >= 72 -> "MILD STRESS" to SunlitAmber
                        else -> "OPTIMAL" to SageSuccess
                    }
                    
                    Surface(
                        color = stressColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = stressLevel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = stressColor
                        )
                    }
                }
            }
        }
    }
}
