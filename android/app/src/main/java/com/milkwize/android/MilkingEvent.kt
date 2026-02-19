package com.milkwize.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MilkingEvent(
    val id: Int? = null,

    @SerialName("cow_id")
    val cowId: String,

    @SerialName("amount")
    val amount: Double,

    @SerialName("created_at")
    val createdAt: String? = null
)