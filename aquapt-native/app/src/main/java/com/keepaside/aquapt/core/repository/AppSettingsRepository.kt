package com.keepaside.aquapt.core.repository

import android.content.Context
import android.content.SharedPreferences
import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface AppSettingsStore {
    val settings: StateFlow<AppSettings>

    suspend fun setSettings(settings: AppSettings)
}

class AppSettingsRepository(
    context: Context
) : AppSettingsStore {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        preferencesFile,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun setSettings(settings: AppSettings) {
        preferences.edit().apply {
            putString(keyOpenRouterApiKey, settings.openRouterApiKey)
            putString(keyAiModel, settings.aiModel)
            putString(keyAssistantMemoryModel, settings.assistantMemoryModel)

            putBoolean(keyNotificationsEnabled, settings.notificationsEnabled)
            putString(keyReminderHours, settings.reminderHours.joinToString(","))
            putBoolean(keyAssistantMemoryEnabled, settings.assistantMemoryEnabled)

            putBoolean(keyBackupSyncEnabled, settings.backupSyncEnabled)
            putIntOrRemove(keyBackupSyncHour, settings.backupSyncHour)

            putString(keyBackupS3Endpoint, settings.backupS3Endpoint)
            putString(keyBackupS3Region, settings.backupS3Region)
            putString(keyBackupS3Bucket, settings.backupS3Bucket)
            putString(keyBackupS3ObjectKey, settings.backupS3ObjectKey)
            putBoolean(keyBackupS3ForcePathStyle, settings.backupS3ForcePathStyle)
            putBoolean(keyBackupUseVersionedKeys, settings.backupUseVersionedKeys)
            putIntOrRemove(keyBackupRetentionDays, settings.backupRetentionDays)
            putBoolean(keyBackupMasterKeySet, settings.backupMasterKeySet)
            putBoolean(keyBackupS3CredentialsSet, settings.backupS3CredentialsSet)
            putString(keyBackupLastSyncedAt, settings.backupLastSyncedAt)
            putString(keyBackupLastRestoredAt, settings.backupLastRestoredAt)
            putString(keyBackupLastAutoSyncDate, settings.backupLastAutoSyncDate)
            putString(keyBackupLastError, settings.backupLastError)

            putString(keyThemePreference, settings.themePreference.name)
            putString(keyRegionalPreferencesMode, settings.regionalPreferencesMode.name)
            putString(keyDefaultLocale, settings.defaultLocale)
            putString(keyDefaultTimezone, settings.defaultTimezone)
            putString(keyDefaultCountryCode, settings.defaultCountryCode)
            putString(keyDefaultCountryName, settings.defaultCountryName)
            putString(keyDefaultCurrency, settings.defaultCurrency)
        }.apply()

        _settings.update { readSettings() }
    }

    private fun readSettings(): AppSettings {
        val reminderHours = preferences.getString(keyReminderHours, null)
            .toReminderHoursList()

        return AppSettings(
            openRouterApiKey = preferences.getString(keyOpenRouterApiKey, "").orEmpty(),
            aiModel = preferences.getString(keyAiModel, "").orEmpty(),
            assistantMemoryModel = preferences.getString(keyAssistantMemoryModel, null),
            notificationsEnabled = preferences.getBoolean(keyNotificationsEnabled, false),
            reminderHours = reminderHours,
            assistantMemoryEnabled = preferences.getBoolean(keyAssistantMemoryEnabled, false),
            backupSyncEnabled = preferences.getBoolean(keyBackupSyncEnabled, false),
            backupSyncHour = preferences.getOptionalInt(keyBackupSyncHour),
            backupS3Endpoint = preferences.getString(keyBackupS3Endpoint, null),
            backupS3Region = preferences.getString(keyBackupS3Region, null),
            backupS3Bucket = preferences.getString(keyBackupS3Bucket, null),
            backupS3ObjectKey = preferences.getString(keyBackupS3ObjectKey, null),
            backupS3ForcePathStyle = preferences.getBoolean(keyBackupS3ForcePathStyle, false),
            backupUseVersionedKeys = preferences.getBoolean(keyBackupUseVersionedKeys, false),
            backupRetentionDays = preferences.getOptionalInt(keyBackupRetentionDays),
            backupMasterKeySet = preferences.getBoolean(keyBackupMasterKeySet, false),
            backupS3CredentialsSet = preferences.getBoolean(keyBackupS3CredentialsSet, false),
            backupLastSyncedAt = preferences.getString(keyBackupLastSyncedAt, null),
            backupLastRestoredAt = preferences.getString(keyBackupLastRestoredAt, null),
            backupLastAutoSyncDate = preferences.getString(keyBackupLastAutoSyncDate, null),
            backupLastError = preferences.getString(keyBackupLastError, null),
            themePreference = preferences.getString(keyThemePreference, null)
                .toThemePreference(),
            regionalPreferencesMode = preferences.getString(keyRegionalPreferencesMode, null)
                .toRegionalPreferencesMode(),
            defaultLocale = preferences.getString(keyDefaultLocale, null),
            defaultTimezone = preferences.getString(keyDefaultTimezone, null),
            defaultCountryCode = preferences.getString(keyDefaultCountryCode, null),
            defaultCountryName = preferences.getString(keyDefaultCountryName, null),
            defaultCurrency = preferences.getString(keyDefaultCurrency, null)
        )
    }

    private fun SharedPreferences.getOptionalInt(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private fun SharedPreferences.Editor.putIntOrRemove(key: String, value: Int?) {
        if (value == null) {
            remove(key)
        } else {
            putInt(key, value)
        }
    }

    private fun String?.toReminderHoursList(): List<Int> =
        this
            ?.split(",")
            ?.map { entry -> entry.trim() }
            ?.filter { entry -> entry.isNotEmpty() }
            ?.mapNotNull { entry -> entry.toIntOrNull()?.takeIf { it in 0..23 } }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    private fun String?.toThemePreference(): AppThemePreference =
        runCatching { this?.let(AppThemePreference::valueOf) }.getOrNull()
            ?: AppThemePreference.SYSTEM

    private fun String?.toRegionalPreferencesMode(): RegionalPreferencesMode =
        runCatching { this?.let(RegionalPreferencesMode::valueOf) }.getOrNull()
            ?: RegionalPreferencesMode.AUTO

    companion object {
        private const val preferencesFile = "aquapt_app_settings"

        private const val keyOpenRouterApiKey = "open_router_api_key"
        private const val keyAiModel = "ai_model"
        private const val keyAssistantMemoryModel = "assistant_memory_model"
        private const val keyNotificationsEnabled = "notifications_enabled"
        private const val keyReminderHours = "reminder_hours"
        private const val keyAssistantMemoryEnabled = "assistant_memory_enabled"

        private const val keyBackupSyncEnabled = "backup_sync_enabled"
        private const val keyBackupSyncHour = "backup_sync_hour"
        private const val keyBackupS3Endpoint = "backup_s3_endpoint"
        private const val keyBackupS3Region = "backup_s3_region"
        private const val keyBackupS3Bucket = "backup_s3_bucket"
        private const val keyBackupS3ObjectKey = "backup_s3_object_key"
        private const val keyBackupS3ForcePathStyle = "backup_s3_force_path_style"
        private const val keyBackupUseVersionedKeys = "backup_use_versioned_keys"
        private const val keyBackupRetentionDays = "backup_retention_days"
        private const val keyBackupMasterKeySet = "backup_master_key_set"
        private const val keyBackupS3CredentialsSet = "backup_s3_credentials_set"
        private const val keyBackupLastSyncedAt = "backup_last_synced_at"
        private const val keyBackupLastRestoredAt = "backup_last_restored_at"
        private const val keyBackupLastAutoSyncDate = "backup_last_auto_sync_date"
        private const val keyBackupLastError = "backup_last_error"

        private const val keyThemePreference = "theme_preference"
        private const val keyRegionalPreferencesMode = "regional_preferences_mode"
        private const val keyDefaultLocale = "default_locale"
        private const val keyDefaultTimezone = "default_timezone"
        private const val keyDefaultCountryCode = "default_country_code"
        private const val keyDefaultCountryName = "default_country_name"
        private const val keyDefaultCurrency = "default_currency"
    }
}