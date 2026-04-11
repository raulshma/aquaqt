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
    const val ADD_AQUARIUM = "add_aquarium"
    const val ADD_LIVESTOCK = "add_livestock"
    const val ADD_ASSET = "add_asset"
    const val ADD_CONSUMABLE = "add_consumable"
    const val CONSUME_CONSUMABLE = "consume_consumable"
    const val SET_ISSUE_STATUS = "set_issue_status"

    val supported = setOf(
        CREATE_TASK_TEMPLATE,
        COMPLETE_TASK,
        LOG_DOSING,
        LOG_PARAMETERS,
        ADD_ISSUE,
        ADD_MEMO,
        SAVE_REMINDER_SETTINGS,
        ADD_AQUARIUM,
        ADD_LIVESTOCK,
        ADD_ASSET,
        ADD_CONSUMABLE,
        CONSUME_CONSUMABLE,
        SET_ISSUE_STATUS
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
    val waterType: WaterType? = null,
    val volumeLiters: Double? = null,
    val dimensions: String? = null,
    val setupDate: String? = null,
    val investmentCost: Double? = null,
    val livestockId: String? = null,
    val livestockName: String? = null,
    val species: String? = null,
    val quantity: Int? = null,
    val livestockKind: LivestockKind? = null,
    val livestockStatus: LivestockStatus? = null,
    val issueId: String? = null,
    val issueStatus: IssueStatus? = null,
    val resolutionNote: String? = null,
    val assetCategory: AssetCategory? = null,
    val brandModel: String? = null,
    val purchasedAt: String? = null,
    val price: Double? = null,
    val consumableId: String? = null,
    val consumableName: String? = null,
    val consumableUnit: ConsumableUnit? = null,
    val remaining: Double? = null,
    val reorderAt: Double? = null,
    val amountUsed: Double? = null,
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
