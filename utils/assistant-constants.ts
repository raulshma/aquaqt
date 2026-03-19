import { TaskFrequency } from "@/types/aquapt";
import type { AssistantDetectedAction } from "@/types/assistant";

export const DRAWER_WIDTH = 300;
export const TAB_BAR_HEIGHT = 68;

export const QUICK_PROMPT_SUGGESTIONS = [
  "What should I do for my tanks today?",
  "Review my open issues and suggest priorities.",
  "Plan this week's maintenance tasks.",
  "Any dosing or parameter checks due today?",
];

export const FREQUENCIES: { label: string; value: TaskFrequency }[] = [
  { label: "Daily", value: "daily" },
  { label: "Weekly", value: "weekly" },
  { label: "Bi-weekly", value: "bi-weekly" },
  { label: "Monthly", value: "monthly" },
];

export const WATER_TYPES: { label: string; value: string }[] = [
  { label: "Freshwater", value: "freshwater" },
  { label: "Marine", value: "marine" },
  { label: "Brackish", value: "brackish" },
];

export const LIVESTOCK_KINDS: { label: string; value: string }[] = [
  { label: "Fish", value: "fish" },
  { label: "Shrimp", value: "shrimp" },
  { label: "Snail", value: "snail" },
  { label: "Coral", value: "coral" },
  { label: "Plant", value: "plant" },
  { label: "Other", value: "other" },
];

export const LIVESTOCK_STATUSES: { label: string; value: string }[] = [
  { label: "Active", value: "active" },
  { label: "Ill", value: "ill" },
  { label: "Deceased", value: "deceased" },
];

export const ASSET_CATEGORIES: { label: string; value: string }[] = [
  { label: "Filter", value: "filter" },
  { label: "Heater", value: "heater" },
  { label: "Light", value: "light" },
  { label: "CO2", value: "co2" },
  { label: "Other", value: "other" },
];

export const CONSUMABLE_UNITS: { label: string; value: string }[] = [
  { label: "g", value: "g" },
  { label: "ml", value: "ml" },
  { label: "pcs", value: "pcs" },
];

export const ISSUE_STATUSES: { label: string; value: string }[] = [
  { label: "Open", value: "open" },
  { label: "Monitoring", value: "monitoring" },
  { label: "Resolved", value: "resolved" },
];

export const AQUARIUM_REQUIRING_ACTIONS = [
  "create_task_template",
  "complete_task",
  "log_dosing",
  "log_parameters",
  "add_issue",
  "add_memo",
  "add_livestock",
  "add_asset",
  "add_consumable",
] as const;

export const PARAMETER_FIELDS = [
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
] as const;

export const nowId = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

export const formatNumber = (
  value: number | undefined,
  digits = 0,
  fallback = "—",
) => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }
  return value.toFixed(digits);
};

export const formatMilliseconds = (value: number | undefined) => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—";
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)}s`;
  }
  return `${value.toFixed(0)}ms`;
};

export const getActionSummary = (action: AssistantDetectedAction): string => {
  switch (action.type) {
    case "create_task_template":
      return action.title ?? "New task";
    case "complete_task":
      return action.taskTitle ?? action.title ?? "Complete task";
    case "log_dosing":
      return `${action.product ?? "?"} – ${action.amountMl ?? "?"}ml`;
    case "log_parameters": {
      if (!action.parameters) return "No params";
      const parts: string[] = [];
      if (action.parameters.ph !== undefined)
        parts.push(`pH ${action.parameters.ph}`);
      if (action.parameters.nitrate !== undefined)
        parts.push(`NO₃ ${action.parameters.nitrate}`);
      if (action.parameters.temperatureC !== undefined)
        parts.push(`${action.parameters.temperatureC}°C`);
      return parts.join(", ") || "Water parameters";
    }
    case "add_issue":
      return action.issueTitle ?? action.title ?? "New issue";
    case "add_memo":
      return (action.memoContent ?? action.description ?? "").slice(0, 50);
    case "add_aquarium":
      return action.title ?? "New aquarium";
    case "edit_aquarium":
      return action.aquariumName ?? "Edit aquarium";
    case "add_livestock":
      return `${action.livestockName ?? action.title ?? "?"} (${action.species ?? "?"})`;
    case "transfer_livestock":
      return `${action.livestockName ?? "?"} → ${action.targetAquariumName ?? "?"}`;
    case "set_livestock_status":
      return `${action.livestockName ?? "?"}: ${action.livestockStatus ?? "?"}`;
    case "add_asset":
      return action.brandModel ?? "New asset";
    case "add_consumable":
      return action.consumableName ?? "New consumable";
    case "consume_consumable":
      return `${action.consumableName ?? "?"} – ${action.amountUsed ?? "?"}`;
    case "set_issue_status":
      return `${action.issueTitle ?? "?"}: ${action.issueStatus ?? "?"}`;
    case "save_reminder_settings":
      return action.reminderEnabled ? "Enable reminders" : "Disable reminders";
    default:
      return action.type;
  }
};
