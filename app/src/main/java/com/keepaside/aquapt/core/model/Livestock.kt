package com.keepaside.aquapt.core.model

import java.util.UUID

data class Livestock(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val kind: LivestockKind = LivestockKind.FISH,
    val name: String,
    val species: String = "",
    val quantity: Int = 1,
    val acquiredAt: String = "",
    val purchasePrice: Double? = null,
    val photoUri: String? = null,
    val dietaryNotes: String? = null,
    val parentId: String? = null,
    val status: LivestockStatus = LivestockStatus.ACTIVE
)
