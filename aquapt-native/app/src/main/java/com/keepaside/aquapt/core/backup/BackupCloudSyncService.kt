package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.repository.BackupS3Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val backupSchemaVersion = 1
private const val encryptionVersion = 1
private const val kdfIterations = 210_000
private const val kdfSaltBytes = 16
private const val gcmNonceBytes = 12
private const val derivedKeyBytes = 32
private const val historyBackupSuffix = ".enc.json"

private val backupJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val amzDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

private val dateStampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneOffset.UTC)

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int,
    val exportedAt: String,
    val appState: JsonElement
)

@Serializable
private data class EncryptedBackupPayload(
    val version: Int,
    val algorithm: String,
    val kdf: EncryptedBackupKdf,
    val nonceB64: String,
    val ciphertextB64: String
)

@Serializable
private data class EncryptedBackupKdf(
    val name: String,
    val iterations: Int,
    val saltB64: String
)

data class BackupSyncOutcome(
    val uploadedAt: String,
    val objectUrl: String,
    val payloadBytes: Int,
    val versionedObjectKey: String? = null,
    val deletedVersionedKeys: List<String> = emptyList()
)

data class BackupCloudObject(
    val objectKey: String,
    val lastModified: String? = null,
    val isLatestObject: Boolean = false
)

data class BackupCloudDeleteOutcome(
    val deletedObjectKey: String,
    val wasLatestObject: Boolean
)

data class BackupRestoreOutcome(
    val restoredAt: String,
    val sourceObjectKey: String,
    val exportedAt: String,
    val restoredSettings: AppSettings,
    val skippedCounts: Map<String, Int> = emptyMap()
)

data class S3HistoryCleanupOutcome(
    val deletedKeys: List<String>,
    val keptKeys: List<String>
)

internal data class BackupS3SyncConfig(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val objectKey: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val forcePathStyle: Boolean
)

internal data class S3ListEntry(
    val key: String,
    val lastModified: String?
)

interface BackupCloudSyncGateway {
    suspend fun syncCurrentStateToCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials
    ): BackupSyncOutcome

    suspend fun listAvailableCloudBackups(
        settings: AppSettings,
        credentials: BackupS3Credentials
    ): List<BackupCloudObject>

    suspend fun restoreFromCloudObject(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        objectKey: String,
        replaceExisting: Boolean = true
    ): BackupRestoreOutcome

    suspend fun restoreLatestFromCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        replaceExisting: Boolean = true
    ): BackupRestoreOutcome

    suspend fun deleteCloudBackupObject(
        settings: AppSettings,
        credentials: BackupS3Credentials,
        objectKey: String
    ): BackupCloudDeleteOutcome

    suspend fun pruneCloudBackupHistory(
        settings: AppSettings,
        credentials: BackupS3Credentials,
        retentionDaysOverride: Int? = null
    ): S3HistoryCleanupOutcome
}

