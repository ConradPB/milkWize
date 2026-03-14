package com.milkwize.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MilkingEvent(
    @SerialName("owner_id")
    val ownerId: String,

    @SerialName("recorded_by")
    val recordedBy: String,

    @SerialName("cow_id")
    val cowId: String,

    @SerialName("milk_liters")
    val milkLiters: Double,

    @SerialName("milking_time")
    val milkingTime: String,

    @SerialName("id")
    val id: String? = null
)
