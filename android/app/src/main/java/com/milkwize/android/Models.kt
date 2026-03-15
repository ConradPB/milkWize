package com.milkwize.android

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val role: String, // "farmer" or "customer"
    val farm_code: String? = null
)

@Serializable
data class Cow(
    val id: String,
    val ownerId: String,
    val name: String,
    val breed: String? = null
)

@Serializable
data class MilkingEvent(
    val id: Int? = null,
    val cowId: String,
    val ownerId: String,
    val recordedBy: String,
    val milkLiters: Double,
    val milkingTime: String
)