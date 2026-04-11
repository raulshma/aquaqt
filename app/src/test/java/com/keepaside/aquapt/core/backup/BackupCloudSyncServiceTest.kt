package com.keepaside.aquapt.core.backup

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCloudSyncServiceTest {

    private val sampleStateJson =
        """{"aquariums":[],"taskTemplates":[],"livestock":[],"taskExecutions":[],"dosingLogs":[],"assets":[],"consumables":[],"parameterLogs":[],"issues":[],"memos":[],"timeline":[],"reminderGroups":[],"settings":{"aiModel":"openai/gpt-4o-mini"}}"""

    @Test
    fun `encrypts and decrypts backup envelope`() {
        val envelope = createBackupEnvelope(
            appStateJson = sampleStateJson,
            exportedAt = "2026-03-19T17:30:00.000Z"
        )

        val encrypted = encryptBackupEnvelope(
            envelope = envelope,
            masterKey = "this-is-a-strong-master-key"
        )

        assertTrue(encrypted.isNotBlank())

        val decrypted = decryptBackupEnvelope(
            encryptedPayloadJson = encrypted,
            masterKey = "this-is-a-strong-master-key"
        )

        assertEquals(1, decrypted.schemaVersion)
        assertEquals(
            "openai/gpt-4o-mini",
            decrypted.appState
                .jsonObject["settings"]
                ?.jsonObject
                ?.get("aiModel")
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun `fails decrypt with wrong key`() {
        val envelope = createBackupEnvelope(sampleStateJson)
        val encrypted = encryptBackupEnvelope(
            envelope = envelope,
            masterKey = "correct-master-key-123"
        )

        val didThrow = runCatching {
            decryptBackupEnvelope(
                encryptedPayloadJson = encrypted,
                masterKey = "wrong-master-key-456"
            )
        }.isFailure

        assertTrue(didThrow)
    }

    @Test
    fun `compares iso timestamps deterministically`() {
        assertEquals(
            1,
            compareBackupIsoTimestamps(
                "2026-03-19T00:00:00.000Z",
                "2026-03-18T23:00:00.000Z"
            )
        )
        assertEquals(
            0,
            compareBackupIsoTimestamps(
                "2026-03-19T00:00:00.000Z",
                "2026-03-19T00:00:00.000Z"
            )
        )
        assertEquals(
            -1,
            compareBackupIsoTimestamps(
                null,
                "2026-03-19T00:00:00.000Z"
            )
        )
    }

    @Test
    fun `builds YYYY-MM-DD backup date stamp`() {
        assertEquals(
            "2026-03-19",
            backupSyncDateStamp("2026-03-19T17:30:00.000Z")
        )
    }

    @Test
    fun `builds versioned history key from latest key`() {
        assertEquals(
            "aquapt/backups/history/2026-03-19.enc.json",
            buildVersionedBackupObjectKey(
                latestObjectKey = "aquapt/backups/latest.enc.json",
                isoTimestamp = "2026-03-19T17:30:00.000Z"
            )
        )
    }
}
