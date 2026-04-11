package com.keepaside.aquapt.core.model

import java.util.UUID

data class TaskExecution(
    val id: String = UUID.randomUUID().toString(),
    val taskTemplateId: String,
    val aquariumId: String,
    val completedAt: String,
    val note: String? = null
)
