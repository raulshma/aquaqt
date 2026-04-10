import {
    IssueStatus,
    TaskFrequency,
    WaterParameters,
    WaterType,
} from "@/types/aquapt";
import {
    AssistantActionExtractionResult,
    AssistantActionType,
    AssistantDetectedAction,
} from "@/types/assistant";

const nowId = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const FREQUENCY_MAP: Record<string, TaskFrequency> = {
  daily: "daily",
  every_day: "daily",
  weekly: "weekly",
  every_week: "weekly",
  biweekly: "bi-weekly",
  "bi-weekly": "bi-weekly",
  fortnightly: "bi-weekly",
  monthly: "monthly",
};

type RawTaskAction = {
  type?: string;
  title?: string;
  frequency?: string;
  aquariumId?: string;
  aquariumName?: string;
  description?: string;
  taskTemplateId?: string;
  taskTitle?: string;
  product?: string;
  amountMl?: number | string;
  note?: string;
  parameters?: Record<string, number | string | null | undefined>;
  issueTitle?: string;
  memoContent?: string;
  reminderEnabled?: boolean;
  reminderHour?: number | string;
  reminderHours?: (number | string)[];
  waterType?: string;
  volumeLiters?: number | string;
  dimensions?: string;
  setupDate?: string;
  investmentCost?: number | string;
  targetAquariumId?: string;
  targetAquariumName?: string;
  livestockId?: string;
  livestockName?: string;
  species?: string;
  quantity?: number | string;
  livestockKind?: string;
  livestockStatus?: string;
  issueId?: string;
  issueStatus?: string;
  resolutionNote?: string;
  assetCategory?: string;
  brandModel?: string;
  purchasedAt?: string;
  price?: number | string;
  consumableId?: string;
  consumableName?: string;
  consumableUnit?: string;
  remaining?: number | string;
  reorderAt?: number | string;
  amountUsed?: number | string;
  confidence?: number;
};

type RawExtractionPayload = {
  actions?: RawTaskAction[];
  warnings?: string[];
};

const extractJsonBlock = (content: string) => {
  const fencedMatch = content.match(/```json\s*([\s\S]*?)\s*```/i);
  if (fencedMatch?.[1]) {
    return fencedMatch[1];
  }

  const firstBrace = content.indexOf("{");
  const lastBrace = content.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return content.slice(firstBrace, lastBrace + 1);
  }

  return content;
};

const toTaskFrequency = (value?: string): TaskFrequency | null => {
  if (!value) {
    return null;
  }

  const normalized = value.trim().toLowerCase().replace(/\s+/g, "_");
  return FREQUENCY_MAP[normalized] ?? null;
};

const ACTION_TYPES: AssistantActionType[] = [
  "create_task_template",
  "complete_task",
  "log_dosing",
  "log_parameters",
  "add_issue",
  "add_memo",
  "save_reminder_settings",
  "add_aquarium",
  "edit_aquarium",
  "add_livestock",
  "transfer_livestock",
  "set_livestock_status",
  "add_asset",
  "add_consumable",
  "consume_consumable",
  "set_issue_status",
];

const toActionType = (value?: string): AssistantActionType => {
  const normalized = value?.trim() as AssistantActionType | undefined;
  return normalized && ACTION_TYPES.includes(normalized)
    ? normalized
    : "create_task_template";
};

const toNumber = (value: unknown) => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value.trim());
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  return undefined;
};

const toWaterParameters = (
  input?: RawTaskAction["parameters"],
): WaterParameters | undefined => {
  if (!input || typeof input !== "object") {
    return undefined;
  }

  const result: WaterParameters = {};
  const keys: (keyof WaterParameters)[] = [
    "ammonia",
    "nitrite",
    "nitrate",
    "ph",
    "temperatureC",
    "gh",
    "kh",
    "salinity",
    "calcium",
    "alkalinity",
  ];

  for (const key of keys) {
    const value = toNumber(input[key]);
    if (value !== undefined) {
      result[key] = value;
    }
  }

  return Object.keys(result).length ? result : undefined;
};

