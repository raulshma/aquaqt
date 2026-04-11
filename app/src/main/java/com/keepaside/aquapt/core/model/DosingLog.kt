package com.keepaside.aquapt.core.model

import java.util.UUID

data class DosingLog(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val product: String,
    val amountMl: Double,
    val createdAt: String,
    val note: String? = null
)
