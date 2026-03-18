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

interface OpenRouterResponse {
  choices?: {
    message?: {
      content?: string;
    };
  }[];
}

export async function requestOpenRouterCompletion({
  apiKey,
  model,
  messages,
  temperature = 0.2,
}: RequestOpenRouterOptions) {
  const trimmedApiKey = apiKey.trim();
  if (!trimmedApiKey) {
    throw new Error("Missing OpenRouter API key. Add it in Settings.");
  }

  const selectedModel = model.trim();
  if (!selectedModel) {
    throw new Error("Missing model ID. Select a model in Settings.");
  }

  const response = await fetch(
    "https://openrouter.ai/api/v1/chat/completions",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${trimmedApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: selectedModel,
        temperature,
        messages,
      }),
    },
  );

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
      errorText || `Assistant request failed (${response.status}).`,
    );
  }

  const data = (await response.json()) as OpenRouterResponse;
  return data.choices?.[0]?.message?.content?.trim() ?? "";
}
