package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BackupAutoSyncSchedulingTest {

    @Test
    fun `backup sync hour resolves from enabled settings and defaults`() {
        assertNull(
            resolveBackupAutoSyncHour(
                AppSettings(
                    backupSyncEnabled = false,
                    backupSyncHour = 9
                )
            )
        )

        assertEquals(
            backupAutoSyncDefaultHour,
            resolveBackupAutoSyncHour(
                AppSettings(
                    backupSyncEnabled = true,
                    backupSyncHour = null
                )
            )
        )

        assertEquals(
            23,
            resolveBackupAutoSyncHour(
                AppSettings(
                    backupSyncEnabled = true,
                    backupSyncHour = 99
                )
            )
        )
    }

    @Test
    fun `daily gate allows one run per local date`() {
        val now = Instant.parse("2026-04-11T12:00:00Z")

        assertTrue(shouldRunBackupAutoSyncToday(null, now, ZoneOffset.UTC))
        assertTrue(!shouldRunBackupAutoSyncToday("2026-04-11", now, ZoneOffset.UTC))
        assertTrue(shouldRunBackupAutoSyncToday("2026-04-10", now, ZoneOffset.UTC))
    }

    @Test
    fun `initial delay targets next configured backup hour`() {
        val baseTime = ZonedDateTime.parse("2026-04-11T02:30:00Z")

        val halfHourDelay = calculateNextBackupAutoSyncInitialDelayMillis(3, baseTime)
        val nextDayDelay = calculateNextBackupAutoSyncInitialDelayMillis(2, baseTime)

        assertEquals(30L * 60L * 1000L, halfHourDelay)
        assertEquals(23L * 60L * 60L * 1000L + 30L * 60L * 1000L, nextDayDelay)
    }

    @Test
    fun `prerequisites require credentials and destination`() {
        val missingCredentials = AppSettings(
            backupSyncEnabled = true,
            backupMasterKeySet = false,
            backupS3CredentialsSet = false,
            backupS3Endpoint = "https://s3.example.com",
            backupS3Bucket = "aquapt-backups"
        )

        assertEquals(
            "Auto backup sync skipped: backup key or S3 credentials are not configured.",
            resolveBackupAutoSyncPrerequisiteError(missingCredentials)
        )

        val missingDestination = AppSettings(
            backupSyncEnabled = true,
            backupMasterKeySet = true,
            backupS3CredentialsSet = true,
            backupS3Endpoint = " ",
            backupS3Bucket = ""
        )

        assertEquals(
            "Auto backup sync skipped: backup endpoint, region, bucket, or object key is missing.",
            resolveBackupAutoSyncPrerequisiteError(missingDestination)
        )

        val ready = AppSettings(
            backupSyncEnabled = true,
            backupMasterKeySet = true,
            backupS3CredentialsSet = true,
            backupS3Endpoint = "https://s3.example.com",
            backupS3Bucket = "aquapt-backups"
        )

        assertNull(resolveBackupAutoSyncPrerequisiteError(ready))
    }
}
