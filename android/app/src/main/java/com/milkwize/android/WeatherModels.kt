package com.milkwize.android

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("main") val main: Main,
    @SerializedName("weather") val weather: List<Weather>,
    @SerializedName("name") val cityName: String
)

data class Main(
    @SerializedName("temp") val temp: Double,
    @SerializedName("humidity") val humidity: Int
)

data class Weather(
    @SerializedName("main") val main: String,
    @SerializedName("description") val description: String
)

interface WeatherService {
    @retrofit2.http.GET("data/2.5/weather")
    suspend fun getWeather(
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lon") lon: Double,
        @retrofit2.http.Query("appid") apiKey: String,
        @retrofit2.http.Query("units") units: String = "metric"
    ): WeatherResponse
}
