import {
    IssueStatus,
    TaskFrequency,
    WaterParameters,
    WaterType,
} from "@/types/aquapt";

export type AssistantMessageRole = "system" | "user" | "assistant";

export interface AssistantChatMessage {
  id: string;
  role: AssistantMessageRole;
  content: string;
  createdAt: string;
  /** True when the user message failed to receive an assistant response. */
  requestFailed?: boolean;
  /** Optional error detail from a failed assistant request. */
  requestError?: string;
  /** IDs of detected actions linked to this assistant message */
  detectedActionIds?: string[];
  /** Runtime metadata for AI responses (usage/provider/perf). */
  responseTelemetry?: AssistantResponseTelemetry;
}

export interface AssistantResponseTelemetry {
  generationId?: string;
  providerName?: string;
  router?: string;
  model?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  cost?: number;
  elapsedMs?: number;
  latencyMs?: number;
  generationTimeMs?: number;
  throughputCharsPerSecond?: number;
  throughputTokensPerSecond?: number;
  finishReason?: string | null;
  nativeFinishReason?: string | null;
  streamed?: boolean;
}

export type AssistantActionType =
  | "create_task_template"
  | "complete_task"
  | "log_dosing"
  | "log_parameters"
  | "add_issue"
  | "add_memo"
  | "save_reminder_settings"
  | "add_aquarium"
  | "edit_aquarium"
  | "add_livestock"
  | "transfer_livestock"
  | "set_livestock_status"
  | "add_asset"
  | "add_consumable"
  | "consume_consumable"
  | "set_issue_status";

export interface AssistantDetectedAction {
  id: string;
  type: AssistantActionType;
  title?: string;
  frequency?: TaskFrequency;
  aquariumId?: string;
  aquariumName?: string;
  description?: string;
  taskTemplateId?: string;
  taskTitle?: string;
  product?: string;
  amountMl?: number;
  note?: string;
  parameters?: WaterParameters;
  issueTitle?: string;
  memoContent?: string;
  reminderEnabled?: boolean;
  reminderHour?: number;
  waterType?: WaterType;
  volumeLiters?: number;
  dimensions?: string;
  setupDate?: string;
  investmentCost?: number;
  targetAquariumId?: string;
  targetAquariumName?: string;
  livestockId?: string;
  livestockName?: string;
  species?: string;
  quantity?: number;
  livestockKind?: "fish" | "shrimp" | "snail" | "coral" | "plant" | "other";
  livestockStatus?: "active" | "ill" | "deceased";
  issueId?: string;
  issueStatus?: IssueStatus;
  resolutionNote?: string;
  assetCategory?: "filter" | "heater" | "light" | "co2" | "other";
  brandModel?: string;
  purchasedAt?: string;
  price?: number;
  consumableId?: string;
  consumableName?: string;
  consumableUnit?: "g" | "ml" | "pcs";
  remaining?: number;
  reorderAt?: number;
  amountUsed?: number;
  confidence: number;
  approved: boolean;
  validationErrors: string[];
  sourceTranscript: string;
}

/** A single conversation thread with its own messages and detected actions */
export interface AssistantConversation {
  id: string;
  title: string;
  pinned?: boolean;
  messages: AssistantChatMessage[];
  detectedActions: AssistantDetectedAction[];
  warnings: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AssistantActionExtractionResult {
  actions: AssistantDetectedAction[];
  warnings: string[];
  raw: string;
}

export interface AssistantTaskExecutionItemResult {
  actionId: string;
  actionType: AssistantActionType;
  created: boolean;
  reason?: string;
  summary?: string;
}

export interface AssistantTaskExecutionResult {
  createdCount: number;
  skippedCount: number;
  results: AssistantTaskExecutionItemResult[];
}

export interface AssistantMemorySnippet {
  id: string;
  content: string;
  similarity?: number;
  createdAt?: string;
  category?: string;
  sourceConversationId?: string;
  sourceMessageId?: string;
}