class BackupCloudSyncService(
    private val backupGateway: BackupCompatibilityGateway
) : BackupCloudSyncGateway {

    override suspend fun syncCurrentStateToCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials
    ): BackupSyncOutcome {
        val exportedStateJson = backupGateway.exportCurrentStateJson(
            settings = settings,
            pretty = false
        )
        val envelope = createBackupEnvelope(exportedStateJson)
        val encryptedPayload = encryptBackupEnvelope(
            envelope = envelope,
            masterKey = masterKey
        )

        val config = parseS3SyncConfig(settings, credentials)

        val latestUpload = uploadEncryptedBackupToS3(
            configInput = config,
            encryptedPayload = encryptedPayload
        )

        var versionedObjectKey: String? = null
        var deletedVersionedKeys: List<String> = emptyList()

        if (settings.backupUseVersionedKeys) {
            val generatedVersionedObjectKey = buildVersionedBackupObjectKey(
                latestObjectKey = config.objectKey,
                isoTimestamp = envelope.exportedAt
            )

            if (generatedVersionedObjectKey != config.objectKey) {
                uploadEncryptedBackupToS3(
                    configInput = config.copy(objectKey = generatedVersionedObjectKey),
                    encryptedPayload = encryptedPayload
                )
            }

            val cleanup = cleanupVersionedBackups(
                configInput = config,
                latestObjectKey = config.objectKey,
                retentionDays = settings.backupRetentionDays ?: 30
            )

            versionedObjectKey = generatedVersionedObjectKey
            deletedVersionedKeys = cleanup.deletedKeys
        }

        return latestUpload.copy(
            versionedObjectKey = versionedObjectKey,
            deletedVersionedKeys = deletedVersionedKeys
        )
    }

    override suspend fun listAvailableCloudBackups(
        settings: AppSettings,
        credentials: BackupS3Credentials
    ): List<BackupCloudObject> {
        val config = parseS3SyncConfig(settings, credentials)
        val latestObjectKey = normalizeKeyPrefix(config.objectKey)
        val historyPrefix = getHistoryPrefixFromObjectKey(latestObjectKey)

        val latestMatches = listS3ObjectsWithPrefix(
            configInput = config,
            prefix = latestObjectKey,
            maxKeys = 50
        ).filter { entry -> entry.key == latestObjectKey }

        val historyMatches = listS3ObjectsWithPrefix(
            configInput = config,
            prefix = historyPrefix,
            maxKeys = 1_000
        ).filter { entry -> entry.key.endsWith(historyBackupSuffix) }

        val merged = (latestMatches + historyMatches)
            .distinctBy { entry -> entry.key }
            .sortedWith(
                compareByDescending<S3ListEntry> { entry -> entry.key == latestObjectKey }
                    .thenByDescending { entry ->
                        entry.lastModified
                            ?.let(::parseInstantOrNull)
                            ?.toEpochMilli()
                            ?: Long.MIN_VALUE
                    }
                    .thenByDescending { entry -> entry.key }
            )

        return merged.map { entry ->
            BackupCloudObject(
                objectKey = entry.key,
                lastModified = entry.lastModified,
                isLatestObject = entry.key == latestObjectKey
            )
        }
    }

    override suspend fun restoreFromCloudObject(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        objectKey: String,
        replaceExisting: Boolean
    ): BackupRestoreOutcome {
        val normalizedObjectKey = normalizeKeyPrefix(objectKey)
        require(normalizedObjectKey.isNotEmpty()) {
            "Cloud backup object key is required."
        }

        val config = parseS3SyncConfig(settings, credentials)
        val encryptedPayload = downloadEncryptedBackupFromS3(
            configInput = config.copy(objectKey = normalizedObjectKey)
        ) ?: throw IllegalStateException(
            "Cloud backup object not found: $normalizedObjectKey"
        )

        val envelope = decryptBackupEnvelope(
            encryptedPayloadJson = encryptedPayload,
            masterKey = masterKey
        )

        val appStateJson = backupJson.encodeToString(
            JsonElement.serializer(),
            envelope.appState
        )

        val importResult = backupGateway.importFromJson(
            payload = appStateJson,
            replaceExisting = replaceExisting
        )

        return BackupRestoreOutcome(
            restoredAt = Instant.now().toString(),
            sourceObjectKey = normalizedObjectKey,
            exportedAt = envelope.exportedAt,
            restoredSettings = importResult.snapshot.settings,
            skippedCounts = importResult.skippedCounts
        )
    }

    override suspend fun restoreLatestFromCloud(
        settings: AppSettings,
        masterKey: String,
        credentials: BackupS3Credentials,
        replaceExisting: Boolean
    ): BackupRestoreOutcome = restoreFromCloudObject(
        settings = settings,
        masterKey = masterKey,
        credentials = credentials,
        objectKey = settings.backupS3ObjectKey
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: backupAutoSyncDefaultObjectKey,
        replaceExisting = replaceExisting
    )

    override suspend fun deleteCloudBackupObject(
        settings: AppSettings,
        credentials: BackupS3Credentials,
        objectKey: String
    ): BackupCloudDeleteOutcome {
        val normalizedObjectKey = normalizeKeyPrefix(objectKey)
        require(normalizedObjectKey.isNotEmpty()) {
            "Cloud backup object key is required."
        }

        val config = parseS3SyncConfig(settings, credentials)
        deleteS3Object(
            configInput = config,
            objectKey = normalizedObjectKey
        )

        val latestObjectKey = normalizeKeyPrefix(
            settings.backupS3ObjectKey
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: backupAutoSyncDefaultObjectKey
        )

        return BackupCloudDeleteOutcome(
            deletedObjectKey = normalizedObjectKey,
            wasLatestObject = normalizedObjectKey == latestObjectKey
        )
    }

    override suspend fun pruneCloudBackupHistory(
        settings: AppSettings,
        credentials: BackupS3Credentials,
        retentionDaysOverride: Int?
    ): S3HistoryCleanupOutcome {
        val config = parseS3SyncConfig(settings, credentials)
        val latestObjectKey = normalizeKeyPrefix(
            settings.backupS3ObjectKey
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: backupAutoSyncDefaultObjectKey
        )
        val retentionDays = retentionDaysOverride
            ?: settings.backupRetentionDays
            ?: 30

        return cleanupVersionedBackups(
            configInput = config,
            latestObjectKey = latestObjectKey,
            retentionDays = retentionDays
        )
    }
}

