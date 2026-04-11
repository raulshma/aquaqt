package com.keepaside.aquapt.core.model

import java.util.UUID

data class Asset(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val category: AssetCategory = AssetCategory.OTHER,
    val brandModel: String = "",
    val purchasedAt: String? = null,
    val price: Double? = null,
    val maintenanceTaskTemplateIds: List<String> = emptyList(),
    val photoUri: String? = null
)
