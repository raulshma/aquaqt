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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

data class AssistantGatewayMessage(
    val role: AssistantMessageRole,
    val content: String
)

data class AssistantGatewayRequest(
    val apiKey: String,
    val model: String,
    val messages: List<AssistantGatewayMessage>
)

data class AssistantGatewayUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null
)

data class AssistantGatewayTelemetry(
    val generationId: String? = null,
    val providerName: String? = null,
    val router: String? = null,
    val model: String? = null,
    val usage: AssistantGatewayUsage? = null,
    val cost: Double? = null,
    val latencyMs: Long? = null,
    val generationTimeMs: Long? = null,
    val finishReason: String? = null,
    val nativeFinishReason: String? = null,
    val streamed: Boolean = true
)

data class AssistantGatewayResponse(
    val text: String,
    val telemetry: AssistantGatewayTelemetry? = null
)

interface AssistantGateway {
    suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): AssistantGatewayResponse
}

class OpenRouterAssistantGateway(
    private val endpoint: String = openRouterChatUrl,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : AssistantGateway {

    private data class OpenRouterGenerationMetadata(
        val providerName: String? = null,
        val router: String? = null,
        val latencyMs: Long? = null,
        val generationTimeMs: Long? = null,
        val totalCost: Double? = null
    )

    override suspend fun requestStreamingReply(
        request: AssistantGatewayRequest,
        onSnapshot: suspend (String) -> Unit
    ): AssistantGatewayResponse = withContext(Dispatchers.IO) {
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
                if (fallback.text.isNotEmpty()) {
                    val enriched = enrichWithGenerationMetadata(
                        apiKey = trimmedApiKey,
                        response = fallback
                    )
                    onSnapshot(enriched.text)
                    return@withContext enriched
                }
                throw IOException("Assistant returned an empty response.")
            }

            val responseBody = connection.inputStream.bufferedReader()
            val responseBuilder = StringBuilder()

            var generationId: String? = null
            var providerName: String? = null
            var responseModel: String? = null
            var finishReason: String? = null
            var nativeFinishReason: String? = null
            var usage: AssistantGatewayUsage? = null

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

                generationId = element.stringValue("id") ?: generationId
                providerName = element.stringValue("provider") ?: providerName
                responseModel = element.stringValue("model") ?: responseModel

                val chunkChoice = element
                    .jsonArrayValue("choices")
                    ?.firstOrNull()
                    ?.jsonObject

                finishReason = chunkChoice
                    ?.stringValue("finish_reason")
                    ?: finishReason
                nativeFinishReason = chunkChoice
                    ?.stringValue("native_finish_reason")
                    ?: nativeFinishReason

                usage = element
                    .jsonObjectValue("usage")
                    ?.toGatewayUsage()
                    ?: usage

                val deltaContent = chunkChoice
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
                val generationMetadata = generationId
                    ?.let { id ->
                        runCatching {
                            fetchGenerationMetadata(
                                apiKey = trimmedApiKey,
                                generationId = id
                            )
                        }.getOrNull()
                    }

                return@withContext AssistantGatewayResponse(
                    text = streamedReply,
                    telemetry = AssistantGatewayTelemetry(
                        generationId = generationId,
                        providerName = generationMetadata?.providerName ?: providerName,
                        router = generationMetadata?.router,
                        model = responseModel ?: selectedModel,
                        usage = usage,
                        cost = usage?.cost ?: generationMetadata?.totalCost,
                        latencyMs = generationMetadata?.latencyMs,
                        generationTimeMs = generationMetadata?.generationTimeMs,
                        finishReason = finishReason,
                        nativeFinishReason = nativeFinishReason,
                        streamed = true
                    )
                )
            }

            throw IOException("Assistant returned an empty response.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNonStreamingReply(body: String): AssistantGatewayResponse {
        if (body.isBlank()) return AssistantGatewayResponse(text = "")

        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject
        }.getOrNull() ?: return AssistantGatewayResponse(text = "")

        val text = parsed
            .jsonArrayValue("choices")
            ?.firstOrNull()
            ?.jsonObject
            ?.jsonObjectValue("message")
            ?.stringValue("content")
            ?.trim()
            .orEmpty()

        val usage = parsed
            .jsonObjectValue("usage")
            ?.toGatewayUsage()

        return AssistantGatewayResponse(
            text = text,
            telemetry = AssistantGatewayTelemetry(
                generationId = parsed.stringValue("id"),
                providerName = parsed.stringValue("provider"),
                model = parsed.stringValue("model"),
                usage = usage,
                cost = usage?.cost,
                finishReason = parsed
                    .jsonArrayValue("choices")
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.stringValue("finish_reason"),
                nativeFinishReason = parsed
                    .jsonArrayValue("choices")
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.stringValue("native_finish_reason"),
                streamed = false
            )
        )
    }

    private fun JsonObject.jsonObjectValue(key: String): JsonObject? =
        this[key]?.let { element -> runCatching { element.jsonObject }.getOrNull() }

    private fun JsonObject.jsonArrayValue(key: String): JsonArray? =
        this[key]?.let { element -> runCatching { element.jsonArray }.getOrNull() }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]
            ?.let { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
            ?.takeIf { value -> value.isNotBlank() }

    private fun JsonObject.intValue(key: String): Int? {
        val primitive = this[key]
            ?.let { element -> runCatching { element.jsonPrimitive }.getOrNull() }
            ?: return null

        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun JsonObject.doubleValue(key: String): Double? {
        val primitive = this[key]
            ?.let { element -> runCatching { element.jsonPrimitive }.getOrNull() }
            ?: return null

        return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
    }

    private fun JsonObject.toGatewayUsage(): AssistantGatewayUsage? {
        val usage = AssistantGatewayUsage(
            promptTokens = intValue("prompt_tokens"),
            completionTokens = intValue("completion_tokens"),
            totalTokens = intValue("total_tokens"),
            cost = doubleValue("cost")
        )

        return if (
            usage.promptTokens == null &&
            usage.completionTokens == null &&
            usage.totalTokens == null &&
            usage.cost == null
        ) {
            null
        } else {
            usage
        }
    }

    private fun enrichWithGenerationMetadata(
        apiKey: String,
        response: AssistantGatewayResponse
    ): AssistantGatewayResponse {
        val telemetry = response.telemetry ?: return response
        val generationId = telemetry.generationId ?: return response

        val generationMetadata = runCatching {
            fetchGenerationMetadata(apiKey = apiKey, generationId = generationId)
        }.getOrNull() ?: return response

        return response.copy(
            telemetry = telemetry.copy(
                providerName = generationMetadata.providerName ?: telemetry.providerName,
                router = generationMetadata.router ?: telemetry.router,
                cost = telemetry.cost ?: telemetry.usage?.cost ?: generationMetadata.totalCost,
                latencyMs = generationMetadata.latencyMs ?: telemetry.latencyMs,
                generationTimeMs = generationMetadata.generationTimeMs ?: telemetry.generationTimeMs
            )
        )
    }

    private fun fetchGenerationMetadata(
        apiKey: String,
        generationId: String
    ): OpenRouterGenerationMetadata? {
        val encodedId = URLEncoder.encode(generationId, StandardCharsets.UTF_8.toString())
        val endpoint = "$openRouterGenerationUrl?id=$encodedId"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) {
                return null
            }

            val parsed = runCatching {
                json.parseToJsonElement(body).jsonObject
            }.getOrNull() ?: return null

            val data = parsed.jsonObjectValue("data") ?: return null

            OpenRouterGenerationMetadata(
                providerName = data.stringValue("provider_name") ?: data.stringValue("providerName"),
                router = data.stringValue("router"),
                latencyMs = data.doubleValue("latency")
                    ?.takeIf { it.isFinite() }
                    ?.roundToLong(),
                generationTimeMs = data.doubleValue("generation_time")
                    ?.takeIf { it.isFinite() }
                    ?.roundToLong(),
                totalCost = data.doubleValue("total_cost")
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val openRouterChatUrl = "https://openrouter.ai/api/v1/chat/completions"
        private const val openRouterGenerationUrl = "https://openrouter.ai/api/v1/generation"
    }
}