fun createBackupEnvelope(
    appStateJson: String,
    exportedAt: String = Instant.now().toString()
): BackupEnvelope {
    val appState = backupJson.parseToJsonElement(appStateJson)
    require(appState is JsonObject) {
        "Backup app state payload must be a JSON object."
    }

    return BackupEnvelope(
        schemaVersion = backupSchemaVersion,
        exportedAt = exportedAt,
        appState = appState
    )
}

fun encryptBackupEnvelope(
    envelope: BackupEnvelope,
    masterKey: String
): String {
    val salt = randomBytes(kdfSaltBytes)
    val nonce = randomBytes(gcmNonceBytes)
    val key = deriveEncryptionKey(masterKey = masterKey, salt = salt)

    val plaintext = backupJson.encodeToString(BackupEnvelope.serializer(), envelope)
        .toByteArray(StandardCharsets.UTF_8)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(128, nonce)
    )

    val encrypted = cipher.doFinal(plaintext)

    val payload = EncryptedBackupPayload(
        version = encryptionVersion,
        algorithm = "AES-256-GCM",
        kdf = EncryptedBackupKdf(
            name = "PBKDF2-HMAC-SHA256",
            iterations = kdfIterations,
            saltB64 = base64Encode(salt)
        ),
        nonceB64 = base64Encode(nonce),
        ciphertextB64 = base64Encode(encrypted)
    )

    return backupJson.encodeToString(EncryptedBackupPayload.serializer(), payload)
}

fun decryptBackupEnvelope(
    encryptedPayloadJson: String,
    masterKey: String
): BackupEnvelope {
    val payload = backupJson.decodeFromString(
        EncryptedBackupPayload.serializer(),
        encryptedPayloadJson
    )

    require(payload.algorithm == "AES-256-GCM") {
        "Unsupported encryption algorithm."
    }
    require(payload.kdf.name == "PBKDF2-HMAC-SHA256") {
        "Unsupported backup key derivation function."
    }

    val salt = base64Decode(payload.kdf.saltB64)
    val nonce = base64Decode(payload.nonceB64)
    val ciphertext = base64Decode(payload.ciphertextB64)

    val key = deriveEncryptionKey(masterKey = masterKey, salt = salt)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(128, nonce)
    )

    val decrypted = cipher.doFinal(ciphertext)
    val decoded = decrypted.toString(StandardCharsets.UTF_8)
    val envelope = backupJson.decodeFromString(BackupEnvelope.serializer(), decoded)

    require(envelope.appState is JsonObject) {
        "Decrypted backup payload is invalid."
    }

    return envelope
}

fun buildVersionedBackupObjectKey(
    latestObjectKey: String,
    isoTimestamp: String
): String {
    val day = backupSyncDateStamp(isoTimestamp)
    require(day.isNotBlank()) {
        "Invalid timestamp for versioned backup key."
    }

    val historyPrefix = getHistoryPrefixFromObjectKey(latestObjectKey)
    return "${historyPrefix}${day}${historyBackupSuffix}"
}

