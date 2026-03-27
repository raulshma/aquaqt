import { useCallback, useMemo, useState } from "react";
import { View } from "react-native";
import {
    ActivityIndicator,
    Button,
    Chip,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";

import {
    DashboardHero,
    DashboardScrollView,
    DashboardSection,
} from "@/components/ui/dashboard-shell";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { requestOpenRouterCompletion } from "@/services/assistant-ai";
import { LivestockKind } from "@/types/aquapt";
import {
    ASSISTANT_MODE_PROMPTS,
    ASSISTANT_QUESTION_PRESETS,
    ASSISTANT_SYSTEM_PROMPT,
    AssistantMode,
} from "@/services/assistant-prompts";

export default function WorkflowSettingsScreen() {
  const theme = useTheme();
  const {
    settings,
    aquariums,
    livestock,
    issues,
    parameterLogs,
    taskTemplates,
    taskExecutions,
  } = useAquapt();
  const [mode, setMode] = useState<AssistantMode>("general");
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [isAsking, setAsking] = useState(false);
  const [assistantError, setAssistantError] = useState<string | null>(null);
  const [diagnosticAnswer, setDiagnosticAnswer] = useState("");
  const [diagnosticError, setDiagnosticError] = useState<string | null>(null);
  const [compatibilityAnswer, setCompatibilityAnswer] = useState("");
  const [compatibilityError, setCompatibilityError] = useState<string | null>(
    null,
  );
  const [diagnosticAquariumId, setDiagnosticAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [diagnosticWindowDays, setDiagnosticWindowDays] = useState("14");
  const [diagnosticSymptoms, setDiagnosticSymptoms] = useState("");
  const [compatibilityAquariumId, setCompatibilityAquariumId] = useState(
    aquariums[0]?.id ?? "",
  );
  const [compatibilitySpecies, setCompatibilitySpecies] = useState("");
  const [compatibilityKind, setCompatibilityKind] = useState<LivestockKind>("shrimp");
  const [compatibilityQuantity, setCompatibilityQuantity] = useState("1");
  const [compatibilityNotes, setCompatibilityNotes] = useState("");

  const assistantContext = useMemo(
    () => ({
      aquariumSummary: aquariums.slice(0, 8).map((aq) => ({
        name: aq.name,
        waterType: aq.waterType,
        latestParams:
          parameterLogs.filter((p) => p.aquariumId === aq.id)[0]?.values ??
          null,
        openIssues: issues
          .filter(
            (issue) =>
              issue.aquariumId === aq.id && issue.status !== "resolved",
          )
          .map((issue) => issue.title),
      })),
      userLocale: {
        locale: settings.defaultLocale,
        timezone: settings.defaultTimezone,
        country: settings.defaultCountryName,
        currency: settings.defaultCurrency,
      },
      livestock: livestock.slice(0, 40),
      recentParameterLogs: parameterLogs.slice(0, 60),
      openIssues: issues.filter((issue) => issue.status !== "resolved"),
      taskTemplates,
      recentTaskExecutions: taskExecutions.slice(0, 80),
    }),
    [
      aquariums,
      issues,
      livestock,
      parameterLogs,
      settings.defaultCountryName,
      settings.defaultCurrency,
      settings.defaultLocale,
      settings.defaultTimezone,
      taskExecutions,
      taskTemplates,
    ],
  );

  const requestAssistantCompletion = useCallback(
    async (workflowMode: AssistantMode, userQuestion: string) => {
      const result = await requestOpenRouterCompletion({
        apiKey: settings.openRouterApiKey,
        model: settings.aiModel,
        messages: [
          { role: "system", content: ASSISTANT_SYSTEM_PROMPT },
          { role: "system", content: `Assistant mode: ${workflowMode}` },
          { role: "system", content: ASSISTANT_MODE_PROMPTS[workflowMode] },
          {
            role: "system",
            content: `App context: ${JSON.stringify(assistantContext)}`,
          },
          { role: "user", content: userQuestion },
        ],
      });

      return result || "No response.";
    },
    [assistantContext, settings.aiModel, settings.openRouterApiKey],
  );

  const askAssistant = async () => {
    if (!settings.openRouterApiKey.trim() || !question.trim()) {
      return;
    }

    setAsking(true);
    setAssistantError(null);
    try {
      setAnswer(await requestAssistantCompletion(mode, question.trim()));
    } catch (error) {
      setAssistantError(
        error instanceof Error ? error.message : "Unknown error",
      );
    } finally {
      setAsking(false);
    }
  };

  const runDiagnosticWorkflow = async () => {
    if (
      !settings.openRouterApiKey.trim() ||
      !diagnosticAquariumId ||
      !diagnosticSymptoms.trim()
    ) {
      return;
    }

    const aquarium = aquariums.find((aq) => aq.id === diagnosticAquariumId);
    const days = Number.parseInt(diagnosticWindowDays.trim(), 10);
    const windowDays = Number.isFinite(days) && days > 0 ? days : 14;

    setAsking(true);
    setDiagnosticError(null);
    setDiagnosticAnswer("");
    try {
      setDiagnosticAnswer(
        await requestAssistantCompletion(
          "diagnostic",
          [
            `Perform a focused diagnostic review for aquarium "${aquarium?.name ?? "Unknown"}" over the last ${windowDays} days.`,
            `Observed symptoms: ${diagnosticSymptoms.trim()}`,
            "Please output:\n1) Most likely root causes ranked\n2) Immediate safe actions (today)\n3) Monitoring checklist for next 7 days\n4) Red flags that require urgent intervention",
          ].join("\n\n"),
        ),
      );
    } catch (error) {
      setDiagnosticError(
        error instanceof Error ? error.message : "Diagnostic request failed",
      );
    } finally {
      setAsking(false);
    }
  };

  const runCompatibilityWorkflow = async () => {
    if (
      !settings.openRouterApiKey.trim() ||
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
      setCompatibilityAnswer(
        await requestAssistantCompletion(
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
        ),
      );
    } catch (error) {
      setCompatibilityError(
        error instanceof Error ? error.message : "Compatibility request failed",
      );
    } finally {
      setAsking(false);
    }
  };

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="AI workflows"
        subtitle="Contextual assistant, diagnostics, and compatibility checks."
        tone="primary"
        chips={
          <Chip compact icon="robot">
            {settings.aiModel || "No model selected"}
          </Chip>
        }
      />

      <DashboardSection
        title="Ask Aquapt AI"
        description="Uses your OpenRouter key and current app context."
      >
        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          {(
            [
              { label: "General", value: "general" },
              { label: "Diagnostic", value: "diagnostic" },
              { label: "Compatibility", value: "compatibility" },
              { label: "Task Suggest", value: "task-suggestion" },
            ] as const
          ).map((item) => (
            <Chip
              key={item.value}
              selected={mode === item.value}
              onPress={() => setMode(item.value)}
            >
              {item.label}
            </Chip>
          ))}
        </View>
        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          {ASSISTANT_QUESTION_PRESETS.map((preset) => (
            <Chip
              key={preset.label}
              onPress={() => {
                setMode(preset.mode);
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
          style={{ marginTop: 12 }}
        />
        <Button
          mode="contained-tonal"
          onPress={() => {
            void askAssistant();
          }}
          disabled={
            isAsking || !settings.openRouterApiKey.trim() || !question.trim()
          }
          style={{ marginTop: 12, alignSelf: "flex-start" }}
        >
          Ask assistant
        </Button>
        {isAsking ? <ActivityIndicator style={{ marginTop: 12 }} /> : null}
        {assistantError ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.error, marginTop: 8 }}
          >
            {assistantError}
          </Text>
        ) : null}
        {answer ? (
          <Text variant="bodyMedium" style={{ marginTop: 12 }}>
            {answer}
          </Text>
        ) : null}
      </DashboardSection>

      <DashboardSection
        title="Diagnostic workflow"
        description="Guided root-cause analysis based on your aquarium context."
      >
        <ScrollableSegmentedButtons
          value={diagnosticAquariumId}
          onValueChange={setDiagnosticAquariumId}
          buttons={aquariums.map((aq) => ({ label: aq.name, value: aq.id }))}
          density="small"
        />
        <TextInput
          mode="outlined"
          label="Review window (days)"
          value={diagnosticWindowDays}
          onChangeText={setDiagnosticWindowDays}
          keyboardType="number-pad"
          style={{ marginTop: 12 }}
        />
        <TextInput
          mode="outlined"
          label="Symptoms observed"
          value={diagnosticSymptoms}
          onChangeText={setDiagnosticSymptoms}
          multiline
          numberOfLines={3}
          style={{ marginTop: 10 }}
        />
        <Button
          mode="contained-tonal"
          onPress={() => {
            void runDiagnosticWorkflow();
          }}
          disabled={
            isAsking || !diagnosticAquariumId || !diagnosticSymptoms.trim()
          }
          style={{ marginTop: 12, alignSelf: "flex-start" }}
        >
          Run diagnostic analysis
        </Button>
        {diagnosticError ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.error, marginTop: 8 }}
          >
            {diagnosticError}
          </Text>
        ) : null}
        {diagnosticAnswer ? (
          <Text variant="bodyMedium" style={{ marginTop: 12 }}>
            {diagnosticAnswer}
          </Text>
        ) : null}
      </DashboardSection>

      <DashboardSection
        title="Compatibility workflow"
        description="Evaluate additions against current tank constraints and risks."
      >
        <ScrollableSegmentedButtons
          value={compatibilityAquariumId}
          onValueChange={setCompatibilityAquariumId}
          buttons={aquariums.map((aq) => ({ label: aq.name, value: aq.id }))}
          density="small"
        />
        <View style={{ flexDirection: "row", gap: 8, marginTop: 12 }}>
          <TextInput
            mode="outlined"
            label="Species"
            value={compatibilitySpecies}
            onChangeText={setCompatibilitySpecies}
            style={{ flex: 1 }}
          />
          <TextInput
            mode="outlined"
            label="Qty"
            value={compatibilityQuantity}
            onChangeText={setCompatibilityQuantity}
            keyboardType="number-pad"
            style={{ width: 90 }}
          />
        </View>
        <ScrollableSegmentedButtons
          value={compatibilityKind}
          onValueChange={(value) =>
            setCompatibilityKind(value as typeof compatibilityKind)
          }
          buttons={[
            { label: "Fish", value: "fish" },
            { label: "Shrimp", value: "shrimp" },
            { label: "Snail", value: "snail" },
            { label: "Coral", value: "coral" },
            { label: "Plant", value: "plant" },
            { label: "Other", value: "other" },
          ]}
          density="small"
          style={{ marginTop: 12 }}
        />
        <TextInput
          mode="outlined"
          label="Notes (optional)"
          value={compatibilityNotes}
          onChangeText={setCompatibilityNotes}
          multiline
          numberOfLines={2}
          style={{ marginTop: 10 }}
        />
        <Button
          mode="contained-tonal"
          onPress={() => {
            void runCompatibilityWorkflow();
          }}
          disabled={
            isAsking || !compatibilityAquariumId || !compatibilitySpecies.trim()
          }
          style={{ marginTop: 12, alignSelf: "flex-start" }}
        >
          Run compatibility check
        </Button>
        {compatibilityError ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.error, marginTop: 8 }}
          >
            {compatibilityError}
          </Text>
        ) : null}
        {compatibilityAnswer ? (
          <Text variant="bodyMedium" style={{ marginTop: 12 }}>
            {compatibilityAnswer}
          </Text>
        ) : null}
      </DashboardSection>
    </DashboardScrollView>
  );
}
