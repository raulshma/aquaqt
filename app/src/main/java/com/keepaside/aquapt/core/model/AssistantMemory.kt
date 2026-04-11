package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AssistantMemorySnippet(
    val id: String,
    val content: String,
    val similarity: Double? = null,
    val createdAt: String? = null,
    val category: String? = null,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null
)

data class AssistantMemoryCompactionPreview(
    val beforeCount: Int,
    val afterCount: Int,
    val facts: List<String>
)
