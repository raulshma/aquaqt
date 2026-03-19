import * as Clipboard from "expo-clipboard";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { GestureResponderEvent } from "react-native";
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
  Chip,
  IconButton,
  Menu,
  Snackbar,
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
  ConversationDrawer,
} from "@/components/assistant/conversation-drawer";
import { ActionReviewForm } from "@/components/assistant/action-review-form";
import { ChatMessage } from "@/components/assistant/chat-message";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { useAquapt } from "@/context/aquapt-context";
import { askAssistantWithTaskDetection } from "@/services/assistant-orchestrator";
import {
  queryAssistantMemorySnippets,
  rememberAssistantTurn,
  rememberManualAssistantSnippet,
  forgetManualAssistantSnippet,
} from "@/services/assistant-memory";
import { isDictationSupported, startPressHoldDictation } from "@/services/voice";
import type { AssistantChatMessage, AssistantDetectedAction } from "@/types/assistant";

import { useAssistantConversations } from "@/hooks/useAssistantConversations";
import { useAssistantActions } from "@/hooks/useAssistantActions";
import {
  DRAWER_WIDTH,
  TAB_BAR_HEIGHT,
  QUICK_PROMPT_SUGGESTIONS,
  nowId,
} from "@/utils/assistant-constants";

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
  } = useAquapt();

  const {
    conversations,
    activeConversationId,
    activeConversation,
    createNewConversation,
    switchConversation,
    deleteConversation,
    toggleConversationPin,
    renameConversation,
    updateConversation,
  } = useAssistantConversations();

  const {
    activeDetectedActions,
    activeWarnings,
    approvedActionCount,
    approvedSelectionCount,
    approvedInvalidActionCount,
    updateAction,
    updateParameterField,
    executeApprovedActionBatch,
  } = useAssistantActions({
    activeConversation,
    updateConversation,
  });

  const [composerText, setComposerText] = useState("");
  const [isAsking, setAsking] = useState(false);
  const [assistantError, setAssistantError] = useState<string | null>(null);
  const [activeStreamingMessageId, setActiveStreamingMessageId] = useState<
    string | null
  >(null);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);
  const [messageMenuState, setMessageMenuState] = useState<{
    x: number;
    y: number;
    messageId: string;
  } | null>(null);
  const [isReviewVisible, setReviewVisible] = useState(false);
  const [isNearBottom, setIsNearBottom] = useState(true);
  const [rememberedAssistantMessageIds, setRememberedAssistantMessageIds] =
    useState<Record<string, boolean>>({});
  const [memoryActionBusyMessageIds, setMemoryActionBusyMessageIds] = useState<
    Record<string, boolean>
  >({});
  const assistantAbortControllerRef = useRef<AbortController | null>(null);

  const [dictationPreview, setDictationPreview] = useState("");
  const [dictationError, setDictationError] = useState<string | null>(null);
  const [isDictating, setDictating] = useState(false);
  const dictationSessionRef = useRef<Awaited<
    ReturnType<typeof startPressHoldDictation>
  > | null>(null);
  const hasVoiceSupport = isDictationSupported();

  const drawerProgress = useSharedValue(0);
  const drawerAnimStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: (drawerProgress.value - 1) * DRAWER_WIDTH }],
  }));
  const backdropAnimStyle = useAnimatedStyle(() => ({
    opacity: drawerProgress.value * 0.45,
  }));

  const [drawerOpen, setDrawerOpen] = useState(false);

  const openDrawer = useCallback(() => {
    setDrawerOpen(true);
    drawerProgress.value = withTiming(1, { duration: 250 });
  }, [drawerProgress]);

  const closeDrawer = useCallback(() => {
    drawerProgress.value = withTiming(0, { duration: 250 });
    setTimeout(() => setDrawerOpen(false), 280);
  }, [drawerProgress]);

  const scrollRef = useRef<ScrollView>(null);
  const scrollToBottom = useCallback(() => {
    setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 80);
  }, []);

  const showSnackbar = useCallback((message: string) => {
    setSnackbarMessage(message);
  }, []);

  const openMessageMenu = useCallback(
    (message: AssistantChatMessage, event: GestureResponderEvent) => {
      setMessageMenuState({
        x: event.nativeEvent.pageX,
        y: event.nativeEvent.pageY,
        messageId: message.id,
      });
    },
    [],
  );

  const closeMessageMenu = useCallback(() => {
    setMessageMenuState(null);
  }, []);

  const activeMessages = useMemo(
    () => activeConversation?.messages ?? [],
    [activeConversation?.messages],
  );

  const selectedMenuMessage = useMemo(
    () =>
      messageMenuState
        ? (activeMessages.find(
            (message) => message.id === messageMenuState.messageId,
          ) ?? null)
        : null,
    [activeMessages, messageMenuState],
  );

  const assistantContext = useMemo(
    () => ({
      userLocale: {
        locale: settings.defaultLocale,
        timezone: settings.defaultTimezone,
        country: settings.defaultCountryName,
        currency: settings.defaultCurrency,
      },
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
      settings.defaultCountryName,
      settings.defaultCurrency,
      settings.defaultLocale,
      settings.defaultTimezone,
      taskExecutions,
      taskTemplates,
    ],
  );

  const withValidation = useCallback(
    (next: AssistantDetectedAction[]) =>
      next.map((action) => action),
    [],
  );

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
      if (!activeConversation) return;
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
          signal: new AbortController().signal,
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
          apiKey: settings.openRouterApiKey,
          model: settings.assistantMemoryModel || settings.aiModel,
          enabled: settings.assistantMemoryEnabled,
        });

        scrollToBottom();
      } catch (error) {
        const isAborted =
          error instanceof DOMException
            ? error.name === "AbortError"
            : error instanceof Error && error.name === "AbortError";

        if (isAborted) {
          showSnackbar("Generation stopped.");
          return;
        }

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
      settings.assistantMemoryModel,
      settings.assistantMemoryEnabled,
      settings.openRouterApiKey,
      showSnackbar,
      scrollToBottom,
      updateConversation,
      withValidation,
    ],
  );

  const copyMessageContent = useCallback(
    async (message: AssistantChatMessage) => {
      const content = message.content.trim();
      if (!content) return;

      try {
        await Clipboard.setStringAsync(content);
        showSnackbar("Message copied.");
      } catch {
        setAssistantError("Could not copy the message right now.");
      }
    },
    [showSnackbar],
  );

  const askAssistant = useCallback(async () => {
    await askAssistantForPrompt({ prompt: composerText });
  }, [askAssistantForPrompt, composerText]);

  const retryFailedMessage = useCallback(
    (message: AssistantChatMessage) => {
      if (message.role !== "user") return;

      void askAssistantForPrompt({
        prompt: message.content,
        retryMessageId: message.id,
      });
    },
    [askAssistantForPrompt],
  );

  const reuseUserMessage = useCallback((message: AssistantChatMessage) => {
    if (message.role !== "user") return;
    setComposerText(message.content);
  }, []);

  const regenerateAssistantReply = useCallback(
    (assistantMessage: AssistantChatMessage) => {
      if (assistantMessage.role !== "assistant" || isAsking) return;

      const messageIndex = activeMessages.findIndex(
        (message) => message.id === assistantMessage.id,
      );

      if (messageIndex <= 0) return;

      const previousUserMessage = [...activeMessages]
        .slice(0, messageIndex)
        .reverse()
        .find((message) => message.role === "user");

      if (!previousUserMessage) return;

      void askAssistantForPrompt({
        prompt: previousUserMessage.content,
        retryMessageId: previousUserMessage.id,
      });
    },
    [activeMessages, askAssistantForPrompt, isAsking],
  );

  const applyQuickPrompt = useCallback((prompt: string) => {
    setComposerText(prompt);
  }, []);

  const handleMessageAreaScroll = useCallback(
    (event: {
      nativeEvent: {
        contentOffset: { y: number };
        layoutMeasurement: { height: number };
        contentSize: { height: number };
      };
    }) => {
      const { contentOffset, layoutMeasurement, contentSize } =
        event.nativeEvent;
      const distanceFromBottom =
        contentSize.height - (contentOffset.y + layoutMeasurement.height);
      setIsNearBottom(distanceFromBottom < 120);
    },
    [],
  );

  const handleToggleApproval = useCallback(
    (actionId: string, approved: boolean) => {
      updateAction(actionId, { approved });
    },
    [updateAction],
  );

  const rememberAssistantMessage = useCallback(
    async (message: AssistantChatMessage) => {
      const content = message.content.trim();
      if (!content) return;

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

  const handleActionUpdate = useCallback(
    (actionId: string, updates: Record<string, unknown>) => {
      updateAction(actionId, updates);
    },
    [updateAction],
  );

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      <KeyboardAvoidingView
        style={[
          styles.main,
          {
            paddingTop: insets.top + 12,
            paddingHorizontal: 12,
          },
        ]}
        behavior="padding"
        keyboardVerticalOffset={-TAB_BAR_HEIGHT}
      >
        <View
          style={[styles.header, { backgroundColor: theme.colors.surface }]}
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

        <ScrollView
          ref={scrollRef}
          style={styles.messageArea}
          contentContainerStyle={[
            styles.messageContent,
            { paddingBottom: TAB_BAR_HEIGHT + 140 },
          ]}
          showsVerticalScrollIndicator={false}
          onScroll={handleMessageAreaScroll}
          scrollEventThrottle={16}
          onContentSizeChange={scrollToBottom}
        >
          {activeMessages.map((message) => {
            const linkedActions = (message.detectedActionIds ?? [])
              .map((aid) => activeDetectedActions.find((a) => a.id === aid))
              .filter(Boolean) as AssistantDetectedAction[];

            return (
              <ChatMessage
                key={message.id}
                message={message}
                isStreaming={activeStreamingMessageId === message.id}
                linkedActions={linkedActions}
                activeDetectedActions={activeDetectedActions}
                onToggleApproval={handleToggleApproval}
                onReviewActions={() => setReviewVisible(true)}
                onCopyMessage={() => copyMessageContent(message)}
                onReuseMessage={
                  message.role === "user"
                    ? () => reuseUserMessage(message)
                    : undefined
                }
                onRetryFailed={
                  message.role === "user" && message.requestFailed
                    ? () => retryFailedMessage(message)
                    : undefined
                }
                onRegenerate={
                  message.role === "assistant"
                    ? () => regenerateAssistantReply(message)
                    : undefined
                }
                onRememberMessage={
                  message.role === "assistant"
                    ? () => rememberAssistantMessage(message)
                    : undefined
                }
                onForgetMemory={
                  message.role === "assistant"
                    ? () => forgetAssistantMessageMemory(message)
                    : undefined
                }
                onOpenMessageMenu={(event) => openMessageMenu(message, event)}
                isRemembered={!!rememberedAssistantMessageIds[message.id]}
                isMemoryBusy={!!memoryActionBusyMessageIds[message.id]}
                isAsking={isAsking}
              />
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

        <View
          style={[
            styles.composer,
            {
              backgroundColor: theme.colors.surface,
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

          {composerText.trim().length === 0 && !isAsking && !isDictating ? (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.quickPromptRow}
              style={styles.quickPromptScroller}
            >
              {QUICK_PROMPT_SUGGESTIONS.map((prompt) => (
                <Chip
                  key={prompt}
                  compact
                  mode="outlined"
                  onPress={() => applyQuickPrompt(prompt)}
                >
                  {prompt}
                </Chip>
              ))}
            </ScrollView>
          ) : null}

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
            {composerText.trim().length > 0 ? (
              <IconButton
                icon="backspace-outline"
                onPress={() => setComposerText("")}
                disabled={isAsking || isDictating}
                size={20}
              />
            ) : null}
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

      <Menu
        visible={!!messageMenuState && !!selectedMenuMessage}
        onDismiss={closeMessageMenu}
        anchor={
          messageMenuState
            ? { x: messageMenuState.x, y: messageMenuState.y }
            : { x: 0, y: 0 }
        }
      >
        {selectedMenuMessage?.content.trim() ? (
          <Menu.Item
            leadingIcon="content-copy"
            title="Copy"
            onPress={() => {
              const message = selectedMenuMessage;
              closeMessageMenu();
              if (message) {
                void copyMessageContent(message);
              }
            }}
          />
        ) : null}

        {selectedMenuMessage?.role === "user" ? (
          <>
            <Menu.Item
              leadingIcon="message-reply-text-outline"
              title="Reuse in composer"
              onPress={() => {
                const message = selectedMenuMessage;
                closeMessageMenu();
                if (message) {
                  reuseUserMessage(message);
                }
              }}
            />
            {selectedMenuMessage.requestFailed ? (
              <Menu.Item
                leadingIcon="refresh"
                title="Retry send"
                disabled={isAsking}
                onPress={() => {
                  const message = selectedMenuMessage;
                  closeMessageMenu();
                  if (message) {
                    retryFailedMessage(message);
                  }
                }}
              />
            ) : null}
          </>
        ) : null}

        {selectedMenuMessage?.role === "assistant" ? (
          <>
            {activeStreamingMessageId === selectedMenuMessage.id ? (
              <Menu.Item
                leadingIcon="stop-circle-outline"
                title="Stop generating"
                onPress={() => {
                  closeMessageMenu();
                  assistantAbortControllerRef.current?.abort();
                }}
              />
            ) : null}
            <Menu.Item
              leadingIcon="refresh"
              title="Regenerate reply"
              disabled={isAsking}
              onPress={() => {
                const message = selectedMenuMessage;
                closeMessageMenu();
                if (message) {
                  regenerateAssistantReply(message);
                }
              }}
            />
            <Menu.Item
              leadingIcon={
                rememberedAssistantMessageIds[selectedMenuMessage.id]
                  ? "bookmark-remove-outline"
                  : "bookmark-outline"
              }
              title={
                rememberedAssistantMessageIds[selectedMenuMessage.id]
                  ? "Forget saved memory"
                  : "Remember this reply"
              }
              disabled={!!memoryActionBusyMessageIds[selectedMenuMessage.id]}
              onPress={() => {
                const message = selectedMenuMessage;
                closeMessageMenu();
                if (!message) return;

                if (rememberedAssistantMessageIds[message.id]) {
                  void forgetAssistantMessageMemory(message);
                  return;
                }

                void rememberAssistantMessage(message);
              }}
            />
          </>
        ) : null}
      </Menu>

      <Snackbar
        visible={!!snackbarMessage}
        onDismiss={() => setSnackbarMessage(null)}
        duration={2200}
        action={{
          label: "Dismiss",
          onPress: () => setSnackbarMessage(null),
        }}
      >
        {snackbarMessage}
      </Snackbar>

      {!isNearBottom && activeMessages.length > 3 ? (
        <IconButton
          icon="chevron-double-down"
          mode="contained-tonal"
          containerColor={theme.colors.secondaryContainer}
          iconColor={theme.colors.onSecondaryContainer}
          style={[
            styles.jumpToLatestButton,
            { bottom: TAB_BAR_HEIGHT + 110 + insets.bottom },
          ]}
          onPress={scrollToBottom}
        />
      ) : null}

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
              disabled={approvedSelectionCount === 0}
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

        {approvedSelectionCount > 0 ? (
          <Text variant="bodySmall" style={styles.helperText}>
            {approvedActionCount} ready to execute
            {approvedInvalidActionCount > 0
              ? ` · ${approvedInvalidActionCount} approved action${approvedInvalidActionCount > 1 ? "s" : ""} still need edits`
              : ""}
          </Text>
        ) : (
          <Text variant="bodySmall" style={styles.helperText}>
            Approve at least one action to execute.
          </Text>
        )}

        <ActionReviewForm
          actions={activeDetectedActions}
          updateAction={handleActionUpdate}
          updateParameterField={updateParameterField}
        />
      </BottomSheet>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  main: {
    flex: 1,
    paddingBottom: 8,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 8,
    height: 56,
    borderRadius: 24,
  },
  headerTitle: {
    flex: 1,
    textAlign: "center",
    fontWeight: "600",
  },
  messageArea: {
    flex: 1,
    marginTop: 10,
  },
  messageContent: {
    paddingHorizontal: 6,
    paddingTop: 12,
    gap: 14,
  },
  messageRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  composer: {
    paddingHorizontal: 8,
    paddingTop: 6,
    borderRadius: 28,
    marginTop: 8,
  },
  quickPromptScroller: {
    marginHorizontal: 4,
    marginBottom: 6,
  },
  quickPromptRow: {
    gap: 6,
    paddingHorizontal: 2,
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
  drawerContainer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    left: 0,
    width: DRAWER_WIDTH,
  },
  jumpToLatestButton: {
    position: "absolute",
    right: 12,
    zIndex: 5,
  },
  warningText: {
    marginTop: 10,
    color: "#8a6d00",
  },
  helperText: {
    marginTop: 8,
    opacity: 0.75,
  },
});
