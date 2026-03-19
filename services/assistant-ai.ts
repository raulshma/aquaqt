type OpenRouterRole = "system" | "user" | "assistant";

export interface OpenRouterMessage {
  role: OpenRouterRole;
  content: string;
}

export interface RequestOpenRouterOptions {
  apiKey: string;
  model: string;
  messages: OpenRouterMessage[];
  temperature?: number;
}

export interface OpenRouterResponseUsage {
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  cost?: number;
}

interface OpenRouterResponse {
  choices?: {
    finish_reason?: string | null;
    native_finish_reason?: string | null;
    message?: {
      content?: string;
    };
  }[];
  id?: string;
  model?: string;
  usage?: OpenRouterResponseUsage;
}

interface OpenRouterStreamChunk {
  id?: string;
  model?: string;
  provider?: string;
  choices?: {
    finish_reason?: string | null;
    native_finish_reason?: string | null;
    delta?: {
      content?: string | null;
    };
  }[];
  usage?: OpenRouterResponseUsage;
  error?: {
    message?: string;
  };
}

interface OpenRouterGenerationData {
  id?: string;
  provider_name?: string;
  latency?: number;
  generation_time?: number;
  total_cost?: number;
  router?: string;
}

interface OpenRouterGenerationResponse {
  data?: OpenRouterGenerationData;
}

export interface OpenRouterStreamSnapshot {
  text: string;
  elapsedMs: number;
  charsPerSecond: number;
  generationId?: string;
  model?: string;
  provider?: string;
}

export interface RequestOpenRouterStreamingOptions extends RequestOpenRouterOptions {
  onDelta?: (snapshot: OpenRouterStreamSnapshot) => void;
  signal?: AbortSignal;
}

export interface OpenRouterStreamResult {
  text: string;
  generationId?: string;
  model?: string;
  provider?: string;
  usage?: OpenRouterResponseUsage;
  finishReason?: string | null;
  nativeFinishReason?: string | null;
  elapsedMs: number;
  throughputCharsPerSecond: number;
  throughputTokensPerSecond?: number;
  generation?: OpenRouterGenerationData;
}

const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const OPENROUTER_GENERATION_URL = "https://openrouter.ai/api/v1/generation";

const ensureApiKey = (apiKey: string) => {
  const trimmedApiKey = apiKey.trim();
  if (!trimmedApiKey) {
    throw new Error("Missing OpenRouter API key. Add it in Settings.");
  }
  return trimmedApiKey;
};

const ensureModel = (model: string) => {
  const selectedModel = model.trim();
  if (!selectedModel) {
    throw new Error("Missing model ID. Select a model in Settings.");
  }
  return selectedModel;
};

const toSeconds = (ms: number) => ms / 1000;

const getCharsPerSecond = (charCount: number, elapsedMs: number) => {
  const seconds = toSeconds(elapsedMs);
  return seconds > 0 ? charCount / seconds : 0;
};

const getBaseHeaders = (apiKey: string) => ({
  Authorization: `Bearer ${apiKey}`,
  "Content-Type": "application/json",
});

