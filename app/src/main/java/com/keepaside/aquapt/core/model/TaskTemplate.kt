package com.keepaside.aquapt.core.model

import java.util.UUID

data class TaskTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val category: TaskCategory? = null,
    val livestockId: String? = null,
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val aquariumIds: List<String> = emptyList(),
    val startDate: String? = null,
    val timesPerDay: Int? = null,
    val reminderHours: List<Int> = emptyList(),
    val reminderGroupId: String? = null
)
