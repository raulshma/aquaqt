import { useState } from "react";
import { View } from "react-native";
import {
    Button,
    Chip,
    Text,
    TextInput,
    useTheme
} from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";

export default function BackupSettingsScreen() {
  const theme = useTheme();
  const {
    settings,
    exportAppState,
    importAppStateFromJson,
    saveBackupSyncSettings,
    saveBackupMasterKey,
    saveBackupS3Credentials,
    runManualBackupSync,
    restoreLatestCloudBackup,
  } = useAquapt();

  const [backupSyncEnabled, setBackupSyncEnabled] = useState(
    settings.backupSyncEnabled ?? false,
  );
  const [backupSyncHour, setBackupSyncHour] = useState(
    settings.backupSyncHour ?? 3,
  );
  const [backupEndpoint, setBackupEndpoint] = useState(
    settings.backupS3Endpoint ?? "",
  );
  const [backupRegion, setBackupRegion] = useState(
    settings.backupS3Region ?? "us-east-1",
  );
  const [backupBucket, setBackupBucket] = useState(
    settings.backupS3Bucket ?? "",
  );
  const [backupObjectKey, setBackupObjectKey] = useState(
    settings.backupS3ObjectKey ?? "aquapt/backups/latest.enc.json",
  );
  const [backupForcePathStyle, setBackupForcePathStyle] = useState(
    settings.backupS3ForcePathStyle ?? true,
  );
  const [backupUseVersionedKeys, setBackupUseVersionedKeys] = useState(
    settings.backupUseVersionedKeys ?? true,
  );
  const [backupRetentionDays, setBackupRetentionDays] = useState(
    String(settings.backupRetentionDays ?? 30),
  );
  const [backupAccessKeyId, setBackupAccessKeyId] = useState("");
  const [backupSecretAccessKey, setBackupSecretAccessKey] = useState("");
  const [backupMasterKeyInput, setBackupMasterKeyInput] = useState("");
  const [backupPayload, setBackupPayload] = useState("");
  const [backupStatus, setBackupStatus] = useState<string | null>(null);
  const [backupSyncStatus, setBackupSyncStatus] = useState<string | null>(null);
  const [isBackupSyncing, setIsBackupSyncing] = useState(false);

  const saveBackupConfiguration = () => {
    const result = saveBackupSyncSettings({
      backupSyncEnabled,
      backupSyncHour,
      backupS3Endpoint: backupEndpoint,
      backupS3Region: backupRegion,
      backupS3Bucket: backupBucket,
      backupS3ObjectKey: backupObjectKey,
      backupS3ForcePathStyle: backupForcePathStyle,
      backupUseVersionedKeys,
      backupRetentionDays: Number.parseInt(backupRetentionDays.trim(), 10),
    });

    setBackupSyncStatus(result.message);
  };

  return (
    <DashboardScrollView>
      <DashboardHero
        title="Backup & restore"
        subtitle="Encrypted S3 sync plus a manual JSON export/import path."
        tone="tertiary"
        chips={
          <>
            <Chip compact icon="shield-lock">
              {settings.backupMasterKeySet
                ? "Master key set"
                : "Master key missing"}
            </Chip>
            <Chip compact icon="key-chain-variant">
              {settings.backupS3CredentialsSet
                ? "S3 credentials set"
                : "S3 credentials missing"}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Encrypted S3 backup sync"
        description="Keep automatic encrypted backups on your own storage."
      >
        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 16,
          }}
        >
          <Chip
            selected={backupSyncEnabled}
            onPress={() => setBackupSyncEnabled(true)}
          >
            Auto-sync enabled
          </Chip>
          <Chip
            selected={!backupSyncEnabled}
            onPress={() => setBackupSyncEnabled(false)}
          >
            Auto-sync disabled
          </Chip>
        </View>

        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          {[0, 1, 2, 3, 4, 6, 8, 12, 18, 22].map((hour) => (
            <Chip
              key={hour}
              selected={backupSyncHour === hour}
              onPress={() => setBackupSyncHour(hour)}
            >
              {String(hour).padStart(2, "0")}:00
            </Chip>
          ))}
        </View>

        <TextInput
          mode="outlined"
          label="S3 endpoint"
          value={backupEndpoint}
          onChangeText={setBackupEndpoint}
          style={{ marginTop: 16 }}
        />
        <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
          <TextInput
            mode="outlined"
            label="Region"
            value={backupRegion}
            onChangeText={setBackupRegion}
            style={{ flex: 1 }}
          />
          <TextInput
            mode="outlined"
            label="Bucket"
            value={backupBucket}
            onChangeText={setBackupBucket}
            style={{ flex: 1 }}
          />
        </View>
        <TextInput
          mode="outlined"
          label="Object key"
          value={backupObjectKey}
          onChangeText={setBackupObjectKey}
          style={{ marginTop: 10 }}
        />
        <TextInput
          mode="outlined"
          label="Retention (days)"
          value={backupRetentionDays}
          onChangeText={setBackupRetentionDays}
          keyboardType="number-pad"
          style={{ marginTop: 10 }}
        />

        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          <Chip
            selected={backupForcePathStyle}
            onPress={() => setBackupForcePathStyle(true)}
          >
            Path-style
          </Chip>
          <Chip
            selected={!backupForcePathStyle}
            onPress={() => setBackupForcePathStyle(false)}
          >
            Virtual-host
          </Chip>
          <Chip
            selected={backupUseVersionedKeys}
            onPress={() => setBackupUseVersionedKeys(true)}
          >
            Versioned backups
          </Chip>
          <Chip
            selected={!backupUseVersionedKeys}
            onPress={() => setBackupUseVersionedKeys(false)}
          >
            Latest object only
          </Chip>
        </View>

        <Button
          mode="contained-tonal"
          onPress={saveBackupConfiguration}
          style={{ marginTop: 16, alignSelf: "flex-start" }}
        >
          Save backup config
        </Button>
        {backupSyncStatus ? (
          <Text variant="bodySmall" style={{ marginTop: 8 }}>
            {backupSyncStatus}
          </Text>
        ) : null}

        <TextInput
          mode="outlined"
          label="Master key"
          value={backupMasterKeyInput}
          onChangeText={setBackupMasterKeyInput}
          secureTextEntry
          style={{ marginTop: 18 }}
        />
        <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
          <TextInput
            mode="outlined"
            label="S3 Access key ID"
            value={backupAccessKeyId}
            onChangeText={setBackupAccessKeyId}
            style={{ flex: 1 }}
          />
          <TextInput
            mode="outlined"
            label="S3 Secret access key"
            value={backupSecretAccessKey}
            onChangeText={setBackupSecretAccessKey}
            secureTextEntry
            style={{ flex: 1 }}
          />
        </View>

        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          <Button
            mode="contained-tonal"
            onPress={async () => {
              const result = await saveBackupMasterKey(backupMasterKeyInput);
              setBackupSyncStatus(result.message);
            }}
          >
            Save master key
          </Button>
          <Button
            mode="contained-tonal"
            onPress={async () => {
              const result = await saveBackupS3Credentials({
                accessKeyId: backupAccessKeyId,
                secretAccessKey: backupSecretAccessKey,
              });
              setBackupSyncStatus(result.message);
            }}
          >
            Save S3 credentials
          </Button>
        </View>

        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          <Button
            mode="contained"
            onPress={async () => {
              setIsBackupSyncing(true);
              try {
                const result = await runManualBackupSync();
                setBackupSyncStatus(result.message);
              } finally {
                setIsBackupSyncing(false);
              }
            }}
            disabled={isBackupSyncing}
          >
            {isBackupSyncing ? "Syncing..." : "Manual sync now"}
          </Button>
          <Button
            mode="outlined"
            onPress={async () => {
              setIsBackupSyncing(true);
              try {
                const result = await restoreLatestCloudBackup();
                setBackupSyncStatus(result.message);
              } finally {
                setIsBackupSyncing(false);
              }
            }}
            disabled={isBackupSyncing}
          >
            Restore latest cloud backup
          </Button>
        </View>

        <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 12 }}>
          Encryption: AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.
        </Text>
      </DashboardSection>

      <DashboardSection
        title="JSON backup & restore"
        description="Export the current app state and restore it later from a JSON snapshot."
      >
        <View style={{ flexDirection: "row", gap: 8, marginTop: 16 }}>
          <Button
            mode="contained-tonal"
            onPress={() => setBackupPayload(exportAppState())}
          >
            Generate backup JSON
          </Button>
          <Button mode="outlined" onPress={() => setBackupPayload("")}>
            Clear payload
          </Button>
        </View>

        <TextInput
          mode="outlined"
          label="Backup payload (JSON)"
          value={backupPayload}
          onChangeText={setBackupPayload}
          multiline
          numberOfLines={10}
          style={{ marginTop: 12 }}
        />

        <Button
          mode="contained"
          onPress={() => {
            const result = importAppStateFromJson(backupPayload);
            setBackupStatus(result.message);
          }}
          disabled={!backupPayload.trim()}
          style={{ marginTop: 12, alignSelf: "flex-start" }}
        >
          Import and restore
        </Button>

        {backupStatus ? (
          <Text variant="bodySmall" style={{ marginTop: 8 }}>
            {backupStatus}
          </Text>
        ) : null}
      </DashboardSection>
    </DashboardScrollView>
  );
}
