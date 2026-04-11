import { Platform } from "react-native";

import { requestOpenRouterCompletion } from "@/services/assistant-ai";
import {
  loadPersistedAssistantMemoryState,
  savePersistedAssistantMemoryState,
} from "@/services/persistence";
import { AssistantMemorySnippet } from "@/types/assistant";

interface MemoryDependencies {
  ALL_MINILM_L6_V2: {
    modelSource: unknown;
    tokenizerSource: unknown;
  };
  ExecuTorchEmbeddings: any;
  OPSQLiteVectorStore: any;
}

interface QueryResultLike {
  id?: string;
  similarity?: number;
  document?: string;
  metadata?: Record<string, unknown>;
}

interface MemoryVectorStoreLike {
  load: () => Promise<MemoryVectorStoreLike>;
  add: (params: {
    id?: string;
    document?: string;
    metadata?: Record<string, unknown>;
  }) => Promise<string>;
  update: (params: {
    id: string;
    document?: string;
    metadata?: Record<string, unknown>;
  }) => Promise<void>;
  delete?: (params: {
    predicate: (value: {
      id?: string;
      document?: string;
      metadata?: Record<string, unknown>;
    }) => boolean;
  }) => Promise<void>;
  deleteVectorStore?: () => Promise<void>;
  query: (params: {
    queryText: string;
    nResults?: number;
  }) => Promise<QueryResultLike[]>;
}

interface PersistedMemoryRuntimeState {
  indexedMessageIds: Set<string>;
}

interface RememberTurnParams {
  conversationId: string;
  userMessageId: string;
  userPrompt: string;
  assistantText: string;
  apiKey?: string;
  model?: string;
}

interface RememberManualSnippetParams {
  conversationId: string;
  sourceMessageId: string;
  content: string;
}

const MEMORY_VECTOR_STORE_NAME = "assistant-memory-v1";
const MAX_MEMORY_CHARS = 1200;
const MANUAL_MEMORY_PREFIX = "manual";
const COMPACT_MEMORY_PREFIX = "compact";

let depsPromise: Promise<MemoryDependencies | null> | null = null;
let vectorStorePromise: Promise<MemoryVectorStoreLike | null> | null = null;
let runtimeStatePromise: Promise<PersistedMemoryRuntimeState> | null = null;
const backgroundSummaryTasks = new Map<string, Promise<void>>();

async function loadDeps(): Promise<MemoryDependencies | null> {
  if (Platform.OS === "web") {
    return null;
  }

  if (!depsPromise) {
    depsPromise = (async () => {
      try {
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const executorch = require("react-native-executorch");
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const ragExecutorch = require("@react-native-rag/executorch");
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const ragOpSqlite = require("@react-native-rag/op-sqlite");

        return {
          ALL_MINILM_L6_V2: executorch.ALL_MINILM_L6_V2,
          ExecuTorchEmbeddings: ragExecutorch.ExecuTorchEmbeddings,
          OPSQLiteVectorStore: ragOpSqlite.OPSQLiteVectorStore,
        };
      } catch (error) {
        console.warn("Assistant memory dependencies unavailable", error);
        return null;
      }
    })();
  }

  return depsPromise;
}

async function getRuntimeState(): Promise<PersistedMemoryRuntimeState> {
  if (!runtimeStatePromise) {
    runtimeStatePromise = (async () => {
      const persisted = await loadPersistedAssistantMemoryState();
      return {
        indexedMessageIds: new Set(persisted?.indexedMessageIds ?? []),
      };
    })();
  }

  return runtimeStatePromise;
}

async function saveRuntimeState(state: PersistedMemoryRuntimeState) {
  await savePersistedAssistantMemoryState({
    indexedMessageIds: Array.from(state.indexedMessageIds),
    updatedAt: new Date().toISOString(),
  });
}

async function getVectorStore(): Promise<MemoryVectorStoreLike | null> {
  if (!vectorStorePromise) {
    vectorStorePromise = (async () => {
      const deps = await loadDeps();
      if (!deps) {
        return null;
      }

      const embeddings = new deps.ExecuTorchEmbeddings({
        modelSource: deps.ALL_MINILM_L6_V2.modelSource,
        tokenizerSource: deps.ALL_MINILM_L6_V2.tokenizerSource,
      });

      const vectorStore = new deps.OPSQLiteVectorStore({
        name: MEMORY_VECTOR_STORE_NAME,
        embeddings,
      });

      return vectorStore.load();
    })();
  }

  return vectorStorePromise;
}

