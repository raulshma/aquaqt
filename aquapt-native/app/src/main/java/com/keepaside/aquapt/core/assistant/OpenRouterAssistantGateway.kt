package com.keepaside.aquapt.core.assistant

import com.keepaside.aquapt.core.model.AssistantMessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class AssistantGatewayMessage(
    val role: AssistantMessageRole,
    val content: String
)

data class AssistantGatewayRequest(
    val apiKey: String,
    val model: String,
    val messages: List<AssistantGatewayMessage>
)

interface AssistantGateway {
    suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): String
}

class OpenRouterAssistantGateway(
    private val endpoint: String = openRouterChatUrl,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : AssistantGateway {

    override suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val trimmedApiKey = request.apiKey.trim()
        if (trimmedApiKey.isEmpty()) {
            throw IOException("Missing OpenRouter API key.")
        }

        val selectedModel = request.model.trim()
        if (selectedModel.isEmpty()) {
            throw IOException("Missing OpenRouter model.")
        }

        val payload = buildJsonObject {
            put("model", JsonPrimitive(selectedModel))
            put("temperature", JsonPrimitive(0.2))
            put("stream", JsonPrimitive(true))
            put(
                "messages",
                buildJsonArray {
                    request.messages
                        .filter { it.content.isNotBlank() }
                        .forEach { message ->
                            add(
                                buildJsonObject {
                                    put(
                                        "role",
                                        JsonPrimitive(
                                            when (message.role) {
                                                AssistantMessageRole.SYSTEM -> "system"
                                                AssistantMessageRole.USER -> "user"
                                                AssistantMessageRole.ASSISTANT -> "assistant"
                                            }
                                        )
                                    )
                                    put("content", JsonPrimitive(message.content))
                                }
                            )
                        }
                }
            )
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 0
            doOutput = true
            setRequestProperty("Authorization", "Bearer $trimmedApiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errorText = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?.trim()
                    .orEmpty()

                throw IOException(
                    errorText.ifBlank {
                        "Assistant request failed ($statusCode)."
                    }
                )
            }

            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val fallback = parseNonStreamingReply(body)
                if (fallback.isNotEmpty()) {
                    onSnapshot(fallback)
                    return@withContext fallback
                }
                throw IOException("Assistant returned an empty response.")
            }

            val responseBody = connection.inputStream.bufferedReader()
            val responseBuilder = StringBuilder()

            val eventLines = mutableListOf<String>()
            var streamDone = false

            suspend fun processEvent(): Boolean {
                if (eventLines.isEmpty()) {
                    return false
                }

                val payloadLines = eventLines
                    .filter { line -> line.startsWith("data:") }
                    .map { line -> line.removePrefix("data:").trimStart() }

                if (payloadLines.isEmpty()) {
                    return false
                }

                val chunkPayload = payloadLines.joinToString("\n").trim()
                if (chunkPayload.isEmpty()) {
                    return false
                }

                if (chunkPayload == "[DONE]") {
                    return true
                }

                val element = runCatching {
                    json.parseToJsonElement(chunkPayload).jsonObject
                }.getOrNull() ?: return false

                val errorMessage = element
                    .jsonObjectValue("error")
                    ?.stringValue("message")

                if (!errorMessage.isNullOrBlank()) {
                    throw IOException(errorMessage)
                }

                val deltaContent = element
                    .jsonArrayValue("choices")
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.jsonObjectValue("delta")
                    ?.stringValue("content")

                if (!deltaContent.isNullOrEmpty()) {
                    responseBuilder.append(deltaContent)
                    onSnapshot(responseBuilder.toString())
                }

                return false
            }

            responseBody.use { reader ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break

                    if (line.isBlank()) {
                        streamDone = processEvent()
                        eventLines.clear()
                        if (streamDone) {
                            break
                        }
                        continue
                    }

                    eventLines += line
                }

                if (!streamDone && eventLines.isNotEmpty()) {
                    processEvent()
                }
            }

            val streamedReply = responseBuilder.toString().trim()
            if (streamedReply.isNotEmpty()) {
                return@withContext streamedReply
            }

            throw IOException("Assistant returned an empty response.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNonStreamingReply(body: String): String {
        if (body.isBlank()) return ""

        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject
        }.getOrNull() ?: return ""

        return parsed
            .jsonArrayValue("choices")
            ?.firstOrNull()
            ?.jsonObject
            ?.jsonObjectValue("message")
            ?.stringValue("content")
            ?.trim()
            .orEmpty()
    }

    private fun JsonObject.jsonObjectValue(key: String): JsonObject? =
        this[key]?.let { element -> runCatching { element.jsonObject }.getOrNull() }

    private fun JsonObject.jsonArrayValue(key: String): JsonArray? =
        this[key]?.let { element -> runCatching { element.jsonArray }.getOrNull() }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]
            ?.let { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf { value -> value.isNotBlank() }

    companion object {
        private const val openRouterChatUrl = "https://openrouter.ai/api/v1/chat/completions"
    }
}