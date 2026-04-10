package com.keepaside.aquapt.core.model

import java.util.UUID

data class TimelineEvent(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val type: TimelineEventType,
    val createdAt: String,
    val title: String,
    val description: String? = null,
    val photoUri: String? = null,
    val source: EntityRef? = null,
    val related: List<EntityRef> = emptyList()
)
