import { useForm } from "@tanstack/react-form";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, ScrollView, StyleSheet, View } from "react-native";
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
import {
  clearDailyReminderSchedule,
  ensureReminderPermissions,
  scheduleDailyReminder,
} from "@/services/notifications";
import { countDueTasks } from "@/services/scheduling";

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
    exportAppState,
    importAppStateFromJson,
    saveReminderSettings,
    saveApiKey,
    saveAiModel,
  } = useAquapt();
  const settingsForm = useForm({
    defaultValues: {
      apiKey: settings.openRouterApiKey,
      model: settings.aiModel,
    },
  });
  const assistantForm = useForm({
    defaultValues: {
      mode: "general" as
        | "general"
        | "diagnostic"
        | "compatibility"
        | "task-suggestion",
      question: "",
    },
  });
  const diagnosticForm = useForm({
    defaultValues: {
      aquariumId: "",
      symptoms: "",
      windowDays: "14",
    },
  });
  const compatibilityForm = useForm({
    defaultValues: {
      aquariumId: "",
      species: "",
      kind: "shrimp",
      quantity: "1",
      notes: "",
    },
  });
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [answer, setAnswer] = useState("");
  const [isAsking, setAsking] = useState(false);
  const [assistantError, setAssistantError] = useState<string | null>(null);
  const [diagnosticError, setDiagnosticError] = useState<string | null>(null);
  const [compatibilityError, setCompatibilityError] = useState<string | null>(
    null,
  );
  const [backupPayload, setBackupPayload] = useState("");
  const [backupStatus, setBackupStatus] = useState<string | null>(null);
  const [remindersEnabled, setRemindersEnabled] = useState(
    settings.notificationsEnabled ?? false,
  );
  const [reminderHour, setReminderHour] = useState(settings.reminderHour ?? 8);
  const [reminderStatus, setReminderStatus] = useState<string | null>(null);
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
    settingsForm.setFieldValue("apiKey", settings.openRouterApiKey);
    settingsForm.setFieldValue("model", settings.aiModel);
  }, [settings.aiModel, settings.openRouterApiKey, settingsForm]);

  useEffect(() => {
    setRemindersEnabled(settings.notificationsEnabled ?? false);
    setReminderHour(settings.reminderHour ?? 8);
  }, [settings.notificationsEnabled, settings.reminderHour]);

  useEffect(() => {
    const diagnosticValues = diagnosticForm.state.values;
    const compatibilityValues = compatibilityForm.state.values;

    if (aquariums.length === 0) {
      diagnosticForm.setFieldValue("aquariumId", "");
      compatibilityForm.setFieldValue("aquariumId", "");
      return;
    }

    if (!aquariums.some((aq) => aq.id === diagnosticValues.aquariumId)) {
      diagnosticForm.setFieldValue("aquariumId", aquariums[0].id);
    }

    if (!aquariums.some((aq) => aq.id === compatibilityValues.aquariumId)) {
      compatibilityForm.setFieldValue("aquariumId", aquariums[0].id);
    }
  }, [aquariums, compatibilityForm, diagnosticForm]);

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
    const values = settingsForm.state.values;
    saveApiKey(values.apiKey);
    saveAiModel(values.model);
    setSavedAt(new Date().toLocaleString());
  };

  const generateBackupPayload = () => {
    const payload = exportAppState();
    setBackupPayload(payload);
    setBackupStatus(
      `Backup snapshot generated (${new Date().toLocaleString()}).`,
    );
  };

  const restoreFromBackupPayload = () => {
    const result = importAppStateFromJson(backupPayload);
    setBackupStatus(result.message);

    if (!result.ok) {
      return;
    }

    setSavedAt(new Date().toLocaleString());
    Alert.alert("Backup imported", result.message);
  };

  const saveReminderPreferences = async () => {
    const normalizedHour = Math.min(23, Math.max(0, reminderHour));

    if (remindersEnabled) {
      const granted = await ensureReminderPermissions();
      if (!granted) {
        setReminderStatus(
          "Notifications permission was not granted. Enable permission in system settings.",
        );
        return;
      }

      const dueCount = countDueTasks(taskTemplates, taskExecutions, new Date());
      await scheduleDailyReminder(normalizedHour, dueCount);
      setReminderStatus(
        `Daily reminder scheduled for ${String(normalizedHour).padStart(2, "0")}:00.`,
      );
    } else {
      await clearDailyReminderSchedule();
      setReminderStatus("Daily reminders disabled.");
    }

    saveReminderSettings({
      notificationsEnabled: remindersEnabled,
      reminderHour: normalizedHour,
    });
  };

  const requestAssistantCompletion = useCallback(
    async (
      mode: "general" | "diagnostic" | "compatibility" | "task-suggestion",
      userQuestion: string,
    ) => {
      const values = settingsForm.state.values;

      const response = await fetch(
        "https://openrouter.ai/api/v1/chat/completions",
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${values.apiKey.trim()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model: values.model.trim() || settings.aiModel,
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
    [assistantContext, settings.aiModel, settingsForm],
  );

  const askAssistant = async () => {
    const settingsValues = settingsForm.state.values;
    const assistantValues = assistantForm.state.values;

    if (!settingsValues.apiKey.trim() || !assistantValues.question.trim()) {
      return;
    }

    const requestId = askRequestIdRef.current + 1;
    askRequestIdRef.current = requestId;

    setAsking(true);
    setAssistantError(null);

    try {
      const result = await requestAssistantCompletion(
        assistantValues.mode,
        assistantValues.question.trim(),
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
    const settingsValues = settingsForm.state.values;
    const diagnosticValues = diagnosticForm.state.values;

    if (
      !settingsValues.apiKey.trim() ||
      !diagnosticValues.symptoms.trim() ||
      !diagnosticValues.aquariumId
    ) {
      return;
    }

    const aquarium = aquariums.find(
      (aq) => aq.id === diagnosticValues.aquariumId,
    );
    const days = Number.parseInt(diagnosticValues.windowDays.trim(), 10);
    const windowDays = Number.isFinite(days) && days > 0 ? days : 14;

    setAsking(true);
    setDiagnosticError(null);
    setDiagnosticAnswer("");

    try {
      const result = await requestAssistantCompletion(
        "diagnostic",
        [
          `Perform a focused diagnostic review for aquarium "${aquarium?.name ?? "Unknown"}" over the last ${windowDays} days.`,
          `Observed symptoms: ${diagnosticValues.symptoms.trim()}`,
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
    const settingsValues = settingsForm.state.values;
    const compatibilityValues = compatibilityForm.state.values;

    if (
      !settingsValues.apiKey.trim() ||
      !compatibilityValues.aquariumId ||
      !compatibilityValues.species.trim()
    ) {
      return;
    }

    const aquarium = aquariums.find(
      (aq) => aq.id === compatibilityValues.aquariumId,
    );
    const quantityValue = Number.parseInt(
      compatibilityValues.quantity.trim(),
      10,
    );
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
          `Candidate addition: ${quantity} x ${compatibilityValues.species.trim()} (${compatibilityValues.kind}).`,
          compatibilityValues.notes.trim()
            ? `Extra notes: ${compatibilityValues.notes.trim()}`
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
            <settingsForm.Field name="apiKey">
              {(field) => (
                <TextInput
                  mode="outlined"
                  label="sk-or-v1-..."
                  secureTextEntry
                  autoCapitalize="none"
                  autoCorrect={false}
                  value={field.state.value}
                  onChangeText={field.handleChange}
                />
              )}
            </settingsForm.Field>
            <settingsForm.Field name="model">
              {(field) => (
                <TextInput
                  mode="outlined"
                  label="Assistant model"
                  value={field.state.value}
                  onChangeText={field.handleChange}
                  autoCapitalize="none"
                  autoCorrect={false}
                  style={styles.modelInput}
                />
              )}
            </settingsForm.Field>
            <View style={styles.modelPickerRow}>
              <Button
                mode="contained-tonal"
                onPress={() => setModelSheetVisible(true)}
              >
                Browse OpenRouter models
              </Button>
            </View>
            <Text variant="bodySmall" style={styles.helperText}>
              Pick a model from the OpenRouter list in the bottom sheet, or type
              your own ID.
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
            title="Task reminders"
            subtitle="Daily notification with deep link to due tasks"
          />
          <Card.Content>
            <View style={styles.modeRow}>
              <Chip
                selected={remindersEnabled}
                onPress={() => setRemindersEnabled(true)}
              >
                Enabled
              </Chip>
              <Chip
                selected={!remindersEnabled}
                onPress={() => setRemindersEnabled(false)}
              >
                Disabled
              </Chip>
            </View>

            <ScrollableSegmentedButtons
              value={String(reminderHour)}
              onValueChange={(value) => setReminderHour(Number(value))}
              buttons={[6, 7, 8, 9, 10, 12, 14, 18, 20, 22].map((hour) => ({
                label: `${String(hour).padStart(2, "0")}:00`,
                value: String(hour),
              }))}
            />

            <Button
              mode="contained-tonal"
              onPress={() => {
                void saveReminderPreferences();
              }}
              style={styles.saveButton}
            >
              Save reminder settings
            </Button>

            <Text variant="bodySmall" style={styles.helperText}>
              Current due tasks snapshot:{" "}
              {countDueTasks(taskTemplates, taskExecutions)}
            </Text>

            {reminderStatus ? (
              <Text variant="bodySmall" style={styles.savedAt}>
                {reminderStatus}
              </Text>
            ) : null}
          </Card.Content>
        </Card>

        <Card mode="outlined" style={styles.noteCard}>
          <Card.Title
            title="Data backup & restore"
            subtitle="Export full app JSON snapshot, or import one to restore"
          />
          <Card.Content>
            <View style={styles.modeRow}>
              <Button mode="contained-tonal" onPress={generateBackupPayload}>
                Generate backup JSON
              </Button>
              <Button mode="outlined" onPress={() => setBackupPayload("")}>
                Clear payload
              </Button>
            </View>

            <TextInput
              mode="outlined"
              label="Backup payload (JSON)"
              value={backupPayload}
              onChangeText={setBackupPayload}
              multiline
              numberOfLines={10}
            />

            <Button
              mode="contained"
              onPress={restoreFromBackupPayload}
              disabled={!backupPayload.trim()}
              style={styles.saveButton}
            >
              Import and restore
            </Button>

            <Text variant="bodySmall" style={styles.helperText}>
              Tip: keep this JSON in a secure notes app or cloud drive as your
              manual backup.
            </Text>

            {backupStatus ? (
              <Text variant="bodySmall" style={styles.savedAt}>
                {backupStatus}
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
            <assistantForm.Subscribe selector={(state) => state.values.mode}>
              {(assistantMode) => (
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
                        assistantForm.setFieldValue(
                          "mode",
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
              )}
            </assistantForm.Subscribe>
            <View style={styles.modeRow}>
              {QUESTION_PRESETS.map((preset) => (
                <Chip
                  key={preset.label}
                  onPress={() => {
                    assistantForm.setFieldValue("mode", preset.mode);
                    assistantForm.setFieldValue("question", preset.question);
                  }}
                >
                  {preset.label}
                </Chip>
              ))}
            </View>
            <assistantForm.Field name="question">
              {(field) => (
                <TextInput
                  mode="outlined"
                  label="Ask Aquapt AI"
                  value={field.state.value}
                  onChangeText={field.handleChange}
                  multiline
                  numberOfLines={3}
                />
              )}
            </assistantForm.Field>
            <assistantForm.Subscribe
              selector={(state) => state.values.question}
            >
              {(question) => (
                <Button
                  mode="contained-tonal"
                  onPress={askAssistant}
                  disabled={
                    isAsking ||
                    !settingsForm.state.values.apiKey.trim() ||
                    !question.trim()
                  }
                  style={styles.askButton}
                >
                  Ask assistant
                </Button>
              )}
            </assistantForm.Subscribe>
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
            <diagnosticForm.Field name="aquariumId">
              {(field) => (
                <ScrollableSegmentedButtons
                  value={field.state.value}
                  onValueChange={field.handleChange}
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                  density="small"
                />
              )}
            </diagnosticForm.Field>
            <View style={styles.filterRow}>
              <diagnosticForm.Field name="windowDays">
                {(field) => (
                  <TextInput
                    mode="outlined"
                    label="Review window (days)"
                    value={field.state.value}
                    onChangeText={field.handleChange}
                    keyboardType="number-pad"
                    style={styles.filterInput}
                  />
                )}
              </diagnosticForm.Field>
            </View>
            <diagnosticForm.Field name="symptoms">
              {(field) => (
                <TextInput
                  mode="outlined"
                  label="Symptoms observed"
                  value={field.state.value}
                  onChangeText={field.handleChange}
                  multiline
                  numberOfLines={3}
                  style={styles.modelInput}
                />
              )}
            </diagnosticForm.Field>
            <diagnosticForm.Subscribe
              selector={(state) => ({
                aquariumId: state.values.aquariumId,
                symptoms: state.values.symptoms,
              })}
            >
              {(values) => (
                <Button
                  mode="contained-tonal"
                  onPress={runDiagnosticWorkflow}
                  disabled={
                    isAsking || !values.aquariumId || !values.symptoms.trim()
                  }
                  style={styles.askButton}
                >
                  Run diagnostic analysis
                </Button>
              )}
            </diagnosticForm.Subscribe>
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
            <compatibilityForm.Field name="aquariumId">
              {(field) => (
                <ScrollableSegmentedButtons
                  value={field.state.value}
                  onValueChange={field.handleChange}
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                  density="small"
                />
              )}
            </compatibilityForm.Field>
            <View style={styles.filterRow}>
              <compatibilityForm.Field name="species">
                {(field) => (
                  <TextInput
                    mode="outlined"
                    label="Species"
                    value={field.state.value}
                    onChangeText={field.handleChange}
                    style={styles.filterInput}
                  />
                )}
              </compatibilityForm.Field>
              <compatibilityForm.Field name="quantity">
                {(field) => (
                  <TextInput
                    mode="outlined"
                    label="Qty"
                    value={field.state.value}
                    onChangeText={field.handleChange}
                    keyboardType="number-pad"
                    style={styles.quantityInput}
                  />
                )}
              </compatibilityForm.Field>
            </View>
            <compatibilityForm.Field name="kind">
              {(field) => (
                <ScrollableSegmentedButtons
                  value={field.state.value}
                  onValueChange={field.handleChange}
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
              )}
            </compatibilityForm.Field>
            <compatibilityForm.Field name="notes">
              {(field) => (
                <TextInput
                  mode="outlined"
                  label="Notes (optional)"
                  value={field.state.value}
                  onChangeText={field.handleChange}
                  multiline
                  numberOfLines={2}
                  style={styles.modelInput}
                />
              )}
            </compatibilityForm.Field>
            <compatibilityForm.Subscribe
              selector={(state) => ({
                aquariumId: state.values.aquariumId,
                species: state.values.species,
              })}
            >
              {(values) => (
                <Button
                  mode="contained-tonal"
                  onPress={runCompatibilityWorkflow}
                  disabled={
                    isAsking || !values.aquariumId || !values.species.trim()
                  }
                  style={styles.askButton}
                >
                  Run compatibility check
                </Button>
              )}
            </compatibilityForm.Subscribe>
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
        title="Select OpenRouter model"
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
              settingsForm.setFieldValue("model", candidate.id);
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
              settingsForm.setFieldValue("model", candidate.id);
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
    paddingBottom: 132,
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
  modelPickerRow: {
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
    borderRadius: 24,
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
