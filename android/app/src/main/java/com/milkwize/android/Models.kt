package com.milkwize.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val role: String, // "farmer" or "customer"
    @SerialName("farm_code")
    val farm_code: String? = null
)

@Serializable
data class Cow(
    val id: String,
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("tag")
    val name: String,
    val breed: String? = null
)

@Serializable
data class MilkingEvent(
    val id: String? = null,
    @SerialName("cow_id")
    val cowId: String,
    @SerialName("owner_id")
    val ownerId: String,
    @SerialName("recorded_by")
    val recordedBy: String,
    @SerialName("milk_liters")
    val milkLiters: Double,
    @SerialName("milking_time")
    val milkingTime: String
)