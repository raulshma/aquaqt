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
    forgetAssistantMemorySnippet,
    listAssistantMemorySnippets,
} from "@/services/assistant-memory";
import { AssistantMemorySnippet } from "@/types/assistant";

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
    try {
      await clearAssistantMemoryStore();
      if (mountedRef.current) {
        setMemorySnippets([]);
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
    try {
      await forgetAssistantMemorySnippet(id);
      setMemorySnippets((prev) => prev.filter((snippet) => snippet.id !== id));
    } catch (error) {
      setMemoryError(
        error instanceof Error ? error.message : "Failed to remove snippet",
      );
    }
  }, []);

  useEffect(() => {
    if (!assistantMemoryEnabled) {
      setMemorySnippets([]);
      return;
    }

    void refreshAssistantMemory();
  }, [assistantMemoryEnabled, refreshAssistantMemory]);

  return (
    <DashboardScrollView>
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
          {isLoadingMemory ? <ActivityIndicator /> : null}
        </View>

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
