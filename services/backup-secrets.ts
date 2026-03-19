import * as SecureStore from "expo-secure-store";

const MASTER_KEY_STORAGE_KEY = "backup-master-key-v1";
const S3_CREDENTIALS_STORAGE_KEY = "backup-s3-credentials-v1";

type StoredS3Credentials = {
  accessKeyId: string;
  secretAccessKey: string;
};

export async function saveBackupMasterKey(masterKey: string) {
  const normalized = masterKey.trim();

  if (!normalized) {
    await SecureStore.deleteItemAsync(MASTER_KEY_STORAGE_KEY);
    return;
  }

  await SecureStore.setItemAsync(MASTER_KEY_STORAGE_KEY, normalized, {
    keychainService: MASTER_KEY_STORAGE_KEY,
  });
}

export async function loadBackupMasterKey() {
  const value = await SecureStore.getItemAsync(MASTER_KEY_STORAGE_KEY, {
    keychainService: MASTER_KEY_STORAGE_KEY,
  });

  return value?.trim() ?? "";
}

export async function hasBackupMasterKey() {
  const key = await loadBackupMasterKey();
  return key.length > 0;
}

export async function saveBackupS3Credentials(input: StoredS3Credentials) {
  const payload = {
    accessKeyId: input.accessKeyId.trim(),
    secretAccessKey: input.secretAccessKey.trim(),
  };

  if (!payload.accessKeyId && !payload.secretAccessKey) {
    await SecureStore.deleteItemAsync(S3_CREDENTIALS_STORAGE_KEY);
    return;
  }

  await SecureStore.setItemAsync(
    S3_CREDENTIALS_STORAGE_KEY,
    JSON.stringify(payload),
    {
      keychainService: S3_CREDENTIALS_STORAGE_KEY,
    },
  );
}

export async function loadBackupS3Credentials(): Promise<StoredS3Credentials | null> {
  const encoded = await SecureStore.getItemAsync(S3_CREDENTIALS_STORAGE_KEY, {
    keychainService: S3_CREDENTIALS_STORAGE_KEY,
  });

  if (!encoded) {
    return null;
  }

  try {
    const parsed = JSON.parse(encoded) as Partial<StoredS3Credentials>;
    const accessKeyId = parsed.accessKeyId?.trim() ?? "";
    const secretAccessKey = parsed.secretAccessKey?.trim() ?? "";

    if (!accessKeyId || !secretAccessKey) {
      return null;
    }

    return {
      accessKeyId,
      secretAccessKey,
    };
  } catch {
    return null;
  }
}

export async function hasBackupS3Credentials() {
  const credentials = await loadBackupS3Credentials();
  return !!credentials;
}
