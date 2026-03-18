import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  KeyboardAvoidingView,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import {
  ActivityIndicator,
  Button,
  Card,
  Chip,
  IconButton,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  ACTION_ICONS,
  ConversationDrawer,
  HUMANIZED_TYPES,
} from "@/components/assistant/conversation-drawer";
import { StreamingMarkdown } from "@/components/assistant/streaming-markdown";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { ScrollableSegmentedButtons } from "@/components/ui/scrollable-segmented-buttons";
import { useAquapt } from "@/context/aquapt-context";
import { executeApprovedActions as runApprovedActions } from "@/services/assistant-executor";
import {
  forgetManualAssistantSnippet,
  queryAssistantMemorySnippets,
  rememberAssistantTurn,
  rememberManualAssistantSnippet,
} from "@/services/assistant-memory";
import { askAssistantWithTaskDetection } from "@/services/assistant-orchestrator";
import {
  initPersistence,
  loadPersistedAssistantState,
  savePersistedAssistantState,
} from "@/services/persistence";
import {
  isDictationSupported,
  startPressHoldDictation,
} from "@/services/voice";
import { TaskFrequency } from "@/types/aquapt";
import {
  AssistantChatMessage,
  AssistantConversation,
  AssistantDetectedAction,
  AssistantResponseTelemetry,
} from "@/types/assistant";

/* ── helpers ─────────────────────────────────────────────────────── */

const nowId = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const DRAWER_WIDTH = 300;
const TAB_BAR_HEIGHT = 68;

const FREQUENCIES: { label: string; value: TaskFrequency }[] = [
  { label: "Daily", value: "daily" },
  { label: "Weekly", value: "weekly" },
  { label: "Bi-weekly", value: "bi-weekly" },
  { label: "Monthly", value: "monthly" },
];

const formatNumber = (
  value: number | undefined,
  digits = 0,
  fallback = "—",
) => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }
  return value.toFixed(digits);
};