fun backupSyncDateStamp(isoTimestamp: String): String {
    val instant = runCatching { Instant.parse(isoTimestamp) }.getOrNull()
        ?: runCatching {
            LocalDate.parse(isoTimestamp)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        }.getOrNull()
        ?: return ""

    return instant.atOffset(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

fun compareBackupIsoTimestamps(a: String?, b: String?): Int {
    val aMillis = parseIsoMillis(a)
    val bMillis = parseIsoMillis(b)

    if (aMillis == null && bMillis == null) {
        return 0
    }
    if (aMillis == null) {
        return -1
    }
    if (bMillis == null) {
        return 1
    }

    return when {
        aMillis == bMillis -> 0
        aMillis > bMillis -> 1
        else -> -1
    }
}

private data class SignedRequestHeaders(
    val headers: Map<String, String>
)

private data class ResolvedS3Url(
    val endpoint: URL,
    val canonicalUri: String,
    val requestUrl: String
)

private data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val body: String
)

private fun parseS3SyncConfig(
    settings: AppSettings,
    credentials: BackupS3Credentials
): BackupS3SyncConfig {
    val endpoint = settings.backupS3Endpoint
        ?.trim()
        .orEmpty()
    val region = settings.backupS3Region
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: backupAutoSyncDefaultRegion
    val bucket = settings.backupS3Bucket
        ?.trim()
        .orEmpty()
    val objectKey = normalizeKeyPrefix(
        settings.backupS3ObjectKey
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: backupAutoSyncDefaultObjectKey
    )

    require(endpoint.isNotEmpty()) {
        "S3 endpoint is required."
    }
    require(region.isNotEmpty()) {
        "S3 region is required."
    }
    require(bucket.isNotEmpty()) {
        "S3 bucket is required."
    }
    require(objectKey.isNotEmpty()) {
        "S3 object key is required."
    }

    val accessKeyId = credentials.accessKeyId.trim()
    val secretAccessKey = credentials.secretAccessKey.trim()

    require(accessKeyId.isNotEmpty() && secretAccessKey.isNotEmpty()) {
        "S3 credentials are required."
    }

    return BackupS3SyncConfig(
        endpoint = endpoint,
        region = region,
        bucket = bucket,
        objectKey = objectKey,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        forcePathStyle = settings.backupS3ForcePathStyle
    )
}

private suspend fun uploadEncryptedBackupToS3(
    configInput: BackupS3SyncConfig,
    encryptedPayload: String
): BackupSyncOutcome {
    val payloadBytes = encryptedPayload.toByteArray(StandardCharsets.UTF_8)
    val resolved = resolveS3ObjectUrl(configInput)

    val signed = buildSignedRequestHeaders(
        method = "PUT",
        endpoint = resolved.endpoint,
        canonicalUri = resolved.canonicalUri,
        canonicalQuery = "",
        payload = payloadBytes,
        config = configInput
    )

    val response = executeHttpRequest(
        method = "PUT",
        requestUrl = resolved.requestUrl,
        headers = signed.headers + mapOf(
            "content-type" to "application/json",
            "content-length" to payloadBytes.size.toString()
        ),
        body = payloadBytes
    )

    if (response.statusCode !in 200..299) {
        throw IllegalStateException(
            "S3 upload failed (${response.statusCode} ${response.statusMessage}): ${response.body.take(220)}"
        )
    }

    return BackupSyncOutcome(
        uploadedAt = Instant.now().toString(),
        objectUrl = resolved.requestUrl,
        payloadBytes = payloadBytes.size
    )
}

private suspend fun downloadEncryptedBackupFromS3(
    configInput: BackupS3SyncConfig
): String? {
    val resolved = resolveS3ObjectUrl(configInput)
    val payload = ByteArray(0)

    val signed = buildSignedRequestHeaders(
        method = "GET",
        endpoint = resolved.endpoint,
        canonicalUri = resolved.canonicalUri,
        canonicalQuery = "",
        payload = payload,
        config = configInput
    )

    val response = executeHttpRequest(
        method = "GET",
        requestUrl = resolved.requestUrl,
        headers = signed.headers
    )

    if (response.statusCode == 404) {
        return null
    }

    if (response.statusCode !in 200..299) {
        throw IllegalStateException(
            "S3 download failed (${response.statusCode} ${response.statusMessage}): ${response.body.take(220)}"
        )
    }

    return response.body
}

private suspend fun cleanupVersionedBackups(
    configInput: BackupS3SyncConfig,
    latestObjectKey: String,
    retentionDays: Int
): S3HistoryCleanupOutcome {
    val historyPrefix = getHistoryPrefixFromObjectKey(latestObjectKey)
    val objects = listS3ObjectsWithPrefix(
        configInput = configInput,
        prefix = historyPrefix,
        maxKeys = 1_000
    )

    val normalizedRetentionDays = retentionDays.coerceIn(1, 3_650)
    val cutoff = Instant.now().minus(Duration.ofDays(normalizedRetentionDays.toLong()))

    val deletable = objects
        .filter { entry -> entry.key.endsWith(historyBackupSuffix) }
        .filter { entry ->
            val lastModified = entry.lastModified
                ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            lastModified != null && lastModified.isBefore(cutoff)
        }
        .sortedBy { entry -> entry.lastModified.orEmpty() }

    for (entry in deletable) {
        deleteS3Object(configInput, entry.key)
    }

    val deletedKeys = deletable.map { entry -> entry.key }
    val keptKeys = objects
        .map { entry -> entry.key }
        .filterNot { key -> key in deletedKeys }

    return S3HistoryCleanupOutcome(
        deletedKeys = deletedKeys,
        keptKeys = keptKeys
    )
}

private suspend fun listS3ObjectsWithPrefix(
    configInput: BackupS3SyncConfig,
    prefix: String,
    maxKeys: Int = 500
): List<S3ListEntry> {
    val resolved = resolveS3BucketRoot(configInput)
    val payload = ByteArray(0)
    val canonicalQuery = canonicalizeQuery(
        mapOf(
            "list-type" to "2",
            "max-keys" to maxKeys.coerceIn(1, 1_000).toString(),
            "prefix" to prefix
        )
    )

    val signed = buildSignedRequestHeaders(
        method = "GET",
        endpoint = resolved.endpoint,
        canonicalUri = resolved.canonicalUri,
        canonicalQuery = canonicalQuery,
        payload = payload,
        config = configInput
    )

    val response = executeHttpRequest(
        method = "GET",
        requestUrl = "${resolved.requestUrl}?${canonicalQuery}",
        headers = signed.headers
    )

    if (response.statusCode !in 200..299) {
        throw IllegalStateException(
            "S3 list failed (${response.statusCode} ${response.statusMessage}): ${response.body.take(220)}"
        )
    }

    return parseS3ListResponse(response.body)
}

private suspend fun deleteS3Object(
    configInput: BackupS3SyncConfig,
    objectKey: String
) {
    val config = configInput.copy(objectKey = normalizeKeyPrefix(objectKey))
    val payload = ByteArray(0)
    val resolved = resolveS3ObjectUrl(config)

    val signed = buildSignedRequestHeaders(
        method = "DELETE",
        endpoint = resolved.endpoint,
        canonicalUri = resolved.canonicalUri,
        canonicalQuery = "",
        payload = payload,
        config = config
    )

    val response = executeHttpRequest(
        method = "DELETE",
        requestUrl = resolved.requestUrl,
        headers = signed.headers
    )

    if (response.statusCode !in 200..299 && response.statusCode != 404) {
        throw IllegalStateException(
            "S3 delete failed (${response.statusCode} ${response.statusMessage}): ${response.body.take(220)}"
        )
    }
}

private fun buildSignedRequestHeaders(
    method: String,
    endpoint: URL,
    canonicalUri: String,
    canonicalQuery: String,
    payload: ByteArray,
    config: BackupS3SyncConfig
): SignedRequestHeaders {
    val timestamp = Instant.now()
    val amzDate = amzDateFormatter.format(timestamp)
    val dateStamp = dateStampFormatter.format(timestamp)
    val payloadHash = sha256Hex(payload)

    val host = endpoint.hostWithOptionalPort()
    val canonicalHeaders =
        "host:$host\n" +
            "x-amz-content-sha256:$payloadHash\n" +
            "x-amz-date:$amzDate\n"
    val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

    val canonicalRequest = listOf(
        method,
        canonicalUri,
        canonicalQuery,
        canonicalHeaders,
        signedHeaders,
        payloadHash
    ).joinToString("\n")

    val credentialScope = "${dateStamp}/${config.region}/s3/aws4_request"
    val stringToSign = listOf(
        "AWS4-HMAC-SHA256",
        amzDate,
        credentialScope,
        sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
    ).joinToString("\n")

    val signingKey = deriveSigV4SigningKey(
        secretAccessKey = config.secretAccessKey,
        dateStamp = dateStamp,
        region = config.region
    )
    val signature = hmacSha256Hex(signingKey, stringToSign.toByteArray(StandardCharsets.UTF_8))

    val authorization =
        "AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

    return SignedRequestHeaders(
        headers = mapOf(
            "Authorization" to authorization,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "host" to host
        )
    )
}

private suspend fun executeHttpRequest(
    method: String,
    requestUrl: String,
    headers: Map<String, String>,
    body: ByteArray? = null
): HttpResponse = withContext(Dispatchers.IO) {
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = method
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.doInput = true

        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body)
            }
        }

        val statusCode = connection.responseCode
        val statusMessage = connection.responseMessage.orEmpty()
        val responseBody =
            if (statusCode in 200..299) {
                readStream(connection.inputStream)
            } else {
                readStream(connection.errorStream)
            }

        HttpResponse(
            statusCode = statusCode,
            statusMessage = statusMessage,
            body = responseBody
        )
    } finally {
        connection.disconnect()
    }
}

