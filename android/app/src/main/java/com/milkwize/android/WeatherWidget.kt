package com.milkwize.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
    var useFahrenheit by remember { mutableStateOf(false) } // Default to Celsius for Africa

    LaunchedEffect(Unit) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(WeatherService::class.java)
            
            val response = withContext(Dispatchers.IO) {
                service.getWeather(
                    lat = -0.6067, 
                    lon = 30.6558, 
                    apiKey = "YOUR_OPENWEATHER_API_KEY"
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
        val tempC = weatherData!!.main.temp
        val humidity = weatherData!!.main.humidity
        val condition = weatherData!!.weather.firstOrNull()?.main ?: "Clear"
        
        // THI calculation always uses Celsius internally as per standard dairy science formula
        val thi = (1.8 * tempC + 32) - ((0.55 - 0.0055 * humidity) * (1.8 * tempC - 26))

        val displayTemp = if (useFahrenheit) (tempC * 9/5) + 32 else tempC
        val unitLabel = if (useFahrenheit) "°F" else "°C"

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { useFahrenheit = !useFahrenheit }, // Allow farmers to toggle by clicking the card
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
                            text = "${displayTemp.toInt()}$unitLabel",
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