const normalizeText = (value: string) =>
  value
    .replace(/\s+/g, " ")
    .replace(/[\u0000-\u001F]/g, "")
    .trim();

const clamp = (value: string, max: number) =>
  value.length > max ? `${value.slice(0, max)}…` : value;

const nowIso = () => new Date().toISOString();

const splitSentences = (text: string) =>
  text
    .split(/(?<=[.!?])\s+/)
    .map((sentence) => normalizeText(sentence))
    .filter((sentence) => sentence.length > 0);

const extractStructuredMemoryLines = (params: RememberTurnParams) => {
  const promptSentences = splitSentences(params.userPrompt);
  const assistantSentences = splitSentences(params.assistantText);

  const preferenceHints = promptSentences.filter((sentence) =>
    /(prefer|usually|always|never|only|schedule|reminder|tank|aquarium|shrimp|marine|freshwater)/i.test(
      sentence,
    ),
  );

  const actionableHints = assistantSentences.filter((sentence) =>
    /(recommend|should|avoid|next step|watch|monitor|dose|change|maintain)/i.test(
      sentence,
    ),
  );

  const selected = [
    ...preferenceHints.slice(0, 3),
    ...actionableHints.slice(0, 2),
  ].slice(0, 4);

  return selected;
};

const buildMemoryDocument = ({
  userPrompt,
  assistantText,
}: RememberTurnParams) => {
  const normalizedPrompt = normalizeText(userPrompt);
  const normalizedReply = normalizeText(assistantText);

  const structuredLines = extractStructuredMemoryLines({
    conversationId: "",
    userMessageId: "",
    userPrompt,
    assistantText,
  });

  const content = structuredLines.length
    ? [
        `Memory facts:`,
        ...structuredLines.map((line, index) => `${index + 1}. ${line}`),
        `Source question: ${normalizedPrompt}`,
      ].join("\n")
    : `User asked: ${normalizedPrompt}\nAssistant answered: ${normalizedReply}`;

  return clamp(content, MAX_MEMORY_CHARS);
};

const buildMemoryDocumentWithAi = async ({
  userPrompt,
  assistantText,
  apiKey,
  model,
}: RememberTurnParams): Promise<string | null> => {
  const trimmedApiKey = apiKey?.trim();
  const trimmedModel = model?.trim();

  if (!trimmedApiKey || !trimmedModel) {
    return null;
  }

  const normalizedPrompt = normalizeText(userPrompt);
  const normalizedReply = normalizeText(assistantText);
  if (!normalizedPrompt || !normalizedReply) {
    return null;
  }

  const raw = await requestOpenRouterCompletion({
    apiKey: trimmedApiKey,
    model: trimmedModel,
    temperature: 0,
    messages: [
      {
        role: "system",
        content:
          "You generate compact long-term memory snippets for an aquarium assistant. Return plain text only (no markdown code fences), max 4 lines, max 450 chars. Keep only durable user preferences, stable constraints, and actionable follow-up context. If nothing durable exists, return EXACTLY: NO_MEMORY.",
      },
      {
        role: "user",
        content: [
          `User prompt: ${normalizedPrompt}`,
          `Assistant reply: ${normalizedReply}`,
        ].join("\n"),
      },
    ],
  });

  const normalized = normalizeText(raw).replace(/^`+|`+$/g, "");
  if (!normalized || normalized === "NO_MEMORY") {
    return null;
  }

  return clamp(normalized, MAX_MEMORY_CHARS);
};

