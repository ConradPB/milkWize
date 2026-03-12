package com.milkwize.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MilkingEvent(
    val id: String? = null,

    @SerialName("owner_id")
    val ownerId: String? = null,

    @SerialName("recorded_by")
    val recordedBy: String? = null,

    @SerialName("cow_id")
    val cowId: String,

    @SerialName("milk_liters")
    val milkLiters: Double,

    @SerialName("milking_time")
    val milkingTime: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)
