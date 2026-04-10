package com.keepaside.aquapt.core.model

import java.util.UUID

data class Aquarium(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val volumeLiters: Double,
    val dimensions: String = "",
    val waterType: WaterType = WaterType.FRESHWATER,
    val setupDate: String = "",
    val investmentCost: Double? = null,
    val photoUri: String? = null
)
