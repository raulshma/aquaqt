import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
  ActivityIndicator,
  Button,
  Card,
  Chip,
  Divider,
  Text,
  TextInput,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAquapt } from "@/context/aquapt-context";

const MODE_PROMPTS: Record<
  "general" | "diagnostic" | "compatibility" | "task-suggestion",
  string
> = {
  general:
    "Answer clearly and concisely. Provide practical aquarium-safe recommendations and include brief rationale.",
  diagnostic:
    "Prioritize diagnosis from trends. List likely causes ranked by confidence, then immediate safe actions, then monitoring checks for the next 7 days.",
  compatibility:
    "Evaluate species compatibility using current livestock, water parameters, and water type. Highlight conflicts and provide safer alternatives if needed.",
  "task-suggestion":
    "Suggest actionable maintenance/task adjustments based on open issues and recent logs. Provide a simple schedule with frequency and expected outcome.",
};

const QUESTION_PRESETS: {
  label: string;
  mode: "general" | "diagnostic" | "compatibility" | "task-suggestion";
  question: string;
}[] = [
  {
    label: "Shrimp issue",
    mode: "diagnostic",
    question:
      "Why are my shrimp struggling lately? Please analyze my recent trends and suggest next actions.",
  },
  {
    label: "Stocking check",
    mode: "compatibility",
    question:
      "Can I add Cherry Shrimp to my current tank safely? Explain compatibility and parameter constraints.",
  },
  {
    label: "Algae plan",
    mode: "task-suggestion",
    question:
      "I keep getting algae reports. What maintenance and dosing schedule should I follow for the next 2 weeks?",
  },
];

type OpenRouterModel = {
  id: string;
  name?: string;
  created?: number;
  context_length?: number;
  pricing?: Record<string, string | number | null | undefined>;
};

