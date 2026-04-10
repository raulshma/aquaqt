package com.keepaside.aquapt.core.model

data class EntityRef(
    val kind: EntityKind,
    val id: String,
    val aquariumId: String? = null
)
