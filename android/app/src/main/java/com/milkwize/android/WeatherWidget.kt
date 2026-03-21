package com.milkwize.android

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
    var useFahrenheit by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshCount by remember { mutableStateOf(0) }

    LaunchedEffect(refreshCount) {
        isLoading = true
        errorMessage = null
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
                    apiKey = BuildConfig.OPENWEATHER_API_KEY
                )
            }
            weatherData = response
            Log.d("WeatherWidget", "Successfully loaded weather for ${response.cityName}")
        } catch (e: Exception) {
            Log.e("WeatherWidget", "Error fetching weather: ${e.message}", e)
            errorMessage = e.message ?: "Failed to connect to weather service"
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ForestGreen, strokeWidth = 3.dp)
        }
    } else if (weatherData != null) {
        WeatherContent(
            tempC = weatherData!!.main.temp,
            humidity = weatherData!!.main.humidity,
            condition = weatherData!!.weather.firstOrNull()?.main ?: "Clear",
            useFahrenheit = useFahrenheit,
            onToggleUnit = { useFahrenheit = !useFahrenheit }
        )
    } else {
        // Error state UI
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PaperWhite),
            border = BorderStroke(1.dp, TerracottaRed.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weather Update Failed", color = TerracottaRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(errorMessage ?: "Check internet connection", color = WarmGray, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { refreshCount++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = ForestGreen)
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(
    tempC: Double,
    humidity: Int,
    condition: String,
    useFahrenheit: Boolean,
    onToggleUnit: () -> Unit
) {
    // THI calculation: (1.8 * T + 32) - [(0.55 - 0.0055 * RH) * (1.8 * T - 26)]
    val thi = (1.8 * tempC + 32) - ((0.55 - 0.0055 * humidity) * (1.8 * tempC - 26))

    val displayTemp = if (useFahrenheit) (tempC * 9/5) + 32 else tempC
    val unitLabel = if (useFahrenheit) "°F" else "°C"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleUnit() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PaperWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = SunlitAmber.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                )
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (condition.contains("Cloud")) Icons.Default.Cloud 
                                         else Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = SunlitAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "${displayTemp.toInt()}$unitLabel",
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Black,
                        color = EarthySlate
                    )
                    Text(
                        text = condition.uppercase(), 
                        style = MaterialTheme.typography.labelMedium, 
                        color = WarmGray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("THI INDEX", style = MaterialTheme.typography.labelSmall, color = WarmGray, fontWeight = FontWeight.Bold)
                Text("${thi.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ForestGreen)
                
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

@Preview
@Composable
fun PreviewWeatherWidget() {
    AndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            WeatherContent(
                tempC = 24.5,
                humidity = 65,
                condition = "Sunny",
                useFahrenheit = false,
                onToggleUnit = {}
            )
        }
    }
}
