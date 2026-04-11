package com.keepaside.aquapt.core.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    val created: Long? = null,
    val contextLength: Long? = null,
    val promptPrice: Double? = null,
    val completionPrice: Double? = null
)

interface OpenRouterModelListingGateway {
    suspend fun fetchModels(): List<OpenRouterModel>
}

class OpenRouterModelListingGatewayImpl(
    private val endpoint: String = openRouterModelsUrl,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : OpenRouterModelListingGateway {

    override suspend fun fetchModels(): List<OpenRouterModel> =
        withContext(Dispatchers.IO) {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    val errorText = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?.trim()
                        .orEmpty()
                    throw IOException(
                        errorText.ifBlank { "Model listing request failed ($statusCode)." }
                    )
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (body.isBlank()) {
                    throw IOException("Model listing returned an empty response.")
                }

                val parsed = runCatching {
                    json.parseToJsonElement(body).jsonObject
                }.getOrNull() ?: throw IOException("Invalid model listing response.")

                val dataArray = parsed["data"]
                    ?.let { element -> runCatching { element.jsonArray }.getOrNull() }
                    ?: throw IOException("Missing model data array in response.")

                dataArray.mapNotNull { element ->
                    runCatching { parseModel(element.jsonObject) }.getOrNull()
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun parseModel(obj: JsonObject): OpenRouterModel {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return OpenRouterModel(id = "")
        if (id.isEmpty()) return OpenRouterModel(id = "")

        val name = obj["name"]?.jsonPrimitive?.contentOrNull
        val created = obj["created"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val contextLength = obj["context_length"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        val pricing = obj["pricing"]?.let { element ->
            runCatching { element.jsonObject }.getOrNull()
        }

        val promptPrice = pricing?.let { p ->
            p["prompt"]?.jsonPrimitive?.let { primitive ->
                primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
            }
        }

        val completionPrice = pricing?.let { p ->
            p["completion"]?.jsonPrimitive?.let { primitive ->
                primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
            }
        }

        return OpenRouterModel(
            id = id,
            name = name,
            created = created,
            contextLength = contextLength,
            promptPrice = promptPrice,
            completionPrice = completionPrice
        )
    }

    companion object {
        private const val openRouterModelsUrl = "https://openrouter.ai/api/v1/models"
    }
}