const LIVESTOCK_KINDS = [
  "fish",
  "shrimp",
  "snail",
  "coral",
  "plant",
  "other",
] as const;

const LIVESTOCK_STATUSES = ["active", "ill", "deceased"] as const;

const ISSUE_STATUSES: IssueStatus[] = ["open", "monitoring", "resolved"];

const WATER_TYPES: WaterType[] = ["freshwater", "marine", "brackish"];

const ASSET_CATEGORIES = ["filter", "heater", "light", "co2", "other"] as const;
const CONSUMABLE_UNITS = ["g", "ml", "pcs"] as const;

const toWaterType = (value?: string): WaterType | undefined => {
  const normalized = value?.trim().toLowerCase() as WaterType | undefined;
  return normalized && WATER_TYPES.includes(normalized)
    ? normalized
    : undefined;
};

const toIssueStatus = (value?: string): IssueStatus | undefined => {
  const normalized = value?.trim().toLowerCase() as IssueStatus | undefined;
  return normalized && ISSUE_STATUSES.includes(normalized)
    ? normalized
    : undefined;
};

const toLivestockKind = (value?: string) => {
  const normalized = value?.trim().toLowerCase() as
    | (typeof LIVESTOCK_KINDS)[number]
    | undefined;
  return normalized && LIVESTOCK_KINDS.includes(normalized)
    ? normalized
    : undefined;
};

const toLivestockStatus = (value?: string) => {
  const normalized = value?.trim().toLowerCase() as
    | (typeof LIVESTOCK_STATUSES)[number]
    | undefined;
  return normalized && LIVESTOCK_STATUSES.includes(normalized)
    ? normalized
    : undefined;
};

const toAssetCategory = (value?: string) => {
  const normalized = value?.trim().toLowerCase() as
    | (typeof ASSET_CATEGORIES)[number]
    | undefined;
  return normalized && ASSET_CATEGORIES.includes(normalized)
    ? normalized
    : undefined;
};

const toConsumableUnit = (value?: string) => {
  const normalized = value?.trim().toLowerCase() as
    | (typeof CONSUMABLE_UNITS)[number]
    | undefined;
  return normalized && CONSUMABLE_UNITS.includes(normalized)
    ? normalized
    : undefined;
};

