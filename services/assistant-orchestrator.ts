import { parseAssistantActionExtraction } from "@/services/assistant-actions";
import {
    requestOpenRouterCompletion,
    requestOpenRouterStreamingCompletion,
} from "@/services/assistant-ai";
import { ASSISTANT_SYSTEM_PROMPT } from "@/services/assistant-prompts";
import { Aquarium } from "@/types/aquapt";
import {
    AssistantActionExtractionResult,
    AssistantChatMessage,
    AssistantMemorySnippet,
    AssistantResponseTelemetry,
} from "@/types/assistant";

interface AskAssistantWithActionsOptions {
  apiKey: string;
  model: string;
  userPrompt: string;
  appContext: unknown;
  aquariums: Aquarium[];
  memorySnippets?: AssistantMemorySnippet[];
  conversationMessages?: AssistantChatMessage[];
  onAssistantDelta?: (snapshot: {
    text: string;
    elapsedMs: number;
    charsPerSecond: number;
    generationId?: string;
    model?: string;
    provider?: string;
  }) => void;
  signal?: AbortSignal;
}

interface AskAssistantWithActionsResult {
  assistantText: string;
  extractedActions: AssistantActionExtractionResult;
  telemetry?: AssistantResponseTelemetry;
}

const stringifyContext = (value: unknown) => {
  try {
    return JSON.stringify(value);
  } catch {
    return "{}";
  }
};

const toOpenRouterRole = (
  role: AssistantChatMessage["role"],
): "system" | "user" | "assistant" => {
  if (role === "user") {
    return "user";
  }

  if (role === "assistant") {
    return "assistant";
  }

  return "system";
};

const buildMemorySystemPrompt = (snippets: AssistantMemorySnippet[]) => {
  if (!snippets.length) {
    return "";
  }

  const lines = snippets.map((snippet, index) => {
    const content = snippet.content.replace(/\s+/g, " ").trim();
    return `${index + 1}. ${content}`;
  });

  return [
    "Long-term memory snippets from previous chats (may be outdated; verify before acting):",
    ...lines,
  ].join("\n");
};

export async function askAssistantWithTaskDetection({
  apiKey,
  model,
  userPrompt,
  appContext,
  aquariums,
  memorySnippets = [],
  conversationMessages = [],
  onAssistantDelta,
  signal,
}: AskAssistantWithActionsOptions): Promise<AskAssistantWithActionsResult> {
  const memoryPrompt = buildMemorySystemPrompt(memorySnippets);
  const recentConversationMessages = conversationMessages
    .slice(-8)
    .map((message) => ({
      role: toOpenRouterRole(message.role),
      content: message.content,
    }))
    .filter((message) => message.content.trim().length > 0);

  const streamedAssistantResult = await requestOpenRouterStreamingCompletion({
    apiKey,
    model,
    temperature: 0.2,
    onDelta: onAssistantDelta,
    signal,
    messages: [
      {
        role: "system",
        content: ASSISTANT_SYSTEM_PROMPT,
      },
      {
        role: "system",
        content: `App context: ${stringifyContext(appContext)}`,
      },
      ...(memoryPrompt
        ? [
            {
              role: "system" as const,
              content: memoryPrompt,
            },
          ]
        : []),
      ...recentConversationMessages,
      {
        role: "user",
        content: userPrompt,
      },
    ],
  });

  const assistantText = streamedAssistantResult.text;

  const aquariumDirectory = aquariums.map((aq) => ({
    id: aq.id,
    name: aq.name,
    waterType: aq.waterType,
  }));

  const extractionRaw = await requestOpenRouterCompletion({
    apiKey,
    model,
    temperature: 0,
    messages: [
      {
        role: "system",
        content:
          'Extract actionable in-app intents from the transcript. Return JSON only with schema: {"actions":[{"type":"create_task_template|complete_task|log_dosing|log_parameters|add_issue|add_memo|save_reminder_settings|add_aquarium|edit_aquarium|add_livestock|transfer_livestock|set_livestock_status|add_asset|add_consumable|consume_consumable|set_issue_status","title":string(optional),"frequency":"daily|weekly|bi-weekly|monthly"(optional),"aquariumId":string(optional),"aquariumName":string(optional),"description":string(optional),"taskTemplateId":string(optional),"taskTitle":string(optional),"product":string(optional),"amountMl":number(optional),"note":string(optional),"parameters":object(optional),"issueTitle":string(optional),"memoContent":string(optional),"reminderEnabled":boolean(optional),"reminderHour":number(optional),"waterType":"freshwater|marine|brackish"(optional),"volumeLiters":number(optional),"dimensions":string(optional),"setupDate":string(optional),"investmentCost":number(optional),"targetAquariumId":string(optional),"targetAquariumName":string(optional),"livestockId":string(optional),"livestockName":string(optional),"species":string(optional),"quantity":number(optional),"livestockKind":"fish|shrimp|snail|coral|plant|other"(optional),"livestockStatus":"active|ill|deceased"(optional),"issueId":string(optional),"issueStatus":"open|monitoring|resolved"(optional),"resolutionNote":string(optional),"assetCategory":"filter|heater|light|co2|other"(optional),"brandModel":string(optional),"purchasedAt":string(optional),"price":number(optional),"consumableId":string(optional),"consumableName":string(optional),"consumableUnit":"g|ml|pcs"(optional),"remaining":number(optional),"reorderAt":number(optional),"amountUsed":number(optional),"confidence":number(0..1)}],"warnings":[string]}. Use only listed action types. If uncertain, add warnings and leave invalid fields blank rather than guessing.',
      },
      {
        role: "system",
        content: `Available aquariums: ${JSON.stringify(aquariumDirectory)}`,
      },
      {
        role: "user",
        content: `Transcript: ${userPrompt}`,
      },
      {
        role: "assistant",
        content: `Assistant reply: ${assistantText}`,
      },
    ],
  });

  const extractedActions = parseAssistantActionExtraction(
    extractionRaw,
    userPrompt,
  );

  const telemetry: AssistantResponseTelemetry = {
    streamed: true,
    generationId: streamedAssistantResult.generationId,
    providerName:
      streamedAssistantResult.generation?.provider_name ??
      streamedAssistantResult.provider,
    router: streamedAssistantResult.generation?.router,
    model: streamedAssistantResult.model,
    promptTokens: streamedAssistantResult.usage?.prompt_tokens,
    completionTokens: streamedAssistantResult.usage?.completion_tokens,
    totalTokens: streamedAssistantResult.usage?.total_tokens,
    cost:
      streamedAssistantResult.usage?.cost ??
      streamedAssistantResult.generation?.total_cost,
    elapsedMs: streamedAssistantResult.elapsedMs,
    latencyMs: streamedAssistantResult.generation?.latency,
    generationTimeMs: streamedAssistantResult.generation?.generation_time,
    throughputCharsPerSecond: streamedAssistantResult.throughputCharsPerSecond,
    throughputTokensPerSecond:
      streamedAssistantResult.throughputTokensPerSecond,
    finishReason: streamedAssistantResult.finishReason,
    nativeFinishReason: streamedAssistantResult.nativeFinishReason,
  };

  return {
    assistantText,
    extractedActions,
    telemetry,
  };
}
