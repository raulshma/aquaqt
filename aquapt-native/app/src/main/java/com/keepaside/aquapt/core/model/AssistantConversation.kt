package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantMessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

@Serializable
data class AssistantChatMessage(
    val id: String,
    val role: AssistantMessageRole,
    val content: String,
    val createdAt: String,
    val requestFailed: Boolean = false,
    val requestError: String? = null,
    val detectedActionIds: List<String> = emptyList()
)

@Serializable
data class AssistantConversation(
    val id: String,
    val title: String,
    val pinned: Boolean = false,
    val messages: List<AssistantChatMessage> = emptyList(),
    val warnings: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)