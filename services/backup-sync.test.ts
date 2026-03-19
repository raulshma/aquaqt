import { describe, expect, test } from "bun:test";

import { PersistedAppState } from "@/services/persistence";

import {
    buildVersionedBackupObjectKey,
    compareIsoTimestamps,
    createBackupEnvelope,
    decryptBackupEnvelope,
    encryptBackupEnvelope,
    getBackupDateStamp,
} from "./backup-sync";

const sampleState: PersistedAppState = {
  aquariums: [],
  taskTemplates: [],
  livestock: [],
  taskExecutions: [],
  dosingLogs: [],
  assets: [],
  consumables: [],
  parameterLogs: [],
  issues: [],
  memos: [],
  timeline: [],
  settings: {
    openRouterApiKey: "",
    aiModel: "nvidia/nemotron-3-super-120b-a12b:free",
  },
};

describe("backup-sync", () => {
  test("encrypts and decrypts backup envelope", () => {
    const envelope = createBackupEnvelope(sampleState);
    const encrypted = encryptBackupEnvelope(
      envelope,
      "this-is-a-strong-master-key",
    );

    expect(typeof encrypted).toBe("string");

    const decrypted = decryptBackupEnvelope(
      encrypted,
      "this-is-a-strong-master-key",
    );

    expect(decrypted.schemaVersion).toBe(1);
    expect(decrypted.appState.settings.aiModel).toBe(
      sampleState.settings.aiModel,
    );
  });

  test("fails decrypt with wrong key", () => {
    const envelope = createBackupEnvelope(sampleState);
    const encrypted = encryptBackupEnvelope(envelope, "correct-master-key-123");

    let didThrow = false;
    try {
      decryptBackupEnvelope(encrypted, "wrong-master-key-456");
    } catch {
      didThrow = true;
    }

    expect(didThrow).toBe(true);
  });

  test("compares iso timestamps deterministically", () => {
    expect(
      compareIsoTimestamps(
        "2026-03-19T00:00:00.000Z",
        "2026-03-18T23:00:00.000Z",
      ),
    ).toBe(1);
    expect(
      compareIsoTimestamps(
        "2026-03-19T00:00:00.000Z",
        "2026-03-19T00:00:00.000Z",
      ),
    ).toBe(0);
    expect(compareIsoTimestamps(undefined, "2026-03-19T00:00:00.000Z")).toBe(
      -1,
    );
  });

  test("builds YYYY-MM-DD backup date stamp", () => {
    expect(getBackupDateStamp("2026-03-19T17:30:00.000Z")).toBe("2026-03-19");
  });

  test("builds versioned history key from latest key", () => {
    expect(
      buildVersionedBackupObjectKey(
        "aquapt/backups/latest.enc.json",
        "2026-03-19T17:30:00.000Z",
      ),
    ).toBe("aquapt/backups/history/2026-03-19.enc.json");
  });
});
