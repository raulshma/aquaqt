package com.keepaside.aquapt.core.backup

internal const val backupAutoSyncDefaultHour = 3
internal const val backupAutoSyncDefaultRegion = "us-east-1"
internal const val backupAutoSyncDefaultObjectKey = "aquapt/backups/latest.enc.json"

internal const val backupAutoSyncWorkerInputHourKey = "backup_auto_sync_hour"
internal const val backupAutoSyncWorkerTag = "aquapt-backup-auto-sync"
internal const val backupAutoSyncWorkerUniqueName = "aquapt-backup-auto-sync-daily"
