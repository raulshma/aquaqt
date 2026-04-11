package com.keepaside.aquapt.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantMessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

@Serializable
data class AssistantResponseTelemetry(
    val generationId: String? = null,
    val providerName: String? = null,
    val router: String? = null,
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val elapsedMs: Long? = null,
    val latencyMs: Long? = null,
    val generationTimeMs: Long? = null,
    val throughputCharsPerSecond: Double? = null,
    val throughputTokensPerSecond: Double? = null,
    val finishReason: String? = null,
    val nativeFinishReason: String? = null,
    val streamed: Boolean? = null
)

@Serializable
data class AssistantChatMessage(
    val id: String,
    val role: AssistantMessageRole,
    val content: String,
    val createdAt: String,
    val requestFailed: Boolean = false,
    val requestError: String? = null,
    val detectedActionIds: List<String> = emptyList(),
    val responseTelemetry: AssistantResponseTelemetry? = null
)

@Serializable
data class AssistantConversation(
    val id: String,
    val title: String,
    val pinned: Boolean = false,
    val messages: List<AssistantChatMessage> = emptyList(),
    val detectedActions: List<AssistantDetectedAction> = emptyList(),
    val warnings: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)