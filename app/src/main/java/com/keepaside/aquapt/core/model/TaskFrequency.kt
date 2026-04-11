package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskFrequency(val kind: TaskFrequencyKind, val customDays: Int? = null) {

    companion object {
        val DAILY = TaskFrequency(TaskFrequencyKind.DAILY)
        val WEEKLY = TaskFrequency(TaskFrequencyKind.WEEKLY)
        val BI_WEEKLY = TaskFrequency(TaskFrequencyKind.BI_WEEKLY)
        val MONTHLY = TaskFrequency(TaskFrequencyKind.MONTHLY)

        fun custom(days: Int): TaskFrequency {
            require(days >= 1) { "Custom frequency days must be >= 1" }
            return TaskFrequency(TaskFrequencyKind.CUSTOM, customDays = days)
        }

        fun parse(value: String): TaskFrequency {
            val lower = value.lowercase()
            return when {
                lower.startsWith("custom-") -> {
                    val days = lower.removePrefix("custom-").toIntOrNull() ?: 1
                    custom(days.coerceAtLeast(1))
                }
                lower == "daily" -> DAILY
                lower == "weekly" -> WEEKLY
                lower == "bi-weekly" -> BI_WEEKLY
                lower == "monthly" -> MONTHLY
                else -> DAILY
            }
        }
    }

    fun serialize(): String = when (kind) {
        TaskFrequencyKind.CUSTOM -> "custom-${customDays ?: 1}"
        TaskFrequencyKind.DAILY -> "daily"
        TaskFrequencyKind.WEEKLY -> "weekly"
        TaskFrequencyKind.BI_WEEKLY -> "bi-weekly"
        TaskFrequencyKind.MONTHLY -> "monthly"
    }

    fun getLabel(): String = when (kind) {
        TaskFrequencyKind.CUSTOM -> {
            val days = customDays ?: 1
            "Every $days day${if (days == 1) "" else "s"}"
        }
        TaskFrequencyKind.DAILY -> "Daily"
        TaskFrequencyKind.WEEKLY -> "Weekly"
        TaskFrequencyKind.BI_WEEKLY -> "Bi-weekly"
        TaskFrequencyKind.MONTHLY -> "Monthly"
    }
}

enum class TaskFrequencyKind {
    DAILY, WEEKLY, BI_WEEKLY, MONTHLY, CUSTOM
}