const toSnippet = (result: QueryResultLike): AssistantMemorySnippet | null => {
  const content =
    typeof result.document === "string" ? result.document.trim() : "";
  if (!content) {
    return null;
  }

  const metadata =
    result.metadata && typeof result.metadata === "object"
      ? result.metadata
      : undefined;

  return {
    id: result.id ?? `memory-${Math.random().toString(36).slice(2, 10)}`,
    content,
    similarity:
      typeof result.similarity === "number" &&
      Number.isFinite(result.similarity)
        ? result.similarity
        : undefined,
    createdAt:
      typeof metadata?.createdAt === "string" ? metadata.createdAt : undefined,
    category:
      typeof metadata?.category === "string" ? metadata.category : undefined,
    sourceConversationId:
      typeof metadata?.conversationId === "string"
        ? metadata.conversationId
        : undefined,
    sourceMessageId:
      typeof metadata?.sourceMessageId === "string"
        ? metadata.sourceMessageId
        : typeof metadata?.userMessageId === "string"
          ? metadata.userMessageId
          : undefined,
  };
};

const buildManualMemoryId = (conversationId: string, sourceMessageId: string) =>
  `${MANUAL_MEMORY_PREFIX}:conv:${conversationId}:msg:${sourceMessageId}`;

const buildCompactMemoryId = (index: number) =>
  `${COMPACT_MEMORY_PREFIX}:fact:${Date.now()}:${index}`;

async function upsertMemoryDocument(
  store: MemoryVectorStoreLike,
  params: {
    id: string;
    document: string;
    metadata: Record<string, unknown>;
  },
) {
  try {
    await store.add(params);
  } catch {
    await store.update(params);
  }
}

function extractCompactFactCandidates(snippets: AssistantMemorySnippet[]) {
  const candidates = snippets.flatMap((snippet) =>
    snippet.content
      .split(/\n+/)
      .map((line) =>
        normalizeText(
          line
            .replace(/^\d+[.)]\s*/, "")
            .replace(/^[-*•]\s*/, "")
            .replace(/^memory facts:\s*/i, "")
            .replace(/^source question:\s*/i, "")
            .replace(/^user asked:\s*/i, "")
            .replace(/^assistant answered:\s*/i, ""),
        ),
      )
      .filter((line) => line.length >= 12),
  );

  return Array.from(new Set(candidates));
}

const coerceCompactedFacts = (raw: string, maxFacts: number) => {
  const lines = raw
    .split(/\n+/)
    .map((line) =>
      normalizeText(
        line
          .replace(/^\d+[.)]\s*/, "")
          .replace(/^[-*•]\s*/, "")
          .replace(/^"|"$/g, ""),
      ),
    )
    .filter((line) => line.length >= 8 && !/^NO_MEMORY$/i.test(line));

  return Array.from(new Set(lines)).slice(0, maxFacts);
};

async function buildCompactedFactsWithAi(params: {
  snippets: AssistantMemorySnippet[];
  apiKey?: string;
  model?: string;
  maxFacts: number;
}): Promise<string[] | null> {
  const trimmedApiKey = params.apiKey?.trim();
  const trimmedModel = params.model?.trim();

  if (!trimmedApiKey || !trimmedModel) {
    return null;
  }

  const inputFacts = extractCompactFactCandidates(params.snippets)
    .slice(0, 80)
    .map((fact, index) => `${index + 1}. ${fact}`)
    .join("\n");

  if (!inputFacts) {
    return null;
  }

  const raw = await requestOpenRouterCompletion({
    apiKey: trimmedApiKey,
    model: trimmedModel,
    temperature: 0,
    messages: [
      {
        role: "system",
        content:
          "You compact long-term memory for an aquarium assistant. Return plain text only, one fact per line, max 12 lines, each line <= 160 chars. Keep only durable user preferences, stable constraints, and reusable context. Remove duplicates and weak one-off details. If no durable facts exist, return EXACTLY: NO_MEMORY.",
      },
      {
        role: "user",
        content: `Facts to compact:\n${inputFacts}`,
      },
    ],
  });

  const compacted = coerceCompactedFacts(raw, params.maxFacts);
  return compacted.length > 0 ? compacted : null;
}

function buildCompactedFactsHeuristically(params: {
  snippets: AssistantMemorySnippet[];
  maxFacts: number;
}): string[] {
  return extractCompactFactCandidates(params.snippets)
    .slice(0, params.maxFacts)
    .map((fact) => clamp(fact, 180));
}