private fun readStream(stream: InputStream?): String {
    if (stream == null) {
        return ""
    }

    return stream.bufferedReader().use { reader ->
        reader.readText()
    }
}

private fun parseS3ListResponse(xml: String): List<S3ListEntry> {
    val contentsRegex = Regex("<Contents>[\\s\\S]*?</Contents>")
    val keyRegex = Regex("<Key>([\\s\\S]*?)</Key>")
    val lastModifiedRegex = Regex("<LastModified>([\\s\\S]*?)</LastModified>")

    return contentsRegex
        .findAll(xml)
        .mapNotNull { matchResult ->
            val block = matchResult.value
            val key = keyRegex.find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::decodeXmlEntities)
                ?.takeIf { value -> value.isNotBlank() }
                ?: return@mapNotNull null

            val lastModified = lastModifiedRegex.find(block)
                ?.groupValues
                ?.getOrNull(1)

            S3ListEntry(
                key = key,
                lastModified = lastModified
            )
        }
        .toList()
}

private fun resolveS3ObjectUrl(config: BackupS3SyncConfig): ResolvedS3Url {
    val endpoint = URL(config.endpoint)
    val normalizedKey = normalizeKeyPrefix(config.objectKey)
    require(normalizedKey.isNotBlank()) {
        "Object key is required."
    }

    val encodedKeyPath = encodeObjectPath(normalizedKey)
    val bucket = config.bucket.trim()
    require(bucket.isNotBlank()) {
        "Bucket is required."
    }

    return if (config.forcePathStyle) {
        val path = "/${encodePathSegment(bucket)}/$encodedKeyPath"
        ResolvedS3Url(
            endpoint = endpoint,
            canonicalUri = path,
            requestUrl = "${endpoint.protocol}://${endpoint.hostWithOptionalPort()}$path"
        )
    } else {
        val virtualHost = "$bucket.${endpoint.hostWithOptionalPort()}"
        val path = "/$encodedKeyPath"
        ResolvedS3Url(
            endpoint = URL("${endpoint.protocol}://$virtualHost"),
            canonicalUri = path,
            requestUrl = "${endpoint.protocol}://$virtualHost$path"
        )
    }
}

