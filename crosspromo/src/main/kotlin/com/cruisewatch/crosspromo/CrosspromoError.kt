package com.cruisewatch.crosspromo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CrosspromoError(
    @SerialName("error") val error: String = "",
)