async function buildAssistantMemoryCompactionPlan(params: {
  apiKey?: string;
  model?: string;
  enabled?: boolean;
  maxFacts?: number;
}): Promise<{ beforeCount: number; facts: string[] }> {
  if (params.enabled === false) {
    return { beforeCount: 0, facts: [] };
  }

  const snippets = await listAssistantMemorySnippets({ limit: 120 });
  if (snippets.length === 0) {
    return { beforeCount: 0, facts: [] };
  }

  const maxFacts = Math.max(1, Math.min(20, params.maxFacts ?? 10));

  let compactedFacts: string[] | null = null;
  try {
    compactedFacts = await buildCompactedFactsWithAi({
      snippets,
      apiKey: params.apiKey,
      model: params.model,
      maxFacts,
    });
  } catch (error) {
    console.warn("Assistant memory compact AI generation failed", error);
  }

  const facts = (
    compactedFacts ??
    buildCompactedFactsHeuristically({
      snippets,
      maxFacts,
    })
  )
    .map((fact) => clamp(normalizeText(fact), 220))
    .filter((fact) => fact.length > 0)
    .slice(0, maxFacts);

  return {
    beforeCount: snippets.length,
    facts: Array.from(new Set(facts)),
  };
}

export async function previewAssistantMemoryFactCompaction(params: {
  apiKey?: string;
  model?: string;
  enabled?: boolean;
  maxFacts?: number;
}): Promise<{ beforeCount: number; afterCount: number; facts: string[] }> {
  const plan = await buildAssistantMemoryCompactionPlan(params);

  return {
    beforeCount: plan.beforeCount,
    afterCount: plan.facts.length,
    facts: plan.facts,
  };
}

export async function queryAssistantMemorySnippets(params: {
  prompt: string;
  limit?: number;
  enabled?: boolean;
}): Promise<AssistantMemorySnippet[]> {
  if (params.enabled === false) {
    return [];
  }

  const query = normalizeText(params.prompt);
  if (!query) {
    return [];
  }

  const store = await getVectorStore();
  if (!store) {
    return [];
  }

  try {
    const results = await store.query({
      queryText: query,
      nResults: params.limit ?? 4,
    });

    return results
      .map((result) => toSnippet(result))
      .filter((result): result is AssistantMemorySnippet => result !== null);
  } catch (error) {
    console.warn("Assistant memory query failed", error);
    return [];
  }
}

export async function rememberAssistantTurn(
  params: RememberTurnParams & { enabled?: boolean },
): Promise<void> {
  if (params.enabled === false) {
    return;
  }

  const fallbackDoc = buildMemoryDocument(params);
  if (!fallbackDoc) {
    return;
  }

  const state = await getRuntimeState();
  if (state.indexedMessageIds.has(params.userMessageId)) {
    return;
  }

  const store = await getVectorStore();
  if (!store) {
    return;
  }

  const memoryId = `conv:${params.conversationId}:msg:${params.userMessageId}`;
  const createdAt = nowIso();

  await upsertMemoryDocument(store, {
    id: memoryId,
    document: fallbackDoc,
    metadata: {
      conversationId: params.conversationId,
      userMessageId: params.userMessageId,
      sourceMessageId: params.userMessageId,
      category: "conversation_turn",
      createdAt,
    },
  });

  state.indexedMessageIds.add(params.userMessageId);
  await saveRuntimeState(state);

  if (!backgroundSummaryTasks.has(memoryId)) {
    const task = (async () => {
      try {
        const aiDoc = await buildMemoryDocumentWithAi(params);
        if (!aiDoc || aiDoc === fallbackDoc) {
          return;
        }

        const activeStore = await getVectorStore();
        if (!activeStore) {
          return;
        }

        await upsertMemoryDocument(activeStore, {
          id: memoryId,
          document: aiDoc,
          metadata: {
            conversationId: params.conversationId,
            userMessageId: params.userMessageId,
            sourceMessageId: params.userMessageId,
            category: "conversation_turn",
            createdAt,
            summarizedAt: nowIso(),
          },
        });
      } catch (error) {
        console.warn("Assistant memory background summarization failed", error);
      } finally {
        backgroundSummaryTasks.delete(memoryId);
      }
    })();

    backgroundSummaryTasks.set(memoryId, task);
  }
}

