package com.milkwize.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Cow(
    val id: String,

    @SerialName("tag") // Maps the Supabase "tag" column to this variable
    val name: String,

    val breed: String? = null,
    val notes: String? = null
)