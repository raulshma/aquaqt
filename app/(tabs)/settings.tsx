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

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
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
  const [diagnosticError, setDiagnosticError] = useState<string | null>(null);
  const [compatibilityError, setCompatibilityError] = useState<string | null>(
    null,
  );
  const [diagnosticAnswer, setDiagnosticAnswer] = useState("");
  const [compatibilityAnswer, setCompatibilityAnswer] = useState("");
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
  const [isModelSheetVisible, setModelSheetVisible] = useState(false);
  const [diagnosticAquariumId, setDiagnosticAquariumId] = useState("");
  const [diagnosticSymptoms, setDiagnosticSymptoms] = useState("");
  const [diagnosticWindowDays, setDiagnosticWindowDays] = useState("14");
  const [compatibilityAquariumId, setCompatibilityAquariumId] = useState("");
  const [compatibilitySpecies, setCompatibilitySpecies] = useState("");
  const [compatibilityKind, setCompatibilityKind] = useState("shrimp");
  const [compatibilityQuantity, setCompatibilityQuantity] = useState("1");
  const [compatibilityNotes, setCompatibilityNotes] = useState("");
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
    if (aquariums.length === 0) {
      setDiagnosticAquariumId("");
      setCompatibilityAquariumId("");
      return;
    }

    if (!aquariums.some((aq) => aq.id === diagnosticAquariumId)) {
      setDiagnosticAquariumId(aquariums[0].id);
    }

    if (!aquariums.some((aq) => aq.id === compatibilityAquariumId)) {
      setCompatibilityAquariumId(aquariums[0].id);
    }
  }, [aquariums, compatibilityAquariumId, diagnosticAquariumId]);

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

  const requestAssistantCompletion = useCallback(
    async (
      mode: "general" | "diagnostic" | "compatibility" | "task-suggestion",
      userQuestion: string,
    ) => {
      const response = await fetch(
        "https://openrouter.ai/api/v1/chat/completions",
        {
          method: "POST",
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
                content: `Assistant mode: ${mode}`,
              },
              {
                role: "system",
                content: MODE_PROMPTS[mode],
              },
              {
                role: "system",
                content: `App context: ${JSON.stringify(assistantContext)}`,
              },
              {
                role: "user",
                content: userQuestion,
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

      return data.choices?.[0]?.message?.content?.trim() ?? "No response.";
    },
    [apiKey, assistantContext, model, settings.aiModel],
  );

  const askAssistant = async () => {
    if (!apiKey.trim() || !question.trim()) {
      return;
    }

    const requestId = askRequestIdRef.current + 1;
    askRequestIdRef.current = requestId;

    setAsking(true);
    setAssistantError(null);

    try {
      const result = await requestAssistantCompletion(
        assistantMode,
        question.trim(),
      );

      if (!mountedRef.current || askRequestIdRef.current !== requestId) {
        return;
      }

      setAnswer(result);
    } catch (error) {
      if (!mountedRef.current || askRequestIdRef.current !== requestId) {
        return;
      }

      setAssistantError(
        error instanceof Error ? error.message : "Unknown error",
      );
    } finally {
      if (mountedRef.current && askRequestIdRef.current === requestId) {
        setAsking(false);
      }
    }
  };

  const runDiagnosticWorkflow = async () => {
    if (!apiKey.trim() || !diagnosticSymptoms.trim() || !diagnosticAquariumId) {
      return;
    }

    const aquarium = aquariums.find((aq) => aq.id === diagnosticAquariumId);
    const days = Number.parseInt(diagnosticWindowDays.trim(), 10);
    const windowDays = Number.isFinite(days) && days > 0 ? days : 14;

    setAsking(true);
    setDiagnosticError(null);
    setDiagnosticAnswer("");

    try {
      const result = await requestAssistantCompletion(
        "diagnostic",
        [
          `Perform a focused diagnostic review for aquarium "${aquarium?.name ?? "Unknown"}" over the last ${windowDays} days.`,
          `Observed symptoms: ${diagnosticSymptoms.trim()}`,
          "Please output:\n1) Most likely root causes ranked\n2) Immediate safe actions (today)\n3) Monitoring checklist for next 7 days\n4) Red flags that require urgent intervention",
        ].join("\n\n"),
      );

      if (!mountedRef.current) {
        return;
      }

      setDiagnosticAnswer(result);
    } catch (error) {
      setDiagnosticError(
        error instanceof Error ? error.message : "Diagnostic request failed",
      );
    } finally {
      if (mountedRef.current) {
        setAsking(false);
      }
    }
  };

  const runCompatibilityWorkflow = async () => {
    if (
      !apiKey.trim() ||
      !compatibilityAquariumId ||
      !compatibilitySpecies.trim()
    ) {
      return;
    }

    const aquarium = aquariums.find((aq) => aq.id === compatibilityAquariumId);
    const quantityValue = Number.parseInt(compatibilityQuantity.trim(), 10);
    const quantity =
      Number.isFinite(quantityValue) && quantityValue > 0 ? quantityValue : 1;

    setAsking(true);
    setCompatibilityError(null);
    setCompatibilityAnswer("");

    try {
      const result = await requestAssistantCompletion(
        "compatibility",
        [
          `Compatibility check for aquarium "${aquarium?.name ?? "Unknown"}" (${aquarium?.waterType ?? "unknown"}).`,
          `Candidate addition: ${quantity} x ${compatibilitySpecies.trim()} (${compatibilityKind}).`,
          compatibilityNotes.trim()
            ? `Extra notes: ${compatibilityNotes.trim()}`
            : "",
          "Please output:\n1) Compatibility verdict (Safe / Caution / Not recommended)\n2) Main conflict risks\n3) Parameter gaps to fix before adding\n4) Safer alternatives if needed",
        ]
          .filter(Boolean)
          .join("\n\n"),
      );

      if (!mountedRef.current) {
        return;
      }

      setCompatibilityAnswer(result);
    } catch (error) {
      setCompatibilityError(
        error instanceof Error ? error.message : "Compatibility request failed",
      );
    } finally {
      if (mountedRef.current) {
        setAsking(false);
      }
    }
  };

  return (
    <>
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
            <Button
              mode="outlined"
              onPress={() => setModelSheetVisible(true)}
              style={styles.modelPickerButton}
            >
              Select from OpenRouter models
            </Button>
            <Text variant="bodySmall" style={styles.helperText}>
              Browse models in a bottom sheet, or type a custom model ID.
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
            {isAsking ? (
              <ActivityIndicator style={styles.answerSpacing} />
            ) : null}
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

        <Card mode="outlined" style={styles.noteCard}>
          <Card.Title
            title="Diagnostic workflow"
            subtitle="Guided root-cause analysis for tank problems"
          />
          <Card.Content>
            <ScrollableSegmentedButtons
              value={diagnosticAquariumId}
              onValueChange={setDiagnosticAquariumId}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              }))}
              density="small"
            />
            <View style={styles.filterRow}>
              <TextInput
                mode="outlined"
                label="Review window (days)"
                value={diagnosticWindowDays}
                onChangeText={setDiagnosticWindowDays}
                keyboardType="number-pad"
                style={styles.filterInput}
              />
            </View>
            <TextInput
              mode="outlined"
              label="Symptoms observed"
              value={diagnosticSymptoms}
              onChangeText={setDiagnosticSymptoms}
              multiline
              numberOfLines={3}
              style={styles.modelInput}
            />
            <Button
              mode="contained-tonal"
              onPress={runDiagnosticWorkflow}
              disabled={
                isAsking || !diagnosticAquariumId || !diagnosticSymptoms.trim()
              }
              style={styles.askButton}
            >
              Run diagnostic analysis
            </Button>
            {diagnosticError ? (
              <Text variant="bodySmall" style={styles.errorText}>
                {diagnosticError}
              </Text>
            ) : null}
            {diagnosticAnswer ? (
              <Text variant="bodyMedium" style={styles.answerSpacing}>
                {diagnosticAnswer}
              </Text>
            ) : null}
          </Card.Content>
        </Card>

        <Card mode="outlined" style={styles.noteCard}>
          <Card.Title
            title="Compatibility workflow"
            subtitle="Evaluate additions against your tank context"
          />
          <Card.Content>
            <ScrollableSegmentedButtons
              value={compatibilityAquariumId}
              onValueChange={setCompatibilityAquariumId}
              buttons={aquariums.map((aq) => ({
                label: aq.name,
                value: aq.id,
              }))}
              density="small"
            />
            <View style={styles.filterRow}>
              <TextInput
                mode="outlined"
                label="Species"
                value={compatibilitySpecies}
                onChangeText={setCompatibilitySpecies}
                style={styles.filterInput}
              />
              <TextInput
                mode="outlined"
                label="Qty"
                value={compatibilityQuantity}
                onChangeText={setCompatibilityQuantity}
                keyboardType="number-pad"
                style={styles.quantityInput}
              />
            </View>
            <ScrollableSegmentedButtons
              value={compatibilityKind}
              onValueChange={setCompatibilityKind}
              buttons={[
                { label: "Fish", value: "fish" },
                { label: "Shrimp", value: "shrimp" },
                { label: "Snail", value: "snail" },
                { label: "Coral", value: "coral" },
                { label: "Plant", value: "plant" },
                { label: "Other", value: "other" },
              ]}
              style={styles.modelInput}
              density="small"
            />
            <TextInput
              mode="outlined"
              label="Notes (optional)"
              value={compatibilityNotes}
              onChangeText={setCompatibilityNotes}
              multiline
              numberOfLines={2}
              style={styles.modelInput}
            />
            <Button
              mode="contained-tonal"
              onPress={runCompatibilityWorkflow}
              disabled={
                isAsking ||
                !compatibilityAquariumId ||
                !compatibilitySpecies.trim()
              }
              style={styles.askButton}
            >
              Run compatibility check
            </Button>
            {compatibilityError ? (
              <Text variant="bodySmall" style={styles.errorText}>
                {compatibilityError}
              </Text>
            ) : null}
            {compatibilityAnswer ? (
              <Text variant="bodyMedium" style={styles.answerSpacing}>
                {compatibilityAnswer}
              </Text>
            ) : null}
          </Card.Content>
        </Card>
      </ScrollView>

      <BottomSheet
        visible={isModelSheetVisible}
        onDismiss={() => setModelSheetVisible(false)}
        title="OpenRouter models"
        actions={
          <>
            <Button onPress={() => setModelSheetVisible(false)}>Close</Button>
          </>
        }
      >
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
            onPress={() => {
              setModel(candidate.id);
              setModelSheetVisible(false);
            }}
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
            onPress={() => {
              setModel(candidate.id);
              setModelSheetVisible(false);
            }}
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
      </BottomSheet>
    </>
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
  modelPickerButton: {
    marginTop: 10,
    alignSelf: "flex-start",
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
  quantityInput: {
    width: 90,
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
