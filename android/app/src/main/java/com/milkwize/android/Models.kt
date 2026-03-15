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
