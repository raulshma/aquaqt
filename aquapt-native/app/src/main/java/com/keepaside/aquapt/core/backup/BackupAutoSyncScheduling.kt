package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppSettings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal data class BackupAutoSyncDestination(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val objectKey: String
)

internal fun resolveBackupAutoSyncHour(settings: AppSettings): Int? {
    if (!settings.backupSyncEnabled) {
        return null
    }

    return (settings.backupSyncHour ?: backupAutoSyncDefaultHour)
        .coerceIn(0, 23)
}

internal fun resolveBackupAutoSyncDestination(settings: AppSettings): BackupAutoSyncDestination {
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

    val objectKey = settings.backupS3ObjectKey
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: backupAutoSyncDefaultObjectKey

    return BackupAutoSyncDestination(
        endpoint = endpoint,
        region = region,
        bucket = bucket,
        objectKey = objectKey
    )
}

internal fun shouldRunBackupAutoSyncToday(
    lastAutoSyncDate: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean {
    val today = backupAutoSyncDateStamp(now, zoneId)
    return lastAutoSyncDate != today
}

internal fun backupAutoSyncDateStamp(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String = now.atZone(zoneId).toLocalDate().toString()

internal fun canRunBackupAutoSyncNow(
    targetHour: Int,
    now: ZonedDateTime = ZonedDateTime.now()
): Boolean = now.hour >= targetHour.coerceIn(0, 23)

internal fun calculateNextBackupAutoSyncInitialDelayMillis(
    hour: Int,
    now: ZonedDateTime = ZonedDateTime.now()
): Long {
    val normalizedHour = hour.coerceIn(0, 23)
    val nextTrigger = now
        .withHour(normalizedHour)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .let { candidate ->
            if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
        }

    return Duration.between(now, nextTrigger)
        .toMillis()
        .coerceAtLeast(0L)
}

internal fun resolveBackupAutoSyncPrerequisiteError(settings: AppSettings): String? {
    if (!settings.backupMasterKeySet || !settings.backupS3CredentialsSet) {
        return "Auto backup sync skipped: backup key or S3 credentials are not configured."
    }

    val destination = resolveBackupAutoSyncDestination(settings)
    if (
        destination.endpoint.isBlank() ||
        destination.region.isBlank() ||
        destination.bucket.isBlank() ||
        destination.objectKey.isBlank()
    ) {
        return "Auto backup sync skipped: backup endpoint, region, bucket, or object key is missing."
    }

    return null
}
