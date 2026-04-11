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
  type OpenRouterModel = {
    id: string;
    name?: string;
    created?: number;
    context_length?: number;
    pricing?: {
      prompt?: string;
      completion?: string;
    };
  };
  type ModelSort = "name" | "created" | "context";

  const [models, setModels] = useState<OpenRouterModel[]>([]);
  const [modelsError, setModelsError] = useState<string | null>(null);
  const [isLoadingModels, setIsLoadingModels] = useState(false);
  const [isModelSheetVisible, setModelSheetVisible] = useState(false);
  const [modelSheetTarget, setModelSheetTarget] = useState<
    "assistant" | "memory"
  >("assistant");
  const [modelQuery, setModelQuery] = useState("");
  const [modelSort, setModelSort] = useState<ModelSort>("name");

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

  const sortedModels = useMemo(() => {
    const query = modelQuery.trim().toLowerCase();
    const filtered = !query
      ? models
      : models.filter((candidate) =>
          `${candidate.id} ${candidate.name ?? ""}`
            .toLowerCase()
            .includes(query),
        );

    return [...filtered].sort((a, b) => {
      if (modelSort === "created") {
        return (b.created ?? 0) - (a.created ?? 0);
      }

      if (modelSort === "context") {
        return (b.context_length ?? 0) - (a.context_length ?? 0);
      }

      const aLabel = (a.name ?? a.id).toLowerCase();
      const bLabel = (b.name ?? b.id).toLowerCase();
      return aLabel.localeCompare(bLabel);
    });
  }, [modelQuery, modelSort, models]);

  const groupedModels = useMemo(() => {
    const parsePrice = (value?: string) => {
      const parsed = Number.parseFloat(value ?? "");
      return Number.isFinite(parsed) ? parsed : undefined;
    };

    const isFreeModel = (candidate: OpenRouterModel) => {
      if (candidate.id.toLowerCase().includes(":free")) {
        return true;
      }

      const promptPrice = parsePrice(candidate.pricing?.prompt);
      const completionPrice = parsePrice(candidate.pricing?.completion);

      return promptPrice === 0 && completionPrice === 0;
    };

    return sortedModels.reduce<{
      free: OpenRouterModel[];
      paid: OpenRouterModel[];
    }>(
      (acc, candidate) => {
        if (isFreeModel(candidate)) {
          acc.free.push(candidate);
        } else {
          acc.paid.push(candidate);
        }

        return acc;
      },
      { free: [], paid: [] },
    );
  }, [sortedModels]);

  const formatCreatedDate = useCallback((created?: number) => {
    if (!created || !Number.isFinite(created)) {
      return "-";
    }

    const asMs = created > 10_000_000_000 ? created : created * 1000;
    return new Date(asMs).toLocaleDateString();
  }, []);

  const renderModelButton = useCallback(
    (candidate: OpenRouterModel) => (
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
        {`${candidate.name ?? candidate.id} • ${candidate.context_length ?? "-"} ctx • ${formatCreatedDate(candidate.created)}`}
      </Button>
    ),
    [formatCreatedDate, modelSheetTarget],
  );

  const filteredModelsCount =
    groupedModels.free.length + groupedModels.paid.length;

  const MAX_MODELS_PER_GROUP = 40;

  const freeModels = groupedModels.free.slice(0, MAX_MODELS_PER_GROUP);
  const paidModels = groupedModels.paid.slice(0, MAX_MODELS_PER_GROUP);

  const sortLabel =
    modelSort === "name"
      ? "name"
      : modelSort === "created"
        ? "created"
        : "context";

  const groupCountLabel = `${groupedModels.free.length} free • ${groupedModels.paid.length} paid`;

  const groupedSummary = `${filteredModelsCount} models shown (${groupCountLabel}, sort: ${sortLabel})`;

  const hasResults = filteredModelsCount > 0;

  const noResultsText =
    "No models match your filter. Try a broader query or refresh the list.";

  const freeGroupTitle = `Free models (${groupedModels.free.length})`;

  const paidGroupTitle = `Paid models (${groupedModels.paid.length})`;

  const truncatedHint =
    filteredModelsCount > MAX_MODELS_PER_GROUP * 2
      ? `Showing the first ${MAX_MODELS_PER_GROUP} models per group.`
      : "";

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
          {groupedSummary}
        </Text>
        <TextInput
          mode="outlined"
          label="Filter by model name or ID"
          value={modelQuery}
          onChangeText={setModelQuery}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 10,
          }}
        >
          <Chip
            selected={modelSort === "name"}
            onPress={() => setModelSort("name")}
          >
            Sort: Name
          </Chip>
          <Chip
            selected={modelSort === "created"}
            onPress={() => setModelSort("created")}
          >
            Sort: Created
          </Chip>
          <Chip
            selected={modelSort === "context"}
            onPress={() => setModelSort("context")}
          >
            Sort: Context
          </Chip>
        </View>
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
        {truncatedHint ? (
          <Text variant="bodySmall" style={{ opacity: 0.65, marginTop: 8 }}>
            {truncatedHint}
          </Text>
        ) : null}
        <View style={{ gap: 8, marginTop: 12 }}>
          {!hasResults ? (
            <Text variant="bodySmall" style={{ opacity: 0.75 }}>
              {noResultsText}
            </Text>
          ) : null}

          {freeModels.length > 0 ? (
            <Text variant="titleSmall" style={{ marginTop: 4 }}>
              {freeGroupTitle}
            </Text>
          ) : null}
          {freeModels.map(renderModelButton)}

          {paidModels.length > 0 ? (
            <Text variant="titleSmall" style={{ marginTop: 8 }}>
              {paidGroupTitle}
            </Text>
          ) : null}
          {paidModels.map(renderModelButton)}
        </View>
      </BottomSheet>
    </DashboardScrollView>
  );
}
