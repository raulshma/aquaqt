import { useCallback, useEffect, useMemo, useState } from "react";
import { View } from "react-native";
import {
  ActivityIndicator,
  Button,
  Chip,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";

export default function AssistantSettingsScreen() {
  const theme = useTheme();
  const { settings, saveApiKey, saveAiModel, saveAssistantMemoryModel } =
    useAquapt();
  const [apiKey, setApiKey] = useState(settings.openRouterApiKey);
  const [model, setModel] = useState(settings.aiModel);
  const [memoryModel, setMemoryModel] = useState(
    settings.assistantMemoryModel || settings.aiModel,
  );
  const [models, setModels] = useState<
    { id: string; name?: string; created?: number; context_length?: number }[]
  >([]);
  const [modelsError, setModelsError] = useState<string | null>(null);
  const [isLoadingModels, setIsLoadingModels] = useState(false);
  const [isModelSheetVisible, setModelSheetVisible] = useState(false);
  const [modelSheetTarget, setModelSheetTarget] = useState<
    "assistant" | "memory"
  >("assistant");
  const [modelQuery, setModelQuery] = useState("");

  useEffect(() => {
    setApiKey(settings.openRouterApiKey);
    setModel(settings.aiModel);
    setMemoryModel(settings.assistantMemoryModel || settings.aiModel);
  }, [
    settings.aiModel,
    settings.assistantMemoryModel,
    settings.openRouterApiKey,
  ]);

  const loadOpenRouterModels = useCallback(async () => {
    setIsLoadingModels(true);
    setModelsError(null);

    try {
      const response = await fetch("https://openrouter.ai/api/v1/models");
      if (!response.ok) {
        throw new Error(`Failed to load models (${response.status})`);
      }

      const data = (await response.json()) as {
        data?: {
          id: string;
          name?: string;
          created?: number;
          context_length?: number;
        }[];
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
    if (!isModelSheetVisible || models.length > 0 || isLoadingModels) {
      return;
    }

    void loadOpenRouterModels();
  }, [
    isLoadingModels,
    isModelSheetVisible,
    loadOpenRouterModels,
    models.length,
  ]);

  const filteredModels = useMemo(() => {
    const query = modelQuery.trim().toLowerCase();
    if (!query) {
      return models;
    }

    return models.filter((candidate) =>
      `${candidate.id} ${candidate.name ?? ""}`.toLowerCase().includes(query),
    );
  }, [modelQuery, models]);

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="OpenRouter assistant"
        subtitle="Save your API key and pick the default model."
        tone="primary"
        chips={
          <View style={{ flexDirection: "row", gap: 8 }}>
            <Chip compact icon="brain">
              Assistant: {model || "None"}
            </Chip>
            <Chip compact icon="database-edit">
              Memory: {memoryModel || "None"}
            </Chip>
          </View>
        }
      />

      <DashboardSection
        title="Credentials"
        description="Your key stays local to the device."
      >
        <TextInput
          mode="outlined"
          label="OpenRouter API key"
          value={apiKey}
          onChangeText={setApiKey}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          style={{ marginTop: 16 }}
        />

        <TextInput
          mode="outlined"
          label="Assistant model ID"
          value={model}
          onChangeText={setModel}
          autoCapitalize="none"
          autoCorrect={false}
          style={{ marginTop: 10 }}
        />

        <TextInput
          mode="outlined"
          label="Memory generation model ID"
          value={memoryModel}
          onChangeText={setMemoryModel}
          autoCapitalize="none"
          autoCorrect={false}
          style={{ marginTop: 10 }}
        />

        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 12,
          }}
        >
          <Button
            mode="contained-tonal"
            onPress={() => {
              setModelSheetTarget("assistant");
              setModelSheetVisible(true);
            }}
          >
            Browse assistant models
          </Button>
          <Button
            mode="contained-tonal"
            onPress={() => {
              setModelSheetTarget("memory");
              setModelSheetVisible(true);
            }}
          >
            Browse memory models
          </Button>
          <Button
            mode="outlined"
            onPress={() => {
              saveApiKey(apiKey);
              saveAiModel(model);
              saveAssistantMemoryModel(memoryModel);
            }}
          >
            Save settings
          </Button>
        </View>
      </DashboardSection>

      <BottomSheet
        visible={isModelSheetVisible}
        onDismiss={() => setModelSheetVisible(false)}
        title={`Select ${modelSheetTarget === "assistant" ? "assistant" : "memory generation"} model`}
      >
        <Text variant="bodySmall" style={{ opacity: 0.75 }}>
          {filteredModels.length} models shown
        </Text>
        <TextInput
          mode="outlined"
          label="Filter by model name or ID"
          value={modelQuery}
          onChangeText={setModelQuery}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <Button
          mode="contained-tonal"
          onPress={() => {
            void loadOpenRouterModels();
          }}
          disabled={isLoadingModels}
          style={{ marginTop: 10, alignSelf: "flex-start" }}
        >
          Refresh list
        </Button>
        {isLoadingModels ? (
          <ActivityIndicator style={{ marginTop: 12 }} />
        ) : null}
        {modelsError ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.error, marginTop: 12 }}
          >
            {modelsError}
          </Text>
        ) : null}
        <View style={{ gap: 8, marginTop: 12 }}>
          {filteredModels.slice(0, 40).map((candidate) => (
            <Button
              key={candidate.id}
              mode="text"
              onPress={() => {
                if (modelSheetTarget === "assistant") {
                  setModel(candidate.id);
                } else {
                  setMemoryModel(candidate.id);
                }
                setModelSheetVisible(false);
              }}
            >
              {`${candidate.name ?? candidate.id} • ${candidate.context_length ?? "-"} ctx`}
            </Button>
          ))}
        </View>
      </BottomSheet>
    </DashboardScrollView>
  );
}
