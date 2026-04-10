package com.keepaside.aquapt.core.model

import java.util.UUID

data class Memo(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val content: String,
    val createdAt: String,
    val photoUri: String? = null
)
