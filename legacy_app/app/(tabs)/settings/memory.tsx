import { useCallback, useEffect, useRef, useState } from "react";
import { View } from "react-native";
import {
  ActivityIndicator,
  Button,
  Card,
  Chip,
  Text,
  useTheme,
} from "react-native-paper";

import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import {
  clearAssistantMemoryStore,
  compactAssistantMemoryFacts,
  forgetAssistantMemorySnippet,
  listAssistantMemorySnippets,
  previewAssistantMemoryFactCompaction,
} from "@/services/assistant-memory";
import { AssistantMemorySnippet } from "@/types/assistant";

interface CompactionPreview {
  beforeCount: number;
  afterCount: number;
  facts: string[];
}

export default function MemorySettingsScreen() {
  const theme = useTheme();
  const { settings, saveAssistantMemoryEnabled } = useAquapt();
  const [assistantMemoryEnabled, setAssistantMemoryEnabled] = useState(
    settings.assistantMemoryEnabled ?? true,
  );
  const [memorySnippets, setMemorySnippets] = useState<
    AssistantMemorySnippet[]
  >([]);
  const [isLoadingMemory, setIsLoadingMemory] = useState(false);
  const [memoryError, setMemoryError] = useState<string | null>(null);
  const [memoryNotice, setMemoryNotice] = useState<string | null>(null);
  const [compactionPreview, setCompactionPreview] =
    useState<CompactionPreview | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const refreshAssistantMemory = useCallback(async () => {
    setIsLoadingMemory(true);
    setMemoryError(null);
    try {
      const snippets = await listAssistantMemorySnippets({ limit: 20 });
      if (mountedRef.current) {
        setMemorySnippets(snippets);
        setCompactionPreview(null);
      }
    } catch (error) {
      setMemoryError(
        error instanceof Error ? error.message : "Failed to load memory",
      );
    } finally {
      if (mountedRef.current) {
        setIsLoadingMemory(false);
      }
    }
  }, []);

  const clearAssistantMemory = useCallback(async () => {
    setIsLoadingMemory(true);
    setMemoryError(null);
    setMemoryNotice(null);
    setCompactionPreview(null);
    try {
      await clearAssistantMemoryStore();
      if (mountedRef.current) {
        setMemorySnippets([]);
        setMemoryNotice("Assistant memory cleared.");
      }
    } catch (error) {
      setMemoryError(
        error instanceof Error ? error.message : "Failed to clear memory",
      );
    } finally {
      if (mountedRef.current) {
        setIsLoadingMemory(false);
      }
    }
  }, []);

  const forgetSnippet = useCallback(async (id: string) => {
    setMemoryError(null);
    setMemoryNotice(null);
    setCompactionPreview(null);
    try {
      await forgetAssistantMemorySnippet(id);
      setMemorySnippets((prev) => prev.filter((snippet) => snippet.id !== id));
    } catch (error) {
      setMemoryError(
        error instanceof Error ? error.message : "Failed to remove snippet",
      );
    }
  }, []);

  const previewCompactFacts = useCallback(async () => {
    setIsLoadingMemory(true);
    setMemoryError(null);
    setMemoryNotice(null);
    setCompactionPreview(null);

    try {
      const preview = await previewAssistantMemoryFactCompaction({
        apiKey: settings.openRouterApiKey,
        model: settings.assistantMemoryModel || settings.aiModel,
        enabled: assistantMemoryEnabled,
        maxFacts: 10,
      });

      if (preview.beforeCount === 0) {
        setMemoryNotice("No memory facts found to compact.");
      } else if (preview.afterCount === 0) {
        setMemoryNotice("No durable facts found in current memory snippets.");
      } else {
        setCompactionPreview(preview);
        setMemoryNotice(
          `Preview ready: ${preview.beforeCount} snippets → ${preview.afterCount} durable fact${preview.afterCount === 1 ? "" : "s"}. Confirm to apply.`,
        );
      }
    } catch (error) {
      setMemoryError(
        error instanceof Error
          ? error.message
          : "Failed to preview compacted memory facts",
      );
    } finally {
      if (mountedRef.current) {
        setIsLoadingMemory(false);
      }
    }
  }, [
    assistantMemoryEnabled,
    settings.aiModel,
    settings.assistantMemoryModel,
    settings.openRouterApiKey,
  ]);

  const cancelCompactionPreview = useCallback(() => {
    setCompactionPreview(null);
    setMemoryNotice("Compaction preview canceled.");
    setMemoryError(null);
  }, []);

  const applyCompactionPreview = useCallback(async () => {
    if (!compactionPreview || compactionPreview.facts.length === 0) {
      return;
    }

    setIsLoadingMemory(true);
    setMemoryError(null);
    setMemoryNotice(null);

    try {
      const result = await compactAssistantMemoryFacts({
        apiKey: settings.openRouterApiKey,
        model: settings.assistantMemoryModel || settings.aiModel,
        enabled: assistantMemoryEnabled,
        maxFacts: Math.max(1, compactionPreview.facts.length),
        precomputedFacts: compactionPreview.facts,
      });

      setMemoryNotice(
        `Compacted ${result.beforeCount} snippets into ${result.afterCount} durable fact${result.afterCount === 1 ? "" : "s"}.`,
      );
      setCompactionPreview(null);

      const snippets = await listAssistantMemorySnippets({ limit: 20 });
      if (mountedRef.current) {
        setMemorySnippets(snippets);
      }
    } catch (error) {
      setMemoryError(
        error instanceof Error ? error.message : "Failed to apply compaction",
      );
    } finally {
      if (mountedRef.current) {
        setIsLoadingMemory(false);
      }
    }
  }, [
    assistantMemoryEnabled,
    compactionPreview,
    settings.aiModel,
    settings.assistantMemoryModel,
    settings.openRouterApiKey,
  ]);

  useEffect(() => {
    if (!assistantMemoryEnabled) {
      setMemorySnippets([]);
      return;
    }

    void refreshAssistantMemory();
  }, [assistantMemoryEnabled, refreshAssistantMemory]);

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="Assistant memory"
        subtitle="Review, clear, and forget semantic snippets."
        tone="secondary"
        chips={
          <Chip compact icon="brain">
            {assistantMemoryEnabled ? "Memory on" : "Memory off"}
          </Chip>
        }
      />

      <DashboardSection
        title="Local memory"
        description="Keep this feature on if you want Aquapt to reuse relevant snippets in future assistant responses."
      >
        <View
          style={{
            flexDirection: "row",
            flexWrap: "wrap",
            gap: 8,
            marginTop: 16,
          }}
        >
          <Chip
            selected={assistantMemoryEnabled}
            onPress={() => {
              setAssistantMemoryEnabled(true);
              saveAssistantMemoryEnabled(true);
            }}
          >
            Assistant memory ON
          </Chip>
          <Chip
            selected={!assistantMemoryEnabled}
            onPress={() => {
              setAssistantMemoryEnabled(false);
              saveAssistantMemoryEnabled(false);
            }}
          >
            Assistant memory OFF
          </Chip>
        </View>

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
              void refreshAssistantMemory();
            }}
            disabled={!assistantMemoryEnabled || isLoadingMemory}
          >
            Refresh memory
          </Button>
          <Button
            mode="outlined"
            onPress={() => {
              void clearAssistantMemory();
            }}
            disabled={!assistantMemoryEnabled || isLoadingMemory}
          >
            Clear all
          </Button>
          <Button
            mode="outlined"
            onPress={() => {
              void previewCompactFacts();
            }}
            disabled={
              !assistantMemoryEnabled ||
              isLoadingMemory ||
              memorySnippets.length < 2
            }
          >
            Preview compact
          </Button>
          {isLoadingMemory ? <ActivityIndicator /> : null}
        </View>

        {compactionPreview ? (
          <Card
            mode="outlined"
            style={{
              marginTop: 12,
              borderColor: theme.colors.primary,
            }}
          >
            <Card.Content>
              <Text variant="titleSmall">Compaction preview</Text>
              <Text variant="bodySmall" style={{ marginTop: 6, opacity: 0.8 }}>
                {compactionPreview.beforeCount} snippets will become{" "}
                {compactionPreview.afterCount} durable facts.
              </Text>
              {compactionPreview.facts.map((fact, index) => (
                <Text
                  key={`preview-fact-${index}`}
                  variant="bodySmall"
                  style={{ marginTop: 8 }}
                >
                  {index + 1}. {fact}
                </Text>
              ))}
              <View
                style={{
                  flexDirection: "row",
                  flexWrap: "wrap",
                  gap: 8,
                  marginTop: 12,
                }}
              >
                <Button
                  mode="contained"
                  onPress={() => {
                    void applyCompactionPreview();
                  }}
                  disabled={isLoadingMemory}
                >
                  Apply compaction
                </Button>
                <Button
                  mode="text"
                  onPress={cancelCompactionPreview}
                  disabled={isLoadingMemory}
                >
                  Cancel
                </Button>
              </View>
            </Card.Content>
          </Card>
        ) : null}

        {!assistantMemoryEnabled ? (
          <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 12 }}>
            Turn assistant memory ON above to store and review snippets.
          </Text>
        ) : null}

        {memoryError ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.error, marginTop: 12 }}
          >
            {memoryError}
          </Text>
        ) : null}

        {memoryNotice ? (
          <Text
            variant="bodySmall"
            style={{ color: theme.colors.primary, marginTop: 12 }}
          >
            {memoryNotice}
          </Text>
        ) : null}

        {assistantMemoryEnabled &&
        memorySnippets.length === 0 &&
        !isLoadingMemory ? (
          <Text variant="bodySmall" style={{ opacity: 0.75, marginTop: 12 }}>
            No memory snippets saved yet.
          </Text>
        ) : null}

        {memorySnippets.map((snippet) => (
          <Card
            key={snippet.id}
            mode="contained"
            style={{
              marginTop: 10,
              backgroundColor: theme.colors.surfaceVariant,
            }}
          >
            <Card.Content>
              <Text variant="bodySmall">{snippet.content}</Text>
              <Text
                variant="labelSmall"
                style={{ opacity: 0.75, marginTop: 8 }}
              >
                {snippet.createdAt
                  ? `Saved: ${new Date(snippet.createdAt).toLocaleString()}`
                  : "Saved: -"}
              </Text>
              <Button
                compact
                mode="text"
                onPress={() => {
                  void forgetSnippet(snippet.id);
                }}
                style={{ alignSelf: "flex-start" }}
              >
                Forget
              </Button>
            </Card.Content>
          </Card>
        ))}
      </DashboardSection>
    </DashboardScrollView>
  );
}