export async function listAssistantMemorySnippets(params?: {
  limit?: number;
}): Promise<AssistantMemorySnippet[]> {
  const store = await getVectorStore();
  if (!store) {
    return [];
  }

  try {
    const results = await store.query({
      queryText: "memory facts aquarium user asked",
      nResults: params?.limit ?? 20,
    });

    return results
      .map((result) => toSnippet(result))
      .filter((result): result is AssistantMemorySnippet => result !== null)
      .sort((a, b) => {
        const aTs = a.createdAt ? Date.parse(a.createdAt) : 0;
        const bTs = b.createdAt ? Date.parse(b.createdAt) : 0;
        return bTs - aTs;
      });
  } catch (error) {
    console.warn("Assistant memory listing failed", error);
    return [];
  }
}

export async function forgetAssistantMemorySnippet(id: string): Promise<void> {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return;
  }

  const store = await getVectorStore();
  if (!store?.delete) {
    return;
  }

  await store.delete({
    predicate: (value) => value.id === normalizedId,
  });
}

export async function clearAssistantMemoryStore(): Promise<void> {
  const store = await getVectorStore();

  if (store?.deleteVectorStore) {
    await store.deleteVectorStore();
  } else if (store?.delete) {
    await store.delete({
      predicate: () => true,
    });
  }

  const state = await getRuntimeState();
  state.indexedMessageIds.clear();
  await saveRuntimeState(state);

  vectorStorePromise = null;
}

export async function rememberManualAssistantSnippet(
  params: RememberManualSnippetParams & { enabled?: boolean },
): Promise<string | null> {
  if (params.enabled === false) {
    return null;
  }

  const content = clamp(normalizeText(params.content), MAX_MEMORY_CHARS);
  if (!content) {
    return null;
  }

  const store = await getVectorStore();
  if (!store) {
    return null;
  }

  const memoryId = buildManualMemoryId(
    params.conversationId,
    params.sourceMessageId,
  );

  await upsertMemoryDocument(store, {
    id: memoryId,
    document: content,
    metadata: {
      conversationId: params.conversationId,
      sourceMessageId: params.sourceMessageId,
      category: "manual",
      createdAt: nowIso(),
    },
  });

  return memoryId;
}

export async function forgetManualAssistantSnippet(params: {
  conversationId: string;
  sourceMessageId: string;
}): Promise<void> {
  const memoryId = buildManualMemoryId(
    params.conversationId,
    params.sourceMessageId,
  );

  await forgetAssistantMemorySnippet(memoryId);
}

export async function compactAssistantMemoryFacts(params: {
  apiKey?: string;
  model?: string;
  enabled?: boolean;
  maxFacts?: number;
  precomputedFacts?: string[];
}): Promise<{ beforeCount: number; afterCount: number }> {
  const plan = await buildAssistantMemoryCompactionPlan(params);
  if (plan.beforeCount === 0) {
    return { beforeCount: 0, afterCount: 0 };
  }

  const maxFacts = Math.max(1, Math.min(20, params.maxFacts ?? 10));
  const finalFacts = (
    Array.isArray(params.precomputedFacts) && params.precomputedFacts.length > 0
      ? params.precomputedFacts
      : plan.facts
  )
    .map((fact) => clamp(normalizeText(fact), 220))
    .filter((fact) => fact.length > 0)
    .slice(0, maxFacts);

  if (finalFacts.length === 0) {
    return { beforeCount: plan.beforeCount, afterCount: 0 };
  }

  const store = await getVectorStore();
  if (!store?.delete) {
    throw new Error(
      "Assistant memory compaction is not available on this device.",
    );
  }

  await store.delete({
    predicate: () => true,
  });

  const createdAt = nowIso();

  for (const [index, fact] of finalFacts.entries()) {
    const normalized = clamp(normalizeText(fact), 220);
    if (!normalized) {
      continue;
    }

    await store.add({
      id: buildCompactMemoryId(index),
      document: normalized,
      metadata: {
        category: "compacted_fact",
        createdAt,
        compactedAt: createdAt,
      },
    });
  }

  return {
    beforeCount: plan.beforeCount,
    afterCount: finalFacts.length,
  };
}
