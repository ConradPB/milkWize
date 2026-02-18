package com.milkwize.app

import kotlinx.serialization.Serializable

@Serializable
data class MilkingEvent(
    val id: Int? = null,
    val cow_id: String,
    val amount: Double,
    val created_at: String? = null
)