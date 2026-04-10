export type WaterType = "freshwater" | "marine" | "brackish";

export type IssueStatus = "open" | "monitoring" | "resolved";

export type TaskFrequency =
  | "daily"
  | "weekly"
  | "bi-weekly"
  | "monthly"
  | `custom-${number}`;

export function parseCustomFrequencyDays(
  frequency: TaskFrequency,
): number | null {
  if (frequency.startsWith("custom-")) {
    const days = Number(frequency.slice(7));
    return Number.isFinite(days) && days >= 1 ? days : null;
  }
  return null;
}

export function getFrequencyLabel(frequency: TaskFrequency): string {
  const custom = parseCustomFrequencyDays(frequency);
  if (custom !== null) {
    return `Every ${custom} day${custom === 1 ? "" : "s"}`;
  }
  const labels: Record<string, string> = {
    daily: "Daily",
    weekly: "Weekly",
    "bi-weekly": "Bi-weekly",
    monthly: "Monthly",
  };
  return labels[frequency] ?? frequency;
}

export type TaskCategory = "maintenance" | "feeding";
export type LivestockKind = "fish" | "shrimp" | "snail" | "coral" | "plant" | "other";
export type LivestockStatus = "active" | "ill" | "deceased";
export type AssetCategory = "filter" | "heater" | "light" | "co2" | "other";
export type ConsumableUnit = "g" | "ml" | "pcs";

export type EditKind = "task-template" | "task-execution";

export type TimelineEventType =
  | "task"
  | "parameter"
  | "issue"
  | "livestock"
  | "memo"
  | "dosing"
  | "asset"
  | "consumable";

export type EntityKind =
  | "aquarium"
  | "task"
  | "livestock"
  | "asset"
  | "consumable"
  | "issue"
  | "memo"
  | "dosing"
  | "parameter-log";

export type AppThemePreference = "system" | "light" | "dark";
export type RegionalPreferencesMode = "auto" | "manual";

export interface EntityRef {
  kind: EntityKind;
  id: string;
  aquariumId?: string;
}

export interface Aquarium {
  id: string;
  name: string;
  volumeLiters: number;
  dimensions: string;
  waterType: WaterType;
  setupDate: string;
  investmentCost?: number;
  photoUri?: string;
}

export interface WaterParameters {
  ammonia?: number;
  nitrite?: number;
  nitrate?: number;
  ph?: number;
  temperatureC?: number;
  gh?: number;
  kh?: number;
  salinity?: number;
  calcium?: number;
  alkalinity?: number;
}

export interface WaterParameterLog {
  id: string;
  aquariumId: string;
  createdAt: string;
  values: WaterParameters;
}

export interface Livestock {
  id: string;
  aquariumId: string;
  kind: LivestockKind;
  name: string;
  species: string;
  quantity: number;
  acquiredAt: string;
  purchasePrice?: number;
  photoUri?: string;
  dietaryNotes?: string;
  parentId?: string;
  status?: LivestockStatus;
}

export interface DosingLog {
  id: string;
  aquariumId: string;
  product: string;
  amountMl: number;
  createdAt: string;
  note?: string;
}

export interface Asset {
  id: string;
  aquariumId: string;
  category: AssetCategory;
  brandModel: string;
  purchasedAt?: string;
  price?: number;
  maintenanceTaskTemplateIds?: string[];
  photoUri?: string;
}

export interface Consumable {
  id: string;
  aquariumId: string;
  name: string;
  unit: ConsumableUnit;
  remaining: number;
  reorderAt?: number;
  updatedAt: string;
  photoUri?: string;
}

export interface TaskTemplate {
  id: string;
  title: string;
  description?: string;
  category?: TaskCategory;
  livestockId?: string;
  frequency: TaskFrequency;
  aquariumIds: string[];
  startDate?: string;
  timesPerDay?: number;
  reminderHours?: number[];
  reminderGroupId?: string;
}

export interface ReminderGroup {
  id: string;
  name: string;
  hours: number[];
}

export interface TaskExecution {
  id: string;
  taskTemplateId: string;
  aquariumId: string;
  completedAt: string;
  note?: string;
}

export interface Issue {
  id: string;
  aquariumId: string;
  title: string;
  status: IssueStatus;
  createdAt: string;
  resolutionNote?: string;
}

export interface Memo {
  id: string;
  aquariumId: string;
  content: string;
  createdAt: string;
  photoUri?: string;
}

export interface TimelineEvent {
  id: string;
  aquariumId: string;
  type: TimelineEventType;
  createdAt: string;
  title: string;
  description?: string;
  photoUri?: string;
  source?: EntityRef;
  related?: EntityRef[];
}

export interface AppSettings {
  openRouterApiKey: string;
  aiModel: string;
  assistantMemoryModel?: string;
  notificationsEnabled?: boolean;
  reminderHour?: number;
  reminderHours?: number[];
  assistantMemoryEnabled?: boolean;
  backupSyncEnabled?: boolean;
  backupSyncHour?: number;
  backupS3Endpoint?: string;
  backupS3Region?: string;
  backupS3Bucket?: string;
  backupS3ObjectKey?: string;
  backupS3ForcePathStyle?: boolean;
  backupUseVersionedKeys?: boolean;
  backupRetentionDays?: number;
  backupMasterKeySet?: boolean;
  backupS3CredentialsSet?: boolean;
  backupLastSyncedAt?: string;
  backupLastRestoredAt?: string;
  backupLastAutoSyncDate?: string;
  backupLastError?: string;
  themePreference?: AppThemePreference;
  regionalPreferencesMode?: RegionalPreferencesMode;
  defaultLocale?: string;
  defaultTimezone?: string;
  defaultCountryCode?: string;
  defaultCountryName?: string;
  defaultCurrency?: string;
}
