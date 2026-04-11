package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

object AssistantActionTypes {
    const val CREATE_TASK_TEMPLATE = "create_task_template"
    const val COMPLETE_TASK = "complete_task"
    const val LOG_DOSING = "log_dosing"
    const val LOG_PARAMETERS = "log_parameters"
    const val ADD_ISSUE = "add_issue"
    const val ADD_MEMO = "add_memo"
    const val SAVE_REMINDER_SETTINGS = "save_reminder_settings"

    val supported = setOf(
        CREATE_TASK_TEMPLATE,
        COMPLETE_TASK,
        LOG_DOSING,
        LOG_PARAMETERS,
        ADD_ISSUE,
        ADD_MEMO,
        SAVE_REMINDER_SETTINGS
    )
}

@Serializable
data class AssistantDetectedAction(
    val id: String,
    val type: String,
    val title: String? = null,
    val frequency: String? = null,
    val aquariumId: String? = null,
    val aquariumName: String? = null,
    val description: String? = null,
    val taskTemplateId: String? = null,
    val taskTitle: String? = null,
    val product: String? = null,
    val amountMl: Double? = null,
    val note: String? = null,
    val parameters: WaterParameters? = null,
    val issueTitle: String? = null,
    val memoContent: String? = null,
    val reminderEnabled: Boolean? = null,
    val reminderHour: Int? = null,
    val reminderHours: List<Int> = emptyList(),
    val confidence: Double = 0.6,
    val approved: Boolean = false,
    val validationErrors: List<String> = emptyList(),
    val sourceTranscript: String = "",
    val sourceMessageId: String? = null
)

data class AssistantActionExtractionResult(
    val actions: List<AssistantDetectedAction>,
    val warnings: List<String>,
    val raw: String
)

data class AssistantActionExecutionItemResult(
    val actionId: String,
    val actionType: String,
    val created: Boolean,
    val reason: String? = null,
    val summary: String? = null
)

data class AssistantActionExecutionResult(
    val createdCount: Int,
    val skippedCount: Int,
    val results: List<AssistantActionExecutionItemResult>
)