const normalizeAction = (
  raw: RawTaskAction,
  transcript: string,
): AssistantDetectedAction => {
  const validationErrors: string[] = [];
  const type = toActionType(raw.type);

  const title = raw.title?.trim() || undefined;
  const frequency = toTaskFrequency(raw.frequency) ?? undefined;
  const amountMl = toNumber(raw.amountMl);
  const reminderHour = toNumber(raw.reminderHour);
  const reminderHours = Array.isArray(raw.reminderHours)
    ? raw.reminderHours.map(toNumber).filter((h): h is number => h !== undefined)
    : undefined;
  const volumeLiters = toNumber(raw.volumeLiters);
  const investmentCost = toNumber(raw.investmentCost);
  const quantity = toNumber(raw.quantity);
  const price = toNumber(raw.price);
  const remaining = toNumber(raw.remaining);
  const reorderAt = toNumber(raw.reorderAt);
  const amountUsed = toNumber(raw.amountUsed);
  const parameters = toWaterParameters(raw.parameters);
  const waterType = toWaterType(raw.waterType);
  const issueStatus = toIssueStatus(raw.issueStatus);
  const livestockKind = toLivestockKind(raw.livestockKind);
  const livestockStatus = toLivestockStatus(raw.livestockStatus);
  const assetCategory = toAssetCategory(raw.assetCategory);
  const consumableUnit = toConsumableUnit(raw.consumableUnit);
  const livestockName = raw.livestockName?.trim() || title;

  const confidenceValue =
    typeof raw.confidence === "number" && Number.isFinite(raw.confidence)
      ? Math.max(0, Math.min(1, raw.confidence))
      : 0.6;

  if (raw.type && !ACTION_TYPES.includes(raw.type as AssistantActionType)) {
    validationErrors.push(`Unsupported action type \"${raw.type}\"`);
  }

  if (type === "create_task_template") {
    if (!title) {
      validationErrors.push("Missing task title");
    }
    if (!frequency) {
      validationErrors.push(
        "Missing or invalid frequency (use daily/weekly/bi-weekly/monthly)",
      );
    }
  }

  if (type === "complete_task") {
    if (!raw.taskTemplateId?.trim() && !raw.taskTitle?.trim()) {
      validationErrors.push(
        "Missing taskTemplateId or taskTitle for completion",
      );
    }
  }

  if (type === "log_dosing") {
    if (!raw.product?.trim()) {
      validationErrors.push("Missing dosing product");
    }
    if (!amountMl || amountMl <= 0) {
      validationErrors.push("Missing or invalid dosing amountMl");
    }
  }

  if (type === "log_parameters") {
    if (!parameters) {
      validationErrors.push("Missing water parameters to log");
    }
  }

  if (type === "add_issue") {
    if (!raw.issueTitle?.trim() && !title) {
      validationErrors.push("Missing issue title");
    }
  }

  if (type === "add_memo") {
    if (!raw.memoContent?.trim() && !raw.description?.trim()) {
      validationErrors.push("Missing memo content");
    }
  }

  if (type === "save_reminder_settings") {
    if (typeof raw.reminderEnabled !== "boolean") {
      validationErrors.push("Missing reminderEnabled true/false");
    }
    if (
      raw.reminderEnabled &&
      reminderHour === undefined &&
      (!reminderHours || reminderHours.length === 0)
    ) {
      validationErrors.push("Missing or invalid reminder hour(s)");
    }
  }

  if (type === "add_aquarium") {
    if (!title?.trim()) {
      validationErrors.push("Missing aquarium name");
    }
    if (!volumeLiters || volumeLiters <= 0) {
      validationErrors.push("Missing or invalid volumeLiters");
    }
    if (!waterType) {
      validationErrors.push("Missing or invalid waterType");
    }
    if (!raw.dimensions?.trim()) {
      validationErrors.push("Missing aquarium dimensions");
    }
  }

  if (type === "edit_aquarium") {
    if (!raw.aquariumId?.trim() && !raw.aquariumName?.trim()) {
      validationErrors.push("Missing aquariumId or aquariumName for update");
    }
  }

  if (type === "add_livestock") {
    if (!livestockName?.trim()) {
      validationErrors.push("Missing livestock name");
    }
    if (!raw.species?.trim()) {
      validationErrors.push("Missing livestock species");
    }
    if (!quantity || quantity <= 0) {
      validationErrors.push("Missing or invalid livestock quantity");
    }
    if (!livestockKind) {
      validationErrors.push("Missing or invalid livestock kind");
    }
  }

  if (type === "transfer_livestock") {
    if (!raw.livestockId?.trim() && !raw.livestockName?.trim()) {
      validationErrors.push("Missing livestockId or livestockName");
    }
    if (!raw.targetAquariumId?.trim() && !raw.targetAquariumName?.trim()) {
      validationErrors.push("Missing targetAquariumId or targetAquariumName");
    }
  }

  if (type === "set_livestock_status") {
    if (!raw.livestockId?.trim() && !raw.livestockName?.trim()) {
      validationErrors.push("Missing livestockId or livestockName");
    }
    if (!livestockStatus) {
      validationErrors.push("Missing or invalid livestock status");
    }
  }

  if (type === "add_asset") {
    if (!assetCategory) {
      validationErrors.push("Missing or invalid asset category");
    }
    if (!raw.brandModel?.trim()) {
      validationErrors.push("Missing asset brandModel");
    }
  }

  if (type === "add_consumable") {
    if (!raw.consumableName?.trim()) {
      validationErrors.push("Missing consumable name");
    }
    if (!consumableUnit) {
      validationErrors.push("Missing or invalid consumable unit");
    }
    if (remaining === undefined || remaining < 0) {
      validationErrors.push("Missing or invalid consumable remaining amount");
    }
  }

  if (type === "consume_consumable") {
    if (!raw.consumableId?.trim() && !raw.consumableName?.trim()) {
      validationErrors.push("Missing consumableId or consumableName");
    }
    if (!amountUsed || amountUsed <= 0) {
      validationErrors.push("Missing or invalid amountUsed");
    }
  }

  if (type === "set_issue_status") {
    if (!raw.issueId?.trim() && !raw.issueTitle?.trim()) {
      validationErrors.push("Missing issueId or issueTitle");
    }
    if (!issueStatus) {
      validationErrors.push("Missing or invalid issueStatus");
    }
  }

  return {
    id: nowId("action"),
    type,
    title,
    frequency,
    aquariumId: raw.aquariumId?.trim() || undefined,
    aquariumName: raw.aquariumName?.trim() || undefined,
    description: raw.description?.trim() || undefined,
    taskTemplateId: raw.taskTemplateId?.trim() || undefined,
    taskTitle: raw.taskTitle?.trim() || undefined,
    product: raw.product?.trim() || undefined,
    amountMl,
    note: raw.note?.trim() || undefined,
    parameters,
    issueTitle: raw.issueTitle?.trim() || undefined,
    memoContent: raw.memoContent?.trim() || undefined,
    reminderEnabled:
      typeof raw.reminderEnabled === "boolean"
        ? raw.reminderEnabled
        : undefined,
    reminderHour,
    reminderHours: reminderHours && reminderHours.length > 0 ? reminderHours : undefined,
    waterType,
    volumeLiters,
    dimensions: raw.dimensions?.trim() || undefined,
    setupDate: raw.setupDate?.trim() || undefined,
    investmentCost,
    targetAquariumId: raw.targetAquariumId?.trim() || undefined,
    targetAquariumName: raw.targetAquariumName?.trim() || undefined,
    livestockId: raw.livestockId?.trim() || undefined,
    livestockName: livestockName?.trim() || undefined,
    species: raw.species?.trim() || undefined,
    quantity,
    livestockKind,
    livestockStatus,
    issueId: raw.issueId?.trim() || undefined,
    issueStatus,
    resolutionNote: raw.resolutionNote?.trim() || undefined,
    assetCategory,
    brandModel: raw.brandModel?.trim() || undefined,
    purchasedAt: raw.purchasedAt?.trim() || undefined,
    price,
    consumableId: raw.consumableId?.trim() || undefined,
    consumableName: raw.consumableName?.trim() || undefined,
    consumableUnit,
    remaining,
    reorderAt,
    amountUsed,
    confidence: confidenceValue,
    approved: false,
    validationErrors,
    sourceTranscript: transcript,
  };
};

export function parseAssistantActionExtraction(
  responseContent: string,
  transcript: string,
): AssistantActionExtractionResult {
  const jsonBlock = extractJsonBlock(responseContent);

  try {
    const parsed = JSON.parse(jsonBlock) as RawExtractionPayload;
    const actions = Array.isArray(parsed.actions)
      ? parsed.actions.map((raw) => normalizeAction(raw, transcript))
      : [];

    return {
      actions,
      warnings: Array.isArray(parsed.warnings)
        ? parsed.warnings.filter(
            (item): item is string => typeof item === "string",
          )
        : [],
      raw: responseContent,
    };
  } catch {
    return {
      actions: [],
      warnings: [
        "The assistant returned a non-JSON action block. Try rephrasing the request.",
      ],
      raw: responseContent,
    };
  }
}
