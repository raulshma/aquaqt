package com.keepaside.aquapt.core.model

import java.util.UUID

data class ReminderGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hours: List<Int> = emptyList()
)
