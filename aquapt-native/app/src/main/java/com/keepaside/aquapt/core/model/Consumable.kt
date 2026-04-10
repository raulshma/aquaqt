package com.keepaside.aquapt.core.model

import java.util.UUID

data class Consumable(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val name: String,
    val unit: ConsumableUnit = ConsumableUnit.ML,
    val remaining: Double = 0.0,
    val reorderAt: Double? = null,
    val updatedAt: String = "",
    val photoUri: String? = null
)
