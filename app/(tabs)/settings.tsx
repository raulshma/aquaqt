import { useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import {
    ActivityIndicator,
    Button,
    Card,
    Chip,
    Text,
    TextInput,
} from "react-native-paper";

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

export default function SettingsScreen() {
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

  const handleSave = () => {
    saveApiKey(apiKey);
    saveAiModel(model);
    setSavedAt(new Date().toLocaleString());
  };

  const askAssistant = async () => {
    if (!apiKey.trim() || !question.trim()) {
      return;
    }

    setAsking(true);
    setAssistantError(null);

    try {
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

      setAnswer(data.choices?.[0]?.message?.content?.trim() ?? "No response.");
    } catch (error) {
      setAssistantError(
        error instanceof Error ? error.message : "Unknown error",
      );
    } finally {
      setAsking(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
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