private fun resolveS3BucketRoot(config: BackupS3SyncConfig): ResolvedS3Url {
    val endpoint = URL(config.endpoint)
    val bucket = config.bucket.trim()
    require(bucket.isNotBlank()) {
        "Bucket is required."
    }

    return if (config.forcePathStyle) {
        val path = "/${encodePathSegment(bucket)}"
        ResolvedS3Url(
            endpoint = endpoint,
            canonicalUri = path,
            requestUrl = "${endpoint.protocol}://${endpoint.hostWithOptionalPort()}$path"
        )
    } else {
        val virtualHost = "$bucket.${endpoint.hostWithOptionalPort()}"
        ResolvedS3Url(
            endpoint = URL("${endpoint.protocol}://$virtualHost"),
            canonicalUri = "/",
            requestUrl = "${endpoint.protocol}://$virtualHost"
        )
    }
}

private fun URL.hostWithOptionalPort(): String {
    val portValue = port
    return if (portValue == -1) host else "$host:$portValue"
}

private fun normalizeKeyPrefix(key: String): String = key
    .trim()
    .replace(Regex("^/+"), "")

private fun encodeObjectPath(path: String): String = path
    .split("/")
    .filter { segment -> segment.isNotBlank() }
    .joinToString("/") { segment -> encodePathSegment(segment) }

