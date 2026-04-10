import * as SQLite from "expo-sqlite";

import {
    AppSettings,
    Aquarium,
    Asset,
    Consumable,
    DosingLog,
    Issue,
    Livestock,
    Memo,
    ReminderGroup,
    TaskExecution,
    TaskTemplate,
    TimelineEvent,
    WaterParameterLog,
} from "@/types/aquapt";
import { AssistantConversation } from "@/types/assistant";

const DB_NAME = "aquapt.db";
const STATE_KEY = "app-state-v1";
const ASSISTANT_CONVERSATIONS_KEY = "assistant-conversations-v1";
const ASSISTANT_MEMORY_KEY = "assistant-memory-v1";

export interface PersistedAppState {
  aquariums: Aquarium[];
  taskTemplates: TaskTemplate[];
  livestock: Livestock[];
  taskExecutions: TaskExecution[];
  dosingLogs: DosingLog[];
  assets: Asset[];
  consumables: Consumable[];
  parameterLogs: WaterParameterLog[];
  issues: Issue[];
  memos: Memo[];
  timeline: TimelineEvent[];
  settings: AppSettings;
  reminderGroups: ReminderGroup[];
}

export interface PersistedAssistantConversationsState {
  conversations: AssistantConversation[];
  activeConversationId: string;
  updatedAt: string;
}

export interface PersistedAssistantMemoryState {
  indexedMessageIds: string[];
  updatedAt: string;
}

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

async function getDb() {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync(DB_NAME);
  }

  return dbPromise;
}

export async function initPersistence() {
  const db = await getDb();

  await db.execAsync(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS app_state (
      key TEXT PRIMARY KEY NOT NULL,
      payload TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);
}

export async function loadPersistedState(): Promise<PersistedAppState | null> {
  const db = await getDb();

  const row = await db.getFirstAsync<{ payload: string }>(
    "SELECT payload FROM app_state WHERE key = ? LIMIT 1",
    [STATE_KEY],
  );

  if (!row?.payload) {
    return null;
  }

  try {
    return JSON.parse(row.payload) as PersistedAppState;
  } catch {
    return null;
  }
}

export async function savePersistedState(state: PersistedAppState) {
  const db = await getDb();
  const payload = JSON.stringify(state);
  const nowIso = new Date().toISOString();

  await db.runAsync(
    `
      INSERT INTO app_state (key, payload, updated_at)
      VALUES (?, ?, ?)
      ON CONFLICT(key) DO UPDATE SET
        payload = excluded.payload,
        updated_at = excluded.updated_at
    `,
    [STATE_KEY, payload, nowIso],
  );
}

export async function loadPersistedAssistantState(): Promise<PersistedAssistantConversationsState | null> {
  const db = await getDb();

  const row = await db.getFirstAsync<{ payload: string }>(
    "SELECT payload FROM app_state WHERE key = ? LIMIT 1",
    [ASSISTANT_CONVERSATIONS_KEY],
  );

  if (!row?.payload) {
    return null;
  }

  try {
    const parsed = JSON.parse(
      row.payload,
    ) as PersistedAssistantConversationsState;
    if (!Array.isArray(parsed.conversations)) {
      return null;
    }
    if (typeof parsed.activeConversationId !== "string") {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export async function savePersistedAssistantState(
  state: PersistedAssistantConversationsState,
) {
  const db = await getDb();
  const payload = JSON.stringify(state);
  const nowIso = new Date().toISOString();

  await db.runAsync(
    `
      INSERT INTO app_state (key, payload, updated_at)
      VALUES (?, ?, ?)
      ON CONFLICT(key) DO UPDATE SET
        payload = excluded.payload,
        updated_at = excluded.updated_at
    `,
    [ASSISTANT_CONVERSATIONS_KEY, payload, nowIso],
  );
}

export async function loadPersistedAssistantMemoryState(): Promise<PersistedAssistantMemoryState | null> {
  const db = await getDb();

  const row = await db.getFirstAsync<{ payload: string }>(
    "SELECT payload FROM app_state WHERE key = ? LIMIT 1",
    [ASSISTANT_MEMORY_KEY],
  );

  if (!row?.payload) {
    return null;
  }

  try {
    const parsed = JSON.parse(row.payload) as PersistedAssistantMemoryState;
    if (!Array.isArray(parsed.indexedMessageIds)) {
      return null;
    }
    if (typeof parsed.updatedAt !== "string") {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export async function savePersistedAssistantMemoryState(
  state: PersistedAssistantMemoryState,
) {
  const db = await getDb();
  const payload = JSON.stringify(state);
  const nowIso = new Date().toISOString();

  await db.runAsync(
    `
      INSERT INTO app_state (key, payload, updated_at)
      VALUES (?, ?, ?)
      ON CONFLICT(key) DO UPDATE SET
        payload = excluded.payload,
        updated_at = excluded.updated_at
    `,
    [ASSISTANT_MEMORY_KEY, payload, nowIso],
  );
}