const parsePricingValue = (value: string | number | null | undefined) => {
  if (typeof value === "number") {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  return null;
};

const isFreeModel = (model: OpenRouterModel) => {
  if (model.id.includes(":free")) {
    return true;
  }

  const pricingValues = Object.values(model.pricing ?? {})
    .map(parsePricingValue)
    .filter((value): value is number => value !== null);

  return pricingValues.length > 0 && pricingValues.every((value) => value <= 0);
};

const formatCreatedDate = (created?: number) => {
  if (!created || !Number.isFinite(created)) {
    return "-";
  }

  return new Date(created * 1000).toLocaleDateString();
};

const formatPricingPreview = (pricing?: OpenRouterModel["pricing"]) => {
  if (!pricing) {
    return "p: - | c: -";
  }

  const prompt = parsePricingValue(pricing.prompt);
  const completion = parsePricingValue(pricing.completion);

  return `p: ${prompt ?? "-"} | c: ${completion ?? "-"}`;
};

export default function SettingsScreen() {
  const insets = useSafeAreaInsets();
  const {
    settings,
    aquariums,
    livestock,
    taskTemplates,
    taskExecutions,
    issues,
    parameterLogs,
    saveApiKey,
    saveAiModel,
  } = useAquapt();
  const [apiKey, setApiKey] = useState(settings.openRouterApiKey);
  const [model, setModel] = useState(settings.aiModel);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [assistantMode, setAssistantMode] = useState<
    "general" | "diagnostic" | "compatibility" | "task-suggestion"
  >("general");
  const [isAsking, setAsking] = useState(false);
  const [assistantError, setAssistantError] = useState<string | null>(null);
  const [models, setModels] = useState<OpenRouterModel[]>([]);
  const [isLoadingModels, setIsLoadingModels] = useState(false);
  const [modelsError, setModelsError] = useState<string | null>(null);
  const [modelQuery, setModelQuery] = useState("");
  const [createdAfter, setCreatedAfter] = useState("");
  const [minContext, setMinContext] = useState("");
  const [showPricing, setShowPricing] = useState<"all" | "free" | "paid">(
    "all",
  );
  const [sortBy, setSortBy] = useState<"name" | "created" | "context">("name");
  const askRequestIdRef = useRef(0);
  const mountedRef = useRef(true);

  const loadOpenRouterModels = useCallback(async () => {
    setIsLoadingModels(true);
    setModelsError(null);

    try {
      const response = await fetch("https://openrouter.ai/api/v1/models");
      if (!response.ok) {
        throw new Error(`Failed to load models (${response.status})`);
      }

      const data = (await response.json()) as {
        data?: OpenRouterModel[];
      };

      setModels(Array.isArray(data.data) ? data.data : []);
    } catch (error) {
      setModelsError(
        error instanceof Error ? error.message : "Failed to load models",
      );
    } finally {
      setIsLoadingModels(false);
    }
  }, []);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    setApiKey(settings.openRouterApiKey);
    setModel(settings.aiModel);
  }, [settings.aiModel, settings.openRouterApiKey]);

  useEffect(() => {
    void loadOpenRouterModels();
  }, [loadOpenRouterModels]);

  const aquariumSummary = useMemo(() => {
    return aquariums
      .map((aq) => {
        const latestParams = parameterLogs
          .filter((p) => p.aquariumId === aq.id)
          .sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))[0];
        const openIssues = issues.filter(
          (issue) => issue.aquariumId === aq.id && issue.status !== "resolved",
        );

        return {
          name: aq.name,
          waterType: aq.waterType,
          latestParams: latestParams?.values ?? null,
          openIssues: openIssues.map((issue) => issue.title),
        };
      })
      .slice(0, 8);
  }, [aquariums, issues, parameterLogs]);

  const assistantContext = useMemo(() => {
    return {
      aquariumSummary,
      livestock: livestock.slice(0, 40),
      recentParameterLogs: parameterLogs.slice(0, 60),
      openIssues: issues.filter((issue) => issue.status !== "resolved"),
      taskTemplates,
      recentTaskExecutions: taskExecutions.slice(0, 80),
    };
  }, [
    aquariumSummary,
    issues,
    livestock,
    parameterLogs,
    taskExecutions,
    taskTemplates,
  ]);

  const minContextValue = useMemo(() => {
    const parsed = Number.parseInt(minContext.trim(), 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [minContext]);

  const createdAfterEpoch = useMemo(() => {
    if (!createdAfter.trim()) {
      return null;
    }

    const parsed = Date.parse(createdAfter.trim());
    if (!Number.isFinite(parsed)) {
      return null;
    }

    return Math.floor(parsed / 1000);
  }, [createdAfter]);

  const filteredModels = useMemo(() => {
    const query = modelQuery.trim().toLowerCase();

    const sorted = models
      .filter((candidate) => {
        if (showPricing === "free" && !isFreeModel(candidate)) {
          return false;
        }
        if (showPricing === "paid" && isFreeModel(candidate)) {
          return false;
        }

        if (query) {
          const haystack =
            `${candidate.id} ${candidate.name ?? ""}`.toLowerCase();
          if (!haystack.includes(query)) {
            return false;
          }
        }

        if (createdAfterEpoch !== null) {
          const created = candidate.created ?? 0;
          if (created < createdAfterEpoch) {
            return false;
          }
        }

        if (minContextValue !== null) {
          const contextLength = candidate.context_length ?? 0;
          if (contextLength < minContextValue) {
            return false;
          }
        }

        return true;
      })
      .sort((a, b) => {
        if (sortBy === "name") {
          return (a.name ?? a.id).localeCompare(b.name ?? b.id);
        }

        if (sortBy === "created") {
          return (b.created ?? 0) - (a.created ?? 0);
        }

        return (b.context_length ?? 0) - (a.context_length ?? 0);
      });

    return sorted;
  }, [
    createdAfterEpoch,
    minContextValue,
    modelQuery,
    models,
    showPricing,
    sortBy,
  ]);

  const freeModels = useMemo(
    () => filteredModels.filter((candidate) => isFreeModel(candidate)),
    [filteredModels],
  );

  const paidModels = useMemo(
    () => filteredModels.filter((candidate) => !isFreeModel(candidate)),
    [filteredModels],
  );

  const handleSave = () => {
    saveApiKey(apiKey);
    saveAiModel(model);
    setSavedAt(new Date().toLocaleString());
  };

  const askAssistant = async () => {
    if (!apiKey.trim() || !question.trim()) {
      return;
    }

    const requestId = askRequestIdRef.current + 1;
    askRequestIdRef.current = requestId;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30_000);

    setAsking(true);
    setAssistantError(null);

    try {
      const response = await fetch(
        "https://openrouter.ai/api/v1/chat/completions",
        {
          method: "POST",
          signal: controller.signal,
          headers: {
            Authorization: `Bearer ${apiKey.trim()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model: model.trim() || settings.aiModel,
            messages: [
              {
                role: "system",
                content:
                  "You are Aquapt assistant. Give concise, practical aquarium advice based on provided context. If uncertain, say so. Prioritize actionable steps with safety-first guidance.",
              },
              {
                role: "system",
                content: `Assistant mode: ${assistantMode}`,
              },
              {
                role: "system",
                content: MODE_PROMPTS[assistantMode],
              },
              {
                role: "system",
                content: `App context: ${JSON.stringify(assistantContext)}`,
              },
              {
                role: "user",
                content: question.trim(),
              },
            ],
          }),
        },
      );

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "Assistant request failed.");
      }

      const data = (await response.json()) as {
        choices?: { message?: { content?: string } }[];
      };

      if (!mountedRef.current || askRequestIdRef.current !== requestId) {
        return;
      }

      setAnswer(data.choices?.[0]?.message?.content?.trim() ?? "No response.");
    } catch (error) {
      if (!mountedRef.current || askRequestIdRef.current !== requestId) {
        return;
      }

      if (error instanceof Error && error.name === "AbortError") {
        setAssistantError("Request timed out. Please try again.");
        return;
      }

      setAssistantError(
        error instanceof Error ? error.message : "Unknown error",
      );
    } finally {
      clearTimeout(timeoutId);
      if (mountedRef.current && askRequestIdRef.current === requestId) {
        setAsking(false);
      }
    }
  };

  return (
    <ScrollView
      contentContainerStyle={[
        styles.container,
        { paddingTop: 16 + insets.top },
      ]}
    >
      <Text variant="headlineMedium">Settings</Text>
      <Text variant="bodyMedium" style={styles.subtitle}>
        Configure your BYOK AI assistant and app preferences.
      </Text>

      <Card mode="contained">
        <Card.Title
          title="OpenRouter API Key (BYOK)"
          subtitle="Stored in local encrypted-ish app storage context"
        />
        <Card.Content>
          <TextInput
            mode="outlined"
            label="sk-or-v1-..."
            secureTextEntry
            autoCapitalize="none"
            autoCorrect={false}
            value={apiKey}
            onChangeText={setApiKey}
          />
          <TextInput
            mode="outlined"
            label="Assistant model"
            value={model}
            onChangeText={setModel}
            autoCapitalize="none"
            autoCorrect={false}
            style={styles.modelInput}
          />
          <Text variant="bodySmall" style={styles.helperText}>
            Pick a model from the OpenRouter list below, or type your own ID.
          </Text>
          <Button
            mode="contained"
            onPress={handleSave}
            style={styles.saveButton}
          >
            Save key
          </Button>
          {savedAt ? (
            <Text variant="bodySmall" style={styles.savedAt}>
              Saved: {savedAt}
            </Text>
          ) : null}
        </Card.Content>
      </Card>

      <Card mode="outlined">
        <Card.Title
          title="OpenRouter models"
          subtitle="Grouped by free and paid with filters"
        />
        <Card.Content>
          <View style={styles.modeRow}>
            <Button
              mode="contained-tonal"
              onPress={() => {
                void loadOpenRouterModels();
              }}
              disabled={isLoadingModels}
            >
              Refresh list
            </Button>
            {isLoadingModels ? <ActivityIndicator /> : null}
          </View>

          <TextInput
            mode="outlined"
            label="Filter by model name or ID"
            value={modelQuery}
            onChangeText={setModelQuery}
            autoCapitalize="none"
            autoCorrect={false}
          />
          <View style={styles.filterRow}>
            <TextInput
              mode="outlined"
              label="Created after (YYYY-MM-DD)"
              value={createdAfter}
              onChangeText={setCreatedAfter}
              autoCapitalize="none"
              autoCorrect={false}
              style={styles.filterInput}
            />
            <TextInput
              mode="outlined"
              label="Min context"
              value={minContext}
              onChangeText={setMinContext}
              keyboardType="number-pad"
              style={styles.filterInput}
            />
          </View>

          <View style={styles.modeRow}>
            {[
              { label: "All", value: "all" as const },
              { label: "Free", value: "free" as const },
              { label: "Paid", value: "paid" as const },
            ].map((option) => (
              <Chip
                key={option.value}
                selected={showPricing === option.value}
                onPress={() => setShowPricing(option.value)}
              >
                {option.label}
              </Chip>
            ))}
          </View>

          <View style={styles.modeRow}>
            {[
              { label: "Sort: Name", value: "name" as const },
              { label: "Sort: Created", value: "created" as const },
              { label: "Sort: Context", value: "context" as const },
            ].map((option) => (
              <Chip
                key={option.value}
                selected={sortBy === option.value}
                onPress={() => setSortBy(option.value)}
              >
                {option.label}
              </Chip>
            ))}
          </View>

          {modelsError ? (
            <Text variant="bodySmall" style={styles.errorText}>
              {modelsError}
            </Text>
          ) : null}

          <Text variant="bodySmall" style={styles.helperText}>
            Showing {filteredModels.length} filtered models
          </Text>

          <Divider style={styles.groupDivider} />
          <Text variant="titleMedium">Free models ({freeModels.length})</Text>
          {freeModels.slice(0, 40).map((candidate) => (
            <Button
              key={candidate.id}
              mode="text"
              onPress={() => setModel(candidate.id)}
              contentStyle={styles.modelRowContent}
              style={styles.modelRowButton}
            >
              {`${candidate.name ?? candidate.id} • ${candidate.context_length ?? "-"} ctx • ${formatCreatedDate(candidate.created)} • ${formatPricingPreview(candidate.pricing)}`}
            </Button>
          ))}
          {freeModels.length > 40 ? (
            <Text variant="bodySmall" style={styles.helperText}>
              +{freeModels.length - 40} more free models (refine filters)
            </Text>
          ) : null}

          <Divider style={styles.groupDivider} />
          <Text variant="titleMedium">Paid models ({paidModels.length})</Text>
          {paidModels.slice(0, 40).map((candidate) => (
            <Button
              key={candidate.id}
              mode="text"
              onPress={() => setModel(candidate.id)}
              contentStyle={styles.modelRowContent}
              style={styles.modelRowButton}
            >
              {`${candidate.name ?? candidate.id} • ${candidate.context_length ?? "-"} ctx • ${formatCreatedDate(candidate.created)} • ${formatPricingPreview(candidate.pricing)}`}
            </Button>
          ))}
          {paidModels.length > 40 ? (
            <Text variant="bodySmall" style={styles.helperText}>
              +{paidModels.length - 40} more paid models (refine filters)
            </Text>
          ) : null}
        </Card.Content>
      </Card>

      <Card mode="outlined" style={styles.noteCard}>
        <Card.Title
          title="Contextual AI Assistant"
          subtitle="Uses your BYOK and current app data context"
        />
        <Card.Content>
          <View style={styles.modeRow}>
            {[
              { label: "General", value: "general" },
              { label: "Diagnostic", value: "diagnostic" },
              { label: "Compatibility", value: "compatibility" },
              { label: "Task Suggest", value: "task-suggestion" },
            ].map((mode) => (
              <Chip
                key={mode.value}
                selected={assistantMode === mode.value}
                onPress={() =>
                  setAssistantMode(
                    mode.value as
                      | "general"
                      | "diagnostic"
                      | "compatibility"
                      | "task-suggestion",
                  )
                }
              >
                {mode.label}
              </Chip>
            ))}
          </View>
          <View style={styles.modeRow}>
            {QUESTION_PRESETS.map((preset) => (
              <Chip
                key={preset.label}
                onPress={() => {
                  setAssistantMode(preset.mode);
                  setQuestion(preset.question);
                }}
              >
                {preset.label}
              </Chip>
            ))}
          </View>
          <TextInput
            mode="outlined"
            label="Ask Aquapt AI"
            value={question}
            onChangeText={setQuestion}
            multiline
            numberOfLines={3}
          />
          <Button
            mode="contained-tonal"
            onPress={askAssistant}
            style={styles.askButton}
          >
            Ask assistant
          </Button>
          {isAsking ? <ActivityIndicator style={styles.answerSpacing} /> : null}
          {assistantError ? (
            <Text variant="bodySmall" style={styles.errorText}>
              {assistantError}
            </Text>
          ) : null}
          {answer ? (
            <Text variant="bodyMedium" style={styles.answerSpacing}>
              {answer}
            </Text>
          ) : null}
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 12,
  },
  subtitle: {
    opacity: 0.75,
    marginBottom: 6,
  },
  saveButton: {
    marginTop: 12,
    alignSelf: "flex-start",
  },
  modelInput: {
    marginTop: 10,
  },
  helperText: {
    marginTop: 8,
    opacity: 0.75,
  },
  filterRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 10,
  },
  filterInput: {
    flex: 1,
  },
  groupDivider: {
    marginTop: 12,
    marginBottom: 10,
  },
  modelRowButton: {
    alignSelf: "stretch",
    marginTop: 4,
  },
  modelRowContent: {
    justifyContent: "flex-start",
  },
  savedAt: {
    marginTop: 8,
    opacity: 0.75,
  },
  noteCard: {
    marginTop: 2,
  },
  askButton: {
    marginTop: 12,
    alignSelf: "flex-start",
  },
  modeRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 10,
  },
  answerSpacing: {
    marginTop: 12,
  },
  errorText: {
    marginTop: 12,
    color: "#b00020",
  },
});