const formatMilliseconds = (value: number | undefined) => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "—";
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)}s`;
  }
  return `${value.toFixed(0)}ms`;
};

function AssistantTelemetry({
  telemetry,
}: {
  telemetry: AssistantResponseTelemetry;
}) {
  const rows = [
    `Provider: ${telemetry.providerName ?? "—"}`,
    `Model: ${telemetry.model ?? "—"}`,
    `Tokens: ${formatNumber(telemetry.promptTokens)} in · ${formatNumber(telemetry.completionTokens)} out · ${formatNumber(telemetry.totalTokens)} total`,
    `Throughput: ${formatNumber(telemetry.throughputTokensPerSecond, 1)} tok/s · ${formatNumber(telemetry.throughputCharsPerSecond, 1)} ch/s`,
    `Latency: ${formatMilliseconds(telemetry.latencyMs)} · Elapsed: ${formatMilliseconds(telemetry.elapsedMs)}`,
    `Cost: ${typeof telemetry.cost === "number" ? `$${telemetry.cost.toFixed(6)}` : "—"} · Finish: ${telemetry.finishReason ?? "—"}`,
  ];

  return (
    <View style={styles.telemetryWrap}>
      <Text variant="labelSmall" style={styles.telemetryLabel}>
        AI runtime metadata
      </Text>
      {rows.map((row) => (
        <Text key={row} variant="labelSmall" style={styles.telemetryText}>
          {row}
        </Text>
      ))}
      {telemetry.generationId ? (
        <Text variant="labelSmall" style={styles.telemetryText}>
          Generation: {telemetry.generationId}
        </Text>
      ) : null}
    </View>
  );
}

function createConversation(): AssistantConversation {
  const ts = new Date().toISOString();
  return {
    id: nowId("conv"),
    title: "New Chat",
    pinned: false,
    messages: [
      {
        id: nowId("msg"),
        role: "assistant",
        content:
          "Hi! I can help you operate Aquapt. Type or dictate what you'd like to do and I'll detect actions for your approval.",
        createdAt: ts,
      },
    ],
    detectedActions: [],
    warnings: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

function getActionSummary(action: AssistantDetectedAction): string {
  switch (action.type) {
    case "create_task_template":
      return action.title ?? "New task";
    case "complete_task":
      return action.taskTitle ?? action.title ?? "Complete task";
    case "log_dosing":
      return `${action.product ?? "?"} – ${action.amountMl ?? "?"}ml`;
    case "log_parameters": {
      if (!action.parameters) return "No params";
      const parts: string[] = [];
      if (action.parameters.ph !== undefined)
        parts.push(`pH ${action.parameters.ph}`);
      if (action.parameters.nitrate !== undefined)
        parts.push(`NO₃ ${action.parameters.nitrate}`);
      if (action.parameters.temperatureC !== undefined)
        parts.push(`${action.parameters.temperatureC}°C`);
      return parts.join(", ") || "Water parameters";
    }
    case "add_issue":
      return action.issueTitle ?? action.title ?? "New issue";
    case "add_memo":
      return (action.memoContent ?? action.description ?? "").slice(0, 50);
    case "add_aquarium":
      return action.title ?? "New aquarium";
    case "edit_aquarium":
      return action.aquariumName ?? "Edit aquarium";
    case "add_livestock":
      return `${action.livestockName ?? action.title ?? "?"} (${action.species ?? "?"})`;
    case "transfer_livestock":
      return `${action.livestockName ?? "?"} → ${action.targetAquariumName ?? "?"}`;
    case "set_livestock_status":
      return `${action.livestockName ?? "?"}: ${action.livestockStatus ?? "?"}`;
    case "add_asset":
      return action.brandModel ?? "New asset";
    case "add_consumable":
      return action.consumableName ?? "New consumable";
    case "consume_consumable":
      return `${action.consumableName ?? "?"} – ${action.amountUsed ?? "?"}`;
    case "set_issue_status":
      return `${action.issueTitle ?? "?"}: ${action.issueStatus ?? "?"}`;
    case "save_reminder_settings":
      return action.reminderEnabled ? "Enable reminders" : "Disable reminders";
    default:
      return action.type;
  }
}

const validateAction = (
  action: AssistantDetectedAction,
  hasMultipleAquariums: boolean,
) => {
  const errors: string[] = [];
  const requiresAquariumSelection = [
    "create_task_template",
    "complete_task",
    "log_dosing",
    "log_parameters",
    "add_issue",
    "add_memo",
    "add_livestock",
    "add_asset",
    "add_consumable",
  ].includes(action.type);

  if (
    requiresAquariumSelection &&
    hasMultipleAquariums &&
    !action.aquariumId &&
    !action.aquariumName?.trim()
  ) {
    errors.push("Pick an aquarium");
  }

  if (action.type === "create_task_template") {
    if (!action.title?.trim()) errors.push("Task title is required");
    if (!action.frequency) errors.push("Frequency is required");
  }
  if (action.type === "complete_task") {
    if (!action.taskTemplateId?.trim() && !action.taskTitle?.trim())
      errors.push("Task to complete is required");
  }
  if (action.type === "log_dosing") {
    if (!action.product?.trim()) errors.push("Dosing product is required");
    if (!action.amountMl || action.amountMl <= 0)
      errors.push("Dosing amount must be greater than 0");
  }
  if (action.type === "log_parameters") {
    if (!action.parameters || Object.keys(action.parameters).length === 0)
      errors.push("At least one water parameter is required");
  }
  if (action.type === "add_issue") {
    if (!action.issueTitle?.trim() && !action.title?.trim())
      errors.push("Issue title is required");
  }
  if (action.type === "add_memo") {
    if (!action.memoContent?.trim() && !action.description?.trim())
      errors.push("Memo content is required");
  }
  if (action.type === "save_reminder_settings") {
    if (typeof action.reminderEnabled !== "boolean")
      errors.push("Reminder enabled/disabled state is required");
    if (action.reminderEnabled && action.reminderHour === undefined)
      errors.push("Reminder hour is required when reminders are enabled");
  }
  if (action.type === "add_aquarium") {
    if (!action.title?.trim()) errors.push("Aquarium name is required");
    if (!action.volumeLiters || action.volumeLiters <= 0)
      errors.push("Aquarium volume must be greater than 0");
    if (!action.waterType) errors.push("Water type is required");
    if (!action.dimensions?.trim()) errors.push("Dimensions are required");
  }
  if (action.type === "edit_aquarium") {
    if (!action.aquariumId?.trim() && !action.aquariumName?.trim())
      errors.push("Aquarium to edit is required");
  }
  if (action.type === "add_livestock") {
    if (!action.livestockName?.trim() && !action.title?.trim())
      errors.push("Livestock name is required");
    if (!action.species?.trim()) errors.push("Species is required");
    if (!action.livestockKind) errors.push("Livestock kind is required");
    if (!action.quantity || action.quantity <= 0)
      errors.push("Quantity must be greater than 0");
  }
  if (action.type === "transfer_livestock") {
    if (!action.livestockId?.trim() && !action.livestockName?.trim())
      errors.push("Livestock to transfer is required");
    if (!action.targetAquariumId?.trim() && !action.targetAquariumName?.trim())
      errors.push("Target aquarium is required");
  }
  if (action.type === "set_livestock_status") {
    if (!action.livestockId?.trim() && !action.livestockName?.trim())
      errors.push("Livestock is required");
    if (!action.livestockStatus) errors.push("Livestock status is required");
  }
  if (action.type === "add_asset") {
    if (!action.assetCategory) errors.push("Asset category is required");
    if (!action.brandModel?.trim()) errors.push("Brand/model is required");
  }
  if (action.type === "add_consumable") {
    if (!action.consumableName?.trim())
      errors.push("Consumable name is required");
    if (!action.consumableUnit) errors.push("Consumable unit is required");
    if (action.remaining === undefined || action.remaining < 0)
      errors.push("Remaining amount must be 0 or greater");
  }
  if (action.type === "consume_consumable") {
    if (!action.consumableId?.trim() && !action.consumableName?.trim())
      errors.push("Consumable is required");
    if (!action.amountUsed || action.amountUsed <= 0)
      errors.push("Amount used must be greater than 0");
  }
  if (action.type === "set_issue_status") {
    if (!action.issueId?.trim() && !action.issueTitle?.trim())
      errors.push("Issue is required");
    if (!action.issueStatus) errors.push("Issue status is required");
  }
  return errors;
};

/* ── inline action card (shown inside chat messages) ─────────── */

function InlineActionCard({
  action,
  onToggleApproval,
  onReview,
}: {
  action: AssistantDetectedAction;
  onToggleApproval: () => void;
  onReview: () => void;
}) {
  const theme = useTheme();
  const label = HUMANIZED_TYPES[action.type] ?? action.type;
  const icon = ACTION_ICONS[action.type] ?? "lightning-bolt";
  const summary = getActionSummary(action);
  const hasErrors = action.validationErrors.length > 0;

  return (
    <Pressable
      onPress={onReview}
      style={({ pressed }) => [
        inlineStyles.card,
        {
          backgroundColor: action.approved
            ? `${theme.colors.primary}18`
            : theme.colors.surfaceVariant,
          borderColor: action.approved
            ? theme.colors.primary
            : theme.colors.outlineVariant,
        },
        pressed && { opacity: 0.7 },
      ]}
    >
      <View style={inlineStyles.topRow}>
        <IconButton
          icon={icon}
          size={16}
          style={inlineStyles.icon}
          iconColor={theme.colors.primary}
        />
        <Text variant="labelMedium" style={inlineStyles.typeLabel}>
          {label}
        </Text>
        <View
          style={[
            inlineStyles.confBadge,
            {
              backgroundColor: theme.colors.surfaceVariant,
              borderRadius: 9,
              paddingHorizontal: 5,
              paddingVertical: 1,
            },
          ]}
        >
          <Text variant="labelSmall">{`${(action.confidence * 100).toFixed(0)}%`}</Text>
        </View>
        <Pressable
          onPress={() => {
            onToggleApproval();
          }}
          hitSlop={8}
        >
          <IconButton
            icon={
              action.approved ? "check-circle" : "checkbox-blank-circle-outline"
            }
            size={18}
            iconColor={
              action.approved ? theme.colors.primary : theme.colors.outline
            }
            style={inlineStyles.approveIcon}
          />
        </Pressable>
      </View>
      <Text variant="bodySmall" numberOfLines={2} style={{ opacity: 0.85 }}>
        {summary}
      </Text>
      {hasErrors ? (
        <Text
          variant="labelSmall"
          style={{ color: theme.colors.error, marginTop: 2 }}
        >
          {action.validationErrors[0]}
          {action.validationErrors.length > 1
            ? ` (+${action.validationErrors.length - 1} more)`
            : ""}
        </Text>
      ) : null}
    </Pressable>
  );
}

const inlineStyles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 10,
    marginTop: 6,
    gap: 4,
  },
  topRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  icon: { margin: 0, width: 22, height: 22 },
  typeLabel: { flex: 1, fontWeight: "600" },
  confBadge: { marginRight: 4 },
  approveIcon: { margin: 0, width: 24, height: 24 },
});

/* ── main screen ─────────────────────────────────────────────── */

export default function AssistantScreen() {
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  const {
    settings,
    aquariums,
    livestock,
    assets,
    consumables,
    issues,
    parameterLogs,
    taskExecutions,
    taskTemplates,
    addTaskTemplate,
    completeTask,
    logDosing,
    logParameters,
    addIssue,
    addMemo,
    saveReminderSettings,
    addAquarium,
    editAquarium,
    addLivestock,
    transferLivestock,
    setLivestockStatus,
    addAsset,
    addConsumable,
    consumeConsumable,
    setIssueStatus,
  } = useAquapt();

  /* ── conversation state ──────────────────────────────────────── */
  const [conversations, setConversations] = useState<AssistantConversation[]>(
    () => [createConversation()],
  );
  const [activeConversationId, setActiveConversationId] = useState(
    () => conversations[0]?.id ?? "",
  );
  const [isConversationsHydrated, setConversationsHydrated] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const persistConversationsTimeoutRef = useRef<ReturnType<
    typeof setTimeout
  > | null>(null);

  /* ── chat state ──────────────────────────────────────────────── */
  const [composerText, setComposerText] = useState("");
  const [isAsking, setAsking] = useState(false);
  const [assistantError, setAssistantError] = useState<string | null>(null);
  const [activeStreamingMessageId, setActiveStreamingMessageId] = useState<
    string | null
  >(null);
  const [isReviewVisible, setReviewVisible] = useState(false);
  const [rememberedAssistantMessageIds, setRememberedAssistantMessageIds] =
    useState<Record<string, boolean>>({});
  const [memoryActionBusyMessageIds, setMemoryActionBusyMessageIds] = useState<
    Record<string, boolean>
  >({});

  /* ── dictation state ─────────────────────────────────────────── */
  const [dictationPreview, setDictationPreview] = useState("");
  const [dictationError, setDictationError] = useState<string | null>(null);
  const [isDictating, setDictating] = useState(false);
  const dictationSessionRef = useRef<Awaited<
    ReturnType<typeof startPressHoldDictation>
  > | null>(null);
  const hasVoiceSupport = isDictationSupported();

  /* ── drawer animation ────────────────────────────────────────── */
  const drawerProgress = useSharedValue(0);
  const drawerAnimStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: (drawerProgress.value - 1) * DRAWER_WIDTH }],
  }));
  const backdropAnimStyle = useAnimatedStyle(() => ({
    opacity: drawerProgress.value * 0.45,
  }));

  const openDrawer = useCallback(() => {
    setDrawerOpen(true);
    drawerProgress.value = withTiming(1, { duration: 250 });
  }, [drawerProgress]);

  const closeDrawer = useCallback(() => {
    drawerProgress.value = withTiming(0, { duration: 250 });
    setTimeout(() => setDrawerOpen(false), 280);
  }, [drawerProgress]);

  /* ── scroll ref ──────────────────────────────────────────────── */
  const scrollRef = useRef<ScrollView>(null);
  const scrollToBottom = useCallback(() => {
    setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 80);
  }, []);

  /* ── derived state ───────────────────────────────────────────── */
  const activeConversation = useMemo(
    () =>
      conversations.find((c) => c.id === activeConversationId) ??
      conversations[0],
    [conversations, activeConversationId],
  );

  useEffect(() => {
    let isMounted = true;

    const hydrateConversations = async () => {
      try {
        await initPersistence();
        const persistedAssistantState = await loadPersistedAssistantState();

        if (!isMounted || !persistedAssistantState) {
          return;
        }

        const persistedConversations = persistedAssistantState.conversations;
        if (persistedConversations.length === 0) {
          return;
        }

        setConversations(persistedConversations);

        const persistedActiveId = persistedAssistantState.activeConversationId;
        const hasPersistedActive = persistedConversations.some(
          (conversation) => conversation.id === persistedActiveId,
        );

        setActiveConversationId(
          hasPersistedActive ? persistedActiveId : persistedConversations[0].id,
        );
      } catch (error) {
        console.warn("Assistant conversation hydration failed", error);
      } finally {
        if (isMounted) {
          setConversationsHydrated(true);
        }
      }
    };

    void hydrateConversations();

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (!isConversationsHydrated) {
      return;
    }

    if (persistConversationsTimeoutRef.current) {
      clearTimeout(persistConversationsTimeoutRef.current);
    }

    const activeId =
      conversations.find(
        (conversation) => conversation.id === activeConversationId,
      )?.id ?? conversations[0]?.id;

    persistConversationsTimeoutRef.current = setTimeout(() => {
      if (!activeId) {
        return;
      }

      void savePersistedAssistantState({
        conversations,
        activeConversationId: activeId,
        updatedAt: new Date().toISOString(),
      });
    }, 250);

    return () => {
      if (persistConversationsTimeoutRef.current) {
        clearTimeout(persistConversationsTimeoutRef.current);
        persistConversationsTimeoutRef.current = null;
      }
    };
  }, [activeConversationId, conversations, isConversationsHydrated]);

  const activeMessages = useMemo(
    () => activeConversation?.messages ?? [],
    [activeConversation?.messages],
  );
  const activeDetectedActions = useMemo(
    () => activeConversation?.detectedActions ?? [],
    [activeConversation?.detectedActions],
  );
  const activeWarnings = useMemo(
    () => activeConversation?.warnings ?? [],
    [activeConversation?.warnings],
  );

  const hasMultipleAquariums = aquariums.length > 1;

  const approvedActionCount = useMemo(
    () =>
      activeDetectedActions.filter(
        (a) => a.approved && a.validationErrors.length === 0,
      ).length,
    [activeDetectedActions],
  );

  const assistantContext = useMemo(
    () => ({
      aquariums: aquariums.map((aq) => ({
        id: aq.id,
        name: aq.name,
        waterType: aq.waterType,
      })),
      livestock: livestock.slice(0, 40),
      assets: assets.slice(0, 40),
      consumables: consumables.slice(0, 60),
      openIssues: issues.filter((issue) => issue.status !== "resolved"),
      recentParameters: parameterLogs.slice(0, 50),
      taskTemplates: taskTemplates.slice(0, 120),
      recentTaskExecutions: taskExecutions.slice(0, 60),
    }),
    [
      assets,
      aquariums,
      consumables,
      issues,
      livestock,
      parameterLogs,
      taskExecutions,
      taskTemplates,
    ],
  );

  /* ── conversation management ─────────────────────────────────── */
  const createNewConversation = useCallback(() => {
    const conv = createConversation();
    setConversations((prev) => [conv, ...prev]);
    setActiveConversationId(conv.id);
    setComposerText("");
    setAssistantError(null);
  }, []);

  const switchConversation = useCallback((id: string) => {
    setActiveConversationId(id);
    setComposerText("");
    setAssistantError(null);
    setReviewVisible(false);
  }, []);

  const deleteConversation = useCallback(
    (id: string) => {
      setConversations((prev) => {
        const next = prev.filter((c) => c.id !== id);
        if (next.length === 0) {
          const fresh = createConversation();
          next.push(fresh);
        }
        if (id === activeConversationId) {
          setActiveConversationId(next[0].id);
        }
        return next;
      });
    },
    [activeConversationId],
  );

  const toggleConversationPin = useCallback((id: string) => {
    setConversations((prev) =>
      prev.map((conversation) =>
        conversation.id === id
          ? {
              ...conversation,
              pinned: !conversation.pinned,
              updatedAt: new Date().toISOString(),
            }
          : conversation,
      ),
    );
  }, []);

  const renameConversation = useCallback((id: string, title: string) => {
    const nextTitle = title.trim();
    if (!nextTitle) {
      return;
    }

    setConversations((prev) =>
      prev.map((conversation) =>
        conversation.id === id
          ? {
              ...conversation,
              title: nextTitle,
              updatedAt: new Date().toISOString(),
            }
          : conversation,
      ),
    );
  }, []);

  const updateConversation = useCallback(
    (
      convId: string,
      updater: (c: AssistantConversation) => AssistantConversation,
    ) => {
      setConversations((prev) =>
        prev.map((c) => (c.id === convId ? updater(c) : c)),
      );
    },
    [],
  );

  /* ── validation helper ───────────────────────────────────────── */
  const withValidation = useCallback(
    (next: AssistantDetectedAction[]) =>
      next.map((a) => ({
        ...a,
        validationErrors: validateAction(a, hasMultipleAquariums),
      })),
    [hasMultipleAquariums],
  );

  /* ── ask assistant ───────────────────────────────────────────── */
  const askAssistantForPrompt = useCallback(
    async ({
      prompt,
      retryMessageId,
    }: {
      prompt: string;
      retryMessageId?: string;
    }) => {
      const normalizedPrompt = prompt.trim();
      if (!normalizedPrompt || isAsking) return;
      if (!settings.openRouterApiKey.trim()) {
        setAssistantError(
          "Missing API key. Add your OpenRouter key in Settings.",
        );
        return;
      }

      const isRetry = !!retryMessageId;
      const userMessageId = retryMessageId ?? nowId("msg");

      setAsking(true);
      setAssistantError(null);

      const userMessage: AssistantChatMessage = {
        id: userMessageId,
        role: "user",
        content: normalizedPrompt,
        createdAt: new Date().toISOString(),
      };
      const assistantDraftId = nowId("msg");
      const assistantDraftMessage: AssistantChatMessage = {
        id: assistantDraftId,
        role: "assistant",
        content: "",
        createdAt: new Date().toISOString(),
        responseTelemetry: {
          streamed: true,
          model: settings.aiModel,
        },
      };

      const conversationMessagesForModel = isRetry
        ? activeConversation.messages.filter(
            (message) => message.id !== userMessageId,
          )
        : activeConversation.messages;

      // Auto-title the conversation from first user message
      const isFirstUserMsg =
        !isRetry &&
        activeConversation.title === "New Chat" &&
        !activeConversation.messages.some((m) => m.role === "user");

      updateConversation(activeConversationId, (c) => ({
        ...c,
        title: isFirstUserMsg
          ? normalizedPrompt.slice(0, 40) +
            (normalizedPrompt.length > 40 ? "…" : "")
          : c.title,
        messages: isRetry
          ? [
              ...c.messages.map((message) =>
                message.id === userMessageId
                  ? {
                      ...message,
                      requestFailed: false,
                      requestError: undefined,
                    }
                  : message,
              ),
              assistantDraftMessage,
            ]
          : [...c.messages, userMessage, assistantDraftMessage],
        updatedAt: new Date().toISOString(),
      }));

      setActiveStreamingMessageId(assistantDraftId);
      if (!isRetry) {
        setComposerText("");
      }
      scrollToBottom();

      try {
        const memorySnippets = await queryAssistantMemorySnippets({
          prompt: normalizedPrompt,
          limit: 4,
          enabled: settings.assistantMemoryEnabled,
        });

        const result = await askAssistantWithTaskDetection({
          apiKey: settings.openRouterApiKey,
          model: settings.aiModel,
          userPrompt: normalizedPrompt,
          appContext: assistantContext,
          aquariums,
          memorySnippets,
          conversationMessages: conversationMessagesForModel,
          onAssistantDelta: (snapshot) => {
            updateConversation(activeConversationId, (conversation) => ({
              ...conversation,
              messages: conversation.messages.map((message) =>
                message.id === assistantDraftId
                  ? {
                      ...message,
                      content: snapshot.text,
                      responseTelemetry: {
                        ...(message.responseTelemetry ?? {}),
                        streamed: true,
                        generationId:
                          snapshot.generationId ??
                          message.responseTelemetry?.generationId,
                        model:
                          snapshot.model ?? message.responseTelemetry?.model,
                        elapsedMs: snapshot.elapsedMs,
                        throughputCharsPerSecond: snapshot.charsPerSecond,
                      },
                    }
                  : message,
              ),
              updatedAt: new Date().toISOString(),
            }));
            scrollToBottom();
          },
        });

        const normalizedActions = withValidation(
          result.extractedActions.actions,
        );

        updateConversation(activeConversationId, (c) => ({
          ...c,
          messages: c.messages.map((message) => {
            if (message.id === assistantDraftId) {
              return {
                ...message,
                content:
                  result.assistantText || "I could not generate a response.",
                detectedActionIds: normalizedActions.map((a) => a.id),
                responseTelemetry: {
                  ...(message.responseTelemetry ?? {}),
                  ...(result.telemetry ?? {}),
                },
              };
            }

            if (message.id === userMessageId && message.role === "user") {
              return {
                ...message,
                requestFailed: false,
                requestError: undefined,
              };
            }

            return message;
          }),
          detectedActions: [...c.detectedActions, ...normalizedActions],
          warnings: result.extractedActions.warnings,
          updatedAt: new Date().toISOString(),
        }));

        if (normalizedActions.length > 0) {
          setReviewVisible(true);
        }

        void rememberAssistantTurn({
          conversationId: activeConversationId,
          userMessageId,
          userPrompt: normalizedPrompt,
          assistantText: result.assistantText,
          enabled: settings.assistantMemoryEnabled,
        });

        scrollToBottom();
      } catch (error) {
        const requestErrorMessage =
          error instanceof Error ? error.message : "Assistant request failed.";

        setAssistantError(requestErrorMessage);
        updateConversation(activeConversationId, (conversation) => ({
          ...conversation,
          messages: conversation.messages
            .filter((message) => message.id !== assistantDraftId)
            .map((message) =>
              message.id === userMessageId && message.role === "user"
                ? {
                    ...message,
                    requestFailed: true,
                    requestError: requestErrorMessage,
                  }
                : message,
            ),
          updatedAt: new Date().toISOString(),
        }));
      } finally {
        setActiveStreamingMessageId(null);
        setAsking(false);
      }
    },
    [
      activeConversation,
      activeConversationId,
      aquariums,
      assistantContext,
      isAsking,
      settings.aiModel,
      settings.assistantMemoryEnabled,
      settings.openRouterApiKey,
      scrollToBottom,
      updateConversation,
      withValidation,
    ],
  );

  const askAssistant = useCallback(async () => {
    await askAssistantForPrompt({ prompt: composerText });
  }, [askAssistantForPrompt, composerText]);

  const retryFailedMessage = useCallback(
    (message: AssistantChatMessage) => {
      if (message.role !== "user") {
        return;
      }

      void askAssistantForPrompt({
        prompt: message.content,
        retryMessageId: message.id,
      });
    },
    [askAssistantForPrompt],
  );

  /* ── action updates ──────────────────────────────────────────── */
  const updateAction = useCallback(
    (
      actionId: string,
      updates: Partial<
        Pick<
          AssistantDetectedAction,
          | "title"
          | "frequency"
          | "description"
          | "aquariumId"
          | "approved"
          | "taskTemplateId"
          | "taskTitle"
          | "product"
          | "amountMl"
          | "note"
          | "issueTitle"
          | "memoContent"
          | "reminderEnabled"
          | "reminderHour"
          | "waterType"
          | "volumeLiters"
          | "dimensions"
          | "setupDate"
          | "investmentCost"
          | "targetAquariumId"
          | "targetAquariumName"
          | "livestockId"
          | "livestockName"
          | "species"
          | "quantity"
          | "livestockKind"
          | "livestockStatus"
          | "issueId"
          | "issueStatus"
          | "resolutionNote"
          | "assetCategory"
          | "brandModel"
          | "purchasedAt"
          | "price"
          | "consumableId"
          | "consumableName"
          | "consumableUnit"
          | "remaining"
          | "reorderAt"
          | "amountUsed"
        >
      >,
    ) => {
      updateConversation(activeConversationId, (c) => ({
        ...c,
        detectedActions: withValidation(
          c.detectedActions.map((a) =>
            a.id === actionId ? { ...a, ...updates } : a,
          ),
        ),
      }));
    },
    [activeConversationId, updateConversation, withValidation],
  );

  const updateParameterField = useCallback(
    (
      actionId: string,
      key:
        | "ammonia"
        | "nitrite"
        | "nitrate"
        | "ph"
        | "temperatureC"
        | "gh"
        | "kh"
        | "salinity"
        | "calcium"
        | "alkalinity",
      value: string,
    ) => {
      const parsed = Number(value);
      updateConversation(activeConversationId, (c) => ({
        ...c,
        detectedActions: withValidation(
          c.detectedActions.map((action) => {
            if (action.id !== actionId) return action;
            const nextParameters = { ...(action.parameters ?? {}) };
            if (Number.isFinite(parsed)) {
              nextParameters[key] = parsed;
            } else {
              delete nextParameters[key];
            }
            return {
              ...action,
              parameters:
                Object.keys(nextParameters).length > 0
                  ? nextParameters
                  : undefined,
            };
          }),
        ),
      }));
    },
    [activeConversationId, updateConversation, withValidation],
  );

  /* ── execute actions ─────────────────────────────────────────── */
  const executeApprovedActionBatch = useCallback(() => {
    const result = runApprovedActions({
      actions: activeDetectedActions,
      aquariums,
      existingTaskTemplates: taskTemplates,
      existingLivestock: livestock,
      existingIssues: issues,
      existingConsumables: consumables,
      addTaskTemplate,
      completeTask,
      logDosing,
      logParameters,
      addIssue,
      addMemo,
      saveReminderSettings,
      addAquarium,
      editAquarium,
      addLivestock,
      transferLivestock,
      setLivestockStatus,
      addAsset,
      addConsumable,
      consumeConsumable,
      setIssueStatus,
    });

    const feedbackParts: string[] = [];
    if (result.createdCount > 0)
      feedbackParts.push(`Executed ${result.createdCount} action(s).`);
    if (result.skippedCount > 0)
      feedbackParts.push(`Skipped ${result.skippedCount} action(s).`);

    const details = result.results
      .filter((item) => !item.created && item.reason)
      .slice(0, 5)
      .map((item) => `• ${item.actionType}: ${item.reason}`)
      .join("\n");

    const systemMsg: AssistantChatMessage = {
      id: nowId("msg"),
      role: "system",
      content: [feedbackParts.join(" "), details].filter(Boolean).join("\n"),
      createdAt: new Date().toISOString(),
    };

    const createdIds = new Set(
      result.results.filter((e) => e.created).map((e) => e.actionId),
    );

    updateConversation(activeConversationId, (c) => ({
      ...c,
      messages: [...c.messages, systemMsg],
      detectedActions: c.detectedActions.filter((a) => !createdIds.has(a.id)),
      updatedAt: new Date().toISOString(),
    }));

    if (result.createdCount > 0) setReviewVisible(false);
    scrollToBottom();
  }, [
    activeConversationId,
    activeDetectedActions,
    addIssue,
    addAquarium,
    addAsset,
    addConsumable,
    addMemo,
    addTaskTemplate,
    aquariums,
    completeTask,
    consumeConsumable,
    consumables,
    editAquarium,
    issues,
    livestock,
    logDosing,
    logParameters,
    saveReminderSettings,
    scrollToBottom,
    setIssueStatus,
    setLivestockStatus,
    taskTemplates,
    transferLivestock,
    updateConversation,
    addLivestock,
  ]);

  const rememberAssistantMessage = useCallback(
    async (message: AssistantChatMessage) => {
      const content = message.content.trim();
      if (!content) {
        return;
      }

      setMemoryActionBusyMessageIds((prev) => ({
        ...prev,
        [message.id]: true,
      }));
      try {
        const memoryId = await rememberManualAssistantSnippet({
          conversationId: activeConversationId,
          sourceMessageId: message.id,
          content,
          enabled: settings.assistantMemoryEnabled,
        });

        if (memoryId) {
          setRememberedAssistantMessageIds((prev) => ({
            ...prev,
            [message.id]: true,
          }));
        }
      } catch (error) {
        setAssistantError(
          error instanceof Error
            ? error.message
            : "Could not save message to assistant memory.",
        );
      } finally {
        setMemoryActionBusyMessageIds((prev) => ({
          ...prev,
          [message.id]: false,
        }));
      }
    },
    [activeConversationId, settings.assistantMemoryEnabled],
  );

  const forgetAssistantMessageMemory = useCallback(
    async (message: AssistantChatMessage) => {
      setMemoryActionBusyMessageIds((prev) => ({
        ...prev,
        [message.id]: true,
      }));
      try {
        await forgetManualAssistantSnippet({
          conversationId: activeConversationId,
          sourceMessageId: message.id,
        });
        setRememberedAssistantMessageIds((prev) => ({
          ...prev,
          [message.id]: false,
        }));
      } catch (error) {
        setAssistantError(
          error instanceof Error
            ? error.message
            : "Could not forget assistant memory snippet.",
        );
      } finally {
        setMemoryActionBusyMessageIds((prev) => ({
          ...prev,
          [message.id]: false,
        }));
      }
    },
    [activeConversationId],
  );

  /* ── dictation handlers (tap-to-toggle) ───────────────────────── */
  const startDictation = useCallback(() => {
    if (isDictating || isAsking) return;
    setDictationError(null);
    setDictationPreview("");
    setDictating(true);
    void (async () => {
      try {
        const session = await startPressHoldDictation((partial) => {
          setDictationPreview(partial);
        });
        dictationSessionRef.current = session;
      } catch {
        setDictating(false);
        setDictationError("Could not start dictation. Please try again.");
      }
    })();
  }, [isAsking, isDictating]);

  const stopDictation = useCallback(() => {
    if (!isDictating) return;
    const currentSession = dictationSessionRef.current;
    dictationSessionRef.current = null;
    if (!currentSession) {
      setDictating(false);
      return;
    }
    void (async () => {
      const result = await currentSession.stop();
      setDictating(false);
      if (result.error) {
        setDictationError(result.error);
        return;
      }
      const transcript = result.transcript.trim();
      if (!transcript) {
        setDictationError("No speech detected. Tap the mic and try again.");
        return;
      }
      setComposerText((prev) => (prev ? `${prev} ${transcript}` : transcript));
      setDictationPreview("");
    })();
  }, [isDictating]);

  const toggleDictation = useCallback(() => {
    if (isDictating) {
      stopDictation();
    } else {
      startDictation();
    }
  }, [isDictating, startDictation, stopDictation]);

  useEffect(() => {
    return () => {
      dictationSessionRef.current?.cancel();
      dictationSessionRef.current = null;
    };
  }, []);

  /* ── render ──────────────────────────────────────────────────── */
  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      {/* ── MAIN CHAT AREA ───────────────────────────────────── */}
      <KeyboardAvoidingView
        style={[styles.main, { paddingTop: insets.top }]}
        behavior="padding"
        keyboardVerticalOffset={-TAB_BAR_HEIGHT}
      >
        {/* Header bar */}
        <View
          style={[
            styles.header,
            { borderBottomColor: theme.colors.outlineVariant },
          ]}
        >
          <IconButton icon="menu" onPress={openDrawer} />
          <Text
            variant="titleMedium"
            numberOfLines={1}
            style={styles.headerTitle}
          >
            {activeConversation?.title ?? "Assistant"}
          </Text>
          <IconButton icon="plus" onPress={createNewConversation} />
        </View>

        {/* Messages */}
        <ScrollView
          ref={scrollRef}
          style={styles.messageArea}
          contentContainerStyle={[
            styles.messageContent,
            { paddingBottom: TAB_BAR_HEIGHT + 140 },
          ]}
          showsVerticalScrollIndicator={false}
          onContentSizeChange={scrollToBottom}
        >
          {activeMessages.map((message) => {
            const isUser = message.role === "user";
            const isSystem = message.role === "system";
            const isAssistant = message.role === "assistant";
            const isStreaming = activeStreamingMessageId === message.id;
            const isRetryableFailedUserMessage =
              isUser && message.requestFailed === true;

            // Find linked actions for this message
            const linkedActions = (message.detectedActionIds ?? [])
              .map((aid) => activeDetectedActions.find((a) => a.id === aid))
              .filter(Boolean) as AssistantDetectedAction[];

            return (
              <View
                key={message.id}
                style={[
                  styles.messageRow,
                  isUser && styles.messageRowUser,
                  isSystem && styles.messageRowSystem,
                ]}
              >
                {/* Assistant avatar */}
                {!isUser && !isSystem ? (
                  <View
                    style={[
                      styles.avatar,
                      { backgroundColor: theme.colors.primaryContainer },
                    ]}
                  >
                    <Text
                      style={{
                        fontSize: 14,
                        color: theme.colors.onPrimaryContainer,
                      }}
                    >
                      🐠
                    </Text>
                  </View>
                ) : null}

                <View
                  style={[
                    styles.bubble,
                    isUser && {
                      backgroundColor: theme.colors.primaryContainer,
                      borderBottomRightRadius: 4,
                    },
                    isSystem && {
                      backgroundColor: theme.colors.surfaceVariant,
                      alignSelf: "center",
                    },
                    !isUser &&
                      !isSystem && {
                        backgroundColor: "transparent",
                        flex: 1,
                      },
                  ]}
                >
                  {isSystem ? (
                    <Text
                      variant="labelSmall"
                      style={{ opacity: 0.65, marginBottom: 2 }}
                    >
                      SYSTEM
                    </Text>
                  ) : null}
                  {isAssistant ? (
                    <StreamingMarkdown
                      content={message.content}
                      isStreaming={isStreaming}
                    />
                  ) : (
                    <Text
                      variant="bodyMedium"
                      style={[
                        isUser && { color: theme.colors.onPrimaryContainer },
                      ]}
                    >
                      {message.content}
                    </Text>
                  )}

                  {isStreaming ? (
                    <Text variant="labelSmall" style={styles.streamingLabel}>
                      Streaming…
                    </Text>
                  ) : null}

                  {isRetryableFailedUserMessage ? (
                    <View style={styles.failedMessageActionsRow}>
                      <Chip compact icon="alert-circle-outline">
                        Failed to send
                      </Chip>
                      <Button
                        compact
                        mode="text"
                        disabled={isAsking}
                        onPress={() => {
                          retryFailedMessage(message);
                        }}
                      >
                        Retry
                      </Button>
                    </View>
                  ) : null}

                  {isRetryableFailedUserMessage && message.requestError ? (
                    <Text
                      variant="labelSmall"
                      style={styles.failedMessageError}
                    >
                      {message.requestError}
                    </Text>
                  ) : null}

                  {isAssistant && message.responseTelemetry ? (
                    <AssistantTelemetry telemetry={message.responseTelemetry} />
                  ) : null}

                  {isAssistant && !isStreaming && message.content.trim() ? (
                    <View style={styles.memoryActionsRow}>
                      {!rememberedAssistantMessageIds[message.id] ? (
                        <Button
                          compact
                          mode="text"
                          loading={!!memoryActionBusyMessageIds[message.id]}
                          disabled={!!memoryActionBusyMessageIds[message.id]}
                          onPress={() => {
                            void rememberAssistantMessage(message);
                          }}
                        >
                          Remember this reply
                        </Button>
                      ) : (
                        <>
                          <Chip compact>Saved to memory</Chip>
                          <Button
                            compact
                            mode="text"
                            loading={!!memoryActionBusyMessageIds[message.id]}
                            disabled={!!memoryActionBusyMessageIds[message.id]}
                            onPress={() => {
                              void forgetAssistantMessageMemory(message);
                            }}
                          >
                            Forget
                          </Button>
                        </>
                      )}
                    </View>
                  ) : null}

                  {/* Inline detected actions */}
                  {linkedActions.length > 0 ? (
                    <View style={styles.inlineActions}>
                      <Text
                        variant="labelSmall"
                        style={{
                          opacity: 0.6,
                          marginBottom: 2,
                          marginTop: 4,
                        }}
                      >
                        Detected {linkedActions.length} action
                        {linkedActions.length > 1 ? "s" : ""}:
                      </Text>
                      {linkedActions.map((action) => (
                        <InlineActionCard
                          key={action.id}
                          action={action}
                          onToggleApproval={() =>
                            updateAction(action.id, {
                              approved: !action.approved,
                            })
                          }
                          onReview={() => setReviewVisible(true)}
                        />
                      ))}
                    </View>
                  ) : null}
                </View>
              </View>
            );
          })}

          {isAsking ? (
            <View style={styles.messageRow}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: theme.colors.primaryContainer },
                ]}
              >
                <Text style={{ fontSize: 14 }}>🐠</Text>
              </View>
              <ActivityIndicator
                size="small"
                style={{ marginLeft: 8, marginTop: 4 }}
              />
            </View>
          ) : null}
        </ScrollView>

        {/* Composer bar */}
        <View
          style={[
            styles.composer,
            {
              backgroundColor: theme.colors.surface,
              borderTopColor: theme.colors.outlineVariant,
              paddingBottom: TAB_BAR_HEIGHT + 12,
            },
          ]}
        >
          {isDictating ? (
            <View
              style={[
                styles.dictationLiveBanner,
                { backgroundColor: `${theme.colors.primary}12` },
              ]}
            >
              <ActivityIndicator size={14} color={theme.colors.primary} />
              <Text
                variant="bodySmall"
                style={[
                  styles.dictationPreview,
                  { color: theme.colors.primary },
                ]}
                numberOfLines={3}
              >
                {dictationPreview || "Listening…"}
              </Text>
            </View>
          ) : null}
          {dictationError ? (
            <Text
              variant="bodySmall"
              style={{ color: theme.colors.error, paddingHorizontal: 14 }}
            >
              {dictationError}
            </Text>
          ) : null}
          {assistantError ? (
            <Text
              variant="bodySmall"
              style={{ color: theme.colors.error, paddingHorizontal: 14 }}
            >
              {assistantError}
            </Text>
          ) : null}

          {/* Pending actions summary bar */}
          {activeDetectedActions.length > 0 ? (
            <Pressable
              onPress={() => setReviewVisible(true)}
              style={[
                styles.pendingBar,
                { backgroundColor: `${theme.colors.primary}15` },
              ]}
            >
              <IconButton
                icon="lightning-bolt"
                size={16}
                iconColor={theme.colors.primary}
                style={{ margin: 0 }}
              />
              <Text variant="labelMedium" style={{ flex: 1 }}>
                {activeDetectedActions.length} pending action
                {activeDetectedActions.length > 1 ? "s" : ""} ·{" "}
                {approvedActionCount} approved
              </Text>
              <Text
                variant="labelSmall"
                style={{ color: theme.colors.primary }}
              >
                Review
              </Text>
            </Pressable>
          ) : null}

          <View style={styles.composerRow}>
            <IconButton
              icon={
                !hasVoiceSupport
                  ? "microphone-off"
                  : isDictating
                    ? "stop-circle-outline"
                    : "microphone-outline"
              }
              mode={isDictating ? "contained" : undefined}
              onPress={toggleDictation}
              disabled={isAsking || !hasVoiceSupport}
              size={22}
            />
            <TextInput
              mode="outlined"
              placeholder={isDictating ? "Listening…" : "Message AquaPT..."}
              value={composerText}
              onChangeText={setComposerText}
              multiline
              contentStyle={{ paddingTop: 10, paddingBottom: 10 }}
              style={styles.composerInput}
              outlineStyle={styles.composerOutline}
              editable={!isDictating}
              right={
                <TextInput.Icon
                  icon="send"
                  disabled={isAsking || !composerText.trim() || isDictating}
                  onPress={() => {
                    void askAssistant();
                  }}
                />
              }
            />
          </View>
        </View>
      </KeyboardAvoidingView>

      {/* ── DRAWER BACKDROP ──────────────────────────────────── */}
      {drawerOpen ? (
        <Animated.View
          style={[
            StyleSheet.absoluteFillObject,
            { backgroundColor: "#000" },
            backdropAnimStyle,
          ]}
        >
          <Pressable
            style={StyleSheet.absoluteFillObject}
            onPress={closeDrawer}
          />
        </Animated.View>
      ) : null}

      {/* ── DRAWER ───────────────────────────────────────────── */}
      <Animated.View style={[styles.drawerContainer, drawerAnimStyle]}>
        <ConversationDrawer
          conversations={conversations}
          activeConversationId={activeConversationId}
          onSelect={switchConversation}
          onNew={createNewConversation}
          onTogglePin={toggleConversationPin}
          onRename={renameConversation}
          onDelete={deleteConversation}
          onClose={closeDrawer}
        />
      </Animated.View>

      {/* ── BOTTOM SHEET for action editing ───────────────────── */}
      <BottomSheet
        visible={isReviewVisible}
        onDismiss={() => setReviewVisible(false)}
        title="Approve detected actions"
        actions={
          <>
            <Button onPress={() => setReviewVisible(false)}>Close</Button>
            <Button
              mode="contained"
              onPress={executeApprovedActionBatch}
              disabled={approvedActionCount === 0}
            >
              Execute approved actions
            </Button>
          </>
        }
      >
        {activeDetectedActions.length === 0 ? (
          <Text variant="bodyMedium">No actions to review yet.</Text>
        ) : null}

        {activeWarnings.length > 0 ? (
          <Text variant="bodySmall" style={styles.warningText}>
            {activeWarnings.join("\n")}
          </Text>
        ) : null}

        {activeDetectedActions.map((action) => (
          <Card key={action.id} mode="outlined" style={styles.actionCard}>
            <Card.Content>
              <View style={styles.actionHeaderRow}>
                <Text variant="titleSmall">
                  {HUMANIZED_TYPES[action.type] ?? action.type}
                </Text>
                <Chip
                  selected={action.approved}
                  onPress={() =>
                    updateAction(action.id, { approved: !action.approved })
                  }
                >
                  {action.approved ? "Approved" : "Not approved"}
                </Chip>
              </View>

              {[
                "create_task_template",
                "complete_task",
                "log_dosing",
                "log_parameters",
                "add_issue",
                "add_memo",
                "add_livestock",
                "add_asset",
                "add_consumable",
              ].includes(action.type) ? (
                <ScrollableSegmentedButtons
                  value={action.aquariumId ?? ""}
                  onValueChange={(value) =>
                    updateAction(action.id, { aquariumId: value })
                  }
                  buttons={aquariums.map((aq) => ({
                    label: aq.name,
                    value: aq.id,
                  }))}
                  style={styles.inputSpacing}
                  density="small"
                />
              ) : null}

              {action.type === "create_task_template" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Task title"
                    value={action.title ?? ""}
                    onChangeText={(v) => updateAction(action.id, { title: v })}
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.frequency ?? "weekly"}
                    onValueChange={(v) =>
                      updateAction(action.id, { frequency: v as TaskFrequency })
                    }
                    buttons={FREQUENCIES.map((i) => ({
                      label: i.label,
                      value: i.value,
                    }))}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Description (optional)"
                    value={action.description ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { description: v })
                    }
                    style={styles.inputSpacing}
                    multiline
                    numberOfLines={2}
                  />
                </>
              ) : null}

              {action.type === "complete_task" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Task title"
                    value={action.taskTitle ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { taskTitle: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Completion note (optional)"
                    value={action.note ?? ""}
                    onChangeText={(v) => updateAction(action.id, { note: v })}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "log_dosing" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Product"
                    value={action.product ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { product: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Amount (ml)"
                    value={action.amountMl ? String(action.amountMl) : ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { amountMl: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Note (optional)"
                    value={action.note ?? ""}
                    onChangeText={(v) => updateAction(action.id, { note: v })}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "log_parameters" ? (
                <View style={styles.inputSpacing}>
                  <Text variant="bodySmall">Water parameters</Text>
                  <View style={styles.row}>
                    <TextInput
                      mode="outlined"
                      label="NO3"
                      value={
                        action.parameters?.nitrate !== undefined
                          ? String(action.parameters.nitrate)
                          : ""
                      }
                      onChangeText={(v) =>
                        updateParameterField(action.id, "nitrate", v)
                      }
                      keyboardType="numeric"
                      style={styles.parameterInput}
                    />
                    <TextInput
                      mode="outlined"
                      label="pH"
                      value={
                        action.parameters?.ph !== undefined
                          ? String(action.parameters.ph)
                          : ""
                      }
                      onChangeText={(v) =>
                        updateParameterField(action.id, "ph", v)
                      }
                      keyboardType="numeric"
                      style={styles.parameterInput}
                    />
                    <TextInput
                      mode="outlined"
                      label="Temp °C"
                      value={
                        action.parameters?.temperatureC !== undefined
                          ? String(action.parameters.temperatureC)
                          : ""
                      }
                      onChangeText={(v) =>
                        updateParameterField(action.id, "temperatureC", v)
                      }
                      keyboardType="numeric"
                      style={styles.parameterInput}
                    />
                  </View>
                </View>
              ) : null}

              {action.type === "add_issue" ? (
                <TextInput
                  mode="outlined"
                  label="Issue title"
                  value={action.issueTitle ?? action.title ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { issueTitle: v })
                  }
                  style={styles.inputSpacing}
                />
              ) : null}

              {action.type === "add_memo" ? (
                <TextInput
                  mode="outlined"
                  label="Memo content"
                  value={action.memoContent ?? action.description ?? ""}
                  onChangeText={(v) =>
                    updateAction(action.id, { memoContent: v })
                  }
                  style={styles.inputSpacing}
                  multiline
                  numberOfLines={2}
                />
              ) : null}

              {action.type === "save_reminder_settings" ? (
                <>
                  <View style={styles.rowWrap}>
                    <Chip
                      selected={action.reminderEnabled === true}
                      onPress={() =>
                        updateAction(action.id, { reminderEnabled: true })
                      }
                    >
                      Reminders enabled
                    </Chip>
                    <Chip
                      selected={action.reminderEnabled === false}
                      onPress={() =>
                        updateAction(action.id, { reminderEnabled: false })
                      }
                    >
                      Reminders disabled
                    </Chip>
                  </View>
                  <TextInput
                    mode="outlined"
                    label="Reminder hour (0-23)"
                    value={
                      action.reminderHour !== undefined
                        ? String(action.reminderHour)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { reminderHour: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "add_aquarium" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Aquarium name"
                    value={action.title ?? ""}
                    onChangeText={(v) => updateAction(action.id, { title: v })}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Volume (L)"
                    value={
                      action.volumeLiters !== undefined
                        ? String(action.volumeLiters)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { volumeLiters: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Dimensions"
                    value={action.dimensions ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { dimensions: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.waterType ?? "freshwater"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        waterType: v as "freshwater" | "marine" | "brackish",
                      })
                    }
                    buttons={[
                      { label: "Freshwater", value: "freshwater" },
                      { label: "Marine", value: "marine" },
                      { label: "Brackish", value: "brackish" },
                    ]}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "edit_aquarium" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="New name (optional)"
                    value={action.title ?? ""}
                    onChangeText={(v) => updateAction(action.id, { title: v })}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="New volume (L)"
                    value={
                      action.volumeLiters !== undefined
                        ? String(action.volumeLiters)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { volumeLiters: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "add_livestock" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Livestock name"
                    value={action.livestockName ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { livestockName: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Species"
                    value={action.species ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { species: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.livestockKind ?? "fish"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        livestockKind: v as
                          | "fish"
                          | "shrimp"
                          | "snail"
                          | "coral"
                          | "plant"
                          | "other",
                      })
                    }
                    buttons={[
                      { label: "Fish", value: "fish" },
                      { label: "Shrimp", value: "shrimp" },
                      { label: "Snail", value: "snail" },
                      { label: "Coral", value: "coral" },
                      { label: "Plant", value: "plant" },
                      { label: "Other", value: "other" },
                    ]}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Quantity"
                    value={
                      action.quantity !== undefined
                        ? String(action.quantity)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { quantity: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "transfer_livestock" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Livestock name"
                    value={action.livestockName ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { livestockName: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.targetAquariumId ?? ""}
                    onValueChange={(v) =>
                      updateAction(action.id, { targetAquariumId: v })
                    }
                    buttons={aquariums.map((aq) => ({
                      label: aq.name,
                      value: aq.id,
                    }))}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "set_livestock_status" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Livestock name"
                    value={action.livestockName ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { livestockName: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.livestockStatus ?? "active"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        livestockStatus: v as "active" | "ill" | "deceased",
                      })
                    }
                    buttons={[
                      { label: "Active", value: "active" },
                      { label: "Ill", value: "ill" },
                      { label: "Deceased", value: "deceased" },
                    ]}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "add_asset" ? (
                <>
                  <ScrollableSegmentedButtons
                    value={action.assetCategory ?? "other"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        assetCategory: v as
                          | "filter"
                          | "heater"
                          | "light"
                          | "co2"
                          | "other",
                      })
                    }
                    buttons={[
                      { label: "Filter", value: "filter" },
                      { label: "Heater", value: "heater" },
                      { label: "Light", value: "light" },
                      { label: "CO2", value: "co2" },
                      { label: "Other", value: "other" },
                    ]}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Brand/model"
                    value={action.brandModel ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { brandModel: v })
                    }
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "add_consumable" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Consumable name"
                    value={action.consumableName ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { consumableName: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.consumableUnit ?? "ml"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        consumableUnit: v as "g" | "ml" | "pcs",
                      })
                    }
                    buttons={[
                      { label: "g", value: "g" },
                      { label: "ml", value: "ml" },
                      { label: "pcs", value: "pcs" },
                    ]}
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Remaining"
                    value={
                      action.remaining !== undefined
                        ? String(action.remaining)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { remaining: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "consume_consumable" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Consumable name"
                    value={action.consumableName ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { consumableName: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <TextInput
                    mode="outlined"
                    label="Amount used"
                    value={
                      action.amountUsed !== undefined
                        ? String(action.amountUsed)
                        : ""
                    }
                    onChangeText={(v) =>
                      updateAction(action.id, { amountUsed: Number(v) })
                    }
                    keyboardType="numeric"
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              {action.type === "set_issue_status" ? (
                <>
                  <TextInput
                    mode="outlined"
                    label="Issue title"
                    value={action.issueTitle ?? ""}
                    onChangeText={(v) =>
                      updateAction(action.id, { issueTitle: v })
                    }
                    style={styles.inputSpacing}
                  />
                  <ScrollableSegmentedButtons
                    value={action.issueStatus ?? "open"}
                    onValueChange={(v) =>
                      updateAction(action.id, {
                        issueStatus: v as "open" | "monitoring" | "resolved",
                      })
                    }
                    buttons={[
                      { label: "Open", value: "open" },
                      { label: "Monitoring", value: "monitoring" },
                      { label: "Resolved", value: "resolved" },
                    ]}
                    style={styles.inputSpacing}
                  />
                </>
              ) : null}

              <View style={styles.rowWrap}>
                <Chip compact>
                  Confidence: {(action.confidence * 100).toFixed(0)}%
                </Chip>
                {action.validationErrors.length > 0 ? (
                  <Chip compact>Needs edits</Chip>
                ) : (
                  <Chip compact>Ready</Chip>
                )}
              </View>

              {action.validationErrors.length > 0 ? (
                <Text variant="bodySmall" style={styles.errorText}>
                  {action.validationErrors.join("\n")}
                </Text>
              ) : null}
            </Card.Content>
          </Card>
        ))}
      </BottomSheet>
    </View>
  );
}

/* ── styles ───────────────────────────────────────────────────── */

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  main: {
    flex: 1,
  },

  /* Header */
  header: {
    flexDirection: "row",
    alignItems: "center",
    borderBottomWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 4,
    height: 52,
  },
  headerTitle: {
    flex: 1,
    textAlign: "center",
    fontWeight: "600",
  },

  /* Messages */
  messageArea: {
    flex: 1,
  },
  messageContent: {
    paddingHorizontal: 14,
    paddingTop: 12,
    gap: 14,
  },
  messageRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  messageRowUser: {
    justifyContent: "flex-end",
  },
  messageRowSystem: {
    justifyContent: "center",
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  bubble: {
    borderRadius: 18,
    paddingVertical: 10,
    paddingHorizontal: 14,
    maxWidth: "82%",
  },
  inlineActions: {
    marginTop: 4,
    gap: 2,
  },
  streamingLabel: {
    marginTop: 6,
    opacity: 0.6,
  },
  telemetryWrap: {
    marginTop: 8,
    borderRadius: 10,
    padding: 8,
    backgroundColor: "rgba(127,127,127,0.12)",
    gap: 1,
  },
  telemetryLabel: {
    fontWeight: "700",
    opacity: 0.8,
    marginBottom: 2,
  },
  telemetryText: {
    opacity: 0.75,
  },
  memoryActionsRow: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  failedMessageActionsRow: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  failedMessageError: {
    marginTop: 4,
    opacity: 0.75,
  },

  /* Composer */
  composer: {
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 8,
    paddingTop: 6,
  },
  composerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
  },
  composerInput: {
    flex: 1,
    maxHeight: 100,
    fontSize: 15,
  },
  composerOutline: {
    borderRadius: 22,
  },
  dictationPreview: {
    paddingHorizontal: 14,
    paddingBottom: 4,
    fontStyle: "italic",
    flex: 1,
  },
  dictationLiveBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginHorizontal: 4,
    marginBottom: 4,
    borderRadius: 10,
  },
  pendingBar: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 10,
    paddingHorizontal: 6,
    paddingVertical: 6,
    marginHorizontal: 4,
    marginBottom: 6,
    gap: 4,
  },

  /* Drawer */
  drawerContainer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    left: 0,
    width: DRAWER_WIDTH,
  },

  /* Bottom sheet action editing (kept from original) */
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 10,
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8,
  },
  warningText: {
    marginTop: 10,
    color: "#8a6d00",
  },
  errorText: {
    marginTop: 10,
    color: "#b00020",
  },
  actionCard: {
    marginTop: 10,
    borderRadius: 16,
  },
  actionHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  inputSpacing: {
    marginTop: 10,
  },
  parameterInput: {
    minWidth: 88,
    flex: 1,
  },
});
