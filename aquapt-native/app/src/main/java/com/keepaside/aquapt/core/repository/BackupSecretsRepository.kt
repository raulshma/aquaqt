package com.keepaside.aquapt.core.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val backupSecretsPrefsFile = "aquapt_backup_secrets"
private const val backupMasterKeyPrefKey = "backup_master_key"
private const val backupS3AccessKeyIdPrefKey = "backup_s3_access_key_id"
private const val backupS3SecretAccessKeyPrefKey = "backup_s3_secret_access_key"

data class BackupS3Credentials(
    val accessKeyId: String,
    val secretAccessKey: String
)

interface BackupSecretsStore {
    suspend fun saveBackupMasterKey(masterKey: String)
    suspend fun loadBackupMasterKey(): String
    suspend fun hasBackupMasterKey(): Boolean
    suspend fun clearBackupMasterKey()

    suspend fun saveBackupS3Credentials(accessKeyId: String, secretAccessKey: String)
    suspend fun loadBackupS3Credentials(): BackupS3Credentials?
    suspend fun hasBackupS3Credentials(): Boolean
    suspend fun clearBackupS3Credentials()
}

class BackupSecretsRepository(
    context: Context
) : BackupSecretsStore {

    private val preferences: SharedPreferences = createEncryptedPreferences(context)

    override suspend fun saveBackupMasterKey(masterKey: String) {
        val normalized = masterKey.trim()

        if (normalized.isEmpty()) {
            clearBackupMasterKey()
            return
        }

        preferences.edit()
            .putString(backupMasterKeyPrefKey, normalized)
            .apply()
    }

    override suspend fun loadBackupMasterKey(): String =
        preferences
            .getString(backupMasterKeyPrefKey, null)
            .orEmpty()
            .trim()

    override suspend fun hasBackupMasterKey(): Boolean = loadBackupMasterKey().isNotEmpty()

    override suspend fun clearBackupMasterKey() {
        preferences.edit()
            .remove(backupMasterKeyPrefKey)
            .apply()
    }

    override suspend fun saveBackupS3Credentials(accessKeyId: String, secretAccessKey: String) {
        val normalizedAccessKeyId = accessKeyId.trim()
        val normalizedSecretAccessKey = secretAccessKey.trim()

        if (normalizedAccessKeyId.isEmpty() && normalizedSecretAccessKey.isEmpty()) {
            clearBackupS3Credentials()
            return
        }

        require(normalizedAccessKeyId.isNotEmpty() && normalizedSecretAccessKey.isNotEmpty()) {
            "Both S3 access key ID and secret access key are required."
        }

        preferences.edit()
            .putString(backupS3AccessKeyIdPrefKey, normalizedAccessKeyId)
            .putString(backupS3SecretAccessKeyPrefKey, normalizedSecretAccessKey)
            .apply()
    }

    override suspend fun loadBackupS3Credentials(): BackupS3Credentials? {
        val accessKeyId = preferences
            .getString(backupS3AccessKeyIdPrefKey, null)
            .orEmpty()
            .trim()
        val secretAccessKey = preferences
            .getString(backupS3SecretAccessKeyPrefKey, null)
            .orEmpty()
            .trim()

        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            return null
        }

        return BackupS3Credentials(
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey
        )
    }

    override suspend fun hasBackupS3Credentials(): Boolean = loadBackupS3Credentials() != null

    override suspend fun clearBackupS3Credentials() {
        preferences.edit()
            .remove(backupS3AccessKeyIdPrefKey)
            .remove(backupS3SecretAccessKeyPrefKey)
            .apply()
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            backupSecretsPrefsFile,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
