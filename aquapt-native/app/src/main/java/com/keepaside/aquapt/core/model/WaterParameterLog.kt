package com.keepaside.aquapt.core.model

import java.util.UUID

data class WaterParameterLog(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val createdAt: String,
    val values: WaterParameters
)