const parseSseEventData = (rawEvent: string): string | null => {
  const lines = rawEvent.split(/\r?\n/);
  const dataLines: string[] = [];

  for (const line of lines) {
    if (!line) continue;
    if (line.startsWith(":")) {
      // SSE keepalive comment (e.g. ": OPENROUTER PROCESSING")
      continue;
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  return dataLines.join("\n");
};

const splitSseEvents = (rawText: string): string[] =>
  rawText
    .split(/\r?\n\r?\n/)
    .map((event) => event.trim())
    .filter((event) => event.length > 0);

const findSseSeparator = (buffer: string) => {
  const match = /\r?\n\r?\n/.exec(buffer);
  if (!match) {
    return null;
  }

  return {
    index: match.index,
    length: match[0].length,
  };
};

async function fetchGenerationMetadata(
  apiKey: string,
  generationId: string,
): Promise<OpenRouterGenerationData | undefined> {
  const url = `${OPENROUTER_GENERATION_URL}?id=${encodeURIComponent(generationId)}`;
  const response = await fetch(url, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${apiKey}`,
    },
  });

  if (!response.ok) {
    return undefined;
  }

  const data = (await response.json()) as OpenRouterGenerationResponse;
  return data.data;
}

export async function requestOpenRouterCompletion({
  apiKey,
  model,
  messages,
  temperature = 0.2,
}: RequestOpenRouterOptions) {
  const trimmedApiKey = ensureApiKey(apiKey);
  const selectedModel = ensureModel(model);

  const response = await fetch(OPENROUTER_CHAT_URL, {
    method: "POST",
    headers: getBaseHeaders(trimmedApiKey),
    body: JSON.stringify({
      model: selectedModel,
      temperature,
      messages,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
      errorText || `Assistant request failed (${response.status}).`,
    );
  }

  const data = (await response.json()) as OpenRouterResponse;
  return data.choices?.[0]?.message?.content?.trim() ?? "";
}

export async function requestOpenRouterStreamingCompletion({
  apiKey,
  model,
  messages,
  temperature = 0.2,
  onDelta,
  signal,
}: RequestOpenRouterStreamingOptions): Promise<OpenRouterStreamResult> {
  const trimmedApiKey = ensureApiKey(apiKey);
  const selectedModel = ensureModel(model);

  const startedAt = Date.now();
  const response = await fetch(OPENROUTER_CHAT_URL, {
    method: "POST",
    headers: getBaseHeaders(trimmedApiKey),
    body: JSON.stringify({
      model: selectedModel,
      temperature,
      messages,
      stream: true,
    }),
    signal,
  });

  let text = "";
  let generationId: string | undefined;
  let streamedModel: string | undefined;
  let provider: string | undefined;
  let usage: OpenRouterResponseUsage | undefined;
  let finishReason: string | null | undefined;
  let nativeFinishReason: string | null | undefined;

  const emitSnapshot = () => {
    if (!onDelta) return;
    const elapsedMs = Date.now() - startedAt;
    onDelta({
      text,
      elapsedMs,
      charsPerSecond: getCharsPerSecond(text.length, elapsedMs),
      generationId,
      model: streamedModel ?? selectedModel,
      provider,
    });
  };

  const processEvent = (rawEvent: string) => {
    const payload = parseSseEventData(rawEvent);
    if (!payload || payload === "[DONE]") {
      return;
    }

    const chunk = JSON.parse(payload) as OpenRouterStreamChunk;

    if (chunk.error?.message) {
      throw new Error(chunk.error.message);
    }

    generationId = chunk.id ?? generationId;
    streamedModel = chunk.model ?? streamedModel;
    provider = chunk.provider ?? provider;

    const choice = chunk.choices?.[0];
    const delta = choice?.delta?.content;
    if (typeof delta === "string" && delta.length > 0) {
      text += delta;
      emitSnapshot();
    }

    if (choice?.finish_reason !== undefined) {
      finishReason = choice.finish_reason;
    }

    if (choice?.native_finish_reason !== undefined) {
      nativeFinishReason = choice.native_finish_reason;
    }

    if (chunk.usage) {
      usage = chunk.usage;
    }
  };

  const finalizeResult = async (): Promise<OpenRouterStreamResult> => {
    const elapsedMs = Date.now() - startedAt;
    const throughputCharsPerSecond = getCharsPerSecond(text.length, elapsedMs);
    const throughputTokensPerSecond =
      usage && usage.completion_tokens > 0 && elapsedMs > 0
        ? usage.completion_tokens / toSeconds(elapsedMs)
        : undefined;

    const generation = generationId
      ? await fetchGenerationMetadata(trimmedApiKey, generationId)
      : undefined;

    return {
      text: text.trim(),
      generationId,
      model: streamedModel ?? selectedModel,
      provider,
      usage,
      finishReason,
      nativeFinishReason,
      elapsedMs,
      throughputCharsPerSecond,
      throughputTokensPerSecond,
      generation,
    };
  };

  const processBufferedResponse = async (): Promise<OpenRouterStreamResult> => {
    const bufferedText = await response.text();
    if (!bufferedText.trim()) {
      throw new Error("Assistant returned an empty response.");
    }

    const events = splitSseEvents(bufferedText);
    if (events.length > 0) {
      for (const rawEvent of events) {
        processEvent(rawEvent);
      }
      return finalizeResult();
    }

    const nonStreamingResponse = JSON.parse(bufferedText) as OpenRouterResponse;
    const fallbackText =
      nonStreamingResponse.choices?.[0]?.message?.content?.trim() ?? "";

    if (!fallbackText) {
      throw new Error("Assistant returned an empty response.");
    }

    text = fallbackText;
    usage = nonStreamingResponse.usage;
    streamedModel = nonStreamingResponse.model;
    generationId = nonStreamingResponse.id;
    emitSnapshot();

    return finalizeResult();
  };

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
      errorText || `Assistant request failed (${response.status}).`,
    );
  }

  if (!response.body) {
    return processBufferedResponse();
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });

      while (true) {
        const separator = findSseSeparator(buffer);
        if (!separator) {
          break;
        }

        const rawEvent = buffer.slice(0, separator.index);
        buffer = buffer.slice(separator.index + separator.length);
        processEvent(rawEvent);
      }
    }

    buffer += decoder.decode();
    const trailing = buffer.trim();
    if (trailing) {
      processEvent(trailing);
    }
  } finally {
    reader.releaseLock();
  }

  return finalizeResult();
}
