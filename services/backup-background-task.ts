import * as BackgroundFetch from "expo-background-fetch";
import * as TaskManager from "expo-task-manager";

import {
    loadBackupMasterKey,
    loadBackupS3Credentials,
} from "@/services/backup-secrets";
import {
    buildVersionedBackupObjectKey,
    cleanupVersionedBackups,
    createBackupEnvelope,
    encryptBackupEnvelope,
    getBackupDateStamp,
    uploadEncryptedBackupToS3,
} from "@/services/backup-sync";
import {
    initPersistence,
    loadPersistedState,
    savePersistedState,
} from "@/services/persistence";
import { AppSettings } from "@/types/aquapt";

export const BACKUP_SYNC_BACKGROUND_TASK = "aquapt-backup-sync";

let taskDefined = false;

function normalizeSettings(settings?: Partial<AppSettings>) {
  return {
    backupSyncEnabled: settings?.backupSyncEnabled ?? false,
    backupSyncHour: settings?.backupSyncHour ?? 3,
    backupS3Endpoint: settings?.backupS3Endpoint?.trim() ?? "",
    backupS3Region: settings?.backupS3Region?.trim() ?? "us-east-1",
    backupS3Bucket: settings?.backupS3Bucket?.trim() ?? "",
    backupS3ObjectKey:
      settings?.backupS3ObjectKey?.trim() || "aquapt/backups/latest.enc.json",
    backupS3ForcePathStyle: settings?.backupS3ForcePathStyle ?? true,
    backupUseVersionedKeys: settings?.backupUseVersionedKeys ?? true,
    backupRetentionDays: Math.max(1, settings?.backupRetentionDays ?? 30),
    backupLastAutoSyncDate: settings?.backupLastAutoSyncDate,
  };
}

function defineBackupTaskIfNeeded() {
  if (taskDefined || TaskManager.isTaskDefined(BACKUP_SYNC_BACKGROUND_TASK)) {
    taskDefined = true;
    return;
  }

  TaskManager.defineTask(BACKUP_SYNC_BACKGROUND_TASK, async () => {
    try {
      await initPersistence();
      const persisted = await loadPersistedState();

      if (!persisted) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      const settings = normalizeSettings(persisted.settings);

      if (!settings.backupSyncEnabled) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      const now = new Date();
      const today = getBackupDateStamp(now.toISOString());
      const configuredHour = Math.min(23, Math.max(0, settings.backupSyncHour));

      if (now.getHours() < configuredHour) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      if (settings.backupLastAutoSyncDate === today) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      const [masterKey, s3Credentials] = await Promise.all([
        loadBackupMasterKey(),
        loadBackupS3Credentials(),
      ]);

      if (!masterKey || !s3Credentials) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      const endpoint = settings.backupS3Endpoint;
      const region = settings.backupS3Region;
      const bucket = settings.backupS3Bucket;
      const objectKey = settings.backupS3ObjectKey;

      if (!endpoint || !region || !bucket || !objectKey) {
        return BackgroundFetch.BackgroundFetchResult.NoData;
      }

      const cloudConfig = {
        endpoint,
        region,
        bucket,
        objectKey,
        forcePathStyle: settings.backupS3ForcePathStyle,
        accessKeyId: s3Credentials.accessKeyId,
        secretAccessKey: s3Credentials.secretAccessKey,
      };

      const envelope = createBackupEnvelope(persisted);
      const encryptedPayload = encryptBackupEnvelope(envelope, masterKey);
      const upload = await uploadEncryptedBackupToS3(
        cloudConfig,
        encryptedPayload,
      );

      if (settings.backupUseVersionedKeys) {
        const versionedKey = buildVersionedBackupObjectKey(
          objectKey,
          envelope.exportedAt,
        );

        if (versionedKey !== objectKey) {
          await uploadEncryptedBackupToS3(
            {
              ...cloudConfig,
              objectKey: versionedKey,
            },
            encryptedPayload,
          );
        }

        await cleanupVersionedBackups(
          cloudConfig,
          objectKey,
          settings.backupRetentionDays,
        );
      }

      await savePersistedState({
        ...persisted,
        settings: {
          ...persisted.settings,
          backupLastSyncedAt: upload.uploadedAt,
          backupLastAutoSyncDate: today,
          backupLastError: undefined,
          backupMasterKeySet: true,
          backupS3CredentialsSet: true,
        },
      });

      return BackgroundFetch.BackgroundFetchResult.NewData;
    } catch (error) {
      try {
        const persisted = await loadPersistedState();
        if (persisted) {
          await savePersistedState({
            ...persisted,
            settings: {
              ...persisted.settings,
              backupLastError:
                error instanceof Error
                  ? error.message
                  : "Background backup task failed.",
            },
          });
        }
      } catch {
        // Intentionally ignore fallback failure.
      }

      return BackgroundFetch.BackgroundFetchResult.Failed;
    }
  });

  taskDefined = true;
}

export async function registerBackupBackgroundTask(intervalMinutes = 60) {
  defineBackupTaskIfNeeded();

  const status = await BackgroundFetch.getStatusAsync();
  if (status === BackgroundFetch.BackgroundFetchStatus.Restricted) {
    return {
      ok: false,
      message: "Background fetch is restricted on this device.",
    };
  }

  if (status === BackgroundFetch.BackgroundFetchStatus.Denied) {
    return {
      ok: false,
      message: "Background fetch is denied on this device.",
    };
  }

  const isRegistered = await TaskManager.isTaskRegisteredAsync(
    BACKUP_SYNC_BACKGROUND_TASK,
  );

  if (!isRegistered) {
    await BackgroundFetch.registerTaskAsync(BACKUP_SYNC_BACKGROUND_TASK, {
      minimumInterval: Math.max(15 * 60, intervalMinutes * 60),
      stopOnTerminate: false,
      startOnBoot: true,
    });
  }

  return {
    ok: true,
    message: "Background backup task registered.",
  };
}

export async function unregisterBackupBackgroundTask() {
  const isRegistered = await TaskManager.isTaskRegisteredAsync(
    BACKUP_SYNC_BACKGROUND_TASK,
  );

  if (isRegistered) {
    await BackgroundFetch.unregisterTaskAsync(BACKUP_SYNC_BACKGROUND_TASK);
  }

  return {
    ok: true,
    message: "Background backup task unregistered.",
  };
}
