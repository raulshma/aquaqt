package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WaterParameters(
    val ammonia: Double? = null,
    val nitrite: Double? = null,
    val nitrate: Double? = null,
    val ph: Double? = null,
    val temperatureC: Double? = null,
    val gh: Double? = null,
    val kh: Double? = null,
    val salinity: Double? = null,
    val calcium: Double? = null,
    val alkalinity: Double? = null
)