private fun encodePathSegment(segment: String): String = percentEncode(segment)

private fun encodeQueryValue(value: String): String = percentEncode(value)

private fun canonicalizeQuery(queryParams: Map<String, String>): String = queryParams
    .filterValues { value -> value.isNotEmpty() }
    .toList()
    .sortedBy { (key, _) -> key }
    .joinToString("&") { (key, value) ->
        "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
    }

private fun percentEncode(value: String): String {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val output = StringBuilder(bytes.size)

    for (byteValue in bytes) {
        val intValue = byteValue.toInt() and 0xFF
        val charValue = intValue.toChar()
        val isUnreserved =
            (charValue in 'A'..'Z') ||
                (charValue in 'a'..'z') ||
                (charValue in '0'..'9') ||
                charValue == '-' ||
                charValue == '_' ||
                charValue == '.' ||
                charValue == '~'

        if (isUnreserved) {
            output.append(charValue)
        } else {
            output.append('%')
            output.append(intValue.toString(16).uppercase().padStart(2, '0'))
        }
    }

    return output.toString()
}

private fun getHistoryPrefixFromObjectKey(objectKey: String): String {
    val normalized = normalizeKeyPrefix(objectKey)
    val slashIndex = normalized.lastIndexOf('/')
    val directory = if (slashIndex >= 0) normalized.substring(0, slashIndex) else ""
    return if (directory.isNotBlank()) {
        "$directory/history/"
    } else {
        "history/"
    }
}

private fun decodeXmlEntities(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

private fun deriveEncryptionKey(masterKey: String, salt: ByteArray): ByteArray {
    val normalizedMasterKey = masterKey.trim()
    require(normalizedMasterKey.length >= 12) {
        "Master key must be at least 12 characters."
    }

    val keySpec = PBEKeySpec(
        normalizedMasterKey.toCharArray(),
        salt,
        kdfIterations,
        derivedKeyBytes * 8
    )

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(keySpec).encoded
}

private fun randomBytes(length: Int): ByteArray = ByteArray(length).also { output ->
    SecureRandom().nextBytes(output)
}

private fun base64Encode(value: ByteArray): String =
    Base64.getEncoder().encodeToString(value)

private fun base64Decode(value: String): ByteArray =
    Base64.getDecoder().decode(value)

private fun sha256Hex(input: ByteArray): String = MessageDigest
    .getInstance("SHA-256")
    .digest(input)
    .toHex()

private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(message)
}

private fun hmacSha256Hex(key: ByteArray, message: ByteArray): String =
    hmacSha256(key, message).toHex()

private fun deriveSigV4SigningKey(
    secretAccessKey: String,
    dateStamp: String,
    region: String
): ByteArray {
    val kDate = hmacSha256(
        "AWS4$secretAccessKey".toByteArray(StandardCharsets.UTF_8),
        dateStamp.toByteArray(StandardCharsets.UTF_8)
    )
    val kRegion = hmacSha256(kDate, region.toByteArray(StandardCharsets.UTF_8))
    val kService = hmacSha256(kRegion, "s3".toByteArray(StandardCharsets.UTF_8))
    return hmacSha256(kService, "aws4_request".toByteArray(StandardCharsets.UTF_8))
}

private fun parseIsoMillis(value: String?): Long? {
    if (value.isNullOrBlank()) {
        return null
    }

    return runCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

private fun parseInstantOrNull(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()

private fun ByteArray.toHex(): String = joinToString(separator = "") { byteValue ->
    (byteValue.toInt() and 0xFF).toString(16).padStart(2, '0')
}